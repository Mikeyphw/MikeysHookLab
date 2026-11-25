package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Read-only navigation choices derived exclusively from registered hook identities. */
public final class CatalogRoutes {
    private CatalogRoutes() {}
    public enum Kind { GROUP, APP, HOOK }
    public static final class Target {
        public final Kind kind;
        public final String id;
        private Target(Kind kind, String id) { this.kind=kind; this.id=id; }
    }
    public static List<TweakSpec> members(List<TweakSpec> registered, String pkg) {
        List<TweakSpec> result=new ArrayList<>();
        if(pkg!=null) for(TweakSpec spec:registered) if(pkg.equals(spec.packageName)) result.add(spec);
        return Collections.unmodifiableList(result);
    }
    public static Target app(List<TweakSpec> registered,String pkg) {
        List<TweakSpec> all=members(registered,pkg);
        return all.isEmpty()?null:all.size()==1?new Target(Kind.HOOK,all.get(0).id):new Target(Kind.APP,pkg);
    }
    public static Target group(List<HookGroupCatalog.Group> groups,String id) {
        HookGroupCatalog.Group group=HookDetailRoute.group(groups,id);
        if(group==null||group.members.isEmpty())return null;
        return group.members.size()==1?new Target(Kind.HOOK,group.members.get(0).id):new Target(Kind.GROUP,id);
    }
    public static List<String> iconPackages(List<String> packages,int limit) {
        if(limit<1||limit>8)throw new IllegalArgumentException("icon limit");
        List<String> result=new ArrayList<>(new TreeSet<>(packages));
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0,Math.min(limit,result.size()))));
    }
}
