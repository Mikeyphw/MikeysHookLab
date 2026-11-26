"""Regression coverage for the actual hosted graph verifier and CI scheduling.
Never execute Android Gradle in the local source-contract lane.
"""
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = [':sdk:jar', ':probe-payload:jar', ':app:buildMod01ProbeDex', ':app:mergeDebugAssets']
RELEASE = [':app:mergeReleaseAssets']


class GeneratedAssetGraphTest(unittest.TestCase):
    def verify(self, debug, release, expected):
        with tempfile.TemporaryDirectory() as temp:
            a, b = Path(temp) / 'debug', Path(temp) / 'release'
            for f, tasks in ((a, debug), (b, release)):
                f.write_text('\n'.join(t + ' SKIPPED' for t in tasks) + '\n')
            result = subprocess.run([sys.executable, '-B', str(ROOT / 'scripts/qa/mod01_gradle_graph.py'), str(a), str(b)], capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, expected, result.stderr)
            return result

    def test_exact_generated_task_dependency_order(self):
        self.assertIn('GRAPH_PASS', self.verify(DEBUG, RELEASE, 0).stdout)

    def test_missing_or_reordered_producers_refused(self):
        for rows in (DEBUG[1:], DEBUG[:1] + DEBUG[2:], DEBUG[:2] + DEBUG[3:], DEBUG[:2] + DEBUG[2:][::-1], DEBUG + [DEBUG[2]], DEBUG[2:] + DEBUG[:2]):
            with self.subTest(rows=rows):
                self.assertIn('GRAPH_REFUSED', self.verify(rows, RELEASE, 2).stderr)

    def test_release_probe_task_refused(self):
        for rows in (RELEASE + [DEBUG[2]], RELEASE + [DEBUG[1]], []):
            with self.subTest(rows=rows):
                self.verify(DEBUG, rows, 2)

    def test_graph_route_refuses_local_gradle(self):
        env = {**os.environ, 'GITHUB_ACTIONS': 'false'}
        result = subprocess.run(['bash', str(ROOT / 'scripts/qa/mod01_gradle_graph.sh')], cwd=ROOT, env=env, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 64)
        self.assertIn('HOSTED_GRAPH_REFUSED', result.stderr)

    def test_probe_inventory_java_array_boundary(self):
        build = (ROOT / 'app/build.gradle').read_text()
        self.assertNotIn('out.list().toSet()', build)
        self.assertIn('if (!exactProbeOutput(out))', build)
        self.assertIn('@groovy.transform.CompileStatic\n    static boolean exactProbeOutput(File out)', build)
        self.assertIn('String[] entries = out.list()', build)
        self.assertIn("entries == null || entries.length != 1 || !'classes.dex'.equals(entries[0])", build)
        self.assertIn('dex.isFile() && size > 0 && size <= 256L * 1024L', build)

    def test_hosted_graph_also_executes_actual_producer(self):
        script = (ROOT / 'scripts/qa/mod01_gradle_graph.sh').read_text()
        command = 'bash gradlew --no-daemon --console=plain --offline --init-script gradle/ci/locks.gradle :app:buildMod01ProbeDex --rerun-tasks'
        self.assertEqual(script.count(command), 1)
        self.assertLess(script.index('scripts/qa/mod01_gradle_graph.py'), script.index(command))
        self.assertLess(script.index(command), script.index('MOD01_GENERATED_PROBE_EXECUTION_PASS'))

    def test_actual_build_and_cache_wiring(self):
        build = (ROOT / 'app/build.gradle').read_text()
        self.assertNotRegex(build, r'android\.sourceSets[^\n]*\.srcDir\(')
        self.assertIn("onVariants(selector().withBuildType('debug'))", build)
        self.assertIn('variant.sources.assets.addGeneratedSourceDirectory(dexTask) { it.outputDirectory }', build)
        self.assertIn('@OutputDirectory abstract DirectoryProperty getOutputDirectory()', build)
        self.assertEqual(build.count('transitive = false'), 2)
        android = (ROOT / '.github/workflows/android.yml').read_text()
        ci24 = (ROOT / '.github/workflows/hooklab-ci24.yml').read_text()
        self.assertNotIn('cache: gradle', android)
        self.assertNotIn('cache: gradle', ci24)
        self.assertIn('max-parallel: 4', ci24)
        self.assertIn('shard: [' + ', '.join(map(str, range(1,25))) + ']', ci24)
        job = android.split('  ci24:\n', 1)[1].split('  design:\n', 1)[0]
        self.assertIn('needs: [impact, source, android]', job)
        self.assertIn('!cancelled()', job)
        self.assertNotIn('needs.android.result', job)
        self.assertIn('shared_dependencies: true', job)
        self.assertIn("dependency_ready: ${{ needs.android.outputs.dependency_ready == 'true' }}", job)
        self.assertIn('uses: ./.github/workflows/hooklab-ci24.yml', job)
        prep=(ROOT/'scripts/qa/dependency_prepare.sh').read_text()
        self.assertLess(prep.index('scripts/qa/mod01_gradle_graph.sh'),prep.index('DEPENDENCY_OFFLINE_EXECUTION_START'))

    def test_android_cache_progress_is_attempt_scoped_and_retained_on_failure(self):
        owner=(ROOT/'.github/actions/hooklab-dependency-prepare/action.yml').read_text()
        consumer=(ROOT/'.github/actions/hooklab-dependency-consume/action.yml').read_text()
        paths=lambda text: re.findall(r'path: \|\n((?: {10}[^\n]+\n)+)',text)
        all_paths=paths(owner)+paths(consumer)
        self.assertEqual(len(all_paths),5)
        self.assertEqual(len(set(all_paths)),1)
        self.assertIn('modules-2',all_paths[0]);self.assertIn('repository',all_paths[0]);self.assertIn('locks',all_paths[0]);self.assertIn('ready.json',all_paths[0])
        self.assertIn('$prefix-ready-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT',owner)
        self.assertIn('$prefix-progress-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT',owner)
        self.assertIn("always() && !cancelled() && steps.prepare.outcome == 'failure'",owner)
        self.assertIn('lookup-only: true',owner);self.assertIn('fail-on-cache-miss: true',consumer)
        self.assertNotIn('actions/cache/save',consumer);self.assertNotIn('restore-keys:',consumer)
        self.assertIn('dependency_prep.py verify',consumer)
        self.assertNotIn('continue-on-error:',owner+consumer)

    def test_dependency_blocked_summary_and_diagnostic_wiring(self):
        ci=(ROOT/'.github/workflows/hooklab-ci24.yml').read_text().split('  summary:',1)[1]
        self.assertIn('needs: [prepare, junit]',ci)
        self.assertIn("needs.junit.result != 'skipped' && needs.junit.result != 'cancelled'",ci)
        self.assertIn("(inputs.shared_dependencies && inputs.dependency_ready) || (!inputs.shared_dependencies && needs.prepare.result == 'success' && needs.prepare.outputs.ready == 'true')",ci)
        owner=(ROOT/'.github/actions/hooklab-dependency-prepare/action.yml').read_text()
        self.assertIn("failure() && steps.prepare.outcome == 'failure'",owner)
        self.assertIn('hooklab-dependency-metadata-refusal-${{ github.run_id }}-${{ github.run_attempt }}',owner)
        self.assertIn('metadata-diagnostics/',owner)
        self.assertNotIn('continue-on-error:',ci+owner)


if __name__ == '__main__':
    unittest.main()
