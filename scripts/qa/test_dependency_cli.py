"""Exercise actual composite shell/CLI integration, never local Gradle.

The hosted environment variables below model an isolated unit-test fixture.
The real Python process and actual composite commands are exercised, but no
network, Gradle, or hosted qualification is claimed by these fixtures.
"""

import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest

import dependency_prep as dependency

ROOT = Path(__file__).resolve().parents[2]
OWNER = ROOT / '.github/actions/hooklab-dependency-prepare/action.yml'
CONSUMER = ROOT / '.github/actions/hooklab-dependency-consume/action.yml'
SHELL = ROOT / 'scripts/qa/dependency_prepare.sh'


def run_step(path, name):
    """Extract one exact inline/block run value from the named composite step."""
    steps = re.split(r'(?m)^    - name: ', path.read_text())[1:]
    matches = [step for step in steps if step.splitlines()[0] == name]
    if len(matches) != 1:
        raise AssertionError(f'expected exactly one step: {name}')
    lines = matches[0].splitlines()
    for index, line in enumerate(lines):
        if line.startswith('      run: '):
            value = line.removeprefix('      run: ')
            if value != '|':
                return value
            body = []
            for row in lines[index + 1:]:
                if row and not row.startswith('        '):
                    break
                body.append(row[8:] if row else '')
            return '\n'.join(body)
    raise AssertionError(f'no run command: {name}')


class DependencyCliTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.runner = self.root / 'runner'
        self.runner.mkdir()
        self.home = self.root / 'home'
        self.home.mkdir()
        self.private = self.runner / 'hooklab-gradle-home'
        self.state = self.runner / 'hooklab-dependencies'
        self.envfile = self.root / 'github-env'
        self.outfile = self.root / 'github-output'
        self.envfile.touch()
        self.outfile.touch()
        self.head = self.git('rev-parse', 'HEAD')
        self.tree = self.git('rev-parse', 'HEAD^{tree}')
        self.env = dict(os.environ)
        self.env.update(
            HOME=str(self.home),
            GITHUB_ACTIONS='true',
            GITHUB_REPOSITORY='Mikeyphw/MikeysHookLab',
            RUNNER_OS='Linux',
            RUNNER_ARCH='X64',
            RUNNER_TEMP=str(self.runner),
            SOURCE_SHA=self.head,
            GITHUB_RUN_ID='123',
            GITHUB_RUN_ATTEMPT='1',
            GITHUB_ENV=str(self.envfile),
            GITHUB_OUTPUT=str(self.outfile),
            PROFILE='full',
        )
        self.seed_command = run_step(
            OWNER, 'Stage only dependency modules and wrapper into private Gradle home'
        )
        self.run_ok(run_step(OWNER, 'Bind reusable resolver inputs and private state'))
        self.env.update(dict(line.split('=', 1) for line in self.envfile.read_text().splitlines()))
        self.assertEqual(self.env['HOOKLAB_DEPENDENCY_STATE'], str(self.state))
        self.assertEqual(self.env['GRADLE_USER_HOME'], str(self.private))

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(ROOT), *args], text=True).strip()

    def run_shell(self, command, **overrides):
        return subprocess.run(
            ['bash', '-e', '-o', 'pipefail', '-c', command],
            cwd=ROOT, env={**self.env, **overrides},
            capture_output=True, text=True, timeout=30,
        )

    def run_ok(self, command, **overrides):
        result = self.run_shell(command, **overrides)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result.stdout

    def fixture_file(self, root, relative, data):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def test_cold_owner_executes_actual_seed_command(self):
        out = self.run_ok(self.seed_command)
        self.assertIn('DEPENDENCY_SEED-STANDARD_PASS', out)
        self.assertTrue(self.state.is_dir())
        self.assertFalse((self.state / 'ready.json').exists())
        self.assertFalse((self.private / 'caches/modules-2').exists())

    def test_standard_private_cache_kept_and_wrapper_copied(self):
        standard = self.fixture_file(self.private, 'caches/modules-2/files-2.1/dep.jar', b'standard')
        self.fixture_file(self.home / '.gradle', 'caches/modules-2/files-2.1/dep.jar', b'old')
        self.fixture_file(self.home / '.gradle', 'wrapper/dists/gradle.zip', b'wrapper')
        out = self.run_ok(self.seed_command)
        self.assertIn('DEPENDENCY_STANDARD_SEED=wrapper/dists', out)
        self.assertEqual(standard.read_bytes(), b'standard')
        self.assertEqual((self.private / 'wrapper/dists/gradle.zip').read_bytes(), b'wrapper')
        self.assertFalse((self.state / 'ready.json').exists())

    def test_historical_home_cache_seeds_only_modules_and_wrapper(self):
        home = self.home / '.gradle'
        self.fixture_file(home, 'caches/modules-2/files-2.1/dep.jar', b'legacy')
        self.fixture_file(home, 'wrapper/dists/gradle.zip', b'wrapper')
        self.fixture_file(home, 'init.d/not-imported.gradle', b'not allowed')
        self.fixture_file(home, 'caches/build-cache-1/task-output', b'not allowed')
        out = self.run_ok(self.seed_command)
        self.assertIn('DEPENDENCY_STANDARD_SEED=caches/modules-2,wrapper/dists', out)
        self.assertEqual((self.private / 'caches/modules-2/files-2.1/dep.jar').read_bytes(), b'legacy')
        self.assertFalse((self.private / 'init.d').exists())
        self.assertFalse((self.private / 'caches/build-cache-1').exists())
        self.assertFalse((self.state / 'ready.json').exists())

    def test_missing_wrong_and_unsafe_state_and_source_refused(self):
        old = 'python3 -B scripts/qa/dependency_prep.py seed-standard'
        for command, overrides, reason in (
            (old, {}, 'private runner dependency state required'),
            (self.seed_command, {'HOOKLAB_DEPENDENCY_STATE': str(self.root / 'outside')}, 'private runner dependency state required'),
            (self.seed_command, {'GITHUB_ACTIONS': 'false'}, 'HOSTED_ONLY_DEPENDENCY_PREP_REFUSED'),
            (self.seed_command, {'SOURCE_SHA': '0' * 40}, 'source identity drift'),
        ):
            with self.subTest(command=command, overrides=overrides):
                result = self.run_shell(command, **overrides)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(reason, result.stderr)
        self.assertFalse((self.root / 'outside').exists())
        self.state.symlink_to(self.home, target_is_directory=True)
        result = self.run_shell(self.seed_command)
        self.assertEqual(result.returncode, 2)
        self.assertIn('private runner dependency state required', result.stderr)
        self.assertFalse((self.home / 'ready.json').exists())

    def test_unsafe_historical_cache_refused(self):
        source = self.fixture_file(self.home / '.gradle', 'caches/modules-2/dep.jar', b'fixture')
        (source.parent / 'unsafe').symlink_to(source)
        result = self.run_shell(self.seed_command)
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn('symlink', result.stderr)
        self.assertFalse((self.private / 'caches/modules-2').exists())
        self.assertFalse((self.state / 'ready.json').exists())

    def test_consumer_real_cli_verifies_exact_snapshot_and_refuses_tamper(self):
        # Synthetic JVM fixture, only for exercising the real verify entrypoint.
        # No actual Gradle or hosted acceptance is represented by this receipt.
        self.state.mkdir()
        dependency.authenticate_repository(self.state, profile='jvm')
        changed = self.fixture_file(self.private, 'caches/modules-2/dep.jar', b'fixture')
        self.fixture_file(self.private, 'wrapper/dists/gradle.zip', b'fixture')
        self.fixture_file(self.state, 'locks/app.lockfile', b'fixture')
        receipt = self.runner / 'dependency-receipt/ready.json'
        receipt.parent.mkdir()
        identity = {'repository': self.env['GITHUB_REPOSITORY'], 'source_sha': self.head,
                    'source_tree': self.tree, 'run_id': '123', 'run_attempt': '1'}
        graph = dependency.graph_digest(ROOT)
        dependency.seal(self.state, self.private, identity, graph, receipt,
                        profile='jvm', resolution=dependency.resolution_digest(ROOT))
        command = run_step(CONSUMER, 'Verify graph, attempt, lock coverage and every snapshot byte')
        self.assertIn('DEPENDENCY_VERIFY_PASS', self.run_ok(command, PROFILE='jvm', GRAPH=graph))
        changed.write_bytes(b'changed')
        result = self.run_shell(command, PROFILE='jvm', GRAPH=graph)
        self.assertEqual(result.returncode, 2)
        self.assertIn('dependency snapshot bytes changed', result.stderr)

    def test_all_dependency_cli_call_sites_supply_required_arguments(self):
        required = {
            'graph': set(), 'resolution': set(), 'seed-standard': {'--state'},
            'check-seed': {'--state'}, 'authenticate': {'--state', '--profile'},
            'warm-check': {'--state', '--graph', '--profile', '--receipt'},
            'seal': {'--state', '--graph', '--profile', '--receipt', '--elapsed-seconds', '--reused-ready'},
            'verify': {'--state', '--graph', '--profile', '--receipt'},
        }
        found = set()
        for path in (OWNER, CONSUMER, SHELL):
            for line in path.read_text().splitlines():
                match = re.search(r'python3 -B scripts/qa/dependency_prep\.py (.+)', line)
                if not match:
                    continue
                args = shlex.split(match[1].removesuffix(')'))
                mode = args[0]
                found.add(mode)
                self.assertIn(mode, required, f'{path}: {line}')
                self.assertTrue(required[mode] <= set(args[1:]), f'{path}: {line}')
        self.assertEqual(found, set(required))


if __name__ == '__main__':
    unittest.main()
