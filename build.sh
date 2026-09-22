#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"
JAVA26_HOME="${JAVA26_HOME:-$(/usr/libexec/java_home -v 26)}"
# Modules included in the root build; only their jars are collected.
MODULES=(
  "framework"
  "mods/measure"
  "mods/tps-tab"
)

echo ">>> Using JAVA_HOME=$JAVA26_HOME"

rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

JAVA_HOME="$JAVA26_HOME" PATH="$JAVA26_HOME/bin:$PATH" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" --no-daemon clean check buildAll

# buildAll collects each module's distributable jars into its own build/libs.
for module in "${MODULES[@]}"; do
  find "$ROOT_DIR/$module/build/libs" \
    -maxdepth 1 -type f -name '*.jar' \
    ! -name '*-dev.jar' \
    ! -name '*-sources.jar' \
    -exec cp {} "$ARTIFACTS_DIR/" \;
done

echo ">>> Artifacts"
find "$ARTIFACTS_DIR" -maxdepth 1 -type f -name '*.jar' -print | sort
