package com.mikeyphw.hooklab.mcc;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
public final class MccRootContextProbeTest {
    @Test public void exactReadOnlyCodeAndNoNamespaceMutation(){String s=MccRootContextProbe.script("/data/user/0/br.art.code.meucronogramacapilar",1234);assertTrue(s.contains("/proc/1234/ns/mnt"));assertTrue(s.contains("/data/data/br.art.code.meucronogramacapilar"));for(String forbidden:new String[]{"nsenter","setenforce","force-stop","chmod","mount -","DiamondBalance","printf '%s' '9'"})assertFalse(s.contains(forbidden));}
    @Test public void otherUserNeverScansUserZero(){String s=MccRootContextProbe.script("/data/user/10/br.art.code.meucronogramacapilar",123);assertFalse(s.contains("/data/user/0/"));assertFalse(s.contains("/data/data/"));}
    @Test public void parsesOnlyExactMetadata(){String code="UR_GR_KN_ID_CS_SU_ANNNN_BEEEE";assertEquals(code,MccRootContextProbe.parse("MCC_R02_CONTEXT="+code));assertNull(MccRootContextProbe.parse("MCC_R02_CONTEXT="+code+" secret"));assertNull(MccRootContextProbe.parse("MCC_R02_CONTEXT=unknown"));}
    @Test public void drainsOversizedOutputAndPreservesFixedMarkers(){String input="x".repeat(100000)+"\nMCC_R02_CONTEXT=UR_GR_KN_ID_CS_SU_ANNNN_BEEEE\nMCC_SCAN_VISIBLE_PRIMARY=0\nMCC_SCAN_VISIBLE_ALIAS=0\n";MccRefillWorker.Output o=new MccRefillWorker.Output(new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)));o.run();assertFalse(o.failed);assertEquals(0,o.primaryVisible);assertEquals(0,o.aliasVisible);assertEquals("_R02_UR_GR_KN_ID_CS_SU_ANNNN_BEEEE",o.contextSuffix());}
}
