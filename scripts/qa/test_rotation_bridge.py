"""Source-bound rotation requests, real parser and production worker control."""
import json
import subprocess
import os
import sys
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch, Mock
import rotation_bridge as r
import adaptive_matrix as a

S='a'*40;N='b'*32

def req(seq,dir,pid=123,**updates):
    return json.dumps({'schema':'hooklab.rotation-request.v1','source':S,'nonce':N,'sequence':seq,'rotation':dir,'pid':pid,**updates}).encode()

class BridgeTest(unittest.TestCase):
    def test_request_validation(self):
        self.assertEqual(r.validate_request(req(1,1),S,N,1,1,123)['rotation'],1)
        for payload in (req(1,1,source='c'*40),req(1,1,nonce='c'*32),req(2,1),req(1,0),req(1,1,124),req(True,1),req(1,1,extra=True),b'',b'{"sequence":1,"sequence":2}',b'x'*2049):
            with self.subTest(payload=payload[:40]),self.assertRaises(ValueError):r.validate_request(payload,S,N,1,1,123)
    def test_actual_worker_two_directions(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r,'read_request',side_effect=[None,req(1,1),req(1,1),req(2,0)]),patch.object(r.n,'txt',return_value='123'):
            b._work()
        self.assertIsNone(b.failure);self.assertEqual(b.absent_polls,1);self.assertEqual(len(b.completed),2);self.assertEqual([(x.args[0]['width_px'],x.args[0]['height_px']) for x in rotate.call_args_list],[(1400,640),(640,1400)])
    def test_poll_separates_absence_transport_and_payload(self):
        with patch.object(r.subprocess,'run',return_value=subprocess.CompletedProcess([],44,b'',b'')) as child:
            self.assertIsNone(r.read_request())
            argv=child.call_args.args[0]
            self.assertEqual(argv[:6],['adb','-s',r.n.SERIAL,'shell','-T','-n'])
            self.assertIn('exit 44',argv[6]);self.assertIn(r.REQUEST,argv[6]);self.assertNotIn('exec-out',argv)
        for rc,stdout,stderr in [(44,b'error',b''),(44,b'',b'PRIVATE'),(1,b'',b'PRIVATE'),(0,b'',b''),(0,b'x'*2049,b''),(0,req(1,1),b'PRIVATE')]:
            with self.subTest(rc=rc,size=len(stdout)),patch.object(r.subprocess,'run',return_value=subprocess.CompletedProcess([],rc,stdout,stderr)),self.assertRaises(ValueError):r.read_request()
        with patch.object(r.subprocess,'run',return_value=subprocess.CompletedProcess([],0,req(1,1),b'')):
            self.assertEqual(r.read_request(),req(1,1))
    def test_real_subprocess_missing_file_protocol_and_legacy_failure(self):
        # Execute the production polling command against a local fake ADB
        # transport, modeling raw exec-out's documented merged streams/rc.
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);request=root/r.REQUEST;request.parent.mkdir(parents=True)
            adb=root/'adb'
            adb.write_text('#!'+sys.executable+'\n'+"import os,subprocess,sys\nargs=sys.argv[1:]\nassert args[:2]==['-s','emulator-5554']\nif args[2]=='exec-out':\n    result=subprocess.run(args[5:],cwd=os.environ['FIXTURE_ROOT'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)\n    sys.stdout.buffer.write(result.stdout);sys.exit(0)\nassert args[2:5]==['shell','-T','-n']\nassert args[5].startswith('run-as com.mikeyphw.hooklab ')\nresult=subprocess.run(['/bin/sh','-c',args[5].removeprefix('run-as com.mikeyphw.hooklab ')],cwd=os.environ['FIXTURE_ROOT'])\nsys.exit(result.returncode)\n");adb.chmod(0o700)
            with patch.dict(os.environ,{'PATH':str(root)+os.pathsep+os.environ['PATH'],'FIXTURE_ROOT':tmp}):
                legacy=subprocess.check_output(['adb','-s',r.n.SERIAL,'exec-out','run-as',r.n.PKG,'cat',r.REQUEST])
                self.assertTrue(legacy);self.assertRaises(ValueError,r.validate_request,legacy,S,N,1,1,123)
                self.assertIsNone(r.read_request())
                request.write_bytes(req(1,1));self.assertEqual(r.read_request(),req(1,1))
                request.unlink();request.symlink_to(root/'not-request');self.assertRaises(ValueError,r.read_request)
                (root/'not-request').write_bytes(req(1,1));self.assertRaises(ValueError,r.read_request)
    def test_poll_transport_failure_is_observable_without_raw_data(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r,'read_request',side_effect=ValueError('PRIVATE ADB ERROR')):b._work()
        row=b.diagnostic();self.assertEqual(row['stage'],'POLL_REQUEST');self.assertEqual(row['failure'],'ROTATION_BRIDGE_REFUSED');self.assertEqual(row['completed'],0);self.assertNotIn('PRIVATE',json.dumps(row));rotate.assert_not_called()
    def test_wrong_direction_refused(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r,'read_request',return_value=req(1,0)),patch.object(r.n,'txt',return_value='123'):b._work()
        self.assertEqual(b.failure,'ROTATION_BRIDGE_REFUSED');rotate.assert_not_called()
    def test_changed_process_refused(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r,'read_request',side_effect=[req(1,1),req(2,0,124)]),patch.object(r.n,'txt',side_effect=['123','123','124']):b._work()
        self.assertEqual(b.failure,'ROTATION_BRIDGE_REFUSED');self.assertEqual(rotate.call_count,1)
    def test_process_dies_during_rotation(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r,'read_request',return_value=req(1,1)),patch.object(r.n,'txt',side_effect=['123','124']):b._work()
        self.assertEqual(b.failure,'ROTATION_BRIDGE_REFUSED');self.assertEqual(b.completed,[])
    def test_incomplete_bridge_cannot_be_accepted(self):
        b=r.Bridge(a.config('api29-compact'),S,N,Mock());b.thread=Mock();b.thread.is_alive.return_value=False
        self.assertFalse(b.finish(True));self.assertTrue(b.finish(False));b.failure='ROTATION_BRIDGE_REFUSED';self.assertFalse(b.finish(False))
    def test_instrument_uses_exact_nonce_and_refuses_incomplete(self):
        result=subprocess.CompletedProcess([],0,'ok','')
        bridge=Mock();bridge.finish.return_value=False;bridge.diagnostic.return_value={'stage':'POLL_REQUEST'}
        with patch.object(a.rotation_bridge,'Bridge',return_value=bridge) as factory,patch.object(a.secrets,'token_hex',return_value=N),patch.object(a.subprocess,'run',return_value=result) as child,patch.object(a.n,'parse_instrumentation'):
            got=a.run_instrumentation(a.JOURNEY,a.config('api29-compact'),S)
        self.assertTrue(got.rotation_bridge_failed);self.assertEqual(got.rotation_bridge_diagnostic,{'stage':'POLL_REQUEST'});bridge.start.assert_called_once();bridge.finish.assert_called_once_with(True)
        self.assertEqual(factory.call_args.args[1:3],(S,N));self.assertIn(N,child.call_args.args[0])

if __name__=='__main__':unittest.main()
