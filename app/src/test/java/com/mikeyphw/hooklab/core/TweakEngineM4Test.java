package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** Host-independent adversarial coverage; run in hosted GitHub Actions JUnit. */
public class TweakEngineM4Test {
    private static final String HOST = "org.example.app";
    private static final TweakSpec SPEC = new TweakSpec("example.safe", HOST, 10, 20);
    private TweakEngine.Entry<String> entry(String id, TweakEngine.Installer<String> install) {
        return new TweakEngine.Entry<>(new TweakSpec(id, HOST, 10, 20), "Test", "Test fixture", install);
    }
    private static final class Policy implements TweakEngine.FaultPolicy {
        boolean blocked;
        boolean cannotRead;
        boolean cannotWrite;
        int reads, writes;
        @Override public boolean quarantined(String id) {
            reads++;
            if (cannotRead) throw new IllegalStateException("unavailable");
            return blocked;
        }
        @Override public void quarantine(String id) {
            writes++;
            if (cannotWrite) throw new IllegalStateException("cannot persist");
            blocked = true;
        }
    }

    @Test public void quarantinedInstallerNeverRunsAndDoesNotReadOptIn() {
        Policy p = new Policy(); p.blocked = true;
        AtomicInteger reads = new AtomicInteger(), calls = new AtomicInteger();
        List<TweakDiagnostics.Outcome> events = new ArrayList<>();
        int count = TweakEngine.dispatch(Collections.singletonList(entry("example.safe", ctx -> calls.incrementAndGet())),
                HOST, HOST, 15, id -> { reads.incrementAndGet(); return true; }, "ctx", (id, e) -> fail(),
                p, (id, event, category) -> events.add(event));
        assertEquals(0, count); assertEquals(0, reads.get()); assertEquals(0, calls.get());
        assertEquals(Collections.singletonList(TweakDiagnostics.Outcome.QUARANTINED), events);
    }

    @Test public void failedQuarantineReadFailsClosed() {
        Policy p = new Policy(); p.cannotRead = true;
        AtomicInteger calls = new AtomicInteger(), errors = new AtomicInteger();
        int count = TweakEngine.dispatch(Collections.singletonList(entry("example.safe", ctx -> calls.incrementAndGet())),
                HOST, HOST, 15, id -> true, "ctx", (id, e) -> errors.incrementAndGet(), p, (id, state, category) -> {});
        assertEquals(0, count); assertEquals(0, calls.get()); assertEquals(1, errors.get());
    }

    @Test public void linkageErrorIsQuarantinedAndIndependentTweakRuns() {
        Policy p = new Policy();
        List<String> outcomes = new ArrayList<>();
        List<String> events = new ArrayList<>();
        int count = TweakEngine.dispatch(Arrays.asList(
                entry("example.safe", ctx -> { throw new NoClassDefFoundError("missing class"); }),
                entry("example.second", ctx -> outcomes.add("second"))),
                HOST, HOST, 15, id -> true, "ctx", (id, e) -> outcomes.add(id), p,
                (id, result, category) -> events.add(id + ":" + result + ":" + category));
        assertEquals(1, count); assertEquals(1, p.writes); assertTrue(p.blocked);
        assertEquals(Arrays.asList("example.safe", "second"), outcomes);
        assertEquals(Arrays.asList("example.safe:INSTALL_FAILED:LINKAGE", "example.second:INSTALLED:NONE"), events);
    }

    @Test public void preferenceReadFailureDoesNotQuarantine() {
        Policy p = new Policy();
        AtomicInteger attempts = new AtomicInteger();
        int count = TweakEngine.dispatch(Collections.singletonList(entry("example.safe", x -> attempts.incrementAndGet())),
                HOST, HOST, 15, id -> { throw new IllegalStateException("denied"); }, "ctx",
                (id, e) -> {}, p, (id, state, category) -> assertEquals(TweakDiagnostics.Outcome.PREFERENCE_UNAVAILABLE, state));
        assertEquals(0, count); assertEquals(0, attempts.get()); assertEquals(0, p.writes);
    }

    @Test public void reporterAndErrorSinkCannotCrashOtherEntries() {
        Policy p = new Policy();
        AtomicInteger good = new AtomicInteger();
        int installed = TweakEngine.dispatch(Arrays.asList(
                entry("example.safe", ctx -> { throw new IllegalArgumentException("bad"); }),
                entry("example.second", ctx -> good.incrementAndGet())),
                HOST, HOST, 15, id -> true, "ctx", (id, e) -> { throw new AssertionError("sink"); },
                p, (id, state, category) -> { throw new IllegalStateException("status sink"); });
        assertEquals(1, installed); assertEquals(1, good.get());
    }

