"""Static integration regressions; actual SDK/host/PTTY tests are separate gates."""
from pathlib import Path
import unittest,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main/java/com/mikeyphw/hooklab'
class R06IntegrationTest(unittest.TestCase):
    def test_host_drawable_and_independent_copy_binding(self):
        s=(BASE/'chatgpt/ChatGptTermuxHandoff.java').read_text()
        for x in ('assetFactory.newInstance((byte)12)','type("lje0")','type("t6d0")','call(drawIcon,null,painter,null,null,0xFFFFFFFF00000000L,a[1],0,0x4)','"Run in Termux"','h.call(h.invoke3,copyLambda,scope,c,0)','state.dispose()','EXTRA_REQUEST_ID','EXTRA_REQUEST_TIME','EXTRA_REQUEST_WALL'):
            self.assertIn(x,s)
        self.assertNotIn('call(text,null,"Run"',s);self.assertNotIn('startService(',s)
    def test_termux_login_and_android_view_api(self):
        policy=(BASE/"termux/TermuxCommandPolicy.java").read_text()
        self.assertIn('[[ -o interactive ]] || return 0',policy)
        self.assertIn('zsh -il',policy)
        self.assertIn('builtin source "${ZDOTDIR-$HOME}/.zprofile"',policy)
        self.assertIn('builtin source "${ZDOTDIR-$HOME}/.zlogin"',policy)
        self.assertNotIn('libtermux-exec.so',policy)
        self.assertIn('row.setMinimumHeight(',(BASE/"ui/FlatUi.java").read_text())
        self.assertIn('spinner.setMinimumHeight(',(BASE/"ui/MainActivity.java").read_text())
    def test_direct_route_and_replay_boundary(self):
        s=(BASE/'termux/TermuxBridgeActivity.java').read_text()
        for x in ('getCallingPackage()','GET_SIGNING_CERTIFICATES','getApkContentsSigners()','HOST_CERT_SHA256','admitRequest(requestId,requestTime,requestWall)','edit.putLong(id,wall).commit()','if(entry.getKey().equals(id))return false','if(!settingsRoute)return false','directMode.isChecked()).commit()','preferences.getBoolean("direct_r06_opt_in",false)','!focused||!direct||dispatched||source==null||status==null','!granted()||!serviceAvailable()','freshWall(requestWall,System.currentTimeMillis())','dispatched=true;if(run!=null)run.setEnabled(false);source=null','setIntent(new Intent());'):
            self.assertIn(x,s)
        self.assertEqual(s.count('startService(request)'),1)
        callback=s.split('public void onRequestPermissionsResult(',1)[1].split('private boolean saveSettings',1)[0]
        self.assertNotIn('dispatch(',callback);self.assertNotIn('attemptDirect(',callback)
    def test_persistent_terminal_policy_and_single_permission(self):
        s=(BASE/'termux/TermuxCommandPolicy.java').read_text();b=(BASE/'termux/TermuxBridgeActivity.java').read_text()
        for x in ('export NOART=1','/data/data/com.termux/files/usr/bin/sh /data/data/com.termux/files/usr/bin/login -c','builtin source "${ZDOTDIR-$HOME}/.zshrc"','builtin eval "$_hl_r06_code"','_hl_r06_status=$?','[HookLab] Command exit: %d','checkedCommand(source),checkedShell(shell),ENV_INIT,PROFILE_INIT,RC_INIT,LOGIN_INIT'):
            self.assertIn(x,s)
        for x in ('TermuxCommandPolicy.EXECUTABLE','"terminal-session"','"com.termux.RUN_COMMAND_BACKGROUND",false','"com.termux.RUN_COMMAND_SESSION_ACTION","0"','"com.termux.RUN_COMMAND_PENDING_INTENT",result'):
            self.assertIn(x,b)
        ns='{http://schemas.android.com/apk/res/android}';m=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        self.assertEqual([x.get(ns+'name') for x in m.findall('uses-permission')],['com.termux.permission.RUN_COMMAND'])
        self.assertEqual([(x.get(ns+'name'),x.get(ns+'exported')) for x in m.findall('./application/receiver')],[('.termux.TermuxSessionReceipt','false')])
        result=(BASE/'termux/TermuxSessionReceipt.java').read_text()
        for x in ('PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_MUTABLE','new Intent(c,TermuxSessionReceipt.class)','active(p.getString(PREFIX+id,""))','b.getInt("exitCode",Integer.MIN_VALUE)','b.getInt("err",Integer.MIN_VALUE)','intent.replaceExtras((Bundle)null)','MAX_PENDING_AGE_MS'):
            self.assertIn(x,result)
        for x in ('getString("stdout")','getString("stderr")','getString("errmsg")','Log.'):
            self.assertNotIn(x,result)
    def test_native_repeat_gate_unchanged_and_independent_settle(self):
        n=(ROOT/'app/src/androidTest/java/com/mikeyphw/hooklab/qa/NativeBaselineTest.java').read_text();s=(ROOT/'scripts/qa/native_smoke.py').read_text()
        for x in ('getScrollBarDefaultDelayBeforeFade()','getScrollBarFadeDuration()','previous.sameAs(cropped)','consecutive>=3','Viewport did not settle for three consecutive samples','before.toString().equals(after.toString())'):
            self.assertIn(x,n)
        for x in ("'native_ssim_min':.99","'native_changed_pixel_max':.01","'NATIVE_REPEAT_FAILED'","'failed_routes':failures",'strict_regression(expected,actual,first,second,tolerances)'):
            self.assertIn(x,s)
        self.assertIn('termux_session_check.py --require-zsh',(ROOT/'.github/workflows/hooklab-design-contract.yml').read_text())
if __name__=='__main__':unittest.main()
