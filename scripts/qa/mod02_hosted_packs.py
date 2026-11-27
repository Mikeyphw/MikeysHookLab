#!/usr/bin/env python3
"""Hosted-only D8 build, independent Java/Go verification and real ART test.
Only public test key and signed test packs leave temporary build storage.
"""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,sys,tempfile,zipfile
from qualification import canonical,require,git,strict_json
from module_sources import ROOT,ROSTER,project,manifest,settings_schema

TEST='com.mikeyphw.hooklab.qa.Mod02PackArtTest#verifiedMigratedDex'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(*args,cwd=ROOT,**kw):return subprocess.run([str(x) for x in args],cwd=cwd,check=True,timeout=180,**kw)
def identity():
    require(os.getenv('GITHUB_ACTIONS')=='true' and os.getenv('RUNNER_OS')=='Linux','hosted only')
    source=os.environ['SOURCE_SHA'];rid=os.environ['GITHUB_RUN_ID'];attempt=os.environ['GITHUB_RUN_ATTEMPT']
    require(re.fullmatch('[a-f0-9]{40}',source) and git(ROOT,'rev-parse','HEAD')==source and git(ROOT,'status','--porcelain','--untracked-files=no')=='','exact tracked source')
    require(re.fullmatch('[1-9][0-9]*',rid) and re.fullmatch('[1-9][0-9]*',attempt),'run identity')
    return {'source_sha':source,'source_tree':git(ROOT,'rev-parse','HEAD^{tree}'),'run_id':int(rid),'run_attempt':int(attempt)}
def verify(out,ident):
    require(out.is_dir() and not out.is_symlink(),'artifact directory');names={'publisher-test.der','receipt.json'}|{m+'.hookpack' for m in ROSTER}
    require({p.name for p in out.iterdir()}==names and all(p.is_file() and not p.is_symlink() for p in out.iterdir()),'exact artifact inventory')
    receipt=strict_json((out/'receipt.json').read_bytes());require(receipt=={'schema':'hooklab.mod02.hosted.v1',**ident,'status':'SIGNED_TEST_PACKS_VERIFIED_NOT_ACTIVATED','files':{name:sha(out/name) for name in sorted(names-{'receipt.json'})}},'artifact receipt mismatch')
    return receipt

def verify_artifacts(out,personal,evidence,ident,cli):
    """Read-only independent receipt, signature, source and payload parity check.
    The caller supplies a locally built reviewed Go verifier; no key is created here.
    This validates evidence consistency, not authenticity of a remote runner.
    """
    signed=verify(out,ident)
    require(personal.is_dir() and not personal.is_symlink(),'personal directory')
    expected={m+'/'+name for m in ROSTER for name in ('manifest.json','classes.dex','provenance.json')}|{'wordmaster/ui.dex','wordmaster/ui-resources.json'}
    paths={p.relative_to(personal).as_posix():p for p in personal.rglob('*')}
    require(set(paths)==expected|set(ROSTER)|{'receipt.json'} and all(not p.is_symlink() and (p.is_dir() if n in ROSTER else p.is_file()) for n,p in paths.items()),'personal input inventory')
    receipt=strict_json((personal/'receipt.json').read_bytes())
    require(receipt=={'schema':'hooklab.mod02.personal-inputs.v1',**ident,'status':'UNSIGNED_NOT_INSTALLABLE','files':{n:sha(paths[n]) for n in sorted(expected)}},'personal receipt mismatch')
    for module in sorted(ROSTER):
        archive=out/(module+'.hookpack');raw=run(cli,'module','inspect',archive,out/'publisher-test.der',capture_output=True).stdout
        metadata=strict_json(raw);require(metadata['archive_sha256']==signed['files'][archive.name] and metadata['signer_sha256']==signed['files']['publisher-test.der'],'pack inspection mismatch')
        require(metadata['manifest']==manifest(module),'migration manifest mismatch')
        provenance={'schema':'hooklab.pack.provenance.v1',**ident,'settings_schema':settings_schema(module),'channel':'test'}
        require(metadata['provenance']==provenance,'signed pack provenance mismatch')
        require(strict_json(paths[module+'/manifest.json'].read_bytes())==metadata['manifest'],'personal manifest mismatch')
        require(strict_json(paths[module+'/provenance.json'].read_bytes())=={**provenance,'channel':'personal'},'personal provenance mismatch')
        # The signed parser verified ZIP structure and member bounds; compare the
        # independently published unsigned bytes to those exact admitted DEX bytes.
        with zipfile.ZipFile(archive) as z:
            require(hashlib.sha256(z.read('runtime/classes.dex')).hexdigest()==receipt['files'][module+'/classes.dex'],'personal DEX differs from tested pack')
            if module=='wordmaster':
                for member,filename in (('ui/classes.dex','ui.dex'),('ui/resources.json','ui-resources.json')):
                    require(hashlib.sha256(z.read(member)).hexdigest()==receipt['files'][module+'/'+filename],'personal UI input differs from tested pack')
    require(evidence.is_file() and not evidence.is_symlink(),'ART receipt missing')
    require(strict_json(evidence.read_bytes())=={'schema':'hooklab.mod02.art.v1',**ident,'status':'ART_LOAD_PASS_NOT_VECTOR_ACCEPTANCE','test':TEST,'pack_receipt_sha256':sha(out/'receipt.json')},'ART receipt mismatch')
    return signed

