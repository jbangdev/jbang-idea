#!/usr/bin/env bash
# Prints, as a JSON array, the newest IntelliJ IDEA Ultimate build of every line (2026.2, 2026.3, ...)
# that the plugin supports, i.e. whose build is >= pluginSinceBuild in gradle.properties. Newest first.
#
# Usage: intellij-versions.sh <channels>     channels: "release" or "release,eap"
#
# Each entry looks like:
#   {"line":"2026.3","type":"release","build":"263.7307.200","platformVersion":"2026.3"}
# platformVersion is the value to pass to `./gradlew test -PplatformVersion=...`:
# the version name for a release, the build number for an EAP.
#
# Set RELEASES_JSON to a local file to read the release list from it instead of the JetBrains API.
set -euo pipefail

channels="${1:?usage: $0 <channels, e.g. release or release,eap>}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
since_build="$(sed -n 's/^pluginSinceBuild *= *\([0-9][0-9]*\).*/\1/p' "$root/gradle.properties")"

if [[ -n "${RELEASES_JSON:-}" ]]; then
  releases="$(cat "$RELEASES_JSON")"
else
  releases="$(curl -fsSL "https://data.services.jetbrains.com/products/releases?code=IIU&type=$channels")"
fi

jq -c --argjson since "$since_build" --arg channels "$channels" '
  def build_key: .build | split(".") | map(tonumber);
  [ .IIU[]
    | select(.type as $type | $channels | split(",") | index($type))
    | select((.build | split(".")[0] | tonumber) >= $since) ]
  | group_by(.majorVersion)
  | map(max_by(build_key))
  | sort_by(build_key) | reverse
  | map({line: .majorVersion, type, build, platformVersion: (if .type == "release" then .version else .build end)})
' <<<"$releases"
