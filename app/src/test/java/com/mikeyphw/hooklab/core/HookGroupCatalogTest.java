package com.mikeyphw.hooklab.core;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Pure-Java, no mocked LSPosed activation or Android app context. */
public final class HookGroupCatalogTest {
    private static TweakSpec spec(String id, String pkg) {
        return new TweakSpec(id, pkg, 1L, 100L);
    }
    private static List<TweakSpec> five() {
        return Arrays.asList(
            spec("mcc.diamond.control", "br.art.code.meucronogramacapilar"),
            spec("neostore.obtainx.manual", "com.machiav3lli.fdroid"),
            spec("droidify.obtainx.manual", "com.looker.droidify"),
            spec("wordmaster.night.filter", "com.fugo.wowsolitaire"),
            spec("chatgpt.codeblock.copy.probe", "com.openai.chatgpt"));
    }
    @Test public void allFiveRegisteredHooksAssignedExactlyOnce() {
        List<HookGroupCatalog.Group> groups = HookGroupCatalog.groups(five());
        assertEquals(4, groups.size());
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (HookGroupCatalog.Group group : groups)
            for (TweakSpec hook : group.members)
                assertTrue("duplicated " + hook.id, ids.add(hook.id));
        assertEquals(5, ids.size());
        assertEquals(2, groups.get(0).members.size());
        assertEquals(2, groups.get(0).packages.size());
        assertEquals("group.obtainx", groups.get(0).id);
    }
    @Test public void independentlySelectTwoHooksSharingOnePackage() {
        List<TweakSpec> synthetic = new ArrayList<>(five());
        synthetic.set(2, spec("droidify.obtainx.manual", "com.machiav3lli.fdroid"));
        Map<String,List<TweakSpec>> packages = HookGroupCatalog.byPackage(synthetic);
        assertEquals(4, packages.size());
        assertEquals(2, packages.get("com.machiav3lli.fdroid").size());
        assertEquals("droidify.obtainx.manual", HookGroupCatalog.byId(synthetic,
                "droidify.obtainx.manual").id);
    }
    @Test public void noPhantomGroupOrUnregisteredHook() {
        List<HookGroupCatalog.Group> groups = HookGroupCatalog.groups(Arrays.asList(
                spec("mcc.diamond.control", "br.art.code.meucronogramacapilar")));
        assertEquals(1, groups.size());
        assertEquals("group.mcc", groups.get(0).id);
    }
    @Test(expected=IllegalStateException.class)
    public void unreviewedRegisteredHookCannotAppearSilently() {
        HookGroupCatalog.groups(Arrays.asList(spec("future.unreviewed", "com.example.app")));
    }
    @Test(expected=IllegalArgumentException.class)
    public void duplicateIdsRefused() {
        HookGroupCatalog.groups(Arrays.asList(
                spec("mcc.diamond.control", "com.example.one"),
                spec("mcc.diamond.control", "com.example.two")));
    }
    @Test public void searchIsCaseInsensitiveAndDoesNotMutateSources() {
        List<TweakSpec> source = five();
        assertTrue(HookGroupCatalog.matches("DROID", "Droid-ify", "ObtainX"));
        assertTrue(HookGroupCatalog.matches("", "any"));
        assertFalse(HookGroupCatalog.matches("Tinder", "Neo Store", "ObtainX"));
        HookGroupCatalog.groups(source);
        assertEquals("mcc.diamond.control", source.get(0).id);
    }
    @Test(expected=UnsupportedOperationException.class)
    public void assignedGroupsAreImmutable() { HookGroupCatalog.groups(five()).clear(); }
}
