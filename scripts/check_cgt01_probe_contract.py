#!/usr/bin/env python3
"""CGT01-P02/P03: source-only guard. No Gradle; device Copy parity remains independent."""
from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
def read(p):return (root/p).read_text(encoding='utf-8')
probe=read('app/src/main/java/com/mikeyphw/hooklab/chatgpt/ChatGptCodeProbe.java')
matcher=read('app/src/main/java/com/mikeyphw/hooklab/chatgpt/CodeBlockProbeMatcher.java')
actions=read('app/src/main/java/com/mikeyphw/hooklab/chatgpt/CodeBlockActionMatcher.java')
catalog=read('app/src/main/java/com/mikeyphw/hooklab/core/TweakCatalog.java')
module=read('app/src/main/java/com/mikeyphw/hooklab/hook/HookLabModule.java')
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
queries=[p.get(ns+'name') for p in manifest.findall('./queries/package')]
assert queries == ['br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
                   'com.looker.droidify','com.fugo.wowsolitaire',
                   'com.openai.chatgpt','dev.bikram.obtainx'], 'UNEXPECTED_PACKAGE_VISIBILITY'
assert manifest.findall('uses-permission')==[], 'NEW_PERMISSION_FORBIDDEN'
scope=read('app/src/main/resources/META-INF/xposed/scope.list').splitlines()
assert scope==['br.art.code.meucronogramacapilar','com.machiav3lli.fdroid',
             'com.looker.droidify','com.fugo.wowsolitaire','com.openai.chatgpt']
assert read('app/src/main/resources/META-INF/xposed/module.prop').splitlines()==[
    'minApiVersion=102','targetApiVersion=102','staticScope=false',
    'exceptionMode=protective','autoHotReload=false'], 'SCOPE_REQUIRES_EXPLICIT_APPROVAL'
assert 'ChatGptCodeProbe::install' in catalog and catalog.count('ChatGptCodeProbe::install')==1
assert 'ChatGptCodeProbe.TWEAK_ID' in catalog and 'ChatGptCodeProbe.VERSION_CODE, ChatGptCodeProbe.VERSION_CODE' in catalog
assert all(x in module for x in ('getRemotePreferences(TweakKeys.GROUP)',
          'settings.getBoolean(TweakKeys.enabled(id), false)',
          'TweakDiagnostics.quarantineKey(id)', 'param.isFirstPackage()'))
for token in ('PACKAGE="com.openai.chatgpt"','VERSION_NAME="1.2026.314"',
              'VERSION_CODE=2631431L','"chatgpt.codeblock.copy.probe"',
              'getLongVersionCode()!=VERSION_CODE','VERSION_NAME.equals(installed.versionName)',
              'context.framework(XposedInterface.class)','getDeclaredMethod("a",',
              'EXPECTED_RENDER_OWNER="p4l"','String.class, String.class',
              'type(loader,"kki0")','type(loader,"yuj0")',
              'type(loader,"bz11")','type(loader,"qvq")',
              'render.getReturnType()!=void.class','ClipboardManager.class.getDeclaredMethod("setPrimaryClip",ClipData.class)',
              'setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)',
              'chain.proceed()','String source=(String)chain.getArg(1);',
              'MATCHER.observePair((String)chain.getArg(0), source);',
              'MATCHER.compareCopiedText(copied)',
              'CGT01_COPY_MATCH_VALUE_ONLY','action_causality=UNVERIFIED',
              'TweakDiagnostics.rethrowFatal(failure)'):
    assert token in probe, 'PROBE_HOOK_MISSING:'+token
for token in ('new SecureRandom().nextBytes(secret)', 'HmacSHA256',
              'getBytes(StandardCharsets.UTF_8)',
              'MAX_CANDIDATES = 128', 'MAX_AGE_MS = 2 * 60 * 1000L',
              'MAX_SOURCE_BYTES = 1024 * 1024', 'AMBIGUOUS_SLOTS',
              'UNSAFE_TEXT', 'UNMATCHED', 'trailingNewlines'):
    assert token in matcher, 'MATCHER_MISSING:'+token
for forbidden in ('getPrimaryClip(', 'termux.RUN_COMMAND', 'com.termux',
                  'startActivity(', 'startService(', 'FileOutputStream',
                  'getExternalFilesDir(', 'su -c', 'Runtime.getRuntime(',
                  'de.robv.android.xposed', 'text.toString().getBytes('):
    assert forbidden not in probe, 'PROBE_FORBIDDEN:'+forbidden
assert not re.search(r'xp\.log\([^;]*(?:chain\.getArg|text\.toString|code\b)',probe), 'RAW_ARGUMENT_LOGGING_FORBIDDEN'
assert (root/'app/src/test/java/com/mikeyphw/hooklab/chatgpt/CodeBlockProbeMatcherTest.java').is_file()
assert "versionCode 10" in read('app/build.gradle') and "versionName '0.7.1'" in read('app/build.gradle')
assert 'com.openai.chatgpt' in read('scripts/check_apk.py')
# Fail closed if P03 silently stops feeding P02's original string into the parity
# matcher, or binds an action before capturing the exact renderer value.
assert probe.index('String source=(String)chain.getArg(1);') < probe.index('MATCHER.observePair((String)chain.getArg(0), source);') < probe.index('bindAction(xp,loader,chain.getArg(10),10,source)')
assert all(x in probe for x in (
    'bindAction(xp,loader,chain.getArg(10),10,source)',
    'bindAction(xp,loader,chain.getArg(14),14,source)',
    'getDeclaredMethod("invoke")',
    'setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)',
    'ACTION.enter(chain.getThisObject())',
    'ACTION.exit(frame)',
    'CGT01_ACTION_CALLBACK_INVOKED',
    'CGT01_ACTION_COPY_BOUND_VALUE_MATCH',
    'visual_block_identity=UNVERIFIED',
    'button_identity=UNVERIFIED'))
assert all(x in actions for x in (
    'WeakReference<Object>', 'ThreadLocal<Frame>', 'HmacSHA256',
    'SHARED_CALLBACK_AMBIGUOUS', 'NO_BOUND_CALLBACK',
    'BOUND_VALUE_MISMATCH', 'MAX_BINDINGS=128', 'MAX_AGE_MS=120000L'))
assert not any(x in actions for x in (
    'Log.', 'FileOutputStream', 'SharedPreferences', 'getPrimaryClip(', 'termux.RUN_COMMAND'))
assert (root/'app/src/test/java/com/mikeyphw/hooklab/chatgpt/CodeBlockActionMatcherTest.java').is_file()
assert 'test_missing_chatgpt_refused'  in read('scripts/test_apk_scope.py')
print('CGT01_P03_SOURCE_CONTRACT=PASS source_only=true hosted_android=NOT_RUN device=NOT_RUN',flush=True)
