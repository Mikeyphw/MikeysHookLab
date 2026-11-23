package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.HookGroupCatalog;
import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure read-only projection; no LSPosed writes, host injection or optimistic claims.
 * The latest imported log event is NEVER authenticated host execution evidence.
 */
public final class HomeDashboardModel {
    private HomeDashboardModel() { }
    public static final class Summary {
        public final int hookCount, groupCount, packageCount, scopedPackageCount;
        public final int eligibleConfigurations, problems, disabled, unknown;
        public final String attentionHookId;
        public final DiagnosticTimeline.Event latestImported;
        public final int importedCount, evictedCount;
        private Summary(int hooks,int groups,int packages,int scoped,int eligible,
                int problems,int disabled,int unknown,String attention,
                DiagnosticTimeline.Event latest,int imported,int evicted) {
            this.hookCount=hooks;this.groupCount=groups;this.packageCount=packages;
            this.scopedPackageCount=scoped;this.eligibleConfigurations=eligible;
            this.problems=problems;this.disabled=disabled;this.unknown=unknown;
            this.attentionHookId=attention;this.latestImported=latest;
            this.importedCount=imported;this.evictedCount=evicted;
        }
    }
    public static Summary project(List<TweakSpec> registered,
            List<HookGroupCatalog.Group> groups, FrameworkSnapshot state,
            DiagnosticTimeline imported) {
        Objects.requireNonNull(registered,"hooks");
        Objects.requireNonNull(groups,"groups");
        Objects.requireNonNull(state,"framework");
        Objects.requireNonNull(imported,"imported evidence");
        Set<String> ids=new HashSet<>(),packages=new HashSet<>(),groupIds=new HashSet<>(),assigned=new HashSet<>();
        for(TweakSpec spec:registered){
            Objects.requireNonNull(spec,"hook");
            if(!ids.add(spec.id))throw new IllegalArgumentException("Duplicate hook ID");
            packages.add(spec.packageName);
        }
        for(HookGroupCatalog.Group group:groups){
            Objects.requireNonNull(group,"group");
            if(!groupIds.add(group.id))throw new IllegalArgumentException("Duplicate group ID");
            for(TweakSpec spec:group.members){
                if(!ids.contains(spec.id)||!assigned.add(spec.id))
                    throw new IllegalArgumentException("Invalid group membership");
            }
        }
        if(!assigned.equals(ids))throw new IllegalArgumentException("Unassigned registered hook");
        int scoped=-1;
        if(state.ready()&&state.scope!=null){
            scoped=0;
            for(String pkg:packages)if(state.scope.contains(pkg))scoped++;
        }
        int eligible=0,problems=0,disabled=0,unknown=0;
        String attention=null;
        for(TweakSpec spec:registered){
            TweakCompatibility.State result=state.eligibility(spec);
            switch(result){
                case ELIGIBLE:eligible++;break;  // Configuration only, NOT execution
                case OUT_OF_SCOPE:
                case QUARANTINED:
                case VERSION_UNSUPPORTED:
                    problems++;
                    if(attention==null)attention=spec.id;
                    break;
                case DISABLED:disabled++;break; // Intentional opt-out is not a fault
                default:unknown++;break; // Includes disconnected, missing prefs/version
            }
        }
        DiagnosticTimeline.Event latest=null;
        List<DiagnosticTimeline.Event> events=imported.filtered(null);
        for(DiagnosticTimeline.Event e:events)
            if(latest==null||e.ordinal>latest.ordinal)latest=e;
        return new Summary(registered.size(),groups.size(),packages.size(),scoped,
                eligible,problems,disabled,unknown,attention,latest,
                imported.size(),imported.dropped());
    }
}
