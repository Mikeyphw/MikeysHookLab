package com.mikeyphw.hooklab.mcc;

import com.mikeyphw.hooklab.core.HookContext;
import com.mikeyphw.hooklab.core.TweakCatalog;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class MccCatalogTest {
    @Test public void exactlyFourDisabledByDefaultAndOneHost() {
        assertEquals(4, TweakCatalog.entries().size());
        assertTrue(TweakCatalog.knowsPackage(MccIds.PACKAGE));
        assertFalse(TweakCatalog.knowsPackage("com.example.unrelated"));
        Set<String> ids = new HashSet<>();
        for (TweakEngine.Entry<HookContext> entry : TweakCatalog.entries()) {
            TweakSpec s = entry.spec;
            assertEquals(MccIds.PACKAGE, s.packageName);
            assertTrue(ids.add(s.id));
            assertTrue(s.eligible(MccIds.PACKAGE, MccIds.PACKAGE, 1L, true));
            assertFalse(s.eligible(MccIds.PACKAGE, MccIds.PACKAGE + ":remote", 1L, true));
            assertFalse(s.eligible(MccIds.PACKAGE, MccIds.PACKAGE, 1L, false));
        }
        assertTrue(ids.contains(MccIds.VIP));
        assertTrue(ids.contains(MccIds.DIAMOND_MANUAL));
        assertTrue(ids.contains(MccIds.DIAMOND_AUTO));
        assertTrue(ids.contains(MccIds.BANNER_HIDE));
    }
}
