#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Spotless and detekt run inside each GTNH island as part of its jar build.
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" --no-daemon check buildGtnh
