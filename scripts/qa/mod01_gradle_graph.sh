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
# Use the same bounded, rate-limit-only retry policy as the real build.
bash scripts/ci_gradle_retry.sh bash gradlew --no-daemon --console=plain :app:mergeDebugAssets --dry-run | tee "$work/debug"
bash scripts/ci_gradle_retry.sh bash gradlew --no-daemon --console=plain :app:mergeReleaseAssets --dry-run | tee "$work/release"
python3 -B scripts/qa/mod01_gradle_graph.py "$work/debug" "$work/release"
