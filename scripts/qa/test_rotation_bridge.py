"""Source-bound rotation requests, real parser and production worker control."""
import json
import subprocess
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
        with patch.object(r.subprocess,'check_output',side_effect=[req(1,1),req(2,0)]),patch.object(r.n,'txt',return_value='123'):
            b._work()
        self.assertIsNone(b.failure);self.assertEqual(len(b.completed),2);self.assertEqual([(x.args[0]['width_px'],x.args[0]['height_px']) for x in rotate.call_args_list],[(1400,640),(640,1400)])
    def test_wrong_direction_refused(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r.subprocess,'check_output',return_value=req(1,0)),patch.object(r.n,'txt',return_value='123'):b._work()
        self.assertEqual(b.failure,'ROTATION_BRIDGE_REFUSED');rotate.assert_not_called()
    def test_changed_process_refused(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r.subprocess,'check_output',side_effect=[req(1,1),req(2,0,124)]),patch.object(r.n,'txt',side_effect=['123','123','124']):b._work()
        self.assertEqual(b.failure,'ROTATION_BRIDGE_REFUSED');self.assertEqual(rotate.call_count,1)
    def test_process_dies_during_rotation(self):
        rotate=Mock();b=r.Bridge(a.config('api29-compact'),S,N,rotate)
        with patch.object(r.subprocess,'check_output',return_value=req(1,1)),patch.object(r.n,'txt',side_effect=['123','124']):b._work()
        self.assertEqual(b.failure,'ROTATION_BRIDGE_REFUSED');self.assertEqual(b.completed,[])
    def test_incomplete_bridge_cannot_be_accepted(self):
        b=r.Bridge(a.config('api29-compact'),S,N,Mock());b.thread=Mock();b.thread.is_alive.return_value=False
        self.assertFalse(b.finish(True));self.assertTrue(b.finish(False));b.failure='ROTATION_BRIDGE_REFUSED';self.assertFalse(b.finish(False))
    def test_instrument_uses_exact_nonce_and_refuses_incomplete(self):
        result=subprocess.CompletedProcess([],0,'ok','')
        bridge=Mock();bridge.finish.return_value=False
        with patch.object(a.rotation_bridge,'Bridge',return_value=bridge) as factory,patch.object(a.secrets,'token_hex',return_value=N),patch.object(a.subprocess,'run',return_value=result) as child,patch.object(a.n,'parse_instrumentation'):
            got=a.run_instrumentation(a.JOURNEY,a.config('api29-compact'),S)
        self.assertTrue(got.rotation_bridge_failed);bridge.start.assert_called_once();bridge.finish.assert_called_once_with(True)
        self.assertEqual(factory.call_args.args[1:3],(S,N));self.assertIn(N,child.call_args.args[0])

if __name__=='__main__':unittest.main()
