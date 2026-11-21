package com.mikeyphw.hooklab.mcc;

import org.junit.Test;
import static org.junit.Assert.*;

public class MccRefillCommandTest {
    @Test public void rootScriptIsScopedAndVerifiesBeforeSuccess() {
        String script = MccRefillCommand.script(false, "/data/user/0/br.art.code.meucronogramacapilar");
        assertTrue(script.contains("/data/user/0/br.art.code.meucronogramacapilar/databases"));
        assertTrue(script.contains("am force-stop \"$pkg\""));
        assertTrue(script.contains("MCC_REFILL_WRITE_VERIFIED"));
        assertTrue(script.contains("SYMLINK_REFUSED"));
        assertTrue(script.contains("ACCOUNT_COUNT_REFUSED"));
        assertFalse(script.contains("rm -rf"));
        assertFalse(script.contains("curl "));
        assertFalse(script.contains("http"));
    }
    @Test public void autoScriptRefusesNonzero() {
        String manual = MccRefillCommand.script(false, "/data/user/0/br.art.code.meucronogramacapilar");
        String auto = MccRefillCommand.script(true, "/data/user/0/br.art.code.meucronogramacapilar");
        assertFalse(manual.contains("MCC_REFILL_AUTO_SKIP_NONZERO"));
        assertTrue(auto.contains("MCC_REFILL_AUTO_SKIP_NONZERO"));
        assertTrue(auto.indexOf("AUTO_SKIP_NONZERO") < auto.indexOf("am force-stop"));
    }
    @Test public void supportsVerifiedNonzeroAndroidUserWithoutHardcodedUser0() {
        String script = MccRefillCommand.script(false, "/data/user/10/br.art.code.meucronogramacapilar");
        assertTrue(script.contains("/data/user/10/br.art.code.meucronogramacapilar/databases"));
        assertFalse(script.contains("base='/data/user/0/"));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsAnotherPackage() {
        MccRefillCommand.script(false, "/data/user/0/org.example.app");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsShellInjectionInDataDir() {
        MccRefillCommand.script(false, "/data/user/0/br.art.code.meucronogramacapilar'; touch /tmp/unsafe");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnexpectedAppDataLocation() {
        MccRefillCommand.script(false, "/sdcard/Download/br.art.code.meucronogramacapilar");
    }

}
