#!/usr/bin/env python3
"""Verify real hosted apksigner output, identity, and monotonic APK metadata."""
from __future__ import annotations
import argparse, pathlib, re, sys

def check(ok, msg):
    if not ok: raise ValueError(msg)

def verify(signature: str, badging: str, expected: str, apk_size: int) -> tuple[int,str]:
    check(re.fullmatch(r'[0-9a-f]{64}', expected) is not None, 'Invalid expected certificate')
    check(apk_size > 4096, 'Empty or invalid APK')
    checks = re.findall(r'(?mi)^Verified using v[234](?:\.1)? scheme \(.*?\): true\s*$',signature)
    check(bool(checks), 'No modern APK signature scheme verified')
    signer = re.findall(r'(?mi)^Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$',signature)
    check(len(signer)==1 and signer[0].lower()==expected, 'APK certificate mismatch')
    check('Signer #2 certificate' not in signature, 'Unexpected additional APK signer')
    rows=re.findall(r"(?m)^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    check(len(rows)==1, 'Badging package metadata missing or ambiguous')
    pkg, version, name = rows[0]
    check(pkg=='com.mikeyphw.hooklab', 'Wrong package identity')
    check(int(version)>=5, 'Release versionCode regression')
    check(bool(re.fullmatch(r'[0-9A-Za-z_.-]{3,48}',name)), 'Invalid release versionName')
    return int(version),name

def main():
    a=argparse.ArgumentParser()
    a.add_argument('--apk',required=True)
    a.add_argument('--signature-report',required=True)
    a.add_argument('--badging',required=True)
    a.add_argument('--expected-cert',required=True)
    x=a.parse_args()
    try:
        apk=pathlib.Path(x.apk)
        version,name=verify(pathlib.Path(x.signature_report).read_text(),
            pathlib.Path(x.badging).read_text(),x.expected_cert.lower(),apk.stat().st_size)
    except (OSError,ValueError) as e:
        print('HOOKLAB_SIGNING_VERIFY_REFUSED='+str(e),file=sys.stderr)
        return 2
    print(f'HOOKLAB_SIGNED_PACKAGE_PASS versionCode={version} versionName={name}')
    print('HOOKLAB_SIGNED_CERTIFICATE_MATCH=PASS')
    return 0

if __name__=='__main__': raise SystemExit(main())
