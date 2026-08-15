#!/usr/bin/env bash
set -euo pipefail

CONFIG_SOURCE="${CONFIG_SOURCE:-/bootstrap/config.json}"
PLUGIN_SOURCE_DIR="${PLUGIN_SOURCE_DIR:-/bootstrap/plugins}"
VELOCITY_INSTALL_SCRIPT="${VELOCITY_INSTALL_SCRIPT:-/usr/local/bin/install-velocity.sh}"
VELOCITY_LMBDA_PATCHER="${VELOCITY_LMBDA_PATCHER:-/usr/local/share/hycopy/velocity/PatchVelocityLmbda.java}"

mkdir -p /server/plugins/hycopyproxy

download_velocity() {
  local velocity_version="${VELOCITY_VERSION:-latest}"
  local user_agent="${USER_AGENT:-hycopy-docker/2.0 (https://example.net)}"

  if [[ ! -x "${VELOCITY_INSTALL_SCRIPT}" ]]; then
    echo "[bootstrap] Velocity installer not found or not executable: ${VELOCITY_INSTALL_SCRIPT}" >&2
    return 1
  fi

  echo "[bootstrap] Downloading Velocity (${velocity_version})..."
  VELOCITY_VERSION="${velocity_version}" USER_AGENT="${user_agent}" "${VELOCITY_INSTALL_SCRIPT}"

  if [[ ! -s /server/velocity.jar ]]; then
    echo "[bootstrap] Velocity download did not produce /server/velocity.jar." >&2
    return 1
  fi

  if [[ -f /server/velocity.version ]]; then
    local version_label
    version_label="$(cat /server/velocity.version)"
    if [[ -f /server/velocity.build ]]; then
      version_label="${version_label} build $(cat /server/velocity.build)"
    fi
    if [[ -f /server/velocity.channel ]]; then
      version_label="${version_label} ($(cat /server/velocity.channel))"
    fi
    echo "[bootstrap] Using Velocity version ${version_label}."
  fi
}

patch_velocity_lmbda() {
  if [[ "${VELOCITY_PATCH_LMBDA:-true}" != "true" ]]; then
    echo "[bootstrap] Velocity lmbda patch disabled."
    return 0
  fi

  if [[ ! -f "${VELOCITY_LMBDA_PATCHER}" ]]; then
    echo "[bootstrap] Velocity lmbda patcher not found: ${VELOCITY_LMBDA_PATCHER}" >&2
    return 1
  fi

  echo "[bootstrap] Patching Velocity lmbda hidden-class bridge..."
  java -cp /server/velocity.jar "${VELOCITY_LMBDA_PATCHER}" /server/velocity.jar
}

download_hangar_velocity_plugin() {
  local project="$1"
  local version="$2"
  local file_name="$3"
  local target="/server/plugins/${file_name}"
  local tmp_target="${target}.download"
  local user_agent="${USER_AGENT:-hycopy-docker/2.0 (https://example.net)}"
  local api_root="https://hangar.papermc.io/api/v1/projects/${project}"
  local resolved_version="${version}"
  local url="https://hangar.papermc.io/api/v1/projects/${project}/versions/${version}/VELOCITY/download"

  if [[ -s "${target}" && "${VIA_FORCE_DOWNLOAD:-false}" != "true" ]]; then
    echo "[bootstrap] Using existing ${file_name}."
    return 0
  fi

  if [[ "${version}" == "latest" ]]; then
    resolved_version="$(curl --globoff -fsSL -H "User-Agent: ${user_agent}" "${api_root}/latestrelease")"
    url="${api_root}/versions/${resolved_version}/VELOCITY/download"
  fi

  echo "[bootstrap] Downloading ${project} ${resolved_version} for Velocity..."
  rm -f "${tmp_target}"
  curl --globoff -fsSL -H "User-Agent: ${user_agent}" -o "${tmp_target}" "${url}"
  mv "${tmp_target}" "${target}"
}

install_via_plugins() {
  if [[ "${VIA_PLUGINS_ENABLED:-true}" != "true" ]]; then
    echo "[bootstrap] Official Via plugin installation disabled."
    return 0
  fi

  mkdir -p /server/plugins
  download_hangar_velocity_plugin "ViaVersion" "${VIAVERSION_VERSION:-latest}" "ViaVersion.jar"
  download_hangar_velocity_plugin "ViaBackwards" "${VIABACKWARDS_VERSION:-latest}" "ViaBackwards.jar"
  download_hangar_velocity_plugin "ViaRewind" "${VIAREWIND_VERSION:-latest}" "ViaRewind.jar"
}

