#!/usr/bin/env python3
import errno
import http.server
import json
import os
import secrets
import shutil
import urllib.parse
import zipfile

import autoscale
import config
import docker


def normalize_restart_mode(value):
    mode = str(value or "").strip().lower()
    return mode if mode in config.VALID_RESTART_MODES else ""


def parse_requested_services(value):
    if value is None:
        return True, None

    raw_values = []
    if isinstance(value, str):
        raw_values = config.clean_csv(value, "")
    elif isinstance(value, (list, tuple, set)):
        for item in value:
            if not isinstance(item, str):
                return False, None
            raw_values.append(item)
    else:
        return False, None

    requested = []
    seen = set()
    for item in raw_values:
        normalized = (item or "").strip().lower()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        requested.append(normalized)
    return True, requested


def merge_requested_services(primary, secondary):
    if primary is None and secondary is None:
        return None
    merged = []
    seen = set()
    for values in (primary, secondary):
        if values is None:
            continue
        for item in values:
            normalized = (item or "").strip().lower()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            merged.append(normalized)
    return merged


def parse_requested_server_ids(value):
    return parse_requested_services(value)


def merge_requested_server_ids(primary, secondary):
    return merge_requested_services(primary, secondary)


def is_restart_target_service(service):
    normalized = (service or "").strip().lower()
    if not normalized or normalized in config.EXCLUDED_SERVICES:
        return False
    if not config.INCLUDE_SERVICES and not config.INCLUDE_PREFIXES:
        return True
    if "*" in config.INCLUDE_SERVICES:
        return True
    if normalized in config.INCLUDE_SERVICES:
        return True
    for prefix in config.INCLUDE_PREFIXES:
        normalized_prefix = (prefix or "").strip().lower()
        if not normalized_prefix:
            continue
        if normalized.startswith(normalized_prefix) or normalized.endswith(normalized_prefix):
            return True
    return False


def ordered_restart_targets(targets):
    order = {(service or "").strip().lower(): index for index, service in enumerate(config.RESTART_SERVICE_ORDER)}
    unknown_rank = len(order)
    return sorted(
        targets,
        key=lambda item: (
            order.get(
                (item.get("service", "") or "").strip().lower(),
                order.get(autoscale.classify_service_kind(item.get("service", "")), unknown_rank),
            ),
            item.get("service", ""),
            item.get("name", ""),
        ),
    )


def ordered_target_services(targets):
    ordered = []
    seen = set()
    for item in targets:
        service = (item.get("service") or "").strip()
        if not service or service in seen:
            continue
        seen.add(service)
        ordered.append(service)
    return ordered


def resolve_restart_targets(containers, requested_services=None):
    base_targets = [item for item in containers if is_restart_target_service(item.get("service", ""))]
    ordered = ordered_restart_targets(base_targets)
    if requested_services is None:
        return ordered
    requested = set((service or "").strip().lower() for service in requested_services if service)
    if not requested:
        return ordered
    requested_kinds = {item for item in requested if item in {"hub", "game"}}
    filtered = []
    for item in ordered:
        service = (item.get("service", "") or "").strip().lower()
        if service in requested:
            filtered.append(item)
            continue
        if autoscale.classify_service_kind(service) in requested_kinds:
            filtered.append(item)
    return filtered


def validate_jar(path):
    if not os.path.isfile(path):
        return False, "file missing"
    try:
        size = os.path.getsize(path)
    except OSError as exc:
        return False, f"size check failed: {exc}"
    if size <= 0:
        return False, "file is empty"
    try:
        with zipfile.ZipFile(path, "r") as jar_file:
            bad_entry = jar_file.testzip()
            if bad_entry is not None:
                return False, f"corrupt entry: {bad_entry}"
    except zipfile.BadZipFile as exc:
        return False, f"bad zip/jar: {exc}"
    except Exception as exc:
        return False, f"jar validation failed: {exc}"
    return True, "ok"


def validate_rollout_plugins():
    if not config.ROLLOUT_PLUGIN_DIR:
        raise RuntimeError("ROLLOUT_PLUGIN_DIR is required")

    os.makedirs(config.ROLLOUT_PLUGIN_DIR, exist_ok=True)
    result = {
        "pluginDir": config.ROLLOUT_PLUGIN_DIR,
        "validated": [],
    }
    for file_name in config.REQUIRED_PLUGIN_FILENAMES:
        source_path = os.path.join(config.ROLLOUT_PLUGIN_DIR, file_name)
        valid, detail = validate_jar(source_path)
        if not valid:
            raise RuntimeError(f"{file_name} failed validation: {detail}")
        result["validated"].append(file_name)
    return result


