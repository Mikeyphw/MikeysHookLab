"""Executable HTTP, trust, exact snapshot and offline route regressions. No Gradle."""
import hashlib, io, json, os, shutil, subprocess, tempfile, unittest, urllib.error, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch
import dependency_prep as d

ROOT=Path(__file__).resolve().parents[2]
REL='org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.1/kotlinx-coroutines-core-jvm-1.7.1.jar'


def artifact(rel,bom_module=False):
    module=Path(rel).parts[-3];version=Path(rel).parts[-2]
    if rel.endswith('.pom'):
        return ('<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.jetbrains.kotlinx</groupId><artifactId>'+module+'</artifactId><version>'+version+'</version>'+('<!-- published-with-gradle-metadata -->' if bom_module and 'bom' in module else '')+'</project>').encode()
    if rel.endswith('.module'):return json.dumps({'component':{'group':'org.jetbrains.kotlinx','module':module,'version':version},'variants':[]}).encode()
    b=io.BytesIO()
    with zipfile.ZipFile(b,'w') as z:z.writestr('META-INF/MANIFEST.MF','fixture')
    return b.getvalue()


def http(url,code):return urllib.error.HTTPError(url,code,'fixture',{},None)


class DependencyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.state=self.root/'state';self.state.mkdir();self.calls=[]
    def canonical(self,url,dest,limit):
        self.calls.append(url);self.assertTrue(url.startswith(d.POLICY['central']));dest.write_bytes(artifact(url[len(d.POLICY['central']):],True))
    def test_canonical_does_not_fetch_mirror_or_keyserver(self):
        d.authenticate_repository(self.state,downloader=self.canonical);manifest=json.loads((self.state/'repository/.hooklab-attestation.json').read_text());self.assertEqual(len(manifest['files']),5);self.assertTrue(all(x['verification']['method']=='CANONICAL_HTTPS' for x in manifest['files']))
    def test_independent_checksum_429_fallback(self):
        target=self.root/'artifact.jar';data=artifact(REL)
        def network(url,dest,limit):
            self.calls.append(url)
            if url==d.POLICY['central']+REL:raise http(url,429)
            if url==d.POLICY['mirror']+REL:dest.write_bytes(data)
            elif url==d.POLICY['central']+REL+'.sha512':dest.write_text(hashlib.sha512(data).hexdigest())
            else:self.fail(url)
        r=d.obtain_verified(REL,target,self.root,downloader=network);self.assertEqual(r['verification']['method'],'CANONICAL_CHECKSUM');self.assertEqual(target.read_bytes(),data);self.assertEqual(len(self.calls),3)
    def test_checksum_mismatch_never_downgrades(self):
        target=self.root/'artifact.jar'
        def network(url,dest,limit):
            self.calls.append(url)
            if url==d.POLICY['central']+REL:raise http(url,429)
            if url==d.POLICY['mirror']+REL:dest.write_bytes(b'wrong')
            elif url==d.POLICY['central']+REL+'.sha512':dest.write_text('0'*128)
            else:self.fail(url)
        with self.assertRaisesRegex(ValueError,'checksum mismatch'):d.obtain_verified(REL,target,self.root,downloader=network)
        self.assertEqual(len(self.calls),3)
    def test_non_transient_http_refused(self):
        for code in (401,403,404):
            self.calls.clear()
            def network(url,dest,limit):self.calls.append(url);raise http(url,code)
            with self.assertRaises(urllib.error.HTTPError):d.obtain_verified(REL,self.root/'a.jar',self.root,downloader=network)
            self.assertEqual(self.calls,[d.POLICY['central']+REL])
    def test_failed_repository_staging_preserves_previous_bytes(self):
        d.authenticate_repository(self.state,downloader=self.canonical);before=d.entries({'repository':self.state/'repository'})
        def network(url,dest,limit):dest.write_bytes(b'not Maven metadata')
        with self.assertRaises((ValueError,ET.ParseError)) as error:d.authenticate_repository(self.state,downloader=network)
        self.assertNotIsInstance(error.exception,AssertionError);self.assertEqual(before,d.entries({'repository':self.state/'repository'}))
    def test_profile_jvm_no_mirror_fetch(self):
        def forbidden(*args):self.fail('unexpected network')
        d.authenticate_repository(self.state,downloader=forbidden,profile='jvm');self.assertEqual(json.loads((self.state/'repository/.hooklab-attestation.json').read_text())['files'],[])
    def test_snapshot_and_wrong_identity(self):
        d.authenticate_repository(self.state,downloader=self.canonical)
        g=self.root/'gradle';(g/'caches/modules-2').mkdir(parents=True);(g/'wrapper/dists').mkdir(parents=True);(self.state/'locks').mkdir()
        for p in (g/'caches/modules-2/dep.jar',g/'wrapper/dists/gradle.jar',self.state/'locks/app.lockfile',self.state/'locks/probe-target.lockfile'):p.write_text('fixture')
        ident={'repository':'Mikeyphw/MikeysHookLab','source_sha':'a'*40,'source_tree':'b'*40,'run_id':'1','run_attempt':'2'};out=self.root/'ready.json';receipt=d.seal(self.state,g,ident,'c'*64,out);d.verify_receipt(receipt,ident,'c'*64);d.verify_repository(self.state,receipt)
        with self.assertRaisesRegex(ValueError,'identity'):d.verify_receipt(receipt,{**ident,'run_attempt':'3'},'c'*64)
        with self.assertRaisesRegex(ValueError,'graph'):d.verify_receipt(receipt,ident,'d'*64)
        (g/'caches/modules-2/dep.jar').write_text('changed');self.assertNotEqual(d.entries(d.snapshot_roots(self.state,g)),receipt['files'])
        (g/'caches/modules-2/unsafe').symlink_to(g/'wrapper/dists/gradle.jar')
        with self.assertRaisesRegex(ValueError,'symlink'):d.entries(d.snapshot_roots(self.state,g))
    @unittest.skipUnless(shutil.which('gpg'),'gpg required for real signature fixture')
    def test_real_pgp_fallback_and_tamper(self):
        # A disposable test key proves the real GnuPG verification path; it is
        # not a substitute for the pinned JetBrains publisher in production.
        home=self.root/'signer';home.mkdir(mode=0o700)
        cmd=['gpg','--batch','--no-tty','--homedir',str(home),'--pinentry-mode','loopback','--passphrase','']
        subprocess.run(cmd+['--quick-generate-key','HookLab fixture <fixture@example.invalid>','ed25519','sign','1d'],check=True,capture_output=True,timeout=30)
        out=subprocess.check_output(cmd+['--with-colons','--list-keys'],text=True);fpr=next(x.split(':')[9] for x in out.splitlines() if x.startswith('fpr:'))
        subprocess.run(cmd+['--quick-add-key',fpr,'ed25519','sign','1d'],check=True,capture_output=True,timeout=30)
        out=subprocess.check_output(cmd+['--with-colons','--list-keys'],text=True);fingerprints=[x.split(':')[9] for x in out.splitlines() if x.startswith('fpr:')];sub=fingerprints[-1]
        payload=self.root/'payload';payload.write_bytes(artifact(REL));sig=self.root/'payload.asc'
        subprocess.run(cmd+['--local-user',sub+'!','--armor','--detach-sign','--output',str(sig),str(payload)],check=True,capture_output=True,timeout=30)
        cert=subprocess.check_output(cmd+['--armor','--export',fpr]);policy={**d.POLICY,'primary_fingerprint':fpr,'signing_fingerprint':sub}
        def network(url,dest,limit):
            if url.startswith(policy['central']):raise http(url,429)
            if url==policy['certificate_url']:dest.write_bytes(cert)
            elif url==policy['mirror']+REL:dest.write_bytes(payload.read_bytes())
            elif url==policy['mirror']+REL+'.asc':dest.write_bytes(sig.read_bytes())
            else:self.fail(url)
        t=self.root/'temp';t.mkdir();target=self.root/'target.jar';proof=d.obtain_verified(REL,target,t,policy,network);self.assertEqual(proof['verification']['method'],'PINNED_PGP')
        target.write_bytes(b'tamper')
        with self.assertRaisesRegex(ValueError,'signature refused'):d.signed(t/'gpg',target,target.with_suffix('.jar.asc'),policy)
        with self.assertRaisesRegex(ValueError,'signature refused'):d.signed(t/'gpg',payload,sig,{**policy,'signing_fingerprint':'A'*40})
    def test_local_entrypoints_refuse_before_gradle(self):
        env={**os.environ,'GITHUB_ACTIONS':'false'}
        for command in (['bash',str(ROOT/'scripts/qa/dependency_prepare.sh'),'full'],['python3','-B',str(ROOT/'scripts/qa/dependency_prep.py'),'authenticate']):
            r=subprocess.run(command,cwd=ROOT,env=env,capture_output=True,timeout=15);self.assertNotEqual(r.returncode,0)


if __name__=='__main__':unittest.main()
