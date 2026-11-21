import sys, zipfile
from pathlib import Path
with zipfile.ZipFile(Path(sys.argv[1])) as z:
    assert z.testzip() is None, 'APK CRC failure'
    path='META-INF/xposed/'
    assert z.read(path+'java_init.list').decode().strip() == 'com.mikeyphw.hooklab.hook.HookLabModule'
    props=dict(line.split('=',1) for line in z.read(path+'module.prop').decode().splitlines())
    assert props == {'minApiVersion':'102','targetApiVersion':'102','staticScope':'false','exceptionMode':'protective','autoHotReload':'false'}
    assert path+'scope.list' not in z.namelist()
    assert 'assets/xposed_init' not in z.namelist()
    assert 'classes.dex' in z.namelist()
print('HOOKLAB_APK_METADATA_PASS')
