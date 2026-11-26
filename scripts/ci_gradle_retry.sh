#!/usr/bin/env bash
# Only for hosted CI: retry verified Maven Central HTTP 429 dependency resolution.
# No local Gradle execution, no errors hidden, no changing dependency repositories.
set -Eeuo pipefail
if (( $# < 1 )); then
    echo "HOOKLAB_CI_RETRY_MISSING_COMMAND" >&2
    exit 64
fi
if [[ -z "${RUNNER_TEMP:-}" || ! -d "${RUNNER_TEMP}" ]]; then
    echo "HOOKLAB_CI_RETRY_NEEDS_RUNNER_TEMP" >&2
    exit 64
fi
log="$(mktemp "${RUNNER_TEMP}/hooklab-gradle-429.XXXXXXXX")"
trap 'rm -f -- "$log"' EXIT
# Native instrumentation resolves a larger graph than ordinary Android CI.
# Only its workflow opts into the longer, bounded rate-limit recovery budget.
case "${HOOKLAB_CI_RETRY_PROFILE:-standard}" in
    standard) delays=(20 60) ;;
    native) delays=(20 60 120 240) ;;
    *) echo "HOOKLAB_CI_RETRY_PROFILE_INVALID" >&2; exit 64 ;;
esac
max_attempts=$(( ${#delays[@]} + 1 ))
attempt=1
while (( attempt <= max_attempts )); do
    : > "$log"
    echo "HOOKLAB_GRADLE_ATTEMPT=${attempt}/${max_attempts}"
    set +e
    "$@" 2>&1 | tee "$log"
    statuses=("${PIPESTATUS[@]}")
    set -e
    if (( ${statuses[1]} != 0 )); then
        echo "HOOKLAB_LOG_CAPTURE_FAILED rc=${statuses[1]}" >&2
        exit "${statuses[1]}"
    fi
    rc="${statuses[0]}"
    if (( rc == 0 )); then
        echo "HOOKLAB_GRADLE_VERIFIED_PASS attempt=${attempt}"
        exit 0
    fi
    # Retry dependency *rate limits* only; Java compilation, tests, lint,
    # authentication and non-429 dependency errors fail immediately.
    if ! grep -Eiq "Could not (GET|HEAD) ['\"]https://repo\.maven\.apache\.org/maven2/[^'\"]+['\"].*(Received status code 429|HTTP 429|Too Many Requests)" "$log" \
       || ! grep -Eq 'Could not resolve|Could not get resource' "$log" \
       || grep -Eiq '(^|[[:space:]])error:|[0-9]+ tests completed, [1-9][0-9]* failed|Lint found [1-9][0-9]* errors|You cannot add Provider instances to the Android SourceSet API|Received status code (401|403|404|5[0-9][0-9])' "$log"; then
        echo "HOOKLAB_GRADLE_NON_429_FAILURE rc=${rc}; no retry" >&2
        exit "$rc"
    fi
    if (( attempt == max_attempts )); then
        echo "HOOKLAB_GRADLE_429_RETRY_EXHAUSTED attempts=${max_attempts} original_rc=${rc}" >&2
        exit "$rc"
    fi
    delay=${delays[attempt-1]}
    echo "HOOKLAB_GRADLE_429_BACKOFF attempt=${attempt} seconds=${delay}" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
done
exit 70
