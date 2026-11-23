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
attempt=1
while (( attempt <= 3 )); do
    : > "$log"
    echo "HOOKLAB_GRADLE_ATTEMPT=${attempt}/3"
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
    if ! grep -Eiq 'Received status code 429|HTTP 429|Too Many Requests' "$log" \
       || ! grep -Eq 'Could not resolve|Could not get resource|Could not GET' "$log"; then
        echo "HOOKLAB_GRADLE_NON_429_FAILURE rc=${rc}; no retry" >&2
        exit "$rc"
    fi
    if (( attempt == 3 )); then
        echo "HOOKLAB_GRADLE_429_RETRY_EXHAUSTED attempts=3 original_rc=${rc}" >&2
        exit "$rc"
    fi
    delay=$(( attempt == 1 ? 20 : 60 ))
    echo "HOOKLAB_GRADLE_429_BACKOFF attempt=${attempt} seconds=${delay}" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
done
exit 70
