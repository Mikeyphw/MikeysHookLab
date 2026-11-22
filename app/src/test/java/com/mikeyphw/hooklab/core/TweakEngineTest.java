package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class TweakEngineTest {
    private static final String HOST = "org.example.app";
    private final TweakSpec spec = new TweakSpec("example.safe", HOST, 10, 20);
    private TweakEngine.Entry<String> entry(TweakSpec s, TweakEngine.Installer<String> hook) {
        return new TweakEngine.Entry<>(s, "Example", "Test fixture", hook);
    }
    @Test public void wrongHostDoesNotConsultPreferences() {
        AtomicInteger reads = new AtomicInteger(), installs = new AtomicInteger();
        List<TweakEngine.Entry<String>> entries = Collections.singletonList(entry(spec, s -> installs.incrementAndGet()));
        TweakEngine.OptIn flags = id -> { reads.incrementAndGet(); return true; };
        for (String pkg : Arrays.asList("org.other", HOST))
            TweakEngine.dispatch(entries, pkg, HOST+":remote", 15, flags, "ctx", (id, err) -> fail());
        TweakEngine.dispatch(entries, HOST, HOST, 9, flags, "ctx", (id, err) -> fail());
        TweakEngine.dispatch(entries, HOST, HOST, 21, flags, "ctx", (id, err) -> fail());
        assertEquals(0, reads.get()); assertEquals(0, installs.get());
    }
    @Test public void defaultsToDisabled() {
        AtomicInteger installs = new AtomicInteger();
        TweakEngine.dispatch(Collections.singletonList(entry(spec, s -> installs.incrementAndGet())),
                HOST, HOST, 15, id -> false, "ctx", (id, err) -> fail());
        assertEquals(0, installs.get());
    }
    @Test public void isolatesFailedInstallers() {
        List<String> calls = new ArrayList<>(), failures = new ArrayList<>();
        TweakEngine.Entry<String> bad = entry(spec, s -> { calls.add("bad"); throw new Exception("failed"); });
        TweakEngine.Entry<String> good = entry(new TweakSpec("example.other", HOST, 10, 20),
                s -> calls.add("good"));
        int success = TweakEngine.dispatch(Arrays.asList(bad, good), HOST, HOST, 15,
                id -> true, "ctx", (id, err) -> failures.add(id));
        assertEquals(1, success);
        assertEquals(Arrays.asList("bad", "good"), calls);
        assertEquals(Collections.singletonList("example.safe"), failures);
    }
    @Test public void preferenceFailureFailsClosed() {
        AtomicInteger installs = new AtomicInteger(), errors = new AtomicInteger();
        TweakEngine.dispatch(Collections.singletonList(entry(spec, s -> installs.incrementAndGet())),
                HOST, HOST, 15, id -> { throw new IllegalStateException("service gone"); },
                "ctx", (id, ex) -> errors.incrementAndGet());
        assertEquals(0, installs.get()); assertEquals(1, errors.get());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsDuplicateIds() {
        TweakEngine.immutableCatalog(Arrays.asList(entry(spec,s -> {}), entry(spec,s -> {})));
    }
    @Test(expected=UnsupportedOperationException.class) public void catalogIsImmutable() {
        TweakEngine.immutableCatalog(Collections.singletonList(entry(spec, s -> {}))).clear();
    }
    @Test public void productionRegistersOnlyReviewedHosts() {
        assertEquals(4, TweakCatalog.entries().size()); assertEquals(4, TweakCatalog.all().size());
        assertFalse(TweakCatalog.knowsPackage(HOST));
        assertTrue(TweakCatalog.knowsPackage("br.art.code.meucronogramacapilar"));
        assertTrue(TweakCatalog.knowsPackage("com.machiav3lli.fdroid"));
        assertTrue(TweakCatalog.knowsPackage("com.looker.droidify"));
        assertTrue(TweakCatalog.knowsPackage("com.fugo.wowsolitaire"));
        assertFalse(TweakCatalog.knowsPackage("dev.bikram.obtainx"));
    }
    @Test public void keyIsNamespaced() {
        assertEquals("enabled.example.safe", TweakKeys.enabled("example.safe"));
        assertEquals("hooklab.tweaks.v1", TweakKeys.GROUP);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnsafeKey() { TweakKeys.enabled("../x"); }
}
