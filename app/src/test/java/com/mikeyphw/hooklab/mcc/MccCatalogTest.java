package com.mikeyphw.hooklab.mcc;

import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakSpec;
import org.junit.Test;
import static org.junit.Assert.*;

public class MccCatalogTest {
    @Test public void onlyManualDiamondTweakIsRegistered() {
        // The catalog can grow without granting MCC additional hooks.
        // Assert the MCC-specific invariant, not the total number of integrations.
        TweakEngine.Entry<HookContext> mccEntry = null;
        for (TweakEngine.Entry<HookContext> candidate : TweakCatalog.entries()) {
            if (!MccIds.PACKAGE.equals(candidate.spec.packageName)) continue;
            assertNull("Duplicate MCC installer", mccEntry);
            mccEntry = candidate;
        }
        assertNotNull("MCC manual installer missing", mccEntry);
        TweakSpec s = mccEntry.spec;
        assertEquals(MccIds.DIAMOND_MANUAL, s.id);
        assertEquals(MccIds.PACKAGE, s.packageName);
        assertTrue(TweakCatalog.knowsPackage(MccIds.PACKAGE));
        assertFalse(TweakCatalog.knowsPackage("com.example.other"));
        assertFalse(s.eligible(MccIds.PACKAGE, MccIds.PACKAGE, 2026093009L, false));
        assertTrue(s.eligible(MccIds.PACKAGE, MccIds.PACKAGE, 2026093009L, true));
        assertFalse(s.eligible(MccIds.PACKAGE, MccIds.PACKAGE + ":remote", 2026093009L, true));
    }

    @Test public void legacyAutoVipAndBannerIdsHaveNoInstaller() {
        for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries()) {
            assertNotEquals(MccIds.DIAMOND_AUTO, entry.spec.id);
            assertNotEquals(MccIds.VIP, entry.spec.id);
            assertNotEquals(MccIds.BANNER_HIDE, entry.spec.id);
        }
        assertFalse(MccDiamondUi.requestAutomatic());
    }
}
