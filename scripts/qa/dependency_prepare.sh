#!/usr/bin/env bash
# Exactly one hosted writer; never invoke Gradle on the developer/Termux device.
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == Linux && -d "${RUNNER_TEMP:-}" ]]
[[ "$(git rev-parse HEAD)" == "${SOURCE_SHA:-}" && -n "${GITHUB_RUN_ID:-}" && -n "${GITHUB_RUN_ATTEMPT:-}" ]]
[[ "${HOOKLAB_DEPENDENCY_STATE:-}" == "$RUNNER_TEMP/hooklab-dependencies" ]]
[[ "${HOOKLAB_VERIFIED_MAVEN:-}" == "$HOOKLAB_DEPENDENCY_STATE/repository" ]]
profile=${1:?dependency profile required}
case "$profile" in
  full) tasks=(:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :probe-target:assembleDebug :probe-target:lintDebug :app:assembleRelease);;
  jvm) tasks=(:app:testDebugUnitTest);;
  *) echo 'DEPENDENCY_PROFILE_REFUSED' >&2; exit 64;;
esac
if [[ "$profile" == jvm ]]; then unset HOOKLAB_VERIFIED_MAVEN; fi
state=$HOOKLAB_DEPENDENCY_STATE
mkdir -p -m 700 "$state"
[[ ! -L "$state" && ! -L "$state/repository" && ! -L "$state/locks" ]]
graph=$(python3 -B scripts/qa/dependency_prep.py graph)
[[ "$graph" =~ ^[a-f0-9]{64}$ ]]
start=$SECONDS
python3 -B scripts/qa/dependency_prep.py check-seed --state "$state"
# A complete same-resolution snapshot can skip the online warming phase. Its prior
# run receipt is never accepted as qualification of the current source.
warm=false
if [[ -e "$state/ready.json" ]]; then
    python3 -B scripts/qa/dependency_prep.py warm-check --state "$state" --graph "$graph" --profile "$profile" --receipt "$state/ready.json"
    warm=true
fi
rm -f -- "$state/ready.json"
if [[ "$warm" == false ]]; then
    # A standard or partial cache is only a download seed, not a trusted lock inventory.
    python3 -B scripts/qa/dependency_prep.py authenticate --state "$state" --profile "$profile"
    rm -rf -- "$state/locks"
    mkdir -m 700 "$state/locks"
    # Actual AGP tasks also resolve detached compiler/lint/D8 dependencies;
    # metadata-only Gradle dependencies reports would miss these artifacts.
    echo 'DEPENDENCY_ONLINE_PREPARATION_START'
    HOOKLAB_CI_RETRY_PROFILE=standard bash scripts/ci_gradle_retry.sh bash gradlew --no-daemon --console=plain --init-script gradle/ci/locks.gradle --max-workers=2 --write-locks "${tasks[@]}"
fi
# Build outputs are never restored from the dependency cache. Clean after a
# cold warm-up so offline execution cannot pass with up-to-date task outputs.
if [[ "$warm" == false ]]; then
    bash gradlew --no-daemon --console=plain --offline --init-script gradle/ci/locks.gradle --max-workers=2 clean
fi
if [[ "$profile" == full ]]; then
    bash scripts/qa/mod01_gradle_graph.sh
fi
# Real offline tasks, not a cache hit or a dry run, establish readiness.
# Do not retry compilation, lint or missing offline dependencies.
echo 'DEPENDENCY_OFFLINE_EXECUTION_START'
bash gradlew --no-daemon --console=plain --offline --init-script gradle/ci/locks.gradle --max-workers=2 "${tasks[@]}"
python3 -B scripts/qa/dependency_prep.py seal --state "$state" --graph "$graph" --profile "$profile" --receipt "$state/ready.json" --elapsed-seconds "$((SECONDS-start))" --reused-ready "$warm"
echo "DEPENDENCY_OFFLINE_READY profile=$profile elapsed_seconds=$((SECONDS-start)) reused_ready=$warm"
