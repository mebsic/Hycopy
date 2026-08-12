#!/usr/bin/env sh
set -eu

version="${VELOCITY_VERSION:-latest}"
build_channel="${VELOCITY_BUILD_CHANNEL:-STABLE}"
requested_build="${VELOCITY_BUILD:-latest}"
user_agent="${USER_AGENT:-hycopy-docker/2.0 (https://example.net)}"
api_root="https://fill.papermc.io/v3/projects/velocity"

http_get() {
  curl --globoff -fsSL -H "User-Agent: ${user_agent}" "$1"
}

list_versions() {
  http_get "${api_root}" \
    | jq -r '
      if (.versions | type) == "object" then
        [.versions[]?[]?]
      elif (.versions | type) == "array" then
        .versions
      else
        []
      end
      | .[]?
    '
}

resolve_build_for_version() {
  target_version="$1"
  builds_doc="$(http_get "${api_root}/versions/${target_version}/builds" || true)"
  if [ -z "${builds_doc}" ]; then
    return 0
  fi

  printf '%s' "${builds_doc}" | jq -r \
    --arg channel "${build_channel}" \
    --arg requested_build "${requested_build}" '
    def build_id: (.id // .build // empty);
    def build_number: build_id | tonumber?;
    def download_url:
      .downloads as $downloads
      | if ($downloads | type) != "object" then
          empty
        else
          $downloads["server:default"].url
          // ([$downloads[]? | .url?] | map(select(. != null and . != "")) | .[0] // empty)
        end;
    def matches_channel:
      ($channel == "" or ($channel | ascii_upcase) == "ANY")
      or ((.channel // "") | ascii_upcase) == ($channel | ascii_upcase);

    [
      .[]?
      | select(build_id != "")
      | select(build_number != null)
      | select($requested_build == "latest" or (build_id | tostring) == $requested_build)
      | select($requested_build != "latest" or matches_channel)
      | select((download_url // "") != "")
    ]
    | sort_by(build_number)
    | reverse
    | .[0]
    | if . == null then
        empty
      else
        "\(build_id)|\(download_url)|\(.channel // "")"
      end
  '
}

url=""
build=""
channel=""
if [ "${version}" = "latest" ]; then
  versions="$(list_versions || true)"
  for candidate in $(printf '%s\n' "${versions}" | awk 'NF' | sort -V -r); do
    [ -z "${candidate}" ] && continue
    resolved="$(resolve_build_for_version "${candidate}" || true)"
    if [ -n "${resolved}" ]; then
      version="${candidate}"
      build="$(printf '%s' "${resolved}" | cut -d'|' -f1)"
      url="$(printf '%s' "${resolved}" | cut -d'|' -f2)"
      channel="$(printf '%s' "${resolved}" | cut -d'|' -f3)"
      break
    fi
  done
else
  resolved="$(resolve_build_for_version "${version}" || true)"
  build="$(printf '%s' "${resolved}" | cut -d'|' -f1)"
  url="$(printf '%s' "${resolved}" | cut -d'|' -f2)"
  channel="$(printf '%s' "${resolved}" | cut -d'|' -f3)"
fi

if [ -z "${url}" ]; then
  echo "Failed to resolve Velocity download URL from ${api_root} for version ${version} channel ${build_channel} build ${requested_build}!" >&2
  exit 1
fi

curl --globoff -fsSL -H "User-Agent: ${user_agent}" -o /server/velocity.jar "${url}"
printf '%s\n' "${version}" > /server/velocity.version
if [ -n "${build}" ]; then
  printf '%s\n' "${build}" > /server/velocity.build
fi
if [ -n "${channel}" ]; then
  printf '%s\n' "${channel}" > /server/velocity.channel
fi
