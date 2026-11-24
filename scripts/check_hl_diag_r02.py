#!/usr/bin/env python3
"""Combined source integration check; never accesses device storage or runs Gradle."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
def read(p):return (root/p).read_text()
base='app/src/main/java/com/mikeyphw/hooklab/mcc/'
worker=read(base+'MccRefillWorker.java');ctx=read(base+'MccRootContextProbe.java');cmd=read(base+'MccRefillCommand.java');control=read(base+'MccControlActivity.java')
for v in ('MccRootContextProbe.script(dir,android.os.Process.myPid())','MccRefillCommand.script(dir,probe)','new ProcessBuilder("su","-c",script)','drain.start();','process.waitFor(TIMEOUT_SECONDS','drain.join(2000L)','MccRootContextProbe.parse(s)','contextAmbiguous','MCC_R02_RESULT=','probe?output.contextSuffix():""','ROOT_OUTPUT_UNVERIFIED'):
    assert v in worker,v
assert worker.index('drain.start();')<worker.index('process.waitFor(TIMEOUT_SECONDS')
for v in ('checkedDataDir(verifiedDir)','/proc/$$/ns/mnt','/proc/1/ns/mnt','/ns/mnt','/proc/$$/attr/current','CapEff:','Permission denied','No such file or directory','MCC_R02_CONTEXT=','mcc_roster','alias.equals(dir)'):
    assert v in ctx,v
assert all(v not in ctx for v in ('nsenter','setenforce','force-stop','chmod','DiamondBalance','mount -','printf \'%s\' \'9\''))
assert 'setPositiveButton("Set to 9"' in control and 'MccRefillWorker.inspect(appContext)' in control
assert 'MCC_STORAGE_READY' in cmd and 'MCC_BALANCE_WRITE_VERIFIED' in cmd
assert (root/'app/src/test/java/com/mikeyphw/hooklab/mcc/MccRootContextProbeTest.java').is_file()
print('HL_DIAG_R02_SOURCE_CONTRACT=PASS native_device=NOT_RUN')