def build(out,personal,ui_fixture):
    ident=identity();require(not out.exists(),'fresh output required');out.mkdir(parents=True)
    require(personal is not None and not personal.exists() and personal.resolve()!=out.resolve(),'fresh personal signing output required');personal.mkdir(parents=True)
    require(ui_fixture is not None and not ui_fixture.exists() and ui_fixture.resolve() not in (out.resolve(),personal.resolve()),'fresh private UI fixture required');ui_fixture.mkdir(mode=0o700,parents=True)
    sdk=Path(os.environ['ANDROID_HOME']);android=sdk/'platforms/android-37.0/android.jar';d8=sdk/'build-tools/36.0.0/d8';require(android.is_file() and d8.is_file(),'exact hosted SDK absent')
    with tempfile.TemporaryDirectory(prefix='hooklab-mod02-') as temp:
        work=Path(temp);classes=work/'classes';classes.mkdir();sdkclasses=work/'sdk';sdkclasses.mkdir();sdkjar=work/'sdk.jar';cli=work/'hooklabctl';key=work/'ephemeral.pem';private=work/'ephemeral.der';public=out/'publisher-test.der'
        sdkfiles=sorted(ROOT.glob('sdk/src/main/java/**/*.java'));runtime=sorted(ROOT.glob('runtime/src/main/java/**/*.java'))
        run('javac','--release','17','-Xlint:all','-Werror','-d',sdkclasses,*sdkfiles);run('jar','--create','--file',sdkjar,'-C',sdkclasses,'.')
        run('javac','--release','17','-Xlint:all','-Werror','-d',classes,*sdkfiles,*runtime,ROOT/'scripts/qa/Mod02PackChecks.java')
        run('go','build','-o',cli,'./cmd/hooklabctl',cwd=ROOT/'tools/hooklabctl')
        run('openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',key)
        run('openssl','pkcs8','-topk8','-nocrypt','-in',key,'-outform','DER','-out',private);private.chmod(0o600)
        run('openssl','pkey','-in',key,'-pubout','-outform','DER','-out',public)
        for module in sorted(ROSTER):
            home=work/module;home.mkdir();src=home/'src';src.mkdir();compiled=home/'classes';compiled.mkdir();dexdir=home/'dex';dexdir.mkdir()
            for name,content in project(module).items():(src/name).write_text(content)
            mf=home/'manifest.json';mf.write_bytes(canonical(manifest(module)))
            provenance=home/'provenance.json';provenance.write_bytes(canonical({'schema':'hooklab.pack.provenance.v1',**ident,'settings_schema':settings_schema(module),'channel':'test'}))
            run('javac','--release','17','-encoding','UTF-8','-classpath',f'{android}:{sdkjar}','-d',compiled,*sorted(src.glob('*.java')))
            jar=home/'payload.jar';run('jar','--create','--file',jar,'-C',compiled,'.')
            run(d8,'--release','--min-api','29','--lib',android,'--classpath',sdkjar,'--output',dexdir,jar)
            require({x.name for x in dexdir.iterdir()}=={'classes.dex'},'single DEX expected')
            print(f'MOD02_HOSTED_DEX_READY module={module} sha256={sha(dexdir/"classes.dex")} bytes={(dexdir/"classes.dex").stat().st_size}',flush=True)
            ui_args=[]
            personal_module=personal/module;personal_module.mkdir()
            if module=='wordmaster':
                ui_src=ROOT/'modules/wordmaster/ui';api=work/'ui-api';api.mkdir();ui_classes=work/'ui-classes';ui_classes.mkdir();ui_dex=work/'ui-dex';ui_dex.mkdir()
                api_jar=work/'ui-api.jar';ui_jar=work/'ui.jar'
                run('javac','--release','17','-encoding','UTF-8','-classpath',f'{android}:{sdkjar}','-d',api,ROOT/'app/src/main/java/com/mikeyphw/hooklab/packs/ModuleUiProvider.java')
                run('jar','--create','--file',api_jar,'-C',api,'.')
                run('javac','--release','17','-encoding','UTF-8','-classpath',f'{android}:{sdkjar}:{api_jar}','-d',ui_classes,ui_src/'Provider.java')
                run('jar','--create','--file',ui_jar,'-C',ui_classes,'.')
                run(d8,'--release','--min-api','29','--lib',android,'--classpath',sdkjar,'--classpath',api_jar,'--output',ui_dex,ui_jar)
                require({x.name for x in ui_dex.iterdir()}=={'classes.dex'},'single UI DEX expected')
                (personal_module/'ui.dex').write_bytes((ui_dex/'classes.dex').read_bytes());(personal_module/'ui-resources.json').write_bytes((ui_src/'resources.json').read_bytes())
                ui_args=['--ui-dex',personal_module/'ui.dex','--ui-resources',personal_module/'ui-resources.json']
            pack=out/(module+'.hookpack');run(cli,'module','pack',mf,provenance,dexdir/'classes.dex',private,pack,*ui_args)
            (personal_module/'manifest.json').write_bytes(mf.read_bytes());(personal_module/'classes.dex').write_bytes((dexdir/'classes.dex').read_bytes())
            (personal_module/'provenance.json').write_bytes(canonical({'schema':'hooklab.pack.provenance.v1',**ident,'settings_schema':settings_schema(module),'channel':'personal'}))
            repeat=home/'repeat.hookpack';run(cli,'module','pack',mf,provenance,dexdir/'classes.dex',private,repeat,*ui_args);require(sha(pack)==sha(repeat),'nonrepeatable pack')
            run(cli,'module','verify',pack,public);run('java','-cp',f'{classes}:{ROOT/"runtime/src/main/resources"}','Mod02PackChecks',pack,public)
            if module=='wordmaster':
                # Isolated emulator only. Never upload this personal fixture or its key.
                (ui_fixture/'publisher.der').write_bytes(public.read_bytes())
                run(cli,'module','pack',mf,personal_module/'provenance.json',dexdir/'classes.dex',private,ui_fixture/'wordmaster.hookpack',*ui_args)
                run(cli,'module','verify',ui_fixture/'wordmaster.hookpack',public)
    (out/'receipt.json').write_bytes(canonical({'schema':'hooklab.mod02.hosted.v1',**ident,'status':'SIGNED_TEST_PACKS_VERIFIED_NOT_ACTIVATED','files':{p.name:sha(p) for p in sorted(out.iterdir())}})+b'\n');verify(out,ident)
    (personal/'receipt.json').write_bytes(canonical({'schema':'hooklab.mod02.personal-inputs.v1',**ident,'status':'UNSIGNED_NOT_INSTALLABLE','files':{p.relative_to(personal).as_posix():sha(p) for p in sorted(personal.rglob('*')) if p.is_file()}})+b'\n')
    print('MOD02_HOSTED_SIGNED_TEST_PACKS_PASS')
