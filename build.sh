#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"
JAVA26_HOME="${JAVA26_HOME:-$(/usr/libexec/java_home -v 26)}"
GRADLE_ARGS=(--no-daemon --no-configuration-cache)

resolve_build_version() {
  if [ -n "${VERSION:-}" ]; then
    printf '%s\n' "$VERSION"
    return
  fi

  JAVA_HOME="$JAVA26_HOME" PATH="$JAVA26_HOME/bin:$PATH" \
    "$ROOT_DIR/gradlew" -p "$ROOT_DIR/knhmp" "${GRADLE_ARGS[@]}" -q printVersion \
    | awk 'NF { version = $0 } END { print version }'
}

BUILD_VERSION="$(resolve_build_version)"
if [ -z "$BUILD_VERSION" ]; then
  echo ">>> KnhMP did not resolve a build version" >&2
  exit 2
fi

echo ">>> Building KNH Core $BUILD_VERSION"
echo ">>> Using JAVA_HOME=$JAVA26_HOME"

rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

VERSION="$BUILD_VERSION" JAVA_HOME="$JAVA26_HOME" PATH="$JAVA26_HOME/bin:$PATH" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR/framework" "${GRADLE_ARGS[@]}" \
  clean check publishToMavenLocal

find "$ROOT_DIR/framework/build/libs" \
  -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-dev.jar' \
  ! -name '*-sources.jar' \
  -exec cp {} "$ARTIFACTS_DIR/" \;

echo ">>> Artifacts"
find "$ARTIFACTS_DIR" -maxdepth 1 -type f -name '*.jar' -print
