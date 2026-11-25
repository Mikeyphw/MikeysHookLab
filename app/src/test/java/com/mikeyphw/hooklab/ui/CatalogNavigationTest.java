package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure production navigation and cache tests; no Android framework writes. */
public final class CatalogNavigationTest {
    private final TweakSpec neo=new TweakSpec("neostore.obtainx.manual","com.example.app",1,5);
    private final TweakSpec droid=new TweakSpec("droidify.obtainx.manual","com.example.app",1,5);
    private final TweakSpec word=new TweakSpec("wordmaster.night.filter","com.example.word",1,5);
    private final List<TweakSpec> all=List.of(neo,droid,word);
    @Test public void groupsUseFullCardinality(){var groups=HookGroupCatalog.groups(all);assertEquals(CatalogRoutes.Kind.GROUP,CatalogRoutes.group(groups,"group.obtainx").kind);assertEquals(word.id,CatalogRoutes.group(groups,"group.wordmaster").id);assertNull(CatalogRoutes.group(groups,"invalid"));}
    @Test public void samePackageDoesNotUseFilteredFirstHook(){assertEquals(CatalogRoutes.Kind.APP,CatalogRoutes.app(all,neo.packageName).kind);assertEquals(List.of(neo,droid),CatalogRoutes.members(all,neo.packageName));assertEquals(word.id,CatalogRoutes.app(all,word.packageName).id);assertNull(CatalogRoutes.app(all,"invalid"));assertSame(droid,HookDetailRoute.hook(all,droid.id));}
    @Test public void deduplicatesAndCapsIconStack(){assertEquals(List.of("a","b","c"),CatalogRoutes.iconPackages(List.of("c","b","a","a","d"),3));assertEquals(List.of("a"),CatalogRoutes.iconPackages(List.of("a","a"),3));}
    @Test public void ancestorRewindAvoidsCycles(){RouteTrail<String> trail=new RouteTrail<>();trail.push("home");trail.push("group");trail.push("hook");assertEquals("group",trail.rewind("group"::equals));assertEquals(List.of("home"),trail.snapshot());assertNull(trail.rewind("missing"::equals));assertEquals("home",trail.pop(x->true));}
    @Test public void newEntryPreservesImmediateOrigin(){RouteTrail<String> trail=new RouteTrail<>();trail.push("hook");trail.removeMatching("hook"::equals);trail.push("group");assertEquals("group",trail.pop(x->true));assertNull(trail.pop(x->true));}
    @Test public void invalidHistoryIsSkippedAndBounded(){RouteTrail<String> trail=new RouteTrail<>();for(int i=0;i<40;i++)trail.push("route"+i);assertEquals(32,trail.snapshot().size());assertEquals("route8",trail.snapshot().get(0));assertEquals("route38",trail.pop("route38"::equals));trail.clear();assertNull(trail.pop(x->true));}
    @Test public void iconCacheVersionUpdateAndEviction(){IconStateCache<String> cache=new IconStateCache<>(2);cache.put("a",1,1,"A");cache.put("b",1,1,"B");assertEquals("A",cache.get("a",1,1));cache.put("c",1,1,"C");assertNull(cache.get("b",1,1));assertEquals(2,cache.size());assertNull(cache.get("a",2,1));assertNull(cache.get("c",1,2));}
    @Test public void cachePackageAndConfigurationInvalidation(){IconStateCache<String> cache=new IconStateCache<>(2);cache.put("a",1,1,"A");cache.invalidate("a");assertNull(cache.get("a",1,1));cache.put("a",1,1,"A");cache.clear();assertEquals(0,cache.size());}
}
