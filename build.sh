#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"
# Modules included in the root build; only their jars are collected.
MODULES=(
  "framework"
  "mods/hotspot"
  "mods/measure"
  "mods/palimpsest"
  "mods/testgui"
  "mods/tps-tab"
)

if [[ $# -gt 1 || ( $# -eq 1 && $1 != "--collect-only" ) ]]; then
  echo "Usage: $0 [--collect-only]" >&2
  exit 2
fi

rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

if [[ ${1:-} != "--collect-only" ]]; then
  JAVA26_HOME="${JAVA26_HOME:-$(/usr/libexec/java_home -v 26)}"
  echo ">>> Using JAVA_HOME=$JAVA26_HOME"
  JAVA_HOME="$JAVA26_HOME" PATH="$JAVA26_HOME/bin:$PATH" \
    "$ROOT_DIR/gradlew" -p "$ROOT_DIR" --no-daemon clean check buildAll
fi

# buildAll collects each module's distributable jars into its own build/libs.
for module in "${MODULES[@]}"; do
  libs="$ROOT_DIR/$module/build/libs"
  if [[ ! -d "$libs" ]]; then
    echo "Missing built jars directory: $libs" >&2
    exit 1
  fi
  found=false
  while IFS= read -r -d '' jar; do
    found=true
    name="${jar##*/}"
    if [[ ! "$name" =~ -(gtnh|fabric-[0-9][0-9.]*|neoforge-[0-9][0-9.]*)- ]]; then
      echo "No loader and Minecraft version in $name" >&2
      exit 1
    fi
    target="${BASH_REMATCH[1]}"
    mkdir -p "$ARTIFACTS_DIR/$target"
    cp "$jar" "$ARTIFACTS_DIR/$target/$name"
  done < <(
    find "$libs" \
      -maxdepth 1 -type f -name '*.jar' \
      ! -name '*-dev.jar' \
      ! -name '*-sources.jar' \
      ! -name '*-javadoc.jar' -print0
  )
  if [[ "$found" == false ]]; then
    echo "No distributable jars in $libs" >&2
    exit 1
  fi
done

echo ">>> Artifacts"
find "$ARTIFACTS_DIR" -mindepth 2 -maxdepth 2 -type f -name '*.jar' -print | sort
