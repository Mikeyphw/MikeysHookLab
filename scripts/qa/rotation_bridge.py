"""One source-bound rotation request per direction from the real test process.

The hosted shell owns the rotation mutation; instrumentation observes the
actual resumed Activity. This avoids changing UiAutomation rotation while
its own runner is observing Activity destruction/recreation.
"""
import json
import re
import shlex
import threading
import subprocess
import native_smoke as n

REQUEST='files/uxr04-adaptive/rotation-request.json'
# exec-out uses a raw transport: remote stderr can be returned as stdout
# with a successful local exit, even when cat failed. Use the shell protocol
# (without a PTY) and an explicit remote absent-file status instead.
READ_SCRIPT='if [ -L "$1" ]; then exit 45; fi; if [ ! -e "$1" ]; then exit 44; fi; if [ ! -f "$1" ]; then exit 45; fi; head -c 2049 "$1"'


def read_request():
    command='run-as '+shlex.quote(n.PKG)+' sh -c '+shlex.quote(READ_SCRIPT)+' sh '+shlex.quote(REQUEST)
    result=subprocess.run(['adb','-s',n.SERIAL,'shell','-T','-n',command],stdin=subprocess.DEVNULL,capture_output=True,timeout=10)
    if result.returncode==44 and not result.stdout and not result.stderr:
        return None
    if result.returncode!=0 or result.stderr or not 0<len(result.stdout)<=2048:
        raise ValueError('rotation poll transport or request file invalid')
    return result.stdout



def validate_request(raw,source,nonce,sequence,direction,pid):
    if not 0<len(raw)<=2048:raise ValueError('rotation request bounds')
    def unique(pairs):
        d={}
        for k,v in pairs:
            if k in d:raise ValueError('duplicate rotation request field')
            d[k]=v
        return d
    row=json.loads(raw,object_pairs_hook=unique)
    expected={'schema':'hooklab.rotation-request.v1','source':source,'nonce':nonce,'sequence':sequence,'rotation':direction,'pid':pid}
    if not isinstance(row,dict) or row!=expected or any(type(row[k]) is not int for k in ('sequence','rotation','pid')):raise ValueError('rotation request identity')
    if not re.fullmatch('[0-9a-f]{40}',source) or not re.fullmatch('[0-9a-f]{32}',nonce) or sequence not in (1,2) or direction not in (0,1) or type(pid) is not int or pid<1:raise ValueError('rotation request domain')
    return row


class Bridge:
    def __init__(self,cfg,source,nonce,rotate):
        self.cfg=cfg;self.source=source;self.nonce=nonce;self.rotate=rotate
        self.stop=threading.Event();self.completed=[];self.failure=None;self.stage="WAIT_REQUEST";self.absent_polls=0;self.sequence=0
        self.thread=threading.Thread(target=self._work,name='hooklab-rotation-bridge',daemon=True)

    def start(self):self.thread.start()

    def _work(self):
        initial=int(self.cfg['width_px']>self.cfg['height_px']);original_pid=None
        try:
            for sequence,direction in ((1,1-initial),(2,initial)):
                self.sequence=sequence
                while not self.stop.is_set():
                    self.stage='POLL_REQUEST'
                    raw=read_request()
                    if raw is None:
                        self.absent_polls=min(self.absent_polls+1,10000)
                        if self.stop.wait(.2):return
                        continue
                    # The previous immutable request can remain until the
                    # instrumentation has independently observed the rotation.
                    if self.completed and raw==self.completed[-1]:
                        if self.stop.wait(.2):return
                        continue
                    self.stage='VALIDATE_PROCESS'
                    pid_text=n.txt('shell','pidof',n.PKG).strip()
                    if not re.fullmatch('[1-9][0-9]*',pid_text):raise ValueError('rotation process identity')
                    pid=int(pid_text)
                    if original_pid is not None and original_pid!=pid:raise ValueError('rotation process changed')
                    self.stage='VALIDATE_REQUEST'
                    validate_request(raw,self.source,self.nonce,sequence,direction,pid)
                    original_pid=pid
                    self.stage='APPLY_ROTATION'
                    self.rotate({**self.cfg,'width_px':max(self.cfg['width_px'],self.cfg['height_px']) if direction else min(self.cfg['width_px'],self.cfg['height_px']),'height_px':min(self.cfg['width_px'],self.cfg['height_px']) if direction else max(self.cfg['width_px'],self.cfg['height_px'])})
                    self.stage='VERIFY_PROCESS'
                    if n.txt('shell','pidof',n.PKG).strip()!=pid_text:raise ValueError('rotation process changed during mutation')
                    self.completed.append(raw);break
                else:return
            self.stage='COMPLETE'
        except (ValueError,TypeError,KeyError,OSError,subprocess.SubprocessError):self.failure='ROTATION_BRIDGE_REFUSED'

    def finish(self,require_complete):
        self.stop.set();self.thread.join(timeout=65)
        return not self.thread.is_alive() and not self.failure and (not require_complete or len(self.completed)==2)

    def diagnostic(self):
        # Deliberately no raw output, PID, nonce, request JSON or exception text.
        return {'schema':'hooklab.rotation-bridge-diagnostic.v1','source':self.source,
                'stage':self.stage,'failure':self.failure,'sequence':self.sequence,
                'completed':len(self.completed),'absent_polls':self.absent_polls,
                'worker_alive':self.thread.is_alive()}
