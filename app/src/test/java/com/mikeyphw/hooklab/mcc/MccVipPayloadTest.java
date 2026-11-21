package com.mikeyphw.hooklab.mcc;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure-Java regression tests; run only via GitHub Actions JUnit. */
public class MccVipPayloadTest {
    private Map<String,Object> input() {
        Map<String,Object> info = new LinkedHashMap<>();
        Map<String,Object> ents = new LinkedHashMap<>();
        ents.put("active", new LinkedHashMap<String,Object>());
        ents.put("all", new LinkedHashMap<String,Object>());
        info.put("entitlements", ents);
        info.put("originalAppUserId", "preserve-me");
        info.put("activeSubscriptions", Collections.singletonList("other.product"));
        return info;
    }
    @Test public void invalidShapesAreNotPatched() {
        assertNull(MccVipPayload.patch(null));
        assertNull(MccVipPayload.patch("text"));
        assertNull(MccVipPayload.patch(Collections.singletonMap("entitlements", "bad")));
    }
    @Test public void mappedDataIsNotModified() {
        Map<String,Object> original = input();
        Map<String,Object> patched = MccVipPayload.patch(original);
        assertNotNull(patched);
        assertNotSame(original, patched);
        assertEquals("preserve-me", patched.get("originalAppUserId"));
        assertFalse(((Map<?,?>)((Map<?,?>)original.get("entitlements")).get("all")).containsKey("premium_noads"));
        assertTrue(((Map<?,?>)((Map<?,?>)patched.get("entitlements")).get("all")).containsKey("premium_noads"));
    }
    @Test public void syntheticEntitlementHasExplicitTestStore() {
        Map<String,Object> patched = MccVipPayload.patch(input());
        Map<?,?> ents = (Map<?,?>)patched.get("entitlements");
        Map<?,?> vip = (Map<?,?>)((Map<?,?>)ents.get("active")).get("premium_noads");
        assertEquals("TEST_STORE", vip.get("store"));
        assertEquals(Boolean.TRUE, vip.get("isActive"));
        assertEquals("premium_noads", vip.get("productIdentifier"));
        assertEquals(Arrays.asList("other.product", "premium_noads"), patched.get("activeSubscriptions"));
    }
    @Test public void emptyFixtureIsCompatible() {
        assertNotNull(MccVipPayload.fixture());
        assertEquals("premium_noads", MccVipPayload.ENTITLEMENT);
    }
}
