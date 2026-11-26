#!/usr/bin/env bash
# Actual Gradle task graph, checked only on the authorized hosted lane.
set -Eeuo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_OS:-}" != Linux || -z "${RUNNER_TEMP:-}" || ! -d "$RUNNER_TEMP" ]]; then
    echo 'MOD01_HOSTED_GRAPH_REFUSED' >&2
    exit 64
fi
[[ "$(git rev-parse HEAD)" == "${SOURCE_SHA:-}" ]] || { echo 'MOD01_GRAPH_SOURCE_MISMATCH' >&2; exit 64; }
work="$(mktemp -d "$RUNNER_TEMP/mod01-graph.XXXXXXXX")"
trap 'rm -rf -- "$work"' EXIT
# Use the verified offline cache and the same strict lock state as the real build.
bash gradlew --no-daemon --console=plain --offline --init-script gradle/ci/locks.gradle :app:mergeDebugAssets --dry-run | tee "$work/debug"
bash gradlew --no-daemon --console=plain --offline --init-script gradle/ci/locks.gradle :app:mergeReleaseAssets --dry-run | tee "$work/release"
python3 -B scripts/qa/mod01_gradle_graph.py "$work/debug" "$work/release"
# The dry-run alone cannot detect failures inside a @TaskAction. Execute the
# exact producer on the hosted runner before any dependent APK assembly.
bash gradlew --no-daemon --console=plain --offline --init-script gradle/ci/locks.gradle :app:buildMod01ProbeDex --rerun-tasks
echo 'MOD01_GENERATED_PROBE_EXECUTION_PASS'
