"""Shared APK transfer for the visual baseline; no local builds or signing."""
import argparse,json,shutil,sys
from pathlib import Path
import native_smoke as n
import adaptive_matrix as a

def release_receipt(folder,ident):
    n.require(folder.is_dir() and not folder.is_symlink() and set(x.name for x in folder.iterdir())=={'release.apk','release-build.json'},'release bundle roster mismatch')
    p=folder/'release-build.json';n.require(p.is_file() and not p.is_symlink() and p.stat().st_size<=16384,'unsafe release bundle receipt')
    result={**a.apk_identity(ident),'release':a.apk_file(folder/'release.apk'),'release_qualified':False}
    n.require(json.loads(p.read_text())==result,'release build identity drift');return result

def publish(folder,ident):
    n.require(not folder.exists(),'fresh release bundle required');folder.mkdir(parents=True)
    src=n.one('app/build/outputs/apk/release/*.apk');a.apk_file(src);shutil.copyfile(src,folder/'release.apk')
    (folder/'release-build.json').write_text(json.dumps({**a.apk_identity(ident),'release':a.apk_file(folder/'release.apk'),'release_qualified':False},sort_keys=True)+'\n')
    release_receipt(folder,ident)

def restore(apks,release,ident):
    a.shared_apks(apks,ident);release_receipt(release,ident)
    for src,dest in [(apks/'app.apk','app/build/outputs/apk/debug/app-debug.apk'),(apks/'android-test.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),(release/'release.apk','app/build/outputs/apk/release/app-release-unsigned.apk')]:
        dest=Path(dest);n.require(not dest.exists() and not dest.is_symlink(),'existing APK refused');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest);n.require(n.digest(src)==n.digest(dest),'restored APK digest drift')

def main():
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['publish','restore','verify']);p.add_argument('--release',required=True,type=Path);p.add_argument('--apks',type=Path);a=p.parse_args()
    try:
        ident=n.expected_identity()
        if a.mode=='publish':publish(a.release,ident)
        elif a.mode=='verify':a.shared_apks(a.apks,ident);release_receipt(a.release,ident)
        else:restore(a.apks,a.release,ident)
    except (OSError,TypeError,ValueError,KeyError) as exc:print('SHARED_NATIVE_APKS_REFUSED',str(exc),file=sys.stderr);return 2
    return 0
if __name__=='__main__':raise SystemExit(main())