def sync_source_rollout_plugins():
    if not config.ROLLOUT_PLUGIN_DIR:
        raise RuntimeError("ROLLOUT_PLUGIN_DIR is required")

    os.makedirs(config.ROLLOUT_PLUGIN_DIR, exist_ok=True)
    result = {
        "sourceDir": config.ROLLOUT_PLUGIN_SOURCE_DIR,
        "pluginDir": config.ROLLOUT_PLUGIN_DIR,
        "copied": [],
        "missingSource": [],
    }
    if not config.ROLLOUT_PLUGIN_SOURCE_DIR:
        return result

    for file_name in config.REQUIRED_PLUGIN_FILENAMES:
        source_path = os.path.join(config.ROLLOUT_PLUGIN_SOURCE_DIR, file_name)
        if not os.path.isfile(source_path):
            result["missingSource"].append(file_name)
            continue

        destination_path = os.path.join(config.ROLLOUT_PLUGIN_DIR, file_name)
        try:
            shutil.copy2(source_path, destination_path)
        except OSError as exc:
            raise RuntimeError(f"failed to copy {file_name} from plugin source: {exc}")
        result["copied"].append(file_name)

    return result


def docker_daemon_ready():
    try:
        status, data = docker.docker_request("GET", "/_ping")
        body = data.decode("utf-8", errors="replace").strip().upper()
        if status != 200 or body != "OK":
            return False, f"unexpected docker ping response ({status}, body={body})"
        return True, "ok"
    except Exception as exc:
        return False, str(exc)


def mongo_ready():
    try:
        db = autoscale.get_mongo_database()
        response = db.command("ping")
        if autoscale.to_int(response.get("ok"), 0) != 1:
            return False, f"unexpected mongo ping response ({response})"
        return True, "ok"
    except Exception as exc:
        return False, str(exc)


def compose_project_ready():
    try:
        compose_project = docker.detect_compose_project()
        if not compose_project:
            return False, "", "compose project not detected"
        docker.list_project_containers(compose_project, include_all=False)
        return True, compose_project, "ok"
    except Exception as exc:
        return False, "", str(exc)


def build_readiness_payload():
    payload = {"ok": True, "checks": {}}

    docker_ok, docker_detail = docker_daemon_ready()
    payload["checks"]["docker"] = {"ok": docker_ok, "detail": docker_detail}
    payload["ok"] = payload["ok"] and docker_ok

    compose_ok, compose_project, compose_detail = compose_project_ready()
    payload["checks"]["composeProject"] = {
        "ok": compose_ok,
        "project": compose_project,
        "detail": compose_detail,
    }
    payload["ok"] = payload["ok"] and compose_ok

    mongo_ok, mongo_detail = mongo_ready()
    payload["checks"]["mongo"] = {"ok": mongo_ok, "detail": mongo_detail}
    payload["ok"] = payload["ok"] and mongo_ok

    return payload


class RolloutHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        message = fmt % args
        print(f"{self.address_string()} - {message}")

    @staticmethod
    def _is_client_disconnect(exc):
        if isinstance(exc, (BrokenPipeError, ConnectionResetError)):
            return True
        if isinstance(exc, OSError):
            return exc.errno in {errno.EPIPE, errno.ECONNRESET}
        return False

    def json_response(self, status_code, payload):
        body = json.dumps(payload).encode("utf-8")
        try:
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return True
        except Exception as exc:
            if self._is_client_disconnect(exc):
                path = urllib.parse.urlparse(self.path).path
                print(f"{self.address_string()} - client disconnected before response was sent ({path})")
                return False
            raise

    def parse_json_body(self):
        length_raw = self.headers.get("Content-Length", "0")
        try:
            length = max(0, int(length_raw))
        except ValueError:
            length = 0
        raw = self.rfile.read(length) if length > 0 else b"{}"
        text = raw.decode("utf-8", errors="replace").strip() or "{}"
        value = json.loads(text)
        if not isinstance(value, dict):
            raise ValueError("json body must be an object")
        return value

    def authorized(self):
        if not config.ROLLOUT_TOKEN:
            return True
        provided = (self.headers.get("X-Rollout-Token") or "").strip()
        return bool(provided) and secrets.compare_digest(provided, config.ROLLOUT_TOKEN)

    def do_GET(self):
        path = urllib.parse.urlparse(self.path).path
        if path == "/healthz":
            self.json_response(
                200,
                {
                    "ok": True,
                    "autoscaleEnabled": config.AUTOSCALE_ENABLED,
                },
            )
            return
        if path == "/readyz":
            payload = build_readiness_payload()
            status = 200 if payload.get("ok") else 503
            self.json_response(status, payload)
            return
        if path == "/autoscale/state":
            if not self.authorized():
                self.json_response(401, {"error": "unauthorized"})
                return
            try:
                db = autoscale.get_mongo_database()
                collection = autoscale.autoscale_collection(db)
                metrics = list(collection.find({"docType": "metrics"}))
                state = list(collection.find({"docType": "state"}))
                players = list(collection.find({"docType": "players"}))
                drains = list(collection.find({"docType": "drain", "active": True}))
                self.json_response(
                    200,
                    {
                        "timestamp": autoscale.now_iso(),
                        "metrics": metrics,
                        "state": state,
                        "players": players,
                        "activeDrains": drains,
                    },
                )
            except Exception as exc:
                self.json_response(500, {"error": str(exc)})
            return
        self.json_response(404, {"error": "not found"})

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        if path == "/restart":
            if not self.authorized():
                self.json_response(401, {"error": "unauthorized"})
                return
            try:
                payload = self.parse_json_body()
            except Exception as exc:
                self.json_response(400, {"error": f"invalid json body: {exc}"})
                return

            request_source = str(payload.get("source") or "").strip()
            request_reason = str(payload.get("reason") or "").strip()
            request_context = {}
            if request_source:
                request_context["source"] = request_source
            if request_reason:
                request_context["reason"] = request_reason
            if request_context:
                print(
                    "restart request context "
                    + " ".join(f"{key}={value}" for key, value in request_context.items())
                )

            requested_mode = normalize_restart_mode(payload.get("mode"))
            if payload.get("mode") is not None and not requested_mode:
                self.json_response(400, {"error": "mode must be one of: restart, recreate, rebuild"})
                return

            services_valid, requested_services = parse_requested_services(payload.get("services"))
            if not services_valid:
                self.json_response(400, {"error": "services must be a string or list of strings"})
                return
            service_valid, single_service = parse_requested_services(payload.get("service"))
            if not service_valid:
                self.json_response(400, {"error": "service must be a string"})
                return
            requested_services = merge_requested_services(requested_services, single_service)
            server_ids_valid, requested_server_ids = parse_requested_server_ids(payload.get("serverIds"))
            if not server_ids_valid:
                self.json_response(400, {"error": "serverIds must be a string or list of strings"})
                return
            server_id_valid, single_server_id = parse_requested_server_ids(payload.get("serverId"))
            if not server_id_valid:
                self.json_response(400, {"error": "serverId must be a string"})
                return
            legacy_server_valid, legacy_server_id = parse_requested_server_ids(payload.get("server"))
            if not legacy_server_valid:
                self.json_response(400, {"error": "server must be a string"})
                return
            requested_server_ids = merge_requested_server_ids(requested_server_ids, single_server_id)
            requested_server_ids = merge_requested_server_ids(requested_server_ids, legacy_server_id)
            if requested_server_ids is not None and not requested_server_ids:
                requested_server_ids = None

            restart_mode = requested_mode or config.ROLLOUT_RESTART_MODE
            if payload.get("rebuild") is True:
                restart_mode = "rebuild"
            elif payload.get("recreate") is True and restart_mode == "restart":
                restart_mode = "recreate"
            if requested_server_ids is not None and restart_mode != "restart":
                self.json_response(
                    400,
                    {
                        "error": "server targeting requires restart mode",
                        "requestedServerIds": requested_server_ids,
                        "restartMode": restart_mode,
                    },
                )
                return
            timestamp = autoscale.now_iso()
            try:
                compose_project = docker.detect_compose_project()
                if not compose_project:
                    raise RuntimeError("compose project not detected; set COMPOSE_PROJECT")
                try:
                    plugin_sync = sync_source_rollout_plugins()
                    plugin_validation = validate_rollout_plugins()
                except Exception as exc:
                    self.json_response(
                        409,
                        {
                            "timestamp": timestamp,
                            "error": "plugin sync/validation failed",
                            "detail": str(exc),
                            "pluginDir": config.ROLLOUT_PLUGIN_DIR,
                            "pluginSourceDir": config.ROLLOUT_PLUGIN_SOURCE_DIR,
                        },
                    )
                    return
                db = autoscale.get_mongo_database() if requested_server_ids is not None else None
                project_containers = docker.list_project_containers(compose_project, include_all=True)
                available_targets = resolve_restart_targets(project_containers)
                targets = resolve_restart_targets(project_containers, requested_services)
                if requested_services is not None and not targets:
                    self.json_response(
                        400,
                        {
                            "error": "requested services did not match any restart targets",
                            "requestedServices": requested_services,
                            "availableServices": ordered_target_services(available_targets),
                        },
                    )
                    return
                target_server_map = {}
                if requested_server_ids is not None:
                    available_server_map = autoscale.map_targets_to_server_ids(db, available_targets)
                    target_server_map = autoscale.map_targets_to_server_ids(db, targets)
                    targets = autoscale.filter_targets_by_server_ids(targets, target_server_map, requested_server_ids)
                    if not targets:
                        self.json_response(
                            400,
                            {
                                "error": "requested servers did not match any restart targets",
                                "requestedServerIds": requested_server_ids,
                                "requestedServices": requested_services,
                                "availableServices": ordered_target_services(available_targets),
                                "availableServerIds": sorted(set(available_server_map.values())),
                            },
                        )
                        return
                    selected_ids = {target.get("id") for target in targets}
                    target_server_map = {
                        container_id: server_id
                        for container_id, server_id in target_server_map.items()
                        if container_id in selected_ids
                    }
                target_services = ordered_target_services(targets)
                scaled_to_minimum = []
                for service in target_services:
                    if autoscale.classify_service_kind(service) not in {"hub", "game"}:
                        continue
                    current_active = len(
                        [
                            item
                            for item in project_containers
                            if item.get("service") == service and autoscale.is_container_active(item)
                        ]
                    )
                    desired_replicas = autoscale.desired_service_replicas(project_containers, service)
                    if current_active >= desired_replicas:
                        continue

                    docker.run_compose_up_service(
                        compose_project,
                        service_name=service,
                        replicas=desired_replicas,
                        build=False,
                        force_recreate=False,
                    )
                    ready, ready_detail = autoscale.wait_for_service_ready(
                        compose_project,
                        service_name=service,
                        expected_count=desired_replicas,
                        timeout_seconds=config.RESTART_HEALTH_WAIT_SECONDS,
                    )
                    project_containers = docker.list_project_containers(compose_project, include_all=True)
                    if not ready:
                        raise RuntimeError(
                            f"failed to ensure minimum replicas for {service}: {ready_detail}"
                        )
                    scaled_to_minimum.append(
                        {
                            "service": service,
                            "from": current_active,
                            "to": desired_replicas,
                            "detail": ready_detail,
                        }
                    )

                targets = resolve_restart_targets(project_containers, requested_services)
                if requested_server_ids is not None:
                    target_server_map = autoscale.map_targets_to_server_ids(db, targets)
                    targets = autoscale.filter_targets_by_server_ids(targets, target_server_map, requested_server_ids)
                    if not targets:
                        self.json_response(
                            409,
                            {
                                "error": "requested server targets became unavailable before restart",
                                "requestedServerIds": requested_server_ids,
                                "availableServerIds": sorted(set(target_server_map.values())),
                            },
                        )
                        return
                    selected_ids = {target.get("id") for target in targets}
                    target_server_map = {
                        container_id: server_id
                        for container_id, server_id in target_server_map.items()
                        if container_id in selected_ids
                    }
                target_services = ordered_target_services(targets)
                restarted = []
                failed = []
                if restart_mode == "restart":
                    for target in targets:
                        service = target.get("service", "")
                        if service == "velocity":
                            ready_hub = autoscale.count_ready_kind_containers(compose_project, "hub")
                            ready_game = autoscale.count_ready_kind_containers(compose_project, "game")
                            if ready_hub < 1 or ready_game < 1:
                                failed.append(
                                    {
                                        "name": target["name"],
                                        "reason": (
                                            "skipped velocity restart; requires at least one healthy hub and game "
                                            f"(hub={ready_hub}, game={ready_game})"
                                        ),
                                    }
                                )
                                continue
                        ok, detail = docker.restart_container(target["id"])
                        if ok:
                            ready, ready_detail = docker.wait_for_container_ready(
                                target["id"], config.RESTART_HEALTH_WAIT_SECONDS
                            )
                            if ready:
                                restarted.append(target["name"])
                            else:
                                failed.append({"name": target["name"], "reason": f"readiness check failed: {ready_detail}"})
                        else:
                            failed.append({"name": target["name"], "reason": detail})
                else:
                    build_images = restart_mode == "rebuild"
                    for service in target_services:
                        if service == "velocity":
                            ready_hub = autoscale.count_ready_kind_containers(compose_project, "hub")
                            ready_game = autoscale.count_ready_kind_containers(compose_project, "game")
                            if ready_hub < 1 or ready_game < 1:
                                failed.append(
                                    {
                                        "name": service,
                                        "reason": (
                                            "skipped velocity restart; requires at least one healthy hub and game "
                                            f"(hub={ready_hub}, game={ready_game})"
                                        ),
                                    }
                                )
                                continue

                        replicas = autoscale.desired_service_replicas(project_containers, service)
                        try:
                            docker.run_compose_up_service(
                                compose_project,
                                service_name=service,
                                replicas=replicas,
                                build=build_images,
                                force_recreate=True,
                            )
                        except Exception as exc:
                            failed.append({"name": service, "reason": str(exc)})
                            continue

                        ready, ready_detail = autoscale.wait_for_service_ready(
                            compose_project,
                            service_name=service,
                            expected_count=replicas,
                            timeout_seconds=config.RESTART_HEALTH_WAIT_SECONDS,
                        )
                        if ready:
                            restarted.append(service)
                        else:
                            failed.append({"name": service, "reason": f"readiness check failed: {ready_detail}"})

                        project_containers = docker.list_project_containers(compose_project, include_all=True)

                status_code = 200 if not failed else 500
                target_server_ids = sorted(
                    {
                        target_server_map.get(target.get("id"), "")
                        for target in targets
                        if target_server_map.get(target.get("id"), "")
                    }
                )
                self.json_response(
                    status_code,
                    {
                        "timestamp": timestamp,
                        "composeProject": compose_project,
                        "targetCount": len(targets),
                        "orderedTargets": [target["name"] for target in targets],
                        "targetServices": target_services,
                        "requestedServices": requested_services,
                        "requestedServerIds": requested_server_ids,
                        "targetServerIds": target_server_ids,
                        "restartMode": restart_mode,
                        "targets": [target["name"] for target in targets],
                        "restarted": restarted,
                        "failed": failed,
                        "pluginSync": plugin_sync,
                        "pluginValidation": plugin_validation,
                        "scaledToMinimum": scaled_to_minimum,
                        "restartServiceOrder": list(config.RESTART_SERVICE_ORDER),
                        "healthWaitSeconds": config.RESTART_HEALTH_WAIT_SECONDS,
                        "requestContext": request_context,
                    },
                )
            except Exception as exc:
                self.json_response(500, {"timestamp": timestamp, "error": str(exc)})
            return

        if path == "/autoscale/tick":
            if not self.authorized():
                self.json_response(401, {"error": "unauthorized"})
                return
            try:
                result = autoscale.run_autoscale_tick("manual")
                self.json_response(200, result)
            except Exception as exc:
                self.json_response(500, {"error": str(exc)})
            return

        if path == "/autoscale/players":
            if not self.authorized():
                self.json_response(401, {"error": "unauthorized"})
                return
            try:
                payload = self.parse_json_body()
                updates = payload.get("updates")
                if not isinstance(updates, list):
                    updates = [payload]
                accepted = []
                db = autoscale.get_mongo_database()
                collection = autoscale.autoscale_collection(db)
                for entry in updates:
                    if not isinstance(entry, dict):
                        continue
                    game_type = autoscale.normalize_game_type(entry.get("gameType"))
                    players = max(0, autoscale.to_int(entry.get("onlinePlayers"), 0))
                    source = (entry.get("source") or "web").strip() or "web"
                    updated_at_ms = autoscale.now_ms()
                    doc_id = autoscale.autoscale_doc_id("players", game_type)
                    document = {
                        "docType": "players",
                        "gameType": game_type,
                        "onlinePlayers": players,
                        "source": source,
                        "updatedAtMs": updated_at_ms,
                        "updatedAt": autoscale.now_iso(),
                    }
                    collection.update_one(
                        {"_id": doc_id},
                        {"$set": document},
                        upsert=True,
                    )
                    accepted.append(
                        {
                            "id": doc_id,
                            "gameType": game_type,
                            "onlinePlayers": players,
                            "source": source,
                        }
                    )
                self.json_response(
                    200,
                    {
                        "accepted": accepted,
                    },
                )
            except Exception as exc:
                self.json_response(400, {"error": str(exc)})
            return

        self.json_response(404, {"error": "not found"})
