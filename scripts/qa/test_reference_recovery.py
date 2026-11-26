"""Capture protocol recovery must not weaken visual/semantic acceptance."""
import tempfile, unittest
from pathlib import Path
from unittest.mock import patch
import reference as r


class BrowserError(Exception): pass
class Browser:
    def __init__(self,version='143'):self.version=version;self.closed=False
    def close(self):self.closed=True


class RecoveryTest(unittest.TestCase):
    def test_recreates_browser_only_for_classified_capture_error(self):
        self.assertTrue(r.is_transient_screenshot(BrowserError('Page.screenshot: Protocol error (Page.captureScreenshot): Unable to capture screenshot')))
        self.assertFalse(r.is_transient_screenshot(BrowserError('Page.screenshot: timeout')))
        self.assertFalse(r.is_transient_screenshot(BrowserError('Page.evaluate: Unable to capture screenshot')))
        with tempfile.TemporaryDirectory() as t:
            first,second=Browser(),Browser();launches=iter((first,second));calls=[]
            def capture(browser,*args):
                calls.append(browser)
                if browser is first:raise r.CaptureUnavailable('fixture screenshot protocol failure')
                return b'png',{'items':[1]}
            with patch.object(r,'capture_case',side_effect=capture):
                records,version=r.capture_with_recovery(lambda:next(launches),Path(t),{},[{'id':'VIS-001'}],Path(t),BrowserError)
            self.assertEqual(calls,[first,second]);self.assertTrue(first.closed and second.closed);self.assertEqual(version,'143');self.assertEqual(records[0]['id'],'VIS-001')
    def test_bounded_failure_does_not_publish_case(self):
        with tempfile.TemporaryDirectory() as t:
            browsers=[]
            def launch():b=Browser();browsers.append(b);return b
            with patch.object(r,'capture_case',side_effect=r.CaptureUnavailable('fixture')):
                with self.assertRaisesRegex(ValueError,'exhausted for VIS-002'):r.capture_with_recovery(launch,Path(t),{},[{'id':'VIS-002'}],Path(t),BrowserError)
            self.assertEqual(len(browsers),3);self.assertTrue(all(b.closed for b in browsers));self.assertEqual(list(Path(t).iterdir()),[])
    def test_semantic_drift_fails_immediately(self):
        with tempfile.TemporaryDirectory() as t:
            browser=Browser();calls=[]
            def launch():calls.append(1);return browser
            with patch.object(r,'capture_case',side_effect=ValueError('semantic drift')):
                with self.assertRaisesRegex(ValueError,'VIS-003: semantic drift'):r.capture_with_recovery(launch,Path(t),{},[{'id':'VIS-003'}],Path(t),BrowserError)
            self.assertEqual(len(calls),1);self.assertTrue(browser.closed)
    def test_version_change_refused(self):
        with tempfile.TemporaryDirectory() as t:
            first,second=Browser('143'),Browser('144');launches=iter((first,second))
            with patch.object(r,'capture_case',side_effect=r.CaptureUnavailable('fixture')):
                with self.assertRaisesRegex(ValueError,'version drift'):r.capture_with_recovery(lambda:next(launches),Path(t),{},[{'id':'VIS-004'}],Path(t),BrowserError)
            self.assertTrue(first.closed and second.closed)
    def test_duplicate_capture_is_not_retryable(self):
        with tempfile.TemporaryDirectory() as t:
            browser=Browser()
            with patch.object(r,'capture_case',return_value=(b'same',{'items':[1]})) as capture:
                with self.assertRaisesRegex(ValueError,'duplicate screenshot VIS-006'):r.capture_with_recovery(lambda:browser,Path(t),{},[{'id':'VIS-005'},{'id':'VIS-006'}],Path(t),BrowserError)
            self.assertEqual(capture.call_count,2);self.assertTrue(browser.closed)

if __name__=='__main__':unittest.main()
