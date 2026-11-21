#!/usr/bin/env python3
"""Local-only signing identity creation, strict GitHub environment and secret upload.

Never prints a key or password. No Gradle, Android SDK, or APK build is run here.
Private material remains under ~/.local/share/hooklab/signing with 0700/0600 modes.
"""
from __future__ import annotations
import argparse, base64, getpass, hashlib, json, os, pathlib, re, secrets, shutil, subprocess, sys

REPO='Mikeyphw/MikeysHookLab'
ENVIRONMENT='hooklab-signing'
HOME=pathlib.Path.home()/'.local/share/hooklab/signing'
KEY=HOME/'hooklab-stable.p12'
META=HOME/'signing.json'
SECRET_NAMES=('HOOKLAB_SIGNING_KEYSTORE_B64','HOOKLAB_SIGNING_STORE_PASSWORD',
    'HOOKLAB_SIGNING_KEY_ALIAS','HOOKLAB_SIGNING_KEY_PASSWORD',
    'HOOKLAB_SIGNING_CERT_SHA256')

class Refused(Exception): pass

def call(argv: list[str], *, input_text: str|None=None, env:dict|None=None, stage:str='operation') -> str:
    try:
        r=subprocess.run(argv,input=input_text,text=True,stdout=subprocess.PIPE,
             stderr=subprocess.PIPE,env=env,check=False,timeout=80)
    except (OSError,subprocess.TimeoutExpired) as ex:
        raise Refused(stage+': '+type(ex).__name__) from ex
    if r.returncode != 0:
        # Do not echo arbitrary provider stderr, passwords or command arguments.
        raise Refused(stage+': returned exit '+str(r.returncode))
    return r.stdout

def gh_api(path:str, *, method:str='GET', obj:dict|None=None):
    cmd=['gh','api',f'repos/{REPO}/{path}']
    if method!='GET': cmd.extend(['-X',method])
    if obj is not None: cmd.extend(['--input','-'])
    out=call(cmd,input_text=json.dumps(obj) if obj is not None else None,stage='github-'+path)
    return json.loads(out) if out.strip() else {}

def digest_key(password:str,alias:str)->str:
    env={**os.environ,'HOOKLAB_KEYTOOL_PASSWORD':password}
    report=call(['keytool','-list','-v','-keystore',str(KEY),'-storetype','PKCS12',
        '-alias',alias,'-storepass:env','HOOKLAB_KEYTOOL_PASSWORD'],env=env,stage='keytool-fingerprint')
    found=re.findall(r'(?m)^\s*SHA256:\s*([A-Fa-f0-9:]{95})\s*$',report)
    if len(found)!=1: raise Refused('Unable to verify signing certificate fingerprint')
    result=found[0].replace(':','').lower()
    if not re.fullmatch(r'[a-f0-9]{64}',result): raise Refused('Invalid certificate fingerprint')
    return result

def private_storage():
    if HOME.is_symlink(): raise Refused('Signing directory is a symlink')
    HOME.mkdir(parents=True,exist_ok=True,mode=0o700)
    HOME.chmod(0o700)
    if HOME.stat().st_mode & 0o077: raise Refused('Signing directory not private')
    for p in (KEY,META):
        if p.is_symlink(): raise Refused('Signing file symlink refused')
        if p.exists() and (not p.is_file() or p.stat().st_mode & 0o077):
            raise Refused('Signing file not private: '+p.name)

def make_or_reuse(args) -> dict:
    private_storage()
    if KEY.exists()!=META.exists(): raise Refused('Incomplete signing identity. Restore matching backup; no rotation')
    if KEY.exists():
        data=json.loads(META.read_text())
        if not isinstance(data,dict) or not {'alias','store_password','key_password','cert_sha256','keystore_sha256'}<=set(data):
            raise Refused('Existing signing metadata malformed')
        if hashlib.sha256(KEY.read_bytes()).hexdigest()!=data['keystore_sha256']:
            raise Refused('Keystore changed; refuse identity rotation')
        if digest_key(data['store_password'],data['alias'])!=data['cert_sha256']:
            raise Refused('Signing certificate fingerprint changed')
        print('HOOKLAB_SIGNING_LOCAL_IDENTITY=REUSED')
        return data
    if args.import_keystore:
        source_path=pathlib.Path(args.import_keystore).expanduser()
        if source_path.is_symlink(): raise Refused('Signing key source symlink refused')
        src=source_path.resolve(strict=True)
        if not src.is_file() or (KEY.exists() and src.samefile(KEY)):
            raise Refused('Unsafe existing keystore source')
        if not args.alias: raise Refused('--alias is required to import an existing key')
        alias=args.alias
        store_pass=getpass.getpass('Existing keystore password (not echoed): ')
        key_pass=getpass.getpass('Existing key password (not echoed; enter store password if same): ')
        if not store_pass or not key_pass: raise Refused('Empty existing signing credential')
        with KEY.open('xb') as dest:
            with src.open('rb') as inp: shutil.copyfileobj(inp,dest)
        KEY.chmod(0o600)
        print('HOOKLAB_SIGNING_LOCAL_IDENTITY=IMPORTED')
    else:
        store_pass=secrets.token_urlsafe(32)
        key_pass=store_pass
        alias='hooklab-stable'
        env={**os.environ,'HOOKLAB_KEYTOOL_PASSWORD':store_pass}
        call(['keytool','-genkeypair','-noprompt','-keyalg','RSA','-keysize','3072',
            '-sigalg','SHA256withRSA','-validity','10000','-storetype','PKCS12',
            '-alias',alias,'-dname','CN=HookLab Stable,O=Personal,C=BR',
            '-keystore',str(KEY),'-storepass:env','HOOKLAB_KEYTOOL_PASSWORD',
            '-keypass:env','HOOKLAB_KEYTOOL_PASSWORD'],env=env,stage='create-local-key')
        KEY.chmod(0o600)
        print('HOOKLAB_SIGNING_LOCAL_IDENTITY=CREATED')
    cert=digest_key(store_pass,alias)
    data={'alias':alias,'store_password':store_pass,'key_password':key_pass,
          'cert_sha256':cert,'keystore_sha256':hashlib.sha256(KEY.read_bytes()).hexdigest()}
    # Intentionally private, plaintext metadata. Back this and the keystore up together.
    with META.open('x',encoding='utf8') as dest:
        os.chmod(META,0o600)
        json.dump(data,dest,sort_keys=True)
    return data