def art(out,evidence):
    from native_smoke import guard,PKG,SERIAL,installed_digest,one,parse_instrumentation
    ident=identity();receipt=verify(out,ident);guard()
    # Native smoke has already installed the exact manager and instrumentation APKs.
    installed_digest(PKG,sha(one('app/build/outputs/apk/debug/*.apk')));installed_digest(PKG+'.test',sha(one('app/build/outputs/apk/androidTest/debug/*.apk')))
    for name in sorted(receipt['files']):
        # Fixed filenames only, app-private input, no broad storage or root.
        with (out/name).open('rb') as source:
            run('adb','-s',SERIAL,'shell','-T','run-as',PKG,'sh','-c',f"'mkdir -p files/mod02-test && cat > files/mod02-test/{name}'",stdin=source)
        copied=subprocess.check_output(['adb','-s',SERIAL,'exec-out','run-as',PKG,'cat','files/mod02-test/'+name],timeout=30)
        require(hashlib.sha256(copied).hexdigest()==receipt['files'][name],'installed pack drift')
    result=run('adb','-s',SERIAL,'shell','am','instrument','-w','-r','-e','class',TEST,PKG+'.test/androidx.test.runner.AndroidJUnitRunner',capture_output=True,text=True)
    parse_instrumentation(result.stdout,TEST);evidence.write_bytes(canonical({'schema':'hooklab.mod02.art.v1',**ident,'status':'ART_LOAD_PASS_NOT_VECTOR_ACCEPTANCE','test':TEST,'pack_receipt_sha256':sha(out/'receipt.json')})+b'\n');print('MOD02_ART_LOAD_PASS_NOT_VECTOR_ACCEPTANCE')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('command',choices=['build','art','verify']);p.add_argument('--out',type=Path,required=True);p.add_argument('--evidence',type=Path);p.add_argument('--personal',type=Path);p.add_argument('--cli',type=Path);p.add_argument('--ui-fixture',type=Path);a=p.parse_args()
    try:
        if a.command=='build':build(a.out,a.personal,a.ui_fixture)
        elif a.command=='art':require(a.evidence is not None,'evidence path required');art(a.out,a.evidence)
        else:
            require(a.evidence is not None and a.personal is not None and a.cli is not None,'artifact verifier paths required')
            verify_artifacts(a.out,a.personal,a.evidence,identity(),a.cli.resolve());print('MOD02_HOSTED_ARTIFACTS_VERIFIED_NOT_VECTOR_ACCEPTANCE')
    except (ValueError,KeyError,OSError,subprocess.SubprocessError) as exc:print('MOD02_HOSTED_REFUSED',str(exc),file=sys.stderr);raise SystemExit(2)
