package com.mikeyphw.hooklab.mcc;

import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakSpec;
import org.junit.Test;
import static org.junit.Assert.*;

public class MccCatalogTest {
    @Test public void onlyManualDiamondTweakIsRegistered() {
        assertEquals(3,TweakCatalog.entries().size());
        TweakEngine.Entry<HookContext> entry=TweakCatalog.entries().get(0);
        TweakSpec s=entry.spec;
        assertEquals(MccIds.DIAMOND_MANUAL,s.id);
        assertEquals(MccIds.PACKAGE,s.packageName);
        assertTrue(TweakCatalog.knowsPackage(MccIds.PACKAGE));
        assertFalse(TweakCatalog.knowsPackage("com.example.other"));
        assertFalse(s.eligible(MccIds.PACKAGE,MccIds.PACKAGE,1L,false));
        assertTrue(s.eligible(MccIds.PACKAGE,MccIds.PACKAGE,1L,true));
        assertFalse(s.eligible(MccIds.PACKAGE,MccIds.PACKAGE+":remote",1L,true));
    }
    @Test public void legacyAutoVipAndBannerIdsHaveNoInstaller() {
        for (TweakEngine.Entry<HookContext> entry:TweakCatalog.entries()) {
            assertNotEquals(MccIds.DIAMOND_AUTO,entry.spec.id);
            assertNotEquals(MccIds.VIP,entry.spec.id);
            assertNotEquals(MccIds.BANNER_HIDE,entry.spec.id);
        }
        assertFalse(MccDiamondUi.requestAutomatic());
    }
}
