#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"
JAVA25_HOME="${JAVA25_HOME:-$(/usr/libexec/java_home -v 25)}"
GRADLE_ARGS=(--no-daemon --no-configuration-cache)
MODULES=(
  "framework"
  "mods/dejavu"
  "mods/measure"
  "mods/tps-tab"
)

rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

run_gradle() {
  local java_home="$1"
  shift
  local project_dir="$1"
  shift
  JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
    "$ROOT_DIR/gradlew" -p "$ROOT_DIR/$project_dir" "${GRADLE_ARGS[@]}" "$@"
}

copy_jars() {
  local project_dir="$1"
  find "$ROOT_DIR/$project_dir/build/libs" -maxdepth 1 -type f -name '*.jar' \
    ! -name '*-dev.jar' \
    ! -name '*-sources.jar' \
    -print0 | while IFS= read -r -d '' jar_path; do
    cp "$jar_path" "$ARTIFACTS_DIR/"
  done
}

echo ">>> Publishing shared framework to mavenLocal"
echo ">>> Using JAVA_HOME=$JAVA25_HOME"
run_gradle "$JAVA25_HOME" "framework" clean publishToMavenLocal
copy_jars "framework"

for module in "${MODULES[@]:1}"; do
  echo ">>> Building $module with JAVA_HOME=$JAVA25_HOME"
  run_gradle "$JAVA25_HOME" "$module" clean build
  copy_jars "$module"
done

echo ">>> Artifacts copied to $ARTIFACTS_DIR"
find "$ARTIFACTS_DIR" -type f -name '*.jar' | sort

