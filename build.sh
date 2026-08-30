#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"
JAVA25_HOME="${JAVA25_HOME:-$(/usr/libexec/java_home -v 25)}"
GRADLE_ARGS=(--no-daemon --no-configuration-cache)
LOG_DIR="$ARTIFACTS_DIR/.logs"
MODULES=(
  "framework"
  "mods/dejavu"
  "mods/measure"
  "mods/testgui"
  "mods/tps-tab"
)

rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"
mkdir -p "$LOG_DIR"

run_gradle_without_version_override() {
  local java_home="$1"
  shift
  local project_dir="$1"
  shift
  JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
    "$ROOT_DIR/gradlew" -p "$ROOT_DIR/$project_dir" "${GRADLE_ARGS[@]}" "$@"
}

resolve_build_version() {
  if [ -n "${VERSION:-}" ]; then
    printf '%s\n' "$VERSION"
    return
  fi

  run_gradle_without_version_override "$JAVA25_HOME" "framework" -q printVersion \
    | awk 'NF { version = $0 } END { print version }'
}

BUILD_VERSION="$(resolve_build_version)"
if [ -z "$BUILD_VERSION" ]; then
  echo ">>> GTNHGradle did not resolve a build version" >&2
  exit 2
fi

echo ">>> Building version $BUILD_VERSION"

run_gradle() {
  local java_home="$1"
  shift
  local project_dir="$1"
  shift
  VERSION="$BUILD_VERSION" JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
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

module_name() {
  local project_dir="$1"
  basename "$project_dir"
}

detect_parallel_jobs() {
  local module_count="$1"
  local job_count="${BUILD_JOBS:-}"

  if [ -z "$job_count" ]; then
    job_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || true)"
  fi

  if [ -z "$job_count" ]; then
    job_count="$(sysctl -n hw.ncpu 2>/dev/null || true)"
  fi

  if ! [[ "$job_count" =~ ^[1-9][0-9]*$ ]]; then
    job_count=1
  fi

  if [ "$job_count" -gt "$module_count" ]; then
    job_count="$module_count"
  fi

  printf '%s\n' "$job_count"
}

build_module() {
  local module="$1"
  run_gradle "$JAVA25_HOME" "$module" clean build
}

finish_module_build() {
  local module="$1"
  local log_file="$2"
  local status="$3"

  if [ "$status" -eq 0 ]; then
    echo ">>> Finished $module"
    copy_jars "$module"
    rm -f "$log_file"
    return 0
  fi

  failed_modules+=("$module")
  failed_logs+=("$log_file")
  failed_statuses+=("$status")
  echo ">>> Failed $module (exit $status)"
  return 1
}

reap_finished_jobs() {
  local new_pids=()
  local new_modules=()
  local new_logs=()
  local reaped_any=1
  local index pid module log_file status

  for index in "${!pids[@]}"; do
    pid="${pids[$index]}"
    module="${running_modules[$index]}"
    log_file="${running_logs[$index]}"

    if kill -0 "$pid" 2>/dev/null; then
      new_pids+=("$pid")
      new_modules+=("$module")
      new_logs+=("$log_file")
      continue
    fi

    reaped_any=0
    if wait "$pid"; then
      status=0
    else
      status=$?
    fi
    finish_module_build "$module" "$log_file" "$status"
  done

  pids=("${new_pids[@]}")
  running_modules=("${new_modules[@]}")
  running_logs=("${new_logs[@]}")

  return "$reaped_any"
}

cleanup_jobs() {
  local pid
  for pid in "${pids[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
}

pids=()
running_modules=()
running_logs=()
failed_modules=()
failed_logs=()
failed_statuses=()
trap cleanup_jobs INT TERM EXIT

echo ">>> Publishing shared framework to mavenLocal"
echo ">>> Using JAVA_HOME=$JAVA25_HOME"
run_gradle "$JAVA25_HOME" "framework" clean publishToMavenLocal
copy_jars "framework"

module_count=$(( ${#MODULES[@]} - 1 ))
max_parallel_jobs="$(detect_parallel_jobs "$module_count")"

echo ">>> Building $module_count modules with up to $max_parallel_jobs parallel jobs"

for module in "${MODULES[@]:1}"; do
  while [ "${#pids[@]}" -ge "$max_parallel_jobs" ]; do
    if ! reap_finished_jobs; then
      sleep 0.2
    fi
  done

  module_label="$(module_name "$module")"
  log_file="$LOG_DIR/$module_label.log"

  echo ">>> Building $module with JAVA_HOME=$JAVA25_HOME (log: $log_file)"
  (
    build_module "$module"
  ) >"$log_file" 2>&1 &

  pids+=("$!")
  running_modules+=("$module")
  running_logs+=("$log_file")
done

while [ "${#pids[@]}" -gt 0 ]; do
  if ! reap_finished_jobs; then
    sleep 0.2
  fi
done

trap - INT TERM EXIT

if [ "${#failed_modules[@]}" -gt 0 ]; then
  echo ">>> One or more module builds failed"
  for index in "${!failed_modules[@]}"; do
    module="${failed_modules[$index]}"
    log_file="${failed_logs[$index]}"
    status="${failed_statuses[$index]}"
    echo
    echo ">>> $module failed with exit $status"
    cat "$log_file"
  done
  exit 1
fi

rmdir "$LOG_DIR" 2>/dev/null || true

artifact_count="$(find "$ARTIFACTS_DIR" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d '[:space:]')"
if [ "$artifact_count" -ne "${#MODULES[@]}" ]; then
  echo ">>> Expected ${#MODULES[@]} runtime jars, found $artifact_count" >&2
  find "$ARTIFACTS_DIR" -maxdepth 1 -type f -name '*.jar' | sort >&2
  exit 1
fi

echo ">>> Artifacts copied to $ARTIFACTS_DIR"
find "$ARTIFACTS_DIR" -type f -name '*.jar' | sort
