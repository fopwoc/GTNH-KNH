#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"$ROOT_DIR/gradlew" -p "$ROOT_DIR/framework" --no-daemon --no-configuration-cache \
  check buildGtnh