    @Test public void failedQuarantineWriteStillIsolatesNextInstaller() {
        Policy p = new Policy(); p.cannotWrite = true;
        AtomicInteger good = new AtomicInteger(), errors = new AtomicInteger();
        int installed = TweakEngine.dispatch(Arrays.asList(
                entry("example.safe", ctx -> { throw new RuntimeException("installer"); }),
                entry("example.second", ctx -> good.incrementAndGet())),
                HOST, HOST, 15, id -> true, "ctx", (id, e) -> errors.incrementAndGet(),
                p, (id, status, category) -> {});
        assertEquals(1, installed); assertEquals(1, good.get()); assertEquals(2, errors.get());
    }

    @Test(expected=OutOfMemoryError.class) public void fatalVmErrorIsRethrown() {
        Policy p = new Policy();
        TweakEngine.dispatch(Collections.singletonList(entry("example.safe", ctx -> { throw new OutOfMemoryError(); })),
                HOST, HOST, 15, id -> true, "ctx", (id, e) -> fail(), p, (id, st, cat) -> fail());
    }

    @Test(expected=ThreadDeath.class) public void threadDeathIsRethrown() {
        Policy p = new Policy();
        TweakEngine.dispatch(Collections.singletonList(entry("example.safe", ctx -> { throw new ThreadDeath(); })),
                HOST, HOST, 15, id -> true, "ctx", (id, e) -> fail(), p, (id, st, cat) -> fail());
    }

    @Test public void incompatibleNeverQueriesQuarantineOrPreferences() {
        Policy p = new Policy();
        AtomicInteger flags = new AtomicInteger();
        for (long version : new long[]{0, 9, 21})
            TweakEngine.dispatch(Collections.singletonList(entry("example.safe", ctx -> fail())),
                    HOST, HOST, version, id -> { flags.incrementAndGet(); return true; }, "ctx",
                    (id, e) -> fail(), p, (id, st, cat) -> fail());
        assertEquals(0, p.reads); assertEquals(0, flags.get());
    }

    @Test public void inProcessFailureLatchSkipsSecondAttempt() {
        Policy p = new Policy();
        AtomicInteger tries = new AtomicInteger();
        TweakEngine.Entry<String> unstable = entry("example.safe", ctx -> {
            tries.incrementAndGet(); throw new IllegalStateException("simulated");
        });
        for (int i = 0; i < 2; i++) {
            TweakEngine.dispatch(Collections.singletonList(unstable), HOST, HOST, 15,
                    id -> true, "ctx", (id, e) -> {}, p, (id, outcome, category) -> {});
        }
        assertEquals(1, tries.get()); assertTrue(p.blocked); assertEquals(1, p.writes);
    }

    @Test public void compatibilityNeverAssumesCurrentVersion() {
        assertEquals(TweakCompatibility.State.FRAMEWORK_UNAVAILABLE,
                TweakCompatibility.assess(SPEC, false, true, true, false, 15L));
        assertEquals(TweakCompatibility.State.SCOPE_UNKNOWN,
                TweakCompatibility.assess(SPEC, true, null, true, false, 15L));
        assertEquals(TweakCompatibility.State.OUT_OF_SCOPE,
                TweakCompatibility.assess(SPEC, true, false, true, false, 15L));
        assertEquals(TweakCompatibility.State.QUARANTINED,
                TweakCompatibility.assess(SPEC, true, true, true, true, 15L));
        assertEquals(TweakCompatibility.State.DISABLED,
                TweakCompatibility.assess(SPEC, true, true, false, false, 15L));
        assertEquals(TweakCompatibility.State.VERSION_UNVERIFIED,
                TweakCompatibility.assess(SPEC, true, true, true, false, null));
        assertEquals(TweakCompatibility.State.VERSION_UNSUPPORTED,
                TweakCompatibility.assess(SPEC, true, true, true, false, 21L));
        assertEquals(TweakCompatibility.State.ELIGIBLE,
                TweakCompatibility.assess(SPEC, true, true, true, false, 15L));
    }

    @Test public void diagnosticsHaveFixedValuesAndValidatedKeys() {
        assertEquals("hooklab.safety.v1", TweakDiagnostics.GROUP);
        assertEquals("quarantine.example.safe", TweakDiagnostics.quarantineKey("example.safe"));
        assertEquals("LINKAGE", TweakDiagnostics.failureCategory(new NoClassDefFoundError()));
        assertEquals("SECURITY", TweakDiagnostics.failureCategory(new SecurityException()));
        try { TweakDiagnostics.quarantineKey("../bad"); fail("Unsafe key accepted"); }
        catch (IllegalArgumentException expected) { /* correct */ }
    }
}
