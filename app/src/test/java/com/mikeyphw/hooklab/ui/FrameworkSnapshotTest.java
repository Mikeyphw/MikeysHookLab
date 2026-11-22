package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** UI-03 host-side tests: unknown is never read as disabled or verified. */
public final class FrameworkSnapshotTest {
    private static final TweakSpec SPEC = new TweakSpec("neo.obtainx", "com.example.neostore", 1304, 1304);

    private static FrameworkSnapshot connected(Boolean enabled, Boolean locked,
                                               Long version, boolean approved) {
        Map<String, FrameworkSnapshot.Flag> flags = new HashMap<>();
        flags.put(SPEC.id, new FrameworkSnapshot.Flag(enabled, locked, version));
        return FrameworkSnapshot.connected("LSPosed", "1.0", 102,
                approved ? new HashSet<>(Arrays.asList(SPEC.packageName)) :
                        new HashSet<>(), flags, false);
    }
    @Test public void loadingIsUnknownNotDisconnected() {
        assertEquals(FrameworkSnapshot.Connection.LOADING, FrameworkSnapshot.loading().connection);
        assertEquals(TweakCompatibility.State.SCOPE_UNKNOWN,
                FrameworkSnapshot.loading().eligibility(SPEC));
    }
    @Test public void unsupportedFrameworkCannotClaimHookEligible() {
        assertEquals(TweakCompatibility.State.FRAMEWORK_UNAVAILABLE,
                FrameworkSnapshot.unsupported(82).eligibility(SPEC));
    }
    @Test public void disconnectedIsNotInstalledOrEligible() {
        assertEquals(TweakCompatibility.State.FRAMEWORK_UNAVAILABLE,
                FrameworkSnapshot.disconnected().eligibility(SPEC));
    }
    @Test public void missingScopeAndPreferencesRemainUnknown() {
        FrameworkSnapshot partial = FrameworkSnapshot.connected("LSPosed", "1.0", 102,
                null, Collections.emptyMap(), null);
        assertNull(partial.inScope(SPEC.packageName));
        assertNull(partial.verboseLogging);
        assertEquals(TweakCompatibility.State.SCOPE_UNKNOWN, partial.eligibility(SPEC));
    }
    @Test public void explicitlyOutOfScopeIsNotMistakenForPreferencesError() {
        assertEquals(TweakCompatibility.State.OUT_OF_SCOPE,
                connected(null, null, null, false).eligibility(SPEC));
    }
    @Test public void unreadablePreferenceIsUnknownNotOff() {
        assertEquals(TweakCompatibility.State.SCOPE_UNKNOWN,
                connected(null, false, 1304L, true).eligibility(SPEC));
    }
    @Test public void manualSafetyLockOverridesOptIn() {
        assertEquals(TweakCompatibility.State.QUARANTINED,
                connected(true, true, 1304L, true).eligibility(SPEC));
    }
    @Test public void eligibilityRequiresVerifiedVersion() {
        assertEquals(TweakCompatibility.State.VERSION_UNVERIFIED,
                connected(true, false, null, true).eligibility(SPEC));
        assertEquals(TweakCompatibility.State.VERSION_UNSUPPORTED,
                connected(true, false, 1305L, true).eligibility(SPEC));
        assertEquals(TweakCompatibility.State.ELIGIBLE,
                connected(true, false, 1304L, true).eligibility(SPEC));
    }
    @Test public void snapshotCopiesInputsAndCannotBeMutated() {
        HashSet<String> scope = new HashSet<>();
        scope.add(SPEC.packageName);
        Map<String, FrameworkSnapshot.Flag> flags = new HashMap<>();
        flags.put(SPEC.id, new FrameworkSnapshot.Flag(true, false, 1304L));
        FrameworkSnapshot value = FrameworkSnapshot.connected("LSPosed", "1.0", 102,
                scope, flags, false);
        scope.clear();
        flags.clear();
        assertEquals(TweakCompatibility.State.ELIGIBLE, value.eligibility(SPEC));
        try { value.scope.clear(); fail("Scope should be immutable"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
        try { value.flags.clear(); fail("Flags should be immutable"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
    }
    @Test public void equivalentSnapshotsDoNotTriggerRedundantRender() {
        assertEquals(connected(true, false, 1304L, true),
                connected(true, false, 1304L, true));
        assertEquals(connected(true, false, 1304L, true).hashCode(),
                connected(true, false, 1304L, true).hashCode());
    }
}
