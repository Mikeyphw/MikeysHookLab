#!/usr/bin/env python3
"""Pure Python signing verifier fixture; no key, Gradle, or network needed."""
import sys, pathlib, unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from verify_signed_apk import verify
import configure_signing as setup
from unittest import mock
CERT='a'*64
SIGNATURE='Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: '+CERT+'\n'
BADGING="package: name='com.mikeyphw.hooklab' versionCode='5' versionName='0.4.1'\n"
class SigningContractTests(unittest.TestCase):
    def test_success(self): self.assertEqual(verify(SIGNATURE,BADGING,CERT,8000),(5,'0.4.1'))
    def test_wrong_cert(self):
        with self.assertRaises(ValueError): verify(SIGNATURE,BADGING,'b'*64,8000)
    def test_wrong_package(self):
        with self.assertRaises(ValueError): verify(SIGNATURE,BADGING.replace('com.mikeyphw.hooklab','example'),CERT,8000)
    def test_missing_v2(self):
        with self.assertRaises(ValueError): verify('Signer #1 certificate SHA-256 digest: '+CERT,BADGING,CERT,8000)
    def test_two_signers(self):
        with self.assertRaises(ValueError): verify(SIGNATURE+'Signer #2 certificate SHA-256 digest: '+'b'*64,BADGING,CERT,8000)
    def test_version_regression(self):
        with self.assertRaises(ValueError): verify(SIGNATURE,BADGING.replace("versionCode='5'","versionCode='4'"),CERT,8000)
    def test_environment_rejects_wildcard_branch(self):
        policy={'deployment_branch_policy':{'protected_branches':False,'custom_branch_policies':True}}
        rows={'total_count':1,'branch_policies':[{'name':'*','type':'branch'}]}
        with mock.patch.object(setup,'call',return_value='{"login":"Mikeyphw"}'), \
             mock.patch.object(setup,'gh_api',side_effect=[policy,rows]):
            with self.assertRaises(setup.Refused): setup.environment_preflight()
    def test_environment_accepts_main_only(self):
        policy={'deployment_branch_policy':{'protected_branches':False,'custom_branch_policies':True}}
        rows={'total_count':1,'branch_policies':[{'name':'main','type':'branch'}]}
        with mock.patch.object(setup,'call',return_value='{"login":"Mikeyphw"}'), \
             mock.patch.object(setup,'gh_api',side_effect=[policy,rows]):
            setup.environment_preflight()
if __name__=='__main__': unittest.main()
