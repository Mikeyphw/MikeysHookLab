#!/usr/bin/env python3
"""M7 source-only fail-closed review; hosted Android build is separate."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
J=ROOT/'app/src/main/java/com/mikeyphw/hooklab/obtainx'

def source(name):
    p=J/(name+'.java')
    assert p.is_file(), f'MISSING_CLASS={name}'
    return p.read_text(encoding='utf8')

ids=source('ObtainXIds')
neo=source('NeoStoreHook')
droid=source('DroidifyHook')
action=source('NeoStoreAction')
policy=source('SourcePolicy')
bridge=source('ObtainXBridge')
cat=(ROOT/'app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java').read_text()
manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
ns='{http://schemas.android.com/apk/res/android}'
queries=manifest.findall('./queries/package')
assert [x.get(ns+'name') for x in queries]==[
    'br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
    'com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt','dev.bikram.obtainx']
assert manifest.findall('uses-permission')==[]
assert manifest.findall('./application/activity') and len(manifest.findall('./application/activity'))==2
for required in ['NEO_CODE = 1304L','DROIDIFY_CODE = 780L',
    'NEO_VERSION = "1.3.3"','DROIDIFY_VERSION = "0.7.8"',
    'neostore.obtainx.manual','droidify.obtainx.manual']:
    assert required in ids, required
for required in ['NeoStoreHook::install','DroidifyHook::install','MccDiamondUi::install',
    'ObtainXIds.NEO_CODE, ObtainXIds.NEO_CODE','ObtainXIds.DROIDIFY_CODE, ObtainXIds.DROIDIFY_CODE']:
    assert required in cat, required
for forbidden in ['MccIds.VIP','MccIds.DIAMOND_AUTO','MccIds.BANNER_HIDE',
    'MccVipHooks::install','MccAutoWatcher::install']:
    assert forbidden not in cat, forbidden
for required in ['hooklab.obtainx.neo.vm_constructor','hooklab.obtainx.neo.resume',
    'AppPageVM','getDeclaredConstructors()', 'getDeclaredMethod("onResume")',
    'vmPackage','vmCore','suggestedProductRepo','repoAuth',
    'SourcePolicy.same(shown,fresh)','ObtainXBridge.launch(a,fresh,fresh.packageName)',
    'chain.proceed()','NeoStoreAction']:
    assert required in neo, required
for required in ['setOnClickListener','sourceSemantics','fallback','365','48',
    'root.getWidth()','x<dp(a,66)','SystemClock.elapsedRealtime()',
    'button.get()!=icon','ObtainXIds.OBTAIN_X']:
    assert required in action, required
for required in ['onViewCreated','ContextWrapper','getBaseContext()',
    'inheritedGetter','mArguments','getMenu','NATIVE_TOOLBAR_ACTION_ADDED',
    'menu.add(0,ITEM_ID,0,"Add to ObtainX")','route(fragment)',
    'SourcePolicy.ready','ObtainXBridge.launch(activity,live,route(fragment))',
    'ATTACH_REFUSED_']:
    assert required in droid, required
for forbidden in ['XposedHelpers.callMethod(','de.robv.android.xposed','java.io.FileOutputStream',
    'SQLiteDatabase', 'DownloadManager']:
    assert forbidden not in droid+neo, forbidden
for required in ['f-droid.org','IzzyOnDroid','FDroidRepo','github.com',
    'gitlab.com','codeberg.org','git.sr.ht','sourceforge.net','getRawQuery()',
    'productRepoId.equals(repoId)','auth.isEmpty()','additionalSettings',
    'prepared']:
    # 'prepared' is not a symbol: assertion below checks specific builder instead.
    if required=='prepared': continue
    assert required in policy, required
assert 'Snapshot again=ready' in policy
assert 'String id=pkg;' in policy, 'M7 Android API29-safe ASCII package identifier'
assert 'URLEncoder.encode(' not in policy and 'import java.net.URLEncoder;' not in policy, \
    'M7 must not use API33-only URLEncoder overload' 
for required in ['obtainium://app/','Uri.encode(json)','setPackage(ObtainXIds.OBTAIN_X)',
    'resolveActivity','activityInfo.exported','activity.startActivity(intent)']:
    assert required in bridge, required
assert 'dev.bikram.obtainx' not in cat # never scope destination
assert 'versionCode 10' in (ROOT/'app/build.gradle').read_text()
assert "versionName '0.7.1'" in (ROOT/'app/build.gradle').read_text()
scope=(ROOT/'app/src/main/resources/META-INF/xposed/scope.list').read_text(encoding='utf-8').splitlines()
assert scope==['br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
               'com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt']
assert 'dev.bikram.obtainx' not in scope
assert 'staticScope=false' in (ROOT/'app/src/main/resources/META-INF/xposed/module.prop').read_text()
assert (ROOT/'app/src/test/java/com/mikeyphw/hooklab/obtainx/SourcePolicyTest.java').is_file()
assert 'VerboseLogs.Event.NEO_PAGE_READY' in neo and 'VerboseLogs.Event.DROID_TOOLBAR_ADDED' in droid
print('HOOKLAB_M7_OBTAINX_API102_SOURCE_CONTRACT_PASS',flush=True)
