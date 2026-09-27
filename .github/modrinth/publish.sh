#!/usr/bin/env bash
# Publishes one release to Modrinth: KNH Core first, then every mod against the KNH Core version of
# the same loader and Minecraft version. Versions already on Modrinth are kept, so a failed run can
# simply be repeated.
#
# Usage: publish.sh <version> <jars dir> <changelog file>
# Env: MODRINTH_TOKEN; DRY_RUN=1 prints the version payloads instead of uploading.
set -euo pipefail

version="$1"
jars="$2"
changelog="$3"
config="$(dirname "$0")/projects.json"
api="https://api.modrinth.com/v2"
agent="fopwoc/GTNH-KNH/release-ci"
dry_run="${DRY_RUN:-}"

modrinth() {
  curl --fail-with-body --silent --show-error --retry 3 \
    -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $agent" "$@"
}

# Target in the jar name → "<loader> <game version> <label>": gtnh, fabric-26.2, neoforge-1.21.1.
describe_target() {
  case "$1" in
    gtnh) echo "forge 1.7.10 GTNH" ;;
    fabric-*) echo "fabric ${1#fabric-} Fabric" ;;
    neoforge-*) echo "neoforge ${1#neoforge-} NeoForge" ;;
    *) echo "Unknown target $1" >&2; return 1 ;;
  esac
}

# KNH Core version id per target, for the mods' dependencies.
declare -A core_versions

publish() {
  local archive="$1" project name environment jar target loader game_version label platform
  project="$(jq -er --arg a "$archive" '.ids[$a]' "$config")"
  name="$(jq -er --arg a "$archive" '.projects[$a].name' "$config")"
  environment="$(jq -er --arg a "$archive" '.projects[$a].environment' "$config")"

  local existing='[]'
  if [[ -z "$dry_run" ]]; then
    existing="$(modrinth "$api/project/$project/version?include_changelog=false")"
  fi

  shopt -s nullglob
  local jar_files=("$jars/$archive"-*-"$version".jar)
  shopt -u nullglob
  if ((${#jar_files[@]} == 0)); then
    echo "No $archive jars for $version in $jars" >&2
    return 1
  fi

  for jar in "${jar_files[@]}"; do
    target="${jar##*/$archive-}"
    target="${target%-$version.jar}"
    read -r loader game_version label <<< "$(describe_target "$target")"
    platform="${target%%-*}"

    local number="$version+$target" id
    id="$(jq -r --arg n "$number" 'map(select(.version_number == $n)) | first | .id // empty' <<< "$existing")"
    if [[ -n "$id" ]]; then
      echo "$name $number is already on Modrinth as $id"
    else
      local core_dependency='[]'
      if [[ "$archive" != knh-core ]]; then
        core_dependency="$(jq -nc --arg v "${core_versions[$target]:-}" \
          'if $v == "" then error("no KNH Core version for this target") else [{version_id: $v, dependency_type: "required"}] end')"
      fi

      local data
      data="$(jq -n \
        --slurpfile config "$config" \
        --rawfile changelog "$changelog" \
        --argjson core "$core_dependency" \
        --arg archive "$archive" --arg project "$project" --arg platform "$platform" \
        --arg name "$name $version ($label $game_version)" --arg number "$number" \
        --arg loader "$loader" --arg game_version "$game_version" --arg environment "$environment" \
        '$config[0] as $c | {
          project_id: $project,
          name: $name,
          version_number: $number,
          changelog: $changelog,
          version_type: "release",
          featured: false,
          loaders: [$loader],
          game_versions: [$game_version],
          environment: $environment,
          dependencies: ($core + (($c.projects[$archive].dependencies[$platform] // {}) | to_entries
            | map({project_id: $c.ids[.key], dependency_type: .value}))),
          file_parts: ["jar"],
          primary_file: "jar"
        }')"

      if [[ -n "$dry_run" ]]; then
        echo "Would publish ${jar##*/}:"
        jq 'del(.changelog)' <<< "$data"
        id="dry-$target"
      else
        local payload
        payload="$(mktemp)"
        echo "$data" > "$payload"
        id="$(modrinth -X POST "$api/version" \
          -F "data=<$payload;type=application/json" \
          -F "jar=@$jar;type=application/java-archive" | jq -er .id)"
        rm -f "$payload"
        echo "Published $name $number as $id"
      fi
    fi

    if [[ "$archive" == knh-core ]]; then
      core_versions[$target]="$id"
    fi
  done
}

if [[ -z "$dry_run" && -z "${MODRINTH_TOKEN:-}" ]]; then
  echo "MODRINTH_TOKEN is not set" >&2
  exit 1
fi

publish knh-core
for archive in $(jq -r '.projects | keys[] | select(. != "knh-core")' "$config"); do
  publish "$archive"
done
