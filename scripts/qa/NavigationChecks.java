package com.mikeyphw.hooklab.ui;
import java.util.*;
import com.mikeyphw.hooklab.core.*;
public final class NavigationChecks {
  static int count=0;
  static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);count++;}
  public static void main(String[] args){
    TweakSpec neo=new TweakSpec("neostore.obtainx.manual","com.example.app",1,5);
    TweakSpec droid=new TweakSpec("droidify.obtainx.manual","com.example.app",1,5);
    TweakSpec word=new TweakSpec("wordmaster.night.filter","com.example.word",1,5);
    List<TweakSpec> all=List.of(neo,droid,word);
    List<HookGroupCatalog.Group> groups=HookGroupCatalog.groups(all);
    check(CatalogRoutes.group(groups,"group.obtainx").kind==CatalogRoutes.Kind.GROUP,"multi-hook group");
    check(CatalogRoutes.group(groups,"group.wordmaster").id.equals(word.id),"single-hook exact identity");
    check(CatalogRoutes.group(groups,"missing")==null,"invalid group refused");
    check(CatalogRoutes.app(all,neo.packageName).kind==CatalogRoutes.Kind.APP,"multi-hook same package");
    check(CatalogRoutes.members(all,neo.packageName).size()==2,"unfiltered membership");
    check(CatalogRoutes.app(all,word.packageName).id.equals(word.id),"single-app exact identity");
    check(CatalogRoutes.app(all,"missing")==null,"invalid package refused");
    check(CatalogRoutes.iconPackages(List.of("c","b","a","a","d"),3).equals(List.of("a","b","c")),"deterministic dedup capped stack");
    check(HookDetailRoute.hook(all,droid.id)==droid,"exact hook not package fallback");
    RouteTrail<String> trail=new RouteTrail<>();trail.push("HOME");trail.push("GROUP");trail.push("HOOK");
    check("GROUP".equals(trail.rewind("GROUP"::equals)),"ancestor route rewind");
    check(trail.snapshot().equals(List.of("HOME")),"rewind removes cycle");
    check(trail.rewind("MISSING"::equals)==null&&trail.snapshot().size()==1,"missing rewind preserves trail");
    trail.push("INVALID");check("HOME".equals(trail.pop("HOME"::equals)),"invalid restored route discarded");
    for(int i=0;i<40;i++)trail.push("route"+i);
    check(trail.snapshot().size()==32&&trail.snapshot().get(0).equals("route8"),"bounded trail");
    trail.clear();check(trail.pop(x->true)==null,"empty history");
    trail.push("HOOK");trail.removeMatching("HOOK"::equals);trail.push("GROUP");check("GROUP".equals(trail.pop(x->true))&&trail.pop(x->true)==null,"preserve immediate origin without cycle");
    IconStateCache<String> cache=new IconStateCache<>(2);
    cache.put("a",1,1,"A");cache.put("b",1,1,"B");check("A".equals(cache.get("a",1,1)),"cache hit");
    cache.put("c",1,1,"C");check(cache.get("b",1,1)==null&&cache.size()==2,"LRU bound");
    check(cache.get("a",2,1)==null,"version change invalidates");
    check(cache.get("c",1,2)==null,"update time invalidates");
    cache.put("a",1,1,"A");cache.invalidate("a");check(cache.get("a",1,1)==null,"package event invalidates");
    cache.put("a",1,1,"A");cache.clear();check(cache.size()==0,"configuration invalidation");
    System.out.println("TOTAL="+count);
  }
}
