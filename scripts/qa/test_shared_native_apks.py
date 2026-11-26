"""Exercise the actual shared-APK command in a clean disposable Git checkout."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

import adaptive_matrix as adaptive

SCRIPT = Path(__file__).resolve().with_name('shared_native_apks.py')

class SharedNativeApksCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '--allow-empty', '-qm', 'fixture')
        self.ident = {'source_sha': self.git('rev-parse', 'HEAD'), 'source_tree': self.git('rev-parse', 'HEAD^{tree}'), 'repository': 'Mikeyphw/MikeysHookLab', 'run_id': '123', 'run_attempt': '1'}
        self.env = {**os.environ, 'SOURCE_SHA':self.ident['source_sha'], 'GITHUB_REPOSITORY':self.ident['repository'], 'GITHUB_RUN_ID':'123', 'GITHUB_RUN_ATTEMPT':'1'}
        self.apks = self.root/'shared'; self.apks.mkdir()
        self.release = self.root/'release'; self.release.mkdir()
        for path in [self.apks/'app.apk', self.apks/'android-test.apk', self.release/'release.apk']:
            with zipfile.ZipFile(path, 'w') as z:
                z.writestr('AndroidManifest.xml', 'fixture'); z.writestr('classes.dex', path.name)
        self.write_receipts()
    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True, timeout=10).strip()
    def write_receipts(self):
        identity = adaptive.apk_identity(self.ident)
        (self.apks/'apk-build.json').write_text(json.dumps({**identity, 'apks':{k:adaptive.apk_file(self.apks/v) for k,v in adaptive.APK_FILES.items()}}))
        (self.release/'release-build.json').write_text(json.dumps({**identity, 'release':adaptive.apk_file(self.release/'release.apk'), 'release_qualified':False}))
    def cli(self, mode, release=None):
        return subprocess.run([sys.executable, '-B', str(SCRIPT), mode, '--apks', str(self.apks), '--release', str(release or self.release)], cwd=self.root, env=self.env, capture_output=True, text=True, timeout=10)
    def assert_pass(self, result):
        self.assertEqual(result.returncode,0,result.stderr)
    def assert_refused(self, result, reason):
        self.assertEqual(result.returncode,2,result.stderr)
        self.assertIn('SHARED_NATIVE_APKS_REFUSED',result.stderr)
        self.assertIn(reason,result.stderr)
        self.assertNotIn('Traceback',result.stderr)
    def test_verify_real_cli(self):
        self.assert_pass(self.cli('verify'))
    def test_all_apk_digests(self):
        for path in [self.apks/'app.apk',self.apks/'android-test.apk',self.release/'release.apk']:
            with self.subTest(path=path.name):
                original=path.read_bytes()
                try:
                    with zipfile.ZipFile(path,'a') as z:z.writestr('tamper','x')
                    self.assert_refused(self.cli('verify'),'drift')
                finally:path.write_bytes(original)
        self.assert_pass(self.cli('verify'))
    def test_all_receipt_identity_fields(self):
        for path in [self.apks/'apk-build.json',self.release/'release-build.json']:
            original=path.read_bytes()
            for key in self.ident:
                with self.subTest(path=path.name,key=key):
                    obj=json.loads(original);obj[key]='wrong';path.write_text(json.dumps(obj))
                    try:self.assert_refused(self.cli('verify'),'drift')
                    finally:path.write_bytes(original)
    def test_extra_member_refused(self):
        (self.release/'extra').write_text('x')
        self.assert_refused(self.cli('verify'),'roster mismatch')
    def test_publish_restore(self):
        self.assert_pass(self.cli('restore'))
        dest=self.root/'app/build/outputs/apk/release/app-release-unsigned.apk'
        self.assertEqual(dest.read_bytes(),(self.release/'release.apk').read_bytes())
        self.assert_refused(self.cli('restore'),'existing APK refused')
        out=self.root/'published'
        self.assert_pass(self.cli('publish',out))
        self.assert_pass(self.cli('verify',out))
        self.assert_refused(self.cli('publish',out),'fresh release bundle required')
    def test_restore_refuses_invalid_before_writing(self):
        (self.release/'extra').write_text('x')
        self.assert_refused(self.cli('restore'),'roster mismatch')
        self.assertFalse((self.root/'app').exists())

if __name__=='__main__':unittest.main()
