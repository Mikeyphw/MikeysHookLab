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

    def test_actual_build_and_cache_wiring(self):
        build = (ROOT / 'app/build.gradle').read_text()
        self.assertNotRegex(build, r'android\.sourceSets[^\n]*\.srcDir\(')
        self.assertIn("onVariants(selector().withBuildType('debug'))", build)
        self.assertIn('variant.sources.assets.addGeneratedSourceDirectory(dexTask) { it.outputDirectory }', build)
        self.assertIn('@OutputDirectory abstract DirectoryProperty getOutputDirectory()', build)
        self.assertEqual(build.count('transitive = false'), 2)
        android = (ROOT / '.github/workflows/android.yml').read_text()
        ci24 = (ROOT / '.github/workflows/hooklab-ci24.yml').read_text()
        # Restrict to the literal multiline cache patterns, not subsequent YAML keys.
        get_cache = lambda s: re.search(r'cache-dependency-path: \|\n((?: {12}\S[^\n]*\n)+)', s).group(1)
        self.assertEqual(get_cache(android), get_cache(ci24))
        self.assertIn('**/*.gradle', get_cache(android))
        self.assertIn('max-parallel: 4', ci24)
        self.assertIn('shard: [' + ', '.join(map(str, range(1,25))) + ']', ci24)
        job = android.split('  ci24:\n', 1)[1].split('  design:\n', 1)[0]
        self.assertIn('needs: [impact, source, android]', job)
        self.assertIn('!cancelled()', job)
        self.assertNotIn('needs.android.result', job)
        self.assertIn('uses: ./.github/workflows/hooklab-ci24.yml', job)
        self.assertIn('scripts/qa/mod01_gradle_graph.sh', android)
        self.assertLess(android.index('scripts/qa/mod01_gradle_graph.sh'), android.index('      - name: Assemble, test and lint'))


if __name__ == '__main__':
    unittest.main()
