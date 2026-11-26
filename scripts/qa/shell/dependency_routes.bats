#!/usr/bin/env bats
# Real shell entrypoints. Never run Gradle or network requests in these tests.
setup() {
    cd "$BATS_TEST_DIRNAME/../../.."
    export RUNNER_TEMP="$BATS_TEST_TMPDIR/runner"
    mkdir -p "$RUNNER_TEMP"
}

@test "retry wrapper preserves a successful command and cleans temporary output" {
    run bash scripts/ci_gradle_retry.sh bash -c 'printf "fixture-pass\n"'
    [ "$status" -eq 0 ]
    [[ "$output" == *fixture-pass* ]]
    [[ "$output" == *HOOKLAB_GRADLE_VERIFIED_PASS* ]]
    [ -z "$(find "$RUNNER_TEMP" -mindepth 1 -print -quit)" ]
}

@test "retry wrapper refuses deterministic failure and preserves its exit code" {
    run bash scripts/ci_gradle_retry.sh bash -c 'printf "error: deterministic compiler failure\n"; exit 17'
    [ "$status" -eq 17 ]
    [[ "$output" == *HOOKLAB_GRADLE_NON_429_FAILURE* ]]
    [[ "$output" != *HOOKLAB_GRADLE_429_BACKOFF* ]]
    [ -z "$(find "$RUNNER_TEMP" -mindepth 1 -print -quit)" ]
}

@test "dependency preparation and producer graph refuse non-hosted execution" {
    run env GITHUB_ACTIONS=false RUNNER_OS=Linux bash scripts/qa/dependency_prepare.sh full
    [ "$status" -ne 0 ]
    run env GITHUB_ACTIONS=false RUNNER_OS=Linux bash scripts/qa/mod01_gradle_graph.sh
    [ "$status" -eq 64 ]
    [[ "$output" == *MOD01_HOSTED_GRAPH_REFUSED* ]]
}

@test "dependency producer refuses stale source before any Gradle invocation" {
    run env GITHUB_ACTIONS=true RUNNER_OS=Linux SOURCE_SHA=invalid bash scripts/qa/mod01_gradle_graph.sh
    [ "$status" -eq 64 ]
    [[ "$output" == *MOD01_GRAPH_SOURCE_MISMATCH* ]]
}

@test "real composite seed and verification CLI routes" {
    run python3 -B -m unittest discover -s scripts/qa -p test_dependency_cli.py
    [ "$status" -eq 0 ]
    [[ "$output" == *'Ran 7 tests'* ]]
}
