package com.mikeyphw.hooklab.mcc;

import org.junit.Test;
import static org.junit.Assert.*;

public class MccRefillCommandTest {
    private static final String DATA="/data/user/0/br.art.code.meucronogramacapilar";

    @Test public void acceptsOnlyExactInstalledDataDirectory() {
        assertEquals(DATA,MccRefillCommand.checkedDataDir(DATA));
        assertEquals("/data/data/br.art.code.meucronogramacapilar",
            MccRefillCommand.checkedDataDir("/data/data/br.art.code.meucronogramacapilar"));
        assertEquals("/data/user/10/br.art.code.meucronogramacapilar",
            MccRefillCommand.checkedDataDir("/data/user/10/br.art.code.meucronogramacapilar"));
    }
    @Test public void refusesPathTraversalAndWrongPackage() {
        for (String bad: new String[]{"/data/user/0/other.pkg", "../databases", "/data/user/0/br.art.code.meucronogramacapilar/../../x", "/data/user/0/br.art.code.meucronogramacapilar/", "/data/user/0/br.art.code.meucronogramacapilar;touch /tmp/a", "/sdcard/x"}) {
            try {MccRefillCommand.checkedDataDir(bad);fail(bad);} catch (IllegalArgumentException expected) { }
        }
        try {MccRefillCommand.checkedDataDir(null);fail();} catch (IllegalArgumentException expected) { }
    }
    @Test public void probeNeverStopsOrWritesToTarget() {
        String s=MccRefillCommand.script(DATA,true);
        assertTrue(s.contains("MCC_STORAGE_READY"));
        assertFalse(s.contains("am force-stop"));
        assertFalse(s.contains("printf '%s' '9' >"));
    }
    @Test public void writeValidatesThenStopsWritesAndVerifies() {
        String s=MccRefillCommand.script(DATA,false);
        assertTrue(s.contains("MCC_BALANCE_WRITE_VERIFIED"));
        assertTrue(s.indexOf("MCC_BALANCE_FILE_NOT_FOUND") < s.indexOf("am force-stop"));
        assertTrue(s.indexOf("MCC_BALANCE_FORMAT_UNKNOWN") < s.indexOf("am force-stop"));
        assertTrue(s.indexOf("am force-stop") < s.indexOf("printf '%s' '9' >"));
        assertTrue(s.indexOf("printf '%s' '9' >") < s.indexOf("MCC_BALANCE_WRITE_VERIFIED"));
        assertTrue(s.contains("am force-stop --user"));
        assertTrue(s.contains("MCC_BALANCE_ALREADY_NINE"));
    }
    @Test public void refusesMultipleAccountsAndSymlinks() {
        String s=MccRefillCommand.script(DATA,false);
        assertTrue(s.contains("[ ! -L \"$dir\" ]"));
        assertTrue(s.contains("[ ! -L \"$candidate\" ]"));
        assertTrue(s.contains("[ \"$n\" -le 1 ]"));
        assertTrue(s.contains("*-DiamondBalance"));
        assertTrue(s.contains("[ ${#prefix} -ge 8 ]"));
    }
    @Test public void doesNotInventBalanceOrChangeSyncRecords() {
        String s=MccRefillCommand.script(DATA,false);
        for (String unsafe:new String[]{"curl ","wget ","http:","https:",
                "LastSyncAzure","LastSyncHasura","ad_id=","onRewardedAdUserEarnedReward", ">>"}) {
            assertFalse(unsafe,s.contains(unsafe));
        }
    }
}
