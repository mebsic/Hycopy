#!/usr/bin/env python3
import http.client
import json
import os
import shutil
import socket
import subprocess
import time
import urllib.parse

import config


class UnixHTTPConnection(http.client.HTTPConnection):
    def __init__(self, unix_socket_path, timeout=10):
        super().__init__("localhost", timeout=timeout)
        self.unix_socket_path = unix_socket_path

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(self.unix_socket_path)


def docker_request(method, path, body=None):
    connection = UnixHTTPConnection(config.DOCKER_SOCKET, timeout=20)
    payload = None
    headers = {}
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    connection.request(method, path, body=payload, headers=headers)
    response = connection.getresponse()
    data = response.read()
    status = response.status
    connection.close()
    return status, data


def docker_json(method, path):
    status, data = docker_request(method, path)
    if status < 200 or status >= 300:
        detail = data.decode("utf-8", errors="replace")
        raise RuntimeError(f"Docker API {method} {path} failed: {status} {detail}")
    if not data:
        return None
    return json.loads(data.decode("utf-8"))


def detect_compose_project():
    if config.COMPOSE_PROJECT:
        return config.COMPOSE_PROJECT

    hostname = (os.getenv("HOSTNAME") or "").strip()
    if not hostname:
        return ""

    encoded = urllib.parse.quote(hostname, safe="")
    inspect = docker_json("GET", f"/containers/{encoded}/json")
    labels = inspect.get("Config", {}).get("Labels", {})
    return labels.get("com.docker.compose.project", "")


def list_project_containers(compose_project, include_all=True):
    filters = {"label": [f"com.docker.compose.project={compose_project}"]}
    encoded_filters = urllib.parse.quote(json.dumps(filters), safe="")
    all_value = "1" if include_all else "0"
    raw = docker_json("GET", f"/containers/json?all={all_value}&filters={encoded_filters}")
    containers = []
    for item in raw:
        names = item.get("Names") or []
        name = names[0].lstrip("/") if names else item.get("Id", "")[:12]
        labels = item.get("Labels") or {}
        containers.append(
            {
                "id": item.get("Id", ""),
                "name": name,
                "service": labels.get("com.docker.compose.service", ""),
                "state": (item.get("State") or "").lower(),
                "status": item.get("Status", ""),
            }
        )
    containers.sort(key=lambda value: (value["service"], value["name"]))
    return containers


def restart_container(container_id):
    encoded = urllib.parse.quote(container_id, safe="")
    status, data = docker_request("POST", f"/containers/{encoded}/restart?t={config.RESTART_TIMEOUT_SECONDS}")
    if status in (204, 304):
        return True, ""
    detail = data.decode("utf-8", errors="replace")
    return False, f"restart failed with status {status}: {detail}"


def inspect_container_state(container_id):
    encoded = urllib.parse.quote(container_id, safe="")
    inspect = docker_json("GET", f"/containers/{encoded}/json")
    state = inspect.get("State") or {}
    lifecycle_status = (state.get("Status") or "").lower()
    health = state.get("Health") or {}
    health_status = (health.get("Status") or "").lower()
    return lifecycle_status, health_status


def is_container_ready_state(lifecycle_status, health_status):
    if lifecycle_status != "running":
        return False
    if not health_status:
        return True
    return health_status == "healthy"


def wait_for_container_ready(container_id, timeout_seconds):
    deadline = time.time() + max(1, timeout_seconds)
    last_detail = "state=unknown"

    while time.time() < deadline:
        try:
            lifecycle_status, health_status = inspect_container_state(container_id)
        except Exception as exc:
            last_detail = f"inspect failed: {exc}"
            time.sleep(1)
            continue

        if lifecycle_status in {"dead", "exited", "removing"}:
            return False, f"state={lifecycle_status}"

        if is_container_ready_state(lifecycle_status, health_status):
            if health_status:
                return True, f"state={lifecycle_status},health={health_status}"
            return True, f"state={lifecycle_status}"

        if health_status:
            last_detail = f"state={lifecycle_status},health={health_status}"
        else:
            last_detail = f"state={lifecycle_status}"
        time.sleep(1)

    return False, f"timed out after {timeout_seconds}s ({last_detail})"


def stop_and_remove_container(container_id):
    encoded = urllib.parse.quote(container_id, safe="")
    stop_status, stop_body = docker_request("POST", f"/containers/{encoded}/stop?t=10")
    if stop_status not in (204, 304, 404):
        detail = stop_body.decode("utf-8", errors="replace")
        raise RuntimeError(f"stop failed ({stop_status}): {detail}")
    remove_status, remove_body = docker_request("DELETE", f"/containers/{encoded}?force=1")
    if remove_status not in (204, 404):
        detail = remove_body.decode("utf-8", errors="replace")
        raise RuntimeError(f"remove failed ({remove_status}): {detail}")


def compose_command_prefix(compose_project):
    if not shutil.which("docker"):
        raise RuntimeError("docker binary not found in control-panel container")
    cmd = ["docker", "compose"]
    if compose_project:
        cmd.extend(["-p", compose_project])
    if config.ROLLOUT_COMPOSE_FILE:
        cmd.extend(["-f", config.ROLLOUT_COMPOSE_FILE])
    if config.ROLLOUT_COMPOSE_ENV_FILE and os.path.isfile(config.ROLLOUT_COMPOSE_ENV_FILE):
        cmd.extend(["--env-file", config.ROLLOUT_COMPOSE_ENV_FILE])
    return cmd


def compose_subprocess_env():
    compose_env = os.environ.copy()
    if not compose_env.get("PWD"):
        candidate = (config.ROLLOUT_COMPOSE_WORKDIR or "").strip()
        if not candidate:
            candidate = os.path.dirname((config.ROLLOUT_COMPOSE_FILE or "").strip())
        if not candidate:
            candidate = "/workspace"
        compose_env["PWD"] = candidate
    return compose_env


def run_compose_scale_service(compose_project, service_name, replicas):
    cmd = compose_command_prefix(compose_project)
    cmd.extend(
        [
            "up",
            "-d",
            "--no-deps",
            "--no-recreate",
            "--scale",
            f"{service_name}={replicas}",
            service_name,
        ]
    )
    compose_env = compose_subprocess_env()
    result = subprocess.run(
        cmd,
        cwd=config.ROLLOUT_COMPOSE_WORKDIR if config.ROLLOUT_COMPOSE_WORKDIR else None,
        env=compose_env,
        capture_output=True,
        text=True,
        check=False,
    )
    combined = ((result.stdout or "") + "\n" + (result.stderr or "")).strip()
    if result.returncode != 0:
        raise RuntimeError(f"compose scale failed for service {service_name}: {combined}")
    return combined


def run_compose_up_service(compose_project, service_name, replicas, build, force_recreate):
    cmd = compose_command_prefix(compose_project)
    cmd.extend(["up", "-d", "--no-deps"])
    if build:
        cmd.append("--build")
    if force_recreate:
        cmd.append("--force-recreate")
    if replicas is not None and replicas > 0:
        cmd.extend(["--scale", f"{service_name}={replicas}"])
    cmd.append(service_name)

    result = subprocess.run(
        cmd,
        cwd=config.ROLLOUT_COMPOSE_WORKDIR if config.ROLLOUT_COMPOSE_WORKDIR else None,
        env=compose_subprocess_env(),
        capture_output=True,
        text=True,
        check=False,
    )
    combined = ((result.stdout or "") + "\n" + (result.stderr or "")).strip()
    if result.returncode != 0:
        raise RuntimeError(f"compose up failed: {combined}")
    return combined
