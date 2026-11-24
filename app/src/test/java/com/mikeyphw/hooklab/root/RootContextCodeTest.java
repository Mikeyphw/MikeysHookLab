package com.mikeyphw.hooklab.root;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RootContextCodeTest {
    @Test public void namespaceComparisonRejectsMalformedLinks(){
        assertEquals("S",RootContextCode.compare("mnt:[123]","mnt:[123]"));
        assertEquals("D",RootContextCode.compare("mnt:[123]","mnt:[456]"));
        assertEquals("X",RootContextCode.compare("some path","some path"));
        assertEquals("X",RootContextCode.compare(null,"mnt:[123]"));
    }
    @Test public void fixedCodesOnly(){
        assertEquals("ROOT_PLATFORM_UR_GR_KN_ID_CS_SM",RootContextCode.checked("ROOT_PLATFORM_UR_GR_KN_ID_CS_SM"));
        assertEquals("ROOT_PLATFORM_BINDER_DIED",RootContextCode.checked("ROOT_PLATFORM_BINDER_DIED"));
        assertEquals("ROOT_PLATFORM_RESULT_UNVERIFIED",RootContextCode.checked("ROOT_PLATFORM_UR_GR_KN_ID_CS_SM /data/private"));
        assertEquals("ROOT_PLATFORM_RESULT_UNVERIFIED",RootContextCode.checked(null));
    }
    @Test public void capabilitiesAndContextAreCategories(){
        assertEquals("Z",RootContextCode.capability("0000"));assertEquals("N",RootContextCode.capability("0a"));assertEquals("X",RootContextCode.capability("not-a-mask"));
        assertEquals("M",RootContextCode.security("u:r:kernel:s0"));assertEquals("U",RootContextCode.security("u:r:untrusted_app:s0"));assertEquals("X",RootContextCode.security("arbitrary label"));
    }
}
