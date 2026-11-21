#!/usr/bin/env python3
"""Static signing contract; does not build APKs or read credentials."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
w=(root/'.github/workflows/stable-release.yml').read_text()
g=(root/'app/build.gradle').read_text()
setter=(root/'scripts/configure_signing.py').read_text()
verify=(root/'scripts/verify_signed_apk.py').read_text()
def check(cond, label):
    if not cond: raise RuntimeError('HOOKLAB_SIGNING_CONTRACT_REFUSED='+label)
    print('HOOKLAB_SIGNING_CONTRACT_PASS='+label)

check('workflow_dispatch:' in w and 'pull_request:' not in w and 'push:' not in w,'manual-only')
check("github.ref == 'refs/heads/main'" in w and 'environment: hooklab-signing' in w,'main-environment')
check('permissions:\n  contents: read' in w and 'persist-credentials: false' in w,'least-privilege')
check('setup-android@v4.0.4' in w and "cmdline-tools-version: '15859902'" in w
      and "'platforms;android-37.0'" in w and 'sdkmanager tools' not in w,'safe-sdk')
check(all(k in w for k in ('HOOKLAB_SIGNING_KEYSTORE_B64','HOOKLAB_SIGNING_STORE_PASSWORD',
      'HOOKLAB_SIGNING_KEY_ALIAS','HOOKLAB_SIGNING_KEY_PASSWORD','HOOKLAB_SIGNING_CERT_SHA256')),
      'five-environment-secrets')
check(':app:testDebugUnitTest :app:lintRelease :app:assembleRelease' in w
      and 'scripts/verify_signed_apk.py' in w and 'apksigner' in w,'hosted-tests-and-signature')
check('git rev-parse HEAD' in w and 'source-commit.txt' in w and 'hooklab-stable-release.apk.sha256' in w,
      'exact-source-artifact-provenance')
check('create-release' not in w and 'gh release create' not in w and 'retention-days: 14' in w,
      'no-public-release')
check('HOOKLAB_SIGNING_PARTIAL_CONFIG_REFUSED' in g and 'signingConfig signingConfigs.hooklabStable' in g
      and "storeType 'PKCS12'" in g,'stable-release-gradle-config')
check('environment_preflight()' in setter and "'main','type':'branch'" in setter
      and 'gh\',\'secret\',\'set\'' in setter and 'HOOKLAB_SIGNING_LOCAL_IDENTITY=REUSED' in setter,
      'main-only-local-idempotent-provisioning')
check('Signer #1 certificate SHA-256 digest' in verify and "'com.mikeyphw.hooklab'" in verify,
      'certificate-and-package-validation')
check(not list(root.rglob('*.p12')) and not list(root.rglob('*.jks')),
      'no-keystores-committed')
print('HOOKLAB_SIGNING_SOURCE_CONTRACT_PASS')