configure_viaversion() {
  if [[ "${VIA_PLUGINS_ENABLED:-true}" != "true" ]]; then
    return 0
  fi

  local config_file="/server/plugins/viaversion/config.yml"
  local interval="${VIA_VELOCITY_PING_INTERVAL:-1}"
  local input_file="/dev/null"
  local tmp_file=""

  mkdir -p "$(dirname "${config_file}")"

  if [[ -f "${config_file}" ]]; then
    input_file="${config_file}"
  fi

  tmp_file="$(mktemp)"
  if awk -v interval="${interval}" '
    BEGIN { updated = 0 }
    /^[[:space:]]*velocity-ping-interval:/ {
      print "velocity-ping-interval: " interval
      updated = 1
      next
    }
    { print }
    END {
      if (!updated) {
        print "velocity-ping-interval: " interval
      }
    }
  ' "${input_file}" > "${tmp_file}"; then
    mv "${tmp_file}" "${config_file}"
    echo "[bootstrap] ViaVersion velocity-ping-interval set to ${interval}s."
  else
    echo "[bootstrap] Failed to configure ViaVersion velocity-ping-interval." >&2
    rm -f "${tmp_file}"
    return 1
  fi
}

stage_proxy_plugin() {
  local file_name="HycopyProxy.jar"
  local runtime_target="/server/plugins/${file_name}"
  local source_target=""

  if [[ -n "${PLUGIN_SOURCE_DIR}" && -d "${PLUGIN_SOURCE_DIR}" ]]; then
    source_target="${PLUGIN_SOURCE_DIR}/${file_name}"

    if [[ -d "${source_target}" ]]; then
      echo "[bootstrap] Fixing plugin path type mismatch for ${file_name}."
      rm -rf "${source_target}"
    fi

    if [[ -f "${source_target}" ]]; then
      cp -f "${source_target}" "${runtime_target}"
    fi
  fi

  if [[ ! -s "${runtime_target}" ]]; then
    echo "[bootstrap] Required proxy plugin ${file_name} not found in ${PLUGIN_SOURCE_DIR} or ${runtime_target}." >&2
    return 1
  fi
}

apply_network_config_overrides() {
  local config_file="$1"
  local redis_password_set="false"
  local tmp_file=""

  [[ -f "${config_file}" ]] || return 0

  if [[ "${REDIS_PASSWORD+x}" == "x" ]]; then
    redis_password_set="true"
  fi

  tmp_file="$(mktemp)"
  if jq \
    --arg mongo_uri "${MONGO_URI:-}" \
    --arg mongo_database "${MONGO_DATABASE:-}" \
    --arg redis_host "${REDIS_HOST:-}" \
    --arg redis_port "${REDIS_PORT:-}" \
    --arg redis_password "${REDIS_PASSWORD:-}" \
    --argjson redis_password_set "${redis_password_set}" \
    --arg redis_database "${REDIS_DATABASE:-}" '
      .mongo = (.mongo // {}) |
      .redis = (.redis // {}) |
      .mongo.uri = (if $mongo_uri != "" then $mongo_uri else .mongo.uri end) |
      .mongo.database = (if $mongo_database != "" then $mongo_database else .mongo.database end) |
      .redis.host = (if $redis_host != "" then $redis_host else .redis.host end) |
      .redis.port = (if $redis_port != "" then ($redis_port | tonumber) else .redis.port end) |
      .redis.password = (if $redis_password_set then $redis_password else .redis.password end) |
      .redis.database = (if $redis_database != "" then ($redis_database | tonumber) else .redis.database end)
    ' "${config_file}" > "${tmp_file}"; then
    mv "${tmp_file}" "${config_file}"
  else
    echo "Warning: failed to apply network overrides to ${config_file}" >&2
    rm -f "${tmp_file}"
  fi
}

if [[ -f "${CONFIG_SOURCE}" ]]; then
  cp "${CONFIG_SOURCE}" /server/plugins/hycopyproxy/config.json
  apply_network_config_overrides /server/plugins/hycopyproxy/config.json
fi

if [[ -f /bootstrap/proxy/server-icon-production.png ]]; then
  cp /bootstrap/proxy/server-icon-production.png /server/server-icon.png
  cp /bootstrap/proxy/server-icon-production.png /server/plugins/hycopyproxy/server-icon.png
fi

if [[ -f /bootstrap/proxy/server-icon-maintenance.png ]]; then
  cp /bootstrap/proxy/server-icon-maintenance.png /server/server-icon-maintenance.png
  cp /bootstrap/proxy/server-icon-maintenance.png /server/plugins/hycopyproxy/server-icon-maintenance.png
fi

if [[ ! -f /server/velocity.toml ]]; then
  cat > /server/velocity.toml <<'TOML'
bind = "0.0.0.0:25565"
motd = "A Hycopy Network"
show-max-players = 200
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "legacy"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "disabled"
enable-player-address-logging = true

[servers]
placeholder = "127.0.0.1:1"
try = ["placeholder"]

config-version = "2.7"

[forced-hosts]

[advanced]
bungee-plugin-message-channel = true
TOML
fi

download_velocity
patch_velocity_lmbda
install_via_plugins
configure_viaversion
stage_proxy_plugin

exec java ${JAVA_OPTS:-"-Xms256M -Xmx512M"} -jar /server/velocity.jar
