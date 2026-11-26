"""Actual hosted retry wrapper exercised with deterministic child commands; never Gradle."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
WRAPPER = ROOT / 'scripts/ci_gradle_retry.sh'
RATE = "Could not resolve all files\nCould not GET 'https://repo.maven.apache.org/maven2/org/example/1/example-1.pom'. Received status code 429 from server: Too Many Requests"

class HostedRetryTest(unittest.TestCase):
    def execute(self, profile, outcomes):
        with tempfile.TemporaryDirectory(prefix='hooklab-retry-test-') as name:
            root = Path(name)
            count, delays = root / 'count', root / 'delays'
            count.write_text('0'); delays.write_text('')
            sleep = root / 'sleep'
            sleep.write_text('#!/bin/sh\nprintf "%s\\n" "$1" >> "$HOOKLAB_TEST_DELAYS"\n')
            sleep.chmod(0o700)
            # Prewrite bounded synthetic child outcomes. The fake child is a
            # shell using builtins, so native validation does not repeatedly
            # start a heavy Python interpreter. All wrapper invocations are real.
            for i, (rc, output) in enumerate(outcomes, 1):
                (root / ('outcome-'+str(i))).write_text(str(rc)+'\n'+output+'\n')
            child = root / 'child'
            child.write_text("""#!/bin/sh
IFS= read -r n < "$HOOKLAB_TEST_COUNT"
n=$((n + 1))
printf '%s\n' "$n" > "$HOOKLAB_TEST_COUNT"
i=$n
[ "$i" -le "$HOOKLAB_TEST_LENGTH" ] || i=$HOOKLAB_TEST_LENGTH
{
    IFS= read -r rc
    while IFS= read -r line; do printf '%s\n' "$line"; done
} < "$HOOKLAB_TEST_ROOT/outcome-$i"
exit "$rc"
""")
            child.chmod(0o700)
            env = dict(os.environ, RUNNER_TEMP=str(root), PATH=str(root)+os.pathsep+os.environ['PATH'],
                       HOOKLAB_TEST_COUNT=str(count), HOOKLAB_TEST_DELAYS=str(delays),
                       HOOKLAB_TEST_ROOT=str(root), HOOKLAB_TEST_LENGTH=str(len(outcomes)))
            env.pop('HOOKLAB_CI_RETRY_PROFILE',None)
            if profile is not None: env['HOOKLAB_CI_RETRY_PROFILE']=profile
            result=subprocess.run(['bash',str(WRAPPER),str(child)],env=env,capture_output=True,text=True,timeout=15)
            self.assertFalse(list(root.glob('hooklab-gradle-429.*')),'temporary log leaked')
            return result,int(count.read_text()),delays.read_text().splitlines()

    def test_default_three_attempts_preserve_exit(self):
        p,n,delays=self.execute(None,[(17,RATE)])
        self.assertEqual((p.returncode,n,delays),(17,3,['20','60']))
        self.assertIn('RETRY_EXHAUSTED attempts=3 original_rc=17',p.stderr)

    def test_native_five_attempts_and_recovery(self):
        p,n,delays=self.execute('native',[(17,RATE)]*4+[(0,'OK')])
        self.assertEqual((p.returncode,n,delays),(0,5,['20','60','120','240']))
        self.assertIn('VERIFIED_PASS attempt=5',p.stdout)
        self.assertEqual(p.stdout.count('Received status code 429'),4)
        p,n,delays=self.execute('native',[(19,RATE)])
        self.assertEqual((p.returncode,n,delays),(19,5,['20','60','120','240']))
        self.assertIn('RETRY_EXHAUSTED attempts=5 original_rc=19',p.stderr)

    def test_refuse_non_429_and_mixed_failures(self):
        for msg in ('error: cannot find symbol', RATE+'\nerror: cannot find symbol', RATE+'\n1 tests completed, 1 failed', RATE+'\nLint found 1 errors', RATE+'\nYou cannot add Provider instances to the Android SourceSet API', 'You cannot add Provider instances to the Android SourceSet API', RATE+'\nReceived status code 403', RATE.replace('429','401').replace('Too Many Requests','Unauthorized'), RATE.replace('repo.maven.apache.org','other.example'), 'HTTP 429'):
            with self.subTest(msg=msg):
                p,n,delays=self.execute('native',[(9,msg)])
                self.assertEqual((p.returncode,n,delays),(9,1,[]))
                self.assertIn('NON_429_FAILURE rc=9',p.stderr)

    def test_invalid_profile_does_not_start_child(self):
        p,n,delays=self.execute('bogus',[(0,'OK')])
        self.assertEqual((p.returncode,n,delays),(64,0,[]))

    def test_profile_and_fixture_boundary(self):
        design=(ROOT/'.github/workflows/hooklab-design-contract.yml').read_text()
        native=(ROOT/'.github/workflows/hooklab-native-ui.yml').read_text()
        android=(ROOT/'.github/workflows/android.yml').read_text()
        fixture=(ROOT/'scripts/qa/termux_session_check.py').read_text()
        policy=(ROOT/'app/src/main/java/com/mikeyphw/hooklab/termux/TermuxCommandPolicy.java').read_text()
        self.assertIn('HOOKLAB_CI_RETRY_PROFILE: native',native)
        self.assertIn('HOOKLAB_CI_RETRY_PROFILE: native',android.split('  impact:\n',1)[0])
        self.assertNotIn('HOOKLAB_CI_RETRY_PROFILE',android.split('  impact:\n',1)[1])
        self.assertIn("(dot/'.zshenv').write_text('unsetopt GLOBAL_RCS\\n",fixture)
        self.assertNotIn('GLOBAL_RCS',policy)
        self.assertIn('termux_session_check.py --require-zsh',android.split('  terminal:\n',1)[1].split('  go:\n',1)[0])
        self.assertNotIn('termux_session_check.py',design)
        self.assertIn("always() && steps.browser_reference.outcome != 'skipped'",design)
        self.assertIn("always() && steps.native_capture.outcome != 'skipped'",native)
        self.assertIn('if-no-files-found: error',design)
        self.assertIn('if-no-files-found: error',native)

if __name__ == '__main__': unittest.main()
