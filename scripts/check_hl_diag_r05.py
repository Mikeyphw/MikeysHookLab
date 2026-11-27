#!/usr/bin/env python3
"""R05 source guards. Does not claim real Compose, Binder or Termux execution."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];j=r/'app/src/main/java/com/mikeyphw/hooklab';t=r/'app/src/test/java/com/mikeyphw/hooklab'
def get(p):return (j/p).read_text()
h=get('chatgpt/ChatGptTermuxHandoff.java');b=get('chatgpt/TermuxOfferGate.java');p=get('chatgpt/ChatGptCodeProbe.java');a=get('termux/TermuxBridgeActivity.java');policy=get('termux/TermuxCommandPolicy.java');s=get('root/RootDiagnosticService.java');c=get('root/RootPlatform.java')
assert 'ChatGptTermuxHandoff::install' in get('core/TweakCatalog.java')
assert 'chatgpt.codeblock.termux.handoff' in (r/'runtime/src/main/resources/META-INF/hooklab/builtin-registry.json').read_text() and 'BuiltinRegistry.current()' in get('core/HookGroupCatalog.java')
assert 'ChatGptCodeProbe.active()' not in h and 'clipboardResult' not in p and 'PopupWindow' not in h
for x in ('HOST_KEY=0xd01ebe4d','host.branch.getByte(delegate)!=8','code!=FRAME.get()','chain.proceed(new Object[]{HOST_KEY,true,wrapped,chain.getArg(3)})','h.call(h.startNode,c)','h.call(h.reuseNode,c)','RUN_SLOT','COPY_SLOT','h.call(h.invoke3,copyLambda,scope,c,0)','h.call(h.end,c,true)','h.call(h.end,c,false)','h.remember','state.remembered()','state.dispose()','startActivityForResult(intent,0x484c)'):
 assert x in h,x
assert all(x in b for x in ('active=true','identity==object','disposed=true','identity=null;source=null','!active||disposed'))
for x in ('getPrimaryClip(','setPrimaryClip(','startService(','RUN_COMMAND','Runtime.getRuntime(','ProcessBuilder('):assert x not in h,x
assert 'getCallingPackage()' in a and 'getLongVersionCode()' in a
assert 'if (TermuxCommandPolicy.ACTION_SETTINGS.equals(action))' in a
assert 'setIntent(new Intent(this, TermuxBridgeActivity.class)' in a
assert '.setAction(TermuxCommandPolicy.ACTION_SETTINGS));' in a
assert 'setIntent(new Intent());' in a  # preview still discards action and payload
assert 'setIntent(inbound)' not in a and 'new Intent(inbound)' not in a
assert a.count('startService(request)')==1 and 'setPositiveButton(R.string.termux_r05_execute,(d,w)->dispatch(text,dir,interpreter))' in a
assert 'if(dispatched||(!confirming&&!direct)||source==null' in a and 'dispatched=true;if(run!=null)run.setEnabled(false);source=null' in a
assert 'requestPermissions(new String[]{TermuxCommandPolicy.PERMISSION}' in a and 'Never dispatch here' in a
assert '"terminal-session"' in a and '"com.termux.RUN_COMMAND_BACKGROUND",false' in a and 'return new String[]{"-c",BOOTSTRAP,"hooklab",checkedCommand(source),checkedShell(shell),ENV_INIT,PROFILE_INIT,RC_INIT,LOGIN_INIT}' in policy
assert 'TermuxBridgeActivity.class' in get('ui/MainActivity.java')
assert '(flags & IBinder.FLAG_ONEWAY)!=0' in s and 'flags != 0' not in s and 'flags!=0' not in s and 'Binder.getCallingUid()!=allowedUid' in s
assert all(x in s for x in ('TRANSACTION_REFUSED','DESCRIPTOR_REFUSED','PAYLOAD_REFUSED','CALLER_REFUSED','CONTEXT_RETURNED'))
assert all(x in c for x in ('ROOT_PLATFORM_IPC_VERDICT=','ROOT_PLATFORM_IPC_TRANSACT_REFUSED','ROOT_PLATFORM_IPC_SECURITY_REFUSED','ROOT_PLATFORM_IPC_REMOTE_FAILURE','ROOT_PLATFORM_IPC_BAD_RESPONSE','"RETURNED":state'))
ns='{http://schemas.android.com/apk/res/android}';m=ET.parse(r/'app/src/main/AndroidManifest.xml').getroot();assert [x.get(ns+'name') for x in m.findall('uses-permission')]==['com.termux.permission.RUN_COMMAND']
assert len([x for x in m.findall('./application/activity') if x.get(ns+'name')=='.termux.TermuxBridgeActivity' and x.get(ns+'exported')=='true' and not x.findall('intent-filter')])==1
assert 'com.termux' not in (r/'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines()
for x in ('chatgpt/TermuxOfferGateTest.java','termux/TermuxCommandPolicyTest.java','root/RootIpcVerdictTest.java'):assert (t/x).is_file(),x
assert not (j/'chatgpt/CompositionRunBinding.java').exists() and not (t/'chatgpt/CompositionRunBindingTest.java').exists()
assert all(x not in b for x in ('TTL_MS','replace(long','available(long','consume(long','PopupWindow'))
assert 'new TermuxOfferGate(code,source,h.options.tapIntervalMs)' in h
assert 'if(!host.options.showRun||!ENABLED.get()' in h
assert 'minimumInterval<500||minimumInterval>2000' in b and 'now-lastTap<minimumInterval' in b
print('HL_DIAG_R05_STATIC=PASS; runtime acceptance pending')
