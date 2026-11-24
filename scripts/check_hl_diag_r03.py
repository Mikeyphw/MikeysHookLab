#!/usr/bin/env python3
"""Static integration checks for the fixed Copy path and one-shot root health IPC."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
def read(p):return (r/p).read_text(encoding='utf8')
java='app/src/main/java/com/mikeyphw/hooklab/'
probe=read(java+'chatgpt/ChatGptCodeProbe.java');worker=read(java+'root/RootPlatform.java');service=read(java+'root/RootDiagnosticService.java');code=read(java+'root/RootContextCode.java');ui=read(java+'mcc/MccControlActivity.java');dex=read('scripts/check_cgt01_p06_apkm.py')
assert 'ixCode=field(ixl,"s",Object.class)' in probe and 'ixCode=field(ixl,"r",Object.class)' not in probe
assert all(v in probe for v in ('a.ownsAnnotated(ixCode.get(o))','a.ownsAnnotated(ixCode.get(result))','IXL_IDENTITY_REFUSED','IXL_CONTINUATION_REFUSED'))
assert all(v in dex for v in ("at(d,c,4,('Lixl;','r',o))","at(d,c,6,('Lixl;','s',o))",'IXL_CONSTRUCTOR_FIELD_FLOW_MISMATCH','IXL_CREATE_ANNOTATED_FLOW_MISMATCH','IXL_CLIPDATA_LABEL_TEXT_FLOW_MISMATCH'))
assert all(v in service for v in ('extends RootService','Binder.getCallingUid()!=allowedUid','data.dataAvail()!=0','data.enforceInterface(DESCRIPTOR)','Binder.getCallingPid()','Os.getuid()==0','reply.writeString(RootContextCode.checked(result))','if(code!=INSPECT'))
assert all(v in worker for v in ('RootService.bind(intent,this)','RootService.unbind(this)','STARTUP_UNCERTAIN.set(true)','connection.ready.await(15,TimeUnit.SECONDS)','call.get(5,TimeUnit.SECONDS)','binder.linkToDeath','binder.unlinkToDeath','finally {if(call!=null','ROOT_PLATFORM_RESULT='))
assert 'rootInspect.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)' in ui and 'setPositiveButton(R.string.root_platform_start,(dialog,which) -> inspectRootPlatform())' in ui
assert 'MccManualOperation.shared().start(true,() -> com.mikeyphw.hooklab.root.RootPlatform.inspect(appContext))' in ui
assert ui.count('RootPlatform.inspect(')==1 and 'rootInspect.setEnabled(!active)' in ui
for bad in ('nsenter','FLAG_MOUNT_MASTER','setenforce','/data/user/','/data/data/','DiamondBalance','ProcessBuilder(','Runtime.getRuntime(','CATEGORY_DAEMON_MODE','RootService.stop(','Shell.cmd('):
    assert bad not in service+worker,'UNBOUNDED_ROOT_API:'+bad
assert 'ROOT_PLATFORM_RESULT_UNVERIFIED' in code and 'Pattern.compile' in code
assert "implementation 'com.github.topjohnwu.libsu:core:6.0.0'" in read('app/build.gradle') and "implementation 'com.github.topjohnwu.libsu:service:6.0.0'" in read('app/build.gradle')
assert "filter { includeGroup 'com.github.topjohnwu.libsu' }" in read('settings.gradle')
assert not ET.parse(r/'app/src/main/AndroidManifest.xml').getroot().findall('uses-permission')
assert (r/'app/src/test/java/com/mikeyphw/hooklab/root/RootContextCodeTest.java').is_file()
assert 'clipboardLabelCannotSubstituteForAnnotatedObject' in read('app/src/test/java/com/mikeyphw/hooklab/chatgpt/AsyncCopyTraceTest.java')
print('HL_DIAG_R03_STATIC=PASS; Android build and device pending')