def environment_preflight():
    identity=json.loads(call(['gh','api','user'],stage='GitHub-account'))
    if identity.get('login')!='Mikeyphw': raise Refused('Wrong GitHub account')
    path=f'environments/{ENVIRONMENT}'
    try:
        env=gh_api(path)
    except Refused:
        print('HOOKLAB_SIGNING_ENVIRONMENT_CREATE=REQUESTED')
        env=gh_api(path,method='PUT',obj={'deployment_branch_policy':{
            'protected_branches':False,'custom_branch_policies':True}})
    policy=env.get('deployment_branch_policy')
    if policy!={'protected_branches':False,'custom_branch_policies':True}:
        raise Refused('Environment policy must be exactly custom main-only; do not loosen existing rules')
    rows=gh_api(path+'/deployment-branch-policies')
    listed=rows.get('branch_policies',[])
    if len(listed)==0:
        gh_api(path+'/deployment-branch-policies',method='POST',obj={'name':'main','type':'branch'})
        rows=gh_api(path+'/deployment-branch-policies')
        listed=rows.get('branch_policies',[])
    if len(listed)!=1 or rows.get('total_count')!=1 or listed[0].get('name')!='main' or listed[0].get('type')!='branch':
        raise Refused('Environment has unexpected branch/tag access; require exactly main')
    print('HOOKLAB_SIGNING_ENVIRONMENT_MAIN_ONLY=PASS')

def secret_names():
    out=call(['gh','secret','list','-R',REPO,'--env',ENVIRONMENT,'--json','name'],stage='list-secrets')
    data=json.loads(out)
    return {x['name'] for x in data}

def publish(data):
    environment_preflight()
    current=secret_names()
    already=set(SECRET_NAMES)&current
    if already:
        raise Refused('Existing signing secrets detected: no silent overwrite or key rotation; restore identity or review manually')
    values=(base64.b64encode(KEY.read_bytes()).decode(),data['store_password'],data['alias'],
            data['key_password'],data['cert_sha256'])
    for key,val in zip(SECRET_NAMES,values):
        call(['gh','secret','set',key,'-R',REPO,'--env',ENVIRONMENT],
            input_text=val,stage='upload-'+key)
        print('HOOKLAB_SIGNING_SECRET_UPLOADED='+key)
    confirmed=secret_names()
    if not set(SECRET_NAMES)<=confirmed:
        raise Refused('Uploaded secret names not all observable; recheck without rotating')
    print('HOOKLAB_SIGNING_ENVIRONMENT_SECRETS=PASS')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--publish-secrets',action='store_true')
    parser.add_argument('--import-keystore',help='Existing compatible keystore; refuse generating a new one')
    parser.add_argument('--alias',help='Required for --import-keystore')
    args=parser.parse_args()
    if args.alias and not args.import_keystore: parser.error('--alias requires --import-keystore')
    if not shutil.which('keytool'): raise Refused('keytool missing: install Termux openjdk-21 or compatible OpenJDK')
    if args.publish_secrets:
        if not shutil.which('gh'): raise Refused('gh missing; configure GitHub CLI')
        # Before generating an identity, prevent wrong-account writes and detect
        # any existing identity. Never silently replace a previous signing key.
        environment_preflight()
        existing=secret_names()
        if any(k in existing for k in SECRET_NAMES):
            raise Refused('Protected signing secrets already exist. Refusing to create or replace a key')
    data=make_or_reuse(args)
    print('HOOKLAB_SIGNING_CERT_SHA256='+data['cert_sha256'])
    print('HOOKLAB_SIGNING_PRIVATE_BACKUP_REQUIRED=YES')
    print('HOOKLAB_SIGNING_LOCAL_DIRECTORY='+str(HOME))
    if args.publish_secrets: publish(data)
    else: print('HOOKLAB_SIGNING_GITHUB_SECRETS=NOT_PUBLISHED')
    print('HOOKLAB_SIGNING_NO_LOCAL_GRADLE=PASS')

if __name__=='__main__':
    try: main()
    except (Refused,OSError,ValueError,KeyError) as ex:
        print('HOOKLAB_SIGNING_REFUSED='+str(ex),file=sys.stderr)
        raise SystemExit(2)
