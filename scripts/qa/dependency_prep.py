#!/usr/bin/env python3
"""Single-owner hosted dependency preparation and exact offline snapshot verification.

Only the two observed Kotlin coordinates may use the alternate mirror. The
publisher certificate and signatures, never mirror-provided checksums alone,
authenticate the bytes. Gradle is invoked only by the hosted shell routes.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, shutil, ssl, stat, subprocess, sys, tempfile, urllib.error, urllib.parse, urllib.request, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY_PATH = ROOT / 'gradle/ci/dependency-policy.json'
POLICY = json.loads(POLICY_PATH.read_text())
HEX = re.compile(r'[a-f0-9]{64}\Z')
SCHEMA = 'hooklab.dependencies.v2'
FULL_TASKS = [':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:testDebugUnitTest', ':app:lintDebug', ':probe-target:assembleDebug', ':probe-target:lintDebug', ':app:assembleRelease']
JVM_TASKS = [':app:testDebugUnitTest']
PROFILES = {'full': FULL_TASKS, 'jvm': JVM_TASKS}
GROUP = 'org.jetbrains.kotlinx'

def _inputs_digest(root, qualification):
    """Separate reusable resolver inputs from exact qualification machinery.

    Unknown build logic under Gradle/buildSrc/build-logic is conservatively
    included. CI changes cannot invalidate reusable dependency bytes, but
    do invalidate the qualification identity independently.
    """
    rows = subprocess.check_output(['git','-C',str(root),'ls-files','-z'],timeout=20).decode().strip('\0').split('\0')
    h=hashlib.sha256()
    for n in sorted(rows):
        build = (n.endswith(('.gradle','.gradle.kts','.lockfile')) or Path(n).name in ('gradle.properties','gradle-wrapper.properties','gradle-wrapper.jar','libs.versions.toml','verification-metadata.xml','versions.properties','gradle.lockfile') or n.startswith(('gradle/','buildSrc/','build-logic/')) or n == 'gradlew')
        qa = n.startswith(('.github/actions/','.github/workflows/','scripts/qa/','config/qualification-')) or n == 'scripts/ci_gradle_retry.sh'
        if build or (qualification and qa):
            p=root/n
            require(p.is_file() and not p.is_symlink(),'unsafe build graph path: '+n)
            h.update(n.encode()+b'\0'+digest(p).encode()+b'\n')
    return h.hexdigest()


def resolution_digest(root):
    return _inputs_digest(root, False)


def graph_digest(root):
    return _inputs_digest(root, True)


def seed_standard_gradle(home, private):
    """Copy only Gradle dependency/wrapper bytes into an isolated owner.

    Standard cache is a download seed, never lock/readiness evidence. Do not
    merge overlapping module stores or copy Gradle init scripts, task outputs,
    configuration caches, credentials, receipts, or symlinked cache data.
    """
    require(home.resolve() != private.resolve() and not home.is_symlink() and not private.is_symlink(), 'unsafe standard seed destination')
    seeded=[]
    for rel in ('caches/modules-2','wrapper/dists'):
        src=home/rel; dst=private/rel
        require(not dst.is_symlink(), 'unsafe private seed destination: '+rel)
        if dst.exists():
            require(dst.is_dir() and not dst.parent.is_symlink(), 'invalid private seed destination: '+rel)
            if any(dst.iterdir()): entries({rel:dst})
            continue  # official setup-java private cache or existing isolated seed
        if not src.exists() and not src.is_symlink(): continue
        require(not src.parent.is_symlink() and not src.is_symlink(), 'unsafe standard seed root: '+rel)
        require(src.is_dir(), 'non-directory standard seed root: '+rel)
        if not any(src.iterdir()): continue
        entries({rel:src})  # validate all source entries before copying
        dst.parent.mkdir(parents=True, exist_ok=True)
        require(not dst.parent.is_symlink(), 'unsafe private seed parent: '+rel)
        shutil.copytree(src,dst,ignore=shutil.ignore_patterns('*.lock','*.lck','gc.properties'))
        seeded.append(rel)
    return seeded


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def dump(path, obj):
    path.write_text(json.dumps(obj, sort_keys=True, indent=2) + '\n', encoding='utf-8')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('unexpected dependency redirect')


def fetch(url, dest, limit):
    require(urllib.parse.urlsplit(url).scheme == 'https', 'HTTPS required')
    opener = urllib.request.build_opener(NoRedirect())
    with opener.open(urllib.request.Request(url, headers={'User-Agent': 'HookLab-CI/1'}), timeout=30) as resp:
        require(resp.geturl() == url and resp.status == 200, 'unexpected dependency response')
        data = resp.read(limit + 1)
        require(0 < len(data) <= limit, 'dependency response size refused')
    dest.write_bytes(data)


def command(argv, **kw):
    return subprocess.run(argv, check=True, timeout=60, capture_output=True, text=True, **kw).stdout


def signed(home, artifact, signature, policy=POLICY):
    # --status-fd is machine output; use full signer and primary fingerprints.
    r = subprocess.run(['gpg', '--batch', '--no-tty', '--homedir', str(home), '--status-fd', '1', '--verify', str(signature), str(artifact)], capture_output=True, text=True, timeout=60)
    rows = [x.split()[1:] for x in r.stdout.splitlines() if x.startswith('[GNUPG:] ')]
    valid = [x for x in rows if x[0] == 'VALIDSIG']
    bad = {'BADSIG', 'ERRSIG', 'NO_PUBKEY', 'EXPSIG', 'EXPKEYSIG', 'REVKEYSIG', 'KEYREVOKED', 'KEYEXPIRED', 'SIGEXPIRED', 'NODATA', 'FAILURE'}
    require(r.returncode == 0 and len(valid) == 1 and len(valid[0]) >= 11 and valid[0][1] == policy['signing_fingerprint'] and valid[0][-1] == policy['primary_fingerprint'] and valid[0][8] in {'8', '9', '10', '11'} and not any(x[0] in bad for x in rows), 'publisher signature refused: ' + artifact.name)


def import_certificate(home, cert, policy=POLICY):
    command(['gpg', '--batch', '--no-tty', '--homedir', str(home), '--import', str(cert)])
    rows = command(['gpg', '--batch', '--no-tty', '--homedir', str(home), '--with-colons', '--list-keys']).splitlines()
    primary = []; subkeys = []; kind = None
    for row in rows:
        fields = row.split(':')
        if fields[0] in ('pub', 'sub'):
            kind = fields[0]
        elif fields[0] == 'fpr' and kind:
            (primary if kind == 'pub' else subkeys).append(fields[9]); kind = None
    require(primary == [policy['primary_fingerprint']] and policy['signing_fingerprint'] in subkeys, 'publisher certificate fingerprint refused')


def coordinates(policy=POLICY):
    for coordinate, extensions in sorted(policy['coordinates'].items()):
        group, module, version = coordinate.split(':')
        require(group == GROUP and module in {'kotlinx-coroutines-core-jvm', 'kotlinx-coroutines-bom'} and version == '1.7.1', 'unapproved mirror coordinate')
        for ext in extensions:
            require(ext in ('pom', 'module', 'jar'), 'unapproved artifact extension')
            yield f'{group.replace(".", "/")}/{module}/{version}/{module}-{version}.{ext}'


def unavailable(error):
    if isinstance(error, urllib.error.HTTPError):
        return error.code in (429, 502, 503, 504)
    if isinstance(error, urllib.error.URLError):
        return not isinstance(error.reason, ssl.SSLError)
    return isinstance(error, TimeoutError)


def canonical_checksum(rel, target, policy, downloader):
    # Independent canonical HTTPS metadata, never a mirror's own checksum.
    with tempfile.TemporaryDirectory(prefix='canonical-checksum-', dir=target.parent) as t:
        for alg, length in (('sha512', 128), ('sha256', 64)):
            sidecar=Path(t)/alg
            try: downloader(policy['central']+rel+'.'+alg,sidecar,4096)
            except urllib.error.HTTPError as exc:
                if exc.code==404 or unavailable(exc):continue
                raise
            except (urllib.error.URLError,TimeoutError) as exc:
                if unavailable(exc):continue
                raise
            line=sidecar.read_text(encoding='ascii').strip()
            match=re.fullmatch(r'([A-Fa-f0-9]{'+str(length)+r'})(?:[ \t]+[*]?'+re.escape(target.name)+r')?',line)
            require(match,'invalid canonical checksum format')
            expected=match.group(1).lower();h=hashlib.new(alg,target.read_bytes()).hexdigest()
            require(h==expected,'independent canonical checksum mismatch: '+rel)
            return {'method':'CANONICAL_CHECKSUM','algorithm':alg,'expected':expected}
    return None


def obtain_verified(rel, target, temp, policy=POLICY, downloader=fetch):
    try:
        downloader(policy['central']+rel,target,32*1024*1024)
        return {'endpoint':policy['central'],'verification':{'method':'CANONICAL_HTTPS'}}
    except (urllib.error.HTTPError,urllib.error.URLError,TimeoutError) as exc:
        if not unavailable(exc):raise
        print('DEPENDENCY_CANONICAL_UNAVAILABLE',rel,type(exc).__name__,getattr(exc,'code',''),flush=True)
    downloader(policy['mirror']+rel,target,32*1024*1024)
    proof=canonical_checksum(rel,target,policy,downloader)
    if proof is None:
        # Only if canonical checksums are unavailable, use the independently
        # pinned publisher. The key service is not needed in the normal route.
        home=temp/'gpg';cert=temp/'publisher.asc'
        if not home.exists():
            home.mkdir(mode=0o700)
            downloader(policy['certificate_url'],cert,1024*1024)
            import_certificate(home,cert,policy)
        sig=target.with_suffix(target.suffix+'.asc')
        downloader(policy['mirror']+rel+'.asc',sig,64*1024)
        signed(home,target,sig,policy)
        proof={'method':'PINNED_PGP','signer':policy['signing_fingerprint'],'primary':policy['primary_fingerprint'],'signature_sha256':digest(sig)}
    return {'endpoint':policy['mirror'],'verification':proof}


# Gradle 9.3.1's module parser treats component.url as ownership metadata,
# not an executable repository redirect. See the 1.1 metadata spec and
# GradleModuleMetadataParser.consumeComponent/consumeVariantLocation.
# Keep the local repository's admitted coordinate roster unchanged.
OWNER = 'kotlinx-coroutines-core'
JVM = 'kotlinx-coroutines-core-jvm'
BOM = 'kotlinx-coroutines-bom'


def module_location(module, version):
    return f'../../{module}/{version}/{module}-{version}.module'


def strict_json_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate metadata JSON key: ' + ascii(key)[:120])
        result[key] = value
    return result


def validate_coordinate(rel, target):
    group = GROUP; module = Path(rel).parts[-3]; version = Path(rel).parts[-2]
    expected = (group, module, version)
    def check(ok, why, observed=None):
        require(ok, f'{why}: path={rel} expected={expected!r} observed={ascii(observed)[:512]} sha256={digest(target)}' if not ok else '')
    check(module in {JVM, BOM} and version == '1.7.1' and rel == f'{GROUP.replace(".", "/")}/{module}/{version}/{module}-{version}{target.suffix}', 'unapproved metadata path', rel)
    if rel.endswith(".pom"):
        raw=target.read_bytes();check(len(raw)<=512*1024 and b"<!DOCTYPE" not in raw and b"<!ENTITY" not in raw,"unsafe POM")
        root=ET.fromstring(raw);ns={"m":"http://maven.apache.org/POM/4.0.0"}
        observed=tuple(root.findtext('m:'+name,namespaces=ns) for name in ('groupId','artifactId','version'))
        check(root.tag=="{http://maven.apache.org/POM/4.0.0}project" and observed==expected,"POM coordinate mismatch", observed)
    elif rel.endswith(".module"):
        check(target.stat().st_size<=512*1024,'oversized Gradle metadata')
        obj=json.loads(target.read_text(encoding='utf-8'),object_pairs_hook=strict_json_object)
        check(isinstance(obj,dict) and obj.get('formatVersion') in ('1.0','1.1'),'unsupported Gradle metadata format',obj.get('formatVersion') if isinstance(obj,dict) else type(obj).__name__)
        # component is optional in GMM. If present it is either the requested
        # Maven module itself or the exact root owner of the known JVM variant.
        c=obj.get('component')
        if 'component' in obj:
            check(isinstance(c,dict),'invalid Gradle component',c)
            actual=tuple(c.get(k) for k in ('group','module','version'))
            url=c.get('url')
            direct=actual==expected and 'url' not in c
            owned=(module==JVM and actual==(group,OWNER,version) and url==module_location(OWNER,version))
            check(direct or owned,'Gradle metadata coordinate/owner mismatch',{'coordinate':actual,'url':url})
        else:
            actual=None;url=None
        variants=obj.get('variants',[])
        check(isinstance(variants,list),'invalid Gradle variants')
        seen=set()
        for variant in variants:
            check(isinstance(variant,dict),'invalid Gradle variant')
            name=variant.get('name');check(isinstance(name,str) and bool(name) and name not in seen,'invalid/duplicate Gradle variant name',name);seen.add(name)
            # This repository serves only JVM/BOM artifacts. A variant in
            # either file must not delegate resolution outside that inventory.
            if 'available-at' in variant:
                ref=variant['available-at'];check(isinstance(ref,dict),'invalid Gradle variant location',ref)
                dest=tuple(ref.get(k) for k in ('group','module','version'))
                check(dest[0]==group and dest[1] in {JVM,BOM} and dest[1]!=module and dest[2]==version and ref.get('url')==module_location(dest[1],version) and not any(k in variant for k in ('files','dependencies','dependencyConstraints')),'unapproved Gradle variant location',ref)
            files=variant.get('files',[]);check(isinstance(files,list),'invalid Gradle file roster',name)
            for file in files:
                check(isinstance(file,dict),'invalid Gradle file record',name)
                # No absolute URLs, traversal, URI encoding or query/fragment.
                # Classifier files are metadata only; never expand mirror scope.
                filename=file.get('url');check(isinstance(filename,str) and re.fullmatch(re.escape(module+'-'+version)+r'(?:-[A-Za-z0-9][A-Za-z0-9._-]*)?\.[A-Za-z0-9]+',filename) is not None,'nonlocal module artifact',filename)
        return {'requested':list(expected),'component':list(actual) if actual is not None else None,'owner_url':url}
    elif rel.endswith(".jar"):
        with zipfile.ZipFile(target) as z:require(z.testzip() is None and bool(z.namelist()),"invalid JAR")

def authenticate_repository(state, policy=POLICY, downloader=fetch, profile="full"):
    # Never activate an incomplete private repository. Partial caches are seeds,
    # not an authority for fallback bytes; authenticate fresh coordinates.
    repo=state/'repository'; old=state/'repository.previous'
    require(not old.exists(),'unreconciled dependency repository')
    with tempfile.TemporaryDirectory(prefix='verified-maven-',dir=state) as t:
        temp=Path(t);staged=temp/'repository';staged.mkdir()
        receipt=[]
        def admit(rel):
            target=staged/rel;target.parent.mkdir(parents=True,exist_ok=True)
            proof=obtain_verified(rel,target,temp,policy,downloader)
            try:
                metadata=validate_coordinate(rel,target)
            except (ValueError,TypeError,KeyError,ET.ParseError) as exc:
                # Public, authenticated metadata only, bounded and isolated
                # from the repository/cache. Capture the exact failing bytes.
                if rel.endswith(('.module','.pom')) and target.stat().st_size<=512*1024:
                    diagnostic=state/'metadata-diagnostics'
                    require(not diagnostic.is_symlink(),'unsafe diagnostic directory')
                    diagnostic.mkdir(mode=0o700,exist_ok=True)
                    path=diagnostic/target.name
                    require(not path.exists() and not path.is_symlink(),'diagnostic already exists')
                    shutil.copyfile(target,path)
                    dump(diagnostic/'failure.json',{'schema':'hooklab.dependency.metadata-failure.v1','path':rel,'sha256':digest(target),'source_sha':os.environ.get('SOURCE_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'run_attempt':os.environ.get('GITHUB_RUN_ATTEMPT'),'reason':ascii(str(exc))[:2048],**proof})
                raise
            row={'path':rel,'sha256':digest(target),**proof}
            if metadata is not None:row['metadata_identity']=metadata
            receipt.append(row)
            return target
        for rel in (coordinates(policy) if profile=="full" else ()):
            target=admit(rel)
            if 'coroutines-bom' in rel and rel.endswith('.pom') and b'published-with-gradle-metadata' in target.read_bytes():
                admit(rel[:-4]+'.module')
        manifest={'primary':policy['primary_fingerprint'],'signer':policy['signing_fingerprint'],'files':receipt}
        if (temp/'publisher.asc').is_file():
            shutil.copyfile(temp/'publisher.asc',staged/'publisher.asc');manifest['certificate_sha256']=digest(staged/'publisher.asc')
        dump(staged/'.hooklab-attestation.json',manifest)
        if repo.exists():repo.rename(old)
        try:staged.rename(repo)
        except BaseException:
            if old.exists():old.rename(repo)
            raise
        if old.exists():shutil.rmtree(old)

def tracked_identity(root, run, attempt):
    def git(*args): return subprocess.check_output(['git', '-C', str(root), *args], text=True, timeout=20).strip()
    sha = git('rev-parse', 'HEAD'); tree = git('rev-parse', 'HEAD^{tree}')
    require(sha == os.environ.get('SOURCE_SHA') and re.fullmatch('[0-9a-f]{40}', sha), 'source identity drift')
    require(re.fullmatch('[1-9][0-9]*', str(run)) and re.fullmatch('[1-9][0-9]*', str(attempt)), 'run identity invalid')
    require(os.environ.get('GITHUB_REPOSITORY') == 'Mikeyphw/MikeysHookLab', 'repository identity drift')
    return {'source_sha': sha, 'source_tree': tree, 'run_id': str(run), 'run_attempt': str(attempt), 'repository': 'Mikeyphw/MikeysHookLab'}


def entries(roots):
    result = {}
    for prefix, root in roots.items():
        require(root.is_dir() and not root.is_symlink(), 'dependency root absent: ' + prefix)
        for p in sorted(root.rglob('*')):
            require(not p.is_symlink(), 'symlink in dependency snapshot')
            if p.is_dir(): continue
            if p.name.endswith(('.lock', '.lck')) or p.name == 'gc.properties': continue
            require(stat.S_ISREG(p.stat().st_mode), 'nonregular dependency entry')
            name = prefix + '/' + p.relative_to(root).as_posix()
            result[name] = digest(p)
    require(result, 'empty dependency snapshot')
    return result


def snapshot_roots(state, gradle):
    return {'modules': gradle / 'caches/modules-2', 'wrapper': gradle / 'wrapper/dists', 'repository': state / 'repository', 'locks': state / 'locks'}


def lock_inventory(state, profile='full'):
    locks = state / 'locks'; require(locks.is_dir(), 'missing locks')
    names = {p.name for p in locks.glob('*.lockfile') if p.is_file() and not p.is_symlink()}
    require(({'app.lockfile', 'probe-target.lockfile'} if profile=='full' else {'app.lockfile'}) <= names, 'Android and probe lock coverage absent')
    return sorted(names)


def seal(state, gradle, identity, graph, output, profile='full', elapsed_seconds=0, reused_ready=False, resolution=None):
    require(HEX.fullmatch(graph) and isinstance(resolution,str) and HEX.fullmatch(resolution), 'invalid graph/resolution digest')
    locks = lock_inventory(state, profile)
    mirror = json.loads((state / 'repository/.hooklab-attestation.json').read_text())
    data = {'schema': SCHEMA, **identity, 'graph_sha256': graph, 'resolution_sha256': resolution, 'profile': profile, 'tasks': PROFILES[profile], 'offline_result': 'PASS', 'elapsed_seconds': elapsed_seconds, 'reused_ready': reused_ready, 'locks': locks, 'mirror': mirror, 'files': entries(snapshot_roots(state, gradle))}
    verify_receipt(data, identity, graph, profile, resolution)
    verify_repository(state,data)
    dump(output, data)
    return data


def verify_receipt(data, identity, graph=None, profile='full', resolution=None):
    require(data.get('schema') == SCHEMA and all(data.get(k) == v for k, v in identity.items()), 'dependency receipt identity mismatch')
    require(HEX.fullmatch(str(data.get('graph_sha256', ''))) and (graph is None or data['graph_sha256'] == graph), 'dependency graph mismatch')
    require(HEX.fullmatch(str(data.get('resolution_sha256',''))) and (resolution is None or data['resolution_sha256']==resolution),'dependency resolution identity mismatch')
    require(data.get('offline_result') == 'PASS' and data.get('profile') == profile and data.get('tasks') == PROFILES[profile], 'offline readiness not established')
    require(type(data.get('elapsed_seconds')) is int and data['elapsed_seconds'] >= 0 and type(data.get('reused_ready')) is bool, 'invalid dependency timing evidence')
    files = data.get('files'); require(isinstance(files, dict) and files and all(isinstance(k, str) and '/' in k and not Path(k).is_absolute() and '..' not in Path(k).parts and HEX.fullmatch(str(v)) for k, v in files.items()), 'invalid file inventory')
    require(all(any(k.startswith(prefix + '/') for k in files) for prefix in ('modules', 'wrapper', 'repository', 'locks')), 'missing snapshot roots')
    locks = data.get('locks'); require(isinstance(locks, list) and locks == sorted(set(locks)) and ({'app.lockfile', 'probe-target.lockfile'} if profile=='full' else {'app.lockfile'}) <= set(locks) and all(files.get('locks/' + n) for n in locks), 'lock receipt mismatch')
    mirror = data.get('mirror', {}); require(mirror.get('primary') == POLICY['primary_fingerprint'] and mirror.get('signer') == POLICY['signing_fingerprint'], 'mirror trust mismatch')
    rows = mirror.get('files', []); roster=[r['path'] for r in rows]
    expected=list(coordinates()) if profile=='full' else [];bom=next((r for r in expected if 'coroutines-bom' in r and r.endswith('.pom')),None)
    require(roster==expected or roster==[x for r in expected for x in ([r,r[:-4]+'.module'] if r==bom else [r])],'mirror inventory mismatch')
    for r in rows:
        require(files.get('repository/'+r['path'])==r['sha256'],'repository artifact hash mismatch')
        if r['path'].endswith('.module'):
            module=Path(r['path']).parts[-3];version=Path(r['path']).parts[-2];expected=[GROUP,module,version]
            mid=r.get('metadata_identity');require(isinstance(mid,dict) and mid.get('requested')==expected and ((mid.get('component') in (None,expected) and mid.get('owner_url') is None) or (module==JVM and mid.get('component')==[GROUP,OWNER,version] and mid.get('owner_url')==module_location(OWNER,version))),'metadata receipt identity mismatch: '+r['path'])
        v=r['verification'];method=v['method']
        if method=='CANONICAL_HTTPS':require(r['endpoint']==POLICY['central'],'canonical origin mismatch')
        elif method=='CANONICAL_CHECKSUM':
            require(r['endpoint']==POLICY['mirror'] and v['algorithm'] in ('sha256','sha512') and re.fullmatch('[a-f0-9]{'+('64' if v['algorithm']=='sha256' else '128')+'}',v['expected']),'invalid canonical checksum proof')
            if v['algorithm']=='sha256':require(v['expected']==r['sha256'],'canonical checksum proof mismatch')
        elif method=='PINNED_PGP':
            require(r['endpoint']==POLICY['mirror'] and v['primary']==POLICY['primary_fingerprint'] and v['signer']==POLICY['signing_fingerprint'] and files.get('repository/'+r['path']+'.asc')==v['signature_sha256'],'mirror signature proof mismatch')
        else:raise ValueError('unknown verification method')
    require('repository/.hooklab-attestation.json' in files,'missing repository attestation')
    if any(r['verification']['method']=='PINNED_PGP' for r in rows):require(files.get('repository/publisher.asc')==mirror.get('certificate_sha256') and HEX.fullmatch(str(mirror.get('certificate_sha256',''))),'publisher certificate missing')

def verify_repository(state, data):
    repo=state/'repository';mirror=data['mirror']
    require(json.loads((repo/'.hooklab-attestation.json').read_text())==mirror,'repository attestation changed')
    with tempfile.TemporaryDirectory(prefix='verify-publisher-',dir=state) as t:
        home=Path(t)/'gpg'
        for r in mirror['files']:
            target=repo/r['path'];metadata=validate_coordinate(r['path'],target);require(r.get('metadata_identity')==metadata,'metadata identity projection drift: '+r['path']);v=r['verification']
            if v['method']=='CANONICAL_CHECKSUM':require(hashlib.new(v['algorithm'],target.read_bytes()).hexdigest()==v['expected'],'cached canonical checksum mismatch')
            if v['method']=='PINNED_PGP':
                if not home.exists():home.mkdir(mode=0o700);import_certificate(home,repo/'publisher.asc')
                signed(home,target,target.with_suffix(target.suffix+'.asc'))
        bom=next((r['path'] for r in mirror['files'] if 'coroutines-bom' in r['path'] and r['path'].endswith('.pom')),None)
        if bom:require((b'published-with-gradle-metadata' in (repo/bom).read_bytes())==any(r['path']==bom[:-4]+'.module' for r in mirror['files']),'BOM Gradle metadata omitted')


def verify_warm_seed(state, gradle, data, identity, resolution, profile):
    prior={k:data[k] for k in identity}
    require(prior['repository']==identity['repository'], 'prior repository mismatch')
    require(all(re.fullmatch('[0-9a-f]{40}',str(prior[k])) for k in ('source_sha','source_tree')) and all(re.fullmatch('[1-9][0-9]*',str(prior[k])) for k in ('run_id','run_attempt')), 'invalid prior identity')
    verify_receipt(data, prior, profile=profile, resolution=resolution)
    require(entries(snapshot_roots(state,gradle))==data['files'],'prior cache bytes changed')
    verify_repository(state,data)


def verify_unsealed_seed(state, gradle):
    # Reject unsafe restored paths before executing any Gradle process.
    # Partial cache completeness is not required; it is never readiness.
    require(not gradle.is_symlink() and not state.is_symlink(),'unsafe seed root')
    require(not (state/'ready.json').is_symlink(), 'unsafe readiness receipt')
    for label, root in snapshot_roots(state,gradle).items():
        require(not root.parent.is_symlink() and not root.is_symlink(),'unsafe seed path: '+label)
        if root.exists():
            require(root.is_dir(),'non-directory seed root: '+label)
            if any(root.iterdir()): entries({label:root})


def main():
    p = argparse.ArgumentParser(); p.add_argument('mode', choices=['graph', 'resolution', 'seed-standard', 'check-seed', 'authenticate', 'seal', 'verify', 'verify-receipt', 'warm-check']); p.add_argument('--root', type=Path, default=ROOT); p.add_argument('--state', type=Path); p.add_argument('--gradle', type=Path, default=Path(os.getenv('GRADLE_USER_HOME',str(Path.home()/'.gradle')))); p.add_argument('--graph'); p.add_argument('--profile',choices=PROFILES,default='full'); p.add_argument('--receipt', type=Path); p.add_argument('--source'); p.add_argument('--tree'); p.add_argument('--run-id', default=os.getenv('GITHUB_RUN_ID')); p.add_argument('--attempt', default=os.getenv('GITHUB_RUN_ATTEMPT')); p.add_argument('--elapsed-seconds', type=int, default=0); p.add_argument('--reused-ready', choices=('true','false'), default='false'); a = p.parse_args()
    try:
        if a.mode == 'graph':
            print(graph_digest(a.root)); return 0
        if a.mode == 'resolution':
            print(resolution_digest(a.root)); return 0
        if a.mode == 'verify-receipt':
            verify_receipt(json.loads(a.receipt.read_text()), {'source_sha':a.source,'source_tree':a.tree,'run_id':str(a.run_id),'run_attempt':str(a.attempt),'repository':'Mikeyphw/MikeysHookLab'},graph_digest(a.root),a.profile,resolution_digest(a.root)); print('DEPENDENCY_RECEIPT_PASS'); return 0
        require(os.getenv('GITHUB_ACTIONS') == 'true' and os.getenv('RUNNER_OS') == 'Linux', 'HOSTED_ONLY_DEPENDENCY_PREP_REFUSED')
        identity = tracked_identity(a.root, a.run_id, a.attempt)
        require(a.state == Path(os.environ['RUNNER_TEMP']) / 'hooklab-dependencies' and not a.state.is_symlink(), 'private runner dependency state required')
        a.state.mkdir(mode=0o700, exist_ok=True)
        if a.mode == 'seed-standard':
            require(a.gradle == Path(os.environ['RUNNER_TEMP']) / 'hooklab-gradle-home','private seed destination required')
            print('DEPENDENCY_STANDARD_SEED='+','.join(seed_standard_gradle(Path.home()/'.gradle',a.gradle)), flush=True)
        elif a.mode == 'check-seed': verify_unsealed_seed(a.state,a.gradle)
        elif a.mode == 'authenticate': authenticate_repository(a.state,profile=a.profile)
        elif a.mode == 'seal': seal(a.state, a.gradle, identity, a.graph, a.receipt, a.profile, a.elapsed_seconds, a.reused_ready=='true', resolution_digest(a.root))
        elif a.mode == 'warm-check':
            verify_warm_seed(a.state,a.gradle,json.loads(a.receipt.read_text()),identity,resolution_digest(a.root),a.profile)
        else:
            data = json.loads(a.receipt.read_text()); verify_receipt(data, identity, a.graph, a.profile, resolution_digest(a.root))
            require(entries(snapshot_roots(a.state, a.gradle)) == data['files'], 'dependency snapshot bytes changed');verify_repository(a.state,data)
        print('DEPENDENCY_'+a.mode.upper()+'_PASS', flush=True); return 0
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError, urllib.error.URLError, ET.ParseError, zipfile.BadZipFile) as e:
        print('DEPENDENCY_PREPARATION_REFUSED', str(e), file=sys.stderr); return 2


if __name__ == '__main__': raise SystemExit(main())
