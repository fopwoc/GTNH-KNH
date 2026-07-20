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

resolve_snapshot_version() {
  local base_version
  local latest_release_tag=""
  local commit_hash="nogit"
  local dirty_suffix=""

  base_version="$(awk -F= '$1 == "frameworkVersion" { print $2; exit }' "$ROOT_DIR/gradle/shared-build.properties")"
  if [ -z "$base_version" ]; then
    echo ">>> Could not resolve frameworkVersion from gradle/shared-build.properties" >&2
    return 1
  fi

  if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    latest_release_tag="$(git -C "$ROOT_DIR" describe --tags --match 'v[0-9]*' --abbrev=0 2>/dev/null || true)"
    if [ -n "$latest_release_tag" ]; then
      base_version="${latest_release_tag#v}"
    fi

    commit_hash="$(git -C "$ROOT_DIR" rev-parse --short=8 HEAD)"
    if [ -n "$(git -C "$ROOT_DIR" status --porcelain --untracked-files=normal)" ]; then
      dirty_suffix=".dirty"
    fi
  fi

  printf '%s-g%s%s-SNAPSHOT\n' "$base_version" "$commit_hash" "$dirty_suffix"
}

if [ "${RELEASE_VERSION+x}" = x ]; then
  if ! [[ "$RELEASE_VERSION" =~ ^[0-9A-Za-z][0-9A-Za-z._+-]*$ ]]; then
    echo ">>> Invalid RELEASE_VERSION: $RELEASE_VERSION" >&2
    exit 2
  fi

  GRADLE_ARGS+=(
    "-PmodVersion=$RELEASE_VERSION"
    "-PframeworkVersion=$RELEASE_VERSION"
  )
  echo ">>> Building release version $RELEASE_VERSION"
else
  SNAPSHOT_VERSION="$(resolve_snapshot_version)"
  GRADLE_ARGS+=(
    "-PmodVersion=$SNAPSHOT_VERSION"
    "-PframeworkVersion=$SNAPSHOT_VERSION"
  )
  echo ">>> Building snapshot version $SNAPSHOT_VERSION"
fi

rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"
mkdir -p "$LOG_DIR"

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
