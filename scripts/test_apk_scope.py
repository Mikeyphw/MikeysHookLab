#!/usr/bin/env python3
"""APK ZIP regression checks for the current M8+WM01 libxposed scope."""
import tempfile
import unittest
import zipfile
from pathlib import Path

from check_apk import EXPECTED_PROPS, EXPECTED_SCOPE, ENTRY, PREFIX, verify, REGISTRY_MEMBER, REGISTRY_PATH


class ApkScopeContractTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="hooklab-apk-scope-")
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / "synthetic.apk"

    def packaged(self, *, scope=EXPECTED_SCOPE, entry=ENTRY, props=None,
                 legacy=False, dex=True, duplicate_scope=False, registry=REGISTRY_PATH.read_bytes()):
        if props is None:
            props = EXPECTED_PROPS
        with zipfile.ZipFile(self.apk, "w", compression=zipfile.ZIP_DEFLATED) as z:
            z.writestr(PREFIX + "java_init.list", entry + "\n")
            z.writestr(PREFIX + "module.prop",
                       "".join(f"{k}={v}\n" for k, v in props.items()))
            if scope is not None:
                z.writestr(PREFIX + "scope.list", scope)
                if duplicate_scope:
                    z.writestr(PREFIX + "scope.list", scope)
            if legacy:
                z.writestr("assets/xposed_init", "old module")
            if registry is not None:
                z.writestr(REGISTRY_MEMBER, registry)
            if dex:
                z.writestr("classes.dex", "synthetic test")
        return self.apk

    def reject(self, **kwargs):
        with self.assertRaises(AssertionError):
            verify(self.packaged(**kwargs))

    def test_valid_dynamic_scope(self):
        verify(self.packaged())

    def test_missing_scope_refused(self):
        self.reject(scope=None)

    def test_obtainx_injection_refused(self):
        self.reject(scope=EXPECTED_SCOPE + b"dev.bikram.obtainx\n")

    def test_reordered_scope_refused(self):
        self.reject(scope=b"com.fugo.wowsolitaire\n" +
                          b"com.looker.droidify\n" +
                          b"com.machiav3lli.fdroid\n" +
                          b"br.art.code.meucronogramacapilar\n")

    def test_missing_chatgpt_refused(self):
        self.reject(scope=EXPECTED_SCOPE.replace(b"com.openai.chatgpt\n", b""))

    def test_missing_word_master_refused(self):
        self.reject(scope=EXPECTED_SCOPE.replace(b"com.fugo.wowsolitaire\n", b""))

    def test_duplicate_scope_refused(self):
        self.reject(scope=EXPECTED_SCOPE + b"com.looker.droidify\n")

    def test_duplicate_zip_member_refused(self):
        self.reject(duplicate_scope=True)

    def test_crlf_scope_refused(self):
        self.reject(scope=EXPECTED_SCOPE.replace(b"\n", b"\r\n"))

    def test_bom_scope_refused(self):
        self.reject(scope=b"\xef\xbb\xbf" + EXPECTED_SCOPE)

    def test_static_scope_refused(self):
        changed=dict(EXPECTED_PROPS)
        changed["staticScope"]="true"
        self.reject(props=changed)

    def test_old_api_refused(self):
        changed=dict(EXPECTED_PROPS)
        changed["targetApiVersion"]="82"
        self.reject(props=changed)

    def test_legacy_entry_refused(self):
        self.reject(legacy=True)

    def test_wrong_entry_refused(self):
        self.reject(entry="com.example.Untrusted")

    def test_registry_missing_refused(self):
        self.reject(registry=None)

    def test_registry_changed_refused(self):
        self.reject(registry=b"{}")

    def test_missing_dex_refused(self):
        self.reject(dex=False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
