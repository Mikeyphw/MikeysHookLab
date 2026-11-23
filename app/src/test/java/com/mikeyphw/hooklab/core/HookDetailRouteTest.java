package com.mikeyphw.hooklab.core;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** No mocked framework activation or implicit scope changes. */
public final class HookDetailRouteTest {
    private static TweakSpec spec(String id,String pkg) {
        return new TweakSpec(id,pkg,1L,100L);
    }
    private static List<TweakSpec> fixtures() {
        return Arrays.asList(
            spec("mcc.diamond.control","br.art.code.meucronogramacapilar"),
            spec("neostore.obtainx.manual","com.machiav3lli.fdroid"),
            spec("droidify.obtainx.manual","com.machiav3lli.fdroid"),
            spec("wordmaster.night.filter","com.fugo.wowsolitaire"),
            spec("chatgpt.codeblock.copy.probe","com.openai.chatgpt"));
    }
    @Test public void samePackageHooksRemainIndependentlyAddressed() {
        List<TweakSpec> hooks=fixtures();
        TweakSpec neo=HookDetailRoute.hook(hooks,"neostore.obtainx.manual");
        TweakSpec droid=HookDetailRoute.hook(hooks,"droidify.obtainx.manual");
        assertNotEquals(neo.id,droid.id);
        assertEquals(neo.packageName,droid.packageName);
        assertSame(neo,HookDetailRoute.hook(hooks,neo.id));
        assertSame(droid,HookDetailRoute.hook(hooks,droid.id));
    }
    @Test public void unknownHookNeverOpensFirstHookInSamePackage() {
        assertNull(HookDetailRoute.hook(fixtures(),"unknown.hook"));
        assertNull(HookDetailRoute.hook(fixtures(),""));
        assertNull(HookDetailRoute.hook(fixtures(),null));
    }
    @Test public void groupsHaveIndependentMemberAndTargetSets() {
        List<HookGroupCatalog.Group> groups=HookGroupCatalog.groups(fixtures());
        HookGroupCatalog.Group obtainx=HookDetailRoute.group(groups,"group.obtainx");
        assertNotNull(obtainx);
        assertEquals(2,obtainx.members.size());
        assertEquals(1,obtainx.packages.size());
        assertTrue(HookDetailRoute.groupContains(obtainx,"neostore.obtainx.manual"));
        assertTrue(HookDetailRoute.groupContains(obtainx,"droidify.obtainx.manual"));
        assertFalse(HookDetailRoute.groupContains(obtainx,"mcc.diamond.control"));
        assertSame(obtainx,HookDetailRoute.groupForHook(groups,"droidify.obtainx.manual"));
    }
    @Test public void invalidGroupsAndMembershipNeverRoute() {
        List<HookGroupCatalog.Group> groups=HookGroupCatalog.groups(fixtures());
        assertNull(HookDetailRoute.group(groups,"group.invented"));
        assertNull(HookDetailRoute.group(groups,null));
        assertNull(HookDetailRoute.groupForHook(groups,"not.registered"));
        assertFalse(HookDetailRoute.groupContains(null,"mcc.diamond.control"));
    }
    @Test(expected=IllegalStateException.class)
    public void duplicateHookIdentityFailsClosed() {
        HookDetailRoute.hook(Arrays.asList(spec("same.hook","com.example.one"),
                spec("same.hook","com.example.two")),"same.hook");
    }
    @Test public void groupMembershipDoesNotChangeHookRegistry() {
        List<TweakSpec> original=fixtures();
        List<HookGroupCatalog.Group> groups=HookGroupCatalog.groups(original);
        Map<String,List<TweakSpec>> byPkg=HookGroupCatalog.byPackage(original);
        assertEquals(4,groups.size());
        assertEquals(4,byPkg.size());
        assertEquals(original.size(),5);
        assertSame(original.get(0),HookDetailRoute.hook(original,original.get(0).id));
    }
}
