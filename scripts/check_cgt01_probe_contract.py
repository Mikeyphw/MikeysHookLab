#!/usr/bin/env python3
"""CGT01-P06 source contract: exact hooks and safety, not device acceptance."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
def read(p):return (root/p).read_text(encoding='utf-8')
java='app/src/main/java/com/mikeyphw/hooklab/'
probe=read(java+'chatgpt/ChatGptCodeProbe.java'); tracker=read(java+'chatgpt/AsyncCopyTrace.java')
matcher=read(java+'chatgpt/CodeBlockProbeMatcher.java'); catalog=read(java+'core/TweakCatalog.java'); module=read(java+'hook/HookLabModule.java')
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
queries=[p.get(ns+'name') for p in manifest.findall('./queries/package')]
assert queries==['br.art.code.meucronogramacapilar','com.machiav3lli.fdroid','com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt','dev.bikram.obtainx','com.termux']
assert [p.get(ns+'name') for p in manifest.findall('uses-permission')]==['com.termux.permission.RUN_COMMAND']
assert read('app/src/main/resources/META-INF/xposed/scope.list').splitlines()==queries[:-2]
assert read('app/src/main/resources/META-INF/xposed/module.prop').splitlines()==['minApiVersion=102','targetApiVersion=102','staticScope=false','exceptionMode=protective','autoHotReload=false']
assert catalog.count('ChatGptCodeProbe::install')==1 and 'ChatGptCodeProbe.VERSION_CODE, ChatGptCodeProbe.VERSION_CODE' in catalog and 'ChatGptCodeProbe.TWEAK_ID' in catalog
assert 'Read-only, opt-in diagnostics for ChatGPT 1.2026.321/2632128.' in read('runtime/src/main/resources/META-INF/hooklab/builtin-registry.json')
assert all(x in module for x in ('getRemotePreferences(TweakKeys.GROUP)','settings.getBoolean(TweakKeys.enabled(id), false)','TweakDiagnostics.quarantineKey(id)','param.isFirstPackage()'))
for token in ('PACKAGE="com.openai.chatgpt"','VERSION_NAME="1.2026.321"','VERSION_CODE=2632128L','"chatgpt.codeblock.copy.probe"','getLongVersionCode()!=VERSION_CODE','VERSION_NAME.equals(installed.versionName)','context.framework(XposedInterface.class)','EXPECTED_RENDER_OWNER="lxl"','method(lxl,"a"','method(lxl,"c"','type(l,"mfk1")','field(d0,"a",co)','field(co,"b",String.class)','twBranch.getByte(o)==0','ixBranch.getByte(o)==0','ixBranch.getByte(result)==0','vmBranch.getByte(result)==2','vmPayload.get(result)==chain.getArg(0)','TRACE.callback(RENDER.get(),o,twCode.get(o))','a.ownsAnnotated(ixCode.get(o))','TRACE.tag(AsyncCopyTrace.Kind.IXL,result,a)','TRACE.tag(AsyncCopyTrace.Kind.SAFE,result,a)','TRACE.tag(AsyncCopyTrace.Kind.CLIP,c,a)','TRACE.clipboard(a,clip,copied)','ClipboardManager.class.getDeclaredMethod("setPrimaryClip",ClipData.class)','CGT01_P06_HOOKS_INSTALLED','CGT01_COPY_MATCH_VALUE_ONLY','action_causality=UNVERIFIED','visual_block_identity=UNVERIFIED','MATCHER.observePair((String)chain.getArg(0),source)','MATCHER.compareCopiedText(copied)','TweakDiagnostics.rethrowFatal(t)'):
    assert token in probe,'P06_MISSING:'+token
assert 'ixCode=field(ixl,"s",Object.class)' in probe and 'ixCode=field(ixl,"r",Object.class)' not in probe, 'P06_ANNOTATED_FIELD_SEMANTIC_DRIFT'
assert probe.count('.setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)')==14
assert probe.count('xp.hook(')==14
assert 'ACTIVE.set(true)' in probe and probe.rindex('xp.hook(')<probe.index('ACTIVE.set(true)')
assert 'bindAction(' not in probe and 'CodeBlockActionMatcher ACTION' not in probe
for token in ('WeakReference<Object>','MessageDigest.isEqual','HmacSHA256','new SecureRandom().nextBytes(secret)','getBytes(StandardCharsets.UTF_8)','TTL=120000L','CAP=256','LOG_ACTIONS=16','e.key.get()==o','a.clipboardReported','ambiguous=true','get(Kind.CLIP,clip)!=a','reportedStages.add(stage)'):
    assert token in tracker,'P06_TRACKER_MISSING:'+token
for token in ('new SecureRandom().nextBytes(secret)','HmacSHA256','getBytes(StandardCharsets.UTF_8)','MAX_CANDIDATES = 128','MAX_AGE_MS = 2 * 60 * 1000L','MAX_SOURCE_BYTES = 1024 * 1024','AMBIGUOUS_SLOTS','UNSAFE_TEXT','UNMATCHED','trailingNewlines'):
    assert token in matcher,'PARITY_MATCHER_MISSING:'+token
for forbidden in ('getPrimaryClip(','termux.RUN_COMMAND','com.termux','startActivity(','startService(','FileOutputStream','getExternalFilesDir(','su -c','Runtime.getRuntime(','de.robv.android.xposed'):
    assert forbidden not in probe+tracker,'FORBIDDEN:'+forbidden
assert not re.search(r'xp\.log\([^;]*(?:chain\.getArg|text\.toString|\+copied)',probe)
assert "versionCode 10" in read('app/build.gradle') and "versionName '0.7.1'" in read('app/build.gradle')
assert 'com.openai.chatgpt' in read('scripts/check_apk.py')
assert 'test_missing_chatgpt_refused' in read('scripts/test_apk_scope.py')
for name in ('CodeBlockProbeMatcherTest','CodeBlockActionMatcherTest','CopyButtonCausalityTrackerTest','AsyncCopyTraceTest'):
    assert (root/f'app/src/test/java/com/mikeyphw/hooklab/chatgpt/{name}.java').is_file()
assert (root/'scripts/check_cgt01_p06_apkm.py').is_file()
print('CGT01_P06_SOURCE_CONTRACT=PASS static_only=true device=NOT_RUN')
