package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Evidence-first, read-only health assessment for a registered HookLab hook.
 * Eligibility is a *configuration* assessment, never proof of execution.
 * Imported log lines are not authenticated runtime state, even when correlated.
 */
public final class HookHealthModel {
    private HookHealthModel() { }
    public enum Axis { OK, PROBLEM, UNKNOWN, OPTED_OUT }
    public enum Advice {
        CONNECT_FRAMEWORK, REVIEW_SCOPE, INSPECT_SAFETY_LOCK, ENABLE_OPT_IN,
        VERIFY_TARGET_VERSION, IMPORT_EVIDENCE, INSPECT_REPORTED_ERRORS,
        REVIEW_IMPORTED_EVIDENCE
    }
    public static final class Report {
        public final String hookId, packageName;
        public final Axis framework, scope, optIn, quarantine, version;
        public final TweakCompatibility.State eligibility;
        public final Advice advice;
        public final int matchingImports, reportedErrors, reportedWarnings;
        public final int unrelatedErrors, totalImports, retainedDropped;
        public final boolean scopeKnown, importedNotAuthenticated;
        public final String collectorGap, sourceSummary;
        public final List<DiagnosticTimeline.Event> recent;
        private Report(TweakSpec spec,Axis framework,Axis scope,Axis optIn,
                Axis quarantine,Axis version,TweakCompatibility.State eligibility,
                Advice advice,int matched,int errors,int warnings,int unrelated,
                int total,int lost,String gap,String sources,List<DiagnosticTimeline.Event> recent){
            this.hookId=spec.id;this.packageName=spec.packageName;
            this.framework=framework;this.scope=scope;this.optIn=optIn;
            this.quarantine=quarantine;this.version=version;
            this.scopeKnown=scope!=Axis.UNKNOWN;this.eligibility=eligibility;this.advice=advice;
            this.matchingImports=matched;this.reportedErrors=errors;
            this.reportedWarnings=warnings;this.unrelatedErrors=unrelated;
            this.totalImports=total;this.retainedDropped=lost;
            this.importedNotAuthenticated=true;this.collectorGap=gap;
            this.sourceSummary=sources;
            this.recent=Collections.unmodifiableList(new ArrayList<>(recent));
        }
        /** Source-aware evidence only. This code deliberately has no ACTIVE state. */
        public boolean configurationEligible(){return eligibility==TweakCompatibility.State.ELIGIBLE;}
        public boolean executionVerified(){return false;}
    }
    public static Report project(TweakSpec spec,FrameworkSnapshot snapshot,
            DiagnosticTimeline timeline){
        Objects.requireNonNull(spec,"registered hook");
        Objects.requireNonNull(snapshot,"framework snapshot");
        Objects.requireNonNull(timeline,"imported evidence");
        Axis framework=snapshot.ready()?Axis.OK:
            snapshot.connection==FrameworkSnapshot.Connection.LOADING ||
            snapshot.connection==FrameworkSnapshot.Connection.UNAVAILABLE
                ?Axis.UNKNOWN:Axis.PROBLEM;
        Boolean inScope=snapshot.ready()?snapshot.inScope(spec.packageName):null;
        Axis scope=inScope==null?Axis.UNKNOWN:inScope?Axis.OK:Axis.PROBLEM;
        FrameworkSnapshot.Flag flag=snapshot.ready()?snapshot.flag(spec.id):null;
        Axis optIn=flag==null||flag.enabled==null?Axis.UNKNOWN:
            flag.enabled?Axis.OK:Axis.OPTED_OUT;
        Axis quarantine=flag==null||flag.locked==null?Axis.UNKNOWN:
            flag.locked?Axis.PROBLEM:Axis.OK;
        Long installed=flag==null?null:flag.installedVersion;
        Axis version=installed==null||installed<1?Axis.UNKNOWN:
            installed>=spec.minVersion&&installed<=spec.maxVersion?Axis.OK:Axis.PROBLEM;
        TweakCompatibility.State eligibility=snapshot.eligibility(spec);
        int matched=0,errors=0,warnings=0,unrelated=0;
        Set<String> sources=new LinkedHashSet<>();
        List<DiagnosticTimeline.Event> recent=new ArrayList<>();
        for(DiagnosticTimeline.Event event:timeline.filtered(null)){
            sources.add(event.source);
            // Never credit a package-only or generic framework line to any hook.
            if(!spec.id.equals(event.hookId)){
                if("ERROR".equals(event.severity))unrelated++;
                continue;
            }
            matched++;
            if("ERROR".equals(event.severity))errors++;
            else if("WARN".equals(event.severity))warnings++;
            recent.add(event);
        }
        recent.sort(Comparator.comparingLong((DiagnosticTimeline.Event e)->e.ordinal).reversed());
        if(recent.size()>5)recent=new ArrayList<>(recent.subList(0,5));
        int lost=timeline.dropped();
        String gap=timeline.size()==0?"NO_IMPORTED_SOURCE":
            matched==0?"NO_EXACT_HOOK_ASSOCIATION":
            lost>0?"EVIDENCE_RETAINED_WITH_EVICTIONS":"IMPORTED_ASSOCIATION_ONLY";
        Advice advice;
        if(framework!=Axis.OK)advice=Advice.CONNECT_FRAMEWORK;
        else if(scope!=Axis.OK)advice=Advice.REVIEW_SCOPE;
        else if(quarantine==Axis.PROBLEM)advice=Advice.INSPECT_SAFETY_LOCK;
        else if(optIn==Axis.OPTED_OUT)advice=Advice.ENABLE_OPT_IN;
        else if(version!=Axis.OK)advice=Advice.VERIFY_TARGET_VERSION;
        else if(matched==0)advice=Advice.IMPORT_EVIDENCE;
        else if(errors>0)advice=Advice.INSPECT_REPORTED_ERRORS;
        else advice=Advice.REVIEW_IMPORTED_EVIDENCE;
        return new Report(spec,framework,scope,optIn,quarantine,version,
            eligibility,advice,matched,errors,warnings,unrelated,timeline.size(),
            lost,gap,String.join(",",sources),recent);
    }
    /** The only export/copy payload: already sanitized event metadata, no raw logs. */
    public static String redactedJson(Report r){
        Objects.requireNonNull(r,"health report");
        StringBuilder json=new StringBuilder();
        json.append("{\"schema\":\"hooklab-dx03-health.v1\",\"hook_id\":")
            .append(DiagnosticTimeline.json(r.hookId))
            .append(",\"package\":").append(DiagnosticTimeline.json(r.packageName))
            .append(",\"framework\":").append(DiagnosticTimeline.json(r.framework.name()))
            .append(",\"scope\":").append(DiagnosticTimeline.json(r.scope.name()))
            .append(",\"opt_in\":").append(DiagnosticTimeline.json(r.optIn.name()))
            .append(",\"safety_lock\":").append(DiagnosticTimeline.json(r.quarantine.name()))
            .append(",\"target_version\":").append(DiagnosticTimeline.json(r.version.name()))
            .append(",\"configuration_eligibility\":").append(DiagnosticTimeline.json(r.eligibility.name()))
            .append(",\"advice\":").append(DiagnosticTimeline.json(r.advice.name()))
            .append(",\"collector_gap\":").append(DiagnosticTimeline.json(r.collectorGap))
            .append(",\"imported_sources\":").append(DiagnosticTimeline.json(r.sourceSummary))
            .append(",\"matching_imports\":").append(r.matchingImports)
            .append(",\"reported_errors\":").append(r.reportedErrors)
            .append(",\"unrelated_error_lines\":").append(r.unrelatedErrors)
            .append(",\"retention_evictions\":").append(r.retainedDropped)
            .append(",\"runtime_execution_verified\":false,\"imported_evidence_authenticated\":false")
            .append(",\"source_events\":[");
        for(int i=0;i<r.recent.size();i++){
            if(i>0)json.append(',');
            json.append(DiagnosticTimeline.toJson(r.recent.get(i)));
        }
        return json.append("]}\n").toString();
    }
}
