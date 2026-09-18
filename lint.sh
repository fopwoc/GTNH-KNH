#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MODULES=(
  framework
  mods/dejavu
  mods/hotspot
  mods/measure
  mods/testgui
  mods/palimpsest
  mods/tps-tab
)

for module in "${MODULES[@]}"; do
  echo ">>> Checking $module"
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR/$module" --no-daemon --no-configuration-cache \
    spotlessKotlinCheck spotlessKotlinGradleCheck detekt
done
