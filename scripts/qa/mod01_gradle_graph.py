"""Verify hosted dry-run dependency order, not build or device acceptance."""
import pathlib
import sys


def roster(path):
    return [line.split()[0] for line in pathlib.Path(path).read_text().splitlines()
            if line.startswith(':') and line.endswith(' SKIPPED')]


def main():
    if len(sys.argv) != 3:
        return 64
    debug, release = map(roster, sys.argv[1:])
    producer = ':app:buildMod01ProbeDex'
    consumers = ':app:mergeDebugAssets', ':app:mergeReleaseAssets'
    if not (debug.count(producer) == 1 and debug.count(consumers[0]) == 1
            and debug.index(producer) < debug.index(consumers[0])
            and all(debug.count(jar) == 1 and debug.index(jar) < debug.index(producer)
                    for jar in (':sdk:jar', ':probe-payload:jar'))
            and release.count(consumers[1]) == 1 and producer not in release
            and ':probe-payload:jar' not in release):
        print('MOD01_GENERATED_ASSET_GRAPH_REFUSED', file=sys.stderr)
        return 2
    print('MOD01_GENERATED_ASSET_GRAPH_PASS')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
