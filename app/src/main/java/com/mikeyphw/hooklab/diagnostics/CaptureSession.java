package com.mikeyphw.hooklab.diagnostics;

import com.mikeyphw.hooklab.core.TweakSpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Explicit SAF-import-only guided reproduction. This is not live instrumentation.
 * All imported events remain unauthenticated, source-attributed observations.
 */
public final class CaptureSession {
    public static final long MAX_DURATION_MS=10*60*1000L;
    public static final int MAX_MARKERS=64;
    public enum State { ACTIVE, STOPPED, EXPIRED, INTERRUPTED, CANCELLED }
    public enum MarkerCode { REPRODUCE_ACTION, OBSERVED_PROBLEM, APP_RESTARTED,
        PERMISSION_PROMPT, IMPORT_CONFIRMED }
    public static final class Marker {
        public final MarkerCode code;
        public final long elapsedMillis, importOrdinal;
        private Marker(MarkerCode code,long millis,long ordinal){
            this.code=code;this.elapsedMillis=millis;this.importOrdinal=ordinal;
        }
    }
    public static final class Report {
        public final String manifest,eventsJsonl,markersJsonl;
        public final int observed,missingDueToRetention;
        private Report(String manifest,String events,String markers,int observed,int missing){
            this.manifest=manifest;this.eventsJsonl=events;this.markersJsonl=markers;
            this.observed=observed;this.missingDueToRetention=missing;
        }
    }
    public final String id,targetHookId,targetPackage,source;
    public final long minVersion,maxVersion;
    public final boolean researchOnly;
    private final long startedElapsed,startedWall,startOrdinal;
    private final int droppedAtStart;
    private long endedElapsed=-1,endOrdinal=-1;
    private State state=State.ACTIVE;
    private final ArrayList<Marker> markers=new ArrayList<>();
    private final ArrayList<long[]> acceptedImportRanges=new ArrayList<>();

    private CaptureSession(String hook,String pkg,long min,long max,boolean research,String source,
            long nowElapsed,long nowWall,long ordinal,int dropped) {
        if(!DiagnosticTimeline.acceptedSource(source))throw new IllegalArgumentException("DX02_UNREVIEWED_SOURCE");
        if(nowElapsed<0||nowWall<=0||ordinal<0||dropped<0)throw new IllegalArgumentException("DX02_INVALID_CLOCK_OR_CURSOR");
        this.id=UUID.randomUUID().toString();this.targetHookId=hook;
        this.targetPackage=pkg;this.minVersion=min;this.maxVersion=max;
        this.researchOnly=research;this.source=source;
        this.startedElapsed=nowElapsed;this.startedWall=nowWall;this.startOrdinal=ordinal;
        this.droppedAtStart=dropped;
    }
    public static CaptureSession forHook(TweakSpec spec,String source,
            long elapsed,long wall,long ordinal,int dropped){
        if(spec==null)throw new IllegalArgumentException("DX02_REGISTERED_HOOK_REQUIRED");
        return new CaptureSession(spec.id,spec.packageName,spec.minVersion,spec.maxVersion,
                false,source,elapsed,wall,ordinal,dropped);
    }
    /** Unregistered packages are research-only and never used for hook creation. */
    public static CaptureSession forResearch(String pkg,String source,
            long elapsed,long wall,long ordinal,int dropped){
        if(pkg==null||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
            throw new IllegalArgumentException("DX02_INVALID_RESEARCH_PACKAGE");
        return new CaptureSession("",pkg,0,0,true,source,elapsed,wall,ordinal,dropped);
    }
    /** Monotonic clock regression (including reboot) interrupts; never restores active capture. */
    public synchronized State expireIfNeeded(long nowElapsed,long currentOrdinal){
        if(state!=State.ACTIVE)return state;
        if(nowElapsed<startedElapsed){finish(State.INTERRUPTED,startedElapsed,currentOrdinal);}
        else if(nowElapsed-startedElapsed>=MAX_DURATION_MS)
            finish(State.EXPIRED,startedElapsed+MAX_DURATION_MS,currentOrdinal);
        return state;
    }
    public synchronized State state(){return state;}
    public synchronized int markersCount(){return markers.size();}
    public synchronized boolean canImport(long nowElapsed,long ordinal){
        return expireIfNeeded(nowElapsed,ordinal)==State.ACTIVE&&acceptedImportRanges.size()<MAX_MARKERS;
    }
    public synchronized void mark(MarkerCode code,long elapsed,long ordinal){
        if(code==null)throw new IllegalArgumentException("DX02_MARKER_CODE_REQUIRED");
        if(expireIfNeeded(elapsed,ordinal)!=State.ACTIVE)throw new IllegalStateException("DX02_NOT_CAPTURING");
        if(ordinal<startOrdinal||markers.size()>=MAX_MARKERS)
            throw new IllegalStateException("DX02_MARKER_LIMIT_OR_CURSOR");
        markers.add(new Marker(code,elapsed-startedElapsed,ordinal));
    }
    /** Attribute only explicit SAF imports completed during this active window.
     * Merely appearing later in the global timeline never admits an event.
     */
    public synchronized boolean registerImport(String importedSource,long previousOrdinal,
            long finalOrdinal,long atElapsed){
        if(expireIfNeeded(atElapsed,Math.max(startOrdinal,finalOrdinal))!=State.ACTIVE)return false;
        if(!source.equals(importedSource)||previousOrdinal<startOrdinal||finalOrdinal<previousOrdinal)
            throw new IllegalArgumentException("DX02_IMPORT_PROVENANCE_MISMATCH");
        if(acceptedImportRanges.size()>=MAX_MARKERS)throw new IllegalStateException("DX02_IMPORT_BATCH_LIMIT");
        if(finalOrdinal==previousOrdinal)return true;
        acceptedImportRanges.add(new long[]{previousOrdinal+1,finalOrdinal});
        if(markers.size()<MAX_MARKERS)
            markers.add(new Marker(MarkerCode.IMPORT_CONFIRMED,atElapsed-startedElapsed,finalOrdinal));
        return true;
    }
    private void finish(State finalState,long elapsed,long ordinal){
        if(ordinal<startOrdinal)throw new IllegalArgumentException("DX02_CURSOR_REGRESSION");
        state=finalState;endedElapsed=elapsed;endOrdinal=ordinal;
    }
    public synchronized State stop(long elapsed,long ordinal){
        if(expireIfNeeded(elapsed,ordinal)!=State.ACTIVE)return state;
        finish(State.STOPPED,elapsed,ordinal);return state;
    }
    public synchronized void cancel(){
        state=State.CANCELLED;markers.clear();acceptedImportRanges.clear();
        endOrdinal=startOrdinal;endedElapsed=startedElapsed;
    }
    public synchronized long elapsed(long now){
        return Math.max(0,(state==State.ACTIVE?now:endedElapsed)-startedElapsed);
    }
    public synchronized List<Marker> markers(){return Collections.unmodifiableList(new ArrayList<>(markers));}
    public String probeSummary(){
        return "metadata_only=true; registered="+(!researchOnly)+"; hook="+targetHookId+
            "; package="+targetPackage+"; supported_versions="+minVersion+".."+maxVersion+
            "; runtime_methods=NOT_ACCESSED; injection=DISABLED";
    }
    public synchronized Report report(DiagnosticTimeline timeline,long nowElapsed){
        if(timeline==null)throw new IllegalArgumentException("DX02_TIMELINE_REQUIRED");
        DiagnosticTimeline.Selection selection=timeline.select(null);
        long highest=0;for(DiagnosticTimeline.Event e:selection.events)highest=Math.max(highest,e.ordinal);
        expireIfNeeded(nowElapsed,highest);
        if(state==State.ACTIVE||state==State.CANCELLED)
            throw new IllegalStateException("DX02_CAPTURE_NOT_REPORTABLE");
        List<DiagnosticTimeline.Event> selected=new ArrayList<>();
        for(DiagnosticTimeline.Event e:selection.events){
            if(e.ordinal<=startOrdinal||e.ordinal>endOrdinal||!source.equals(e.source))continue;
            for(long[] batch:acceptedImportRanges)
                if(e.ordinal>=batch[0]&&e.ordinal<=batch[1]){selected.add(e);break;}
        }
        selected.sort(Comparator.comparingLong(e->e.ordinal));
        int loss=Math.max(0,selection.dropped-droppedAtStart);
        String eventJsonl=DiagnosticTimeline.toJsonl(selected);
        StringBuilder marks=new StringBuilder();
        for(Marker m:markers){
            marks.append("{\"marker\":").append(DiagnosticTimeline.json(m.code.name()))
                .append(",\"elapsed_ms\":").append(m.elapsedMillis)
                .append(",\"import_ordinal_at_marker\":").append(m.importOrdinal)
                .append("}\n");
        }
        String markerJsonl=marks.toString();
        byte[] ev=eventJsonl.getBytes(StandardCharsets.UTF_8);
        byte[] mk=markerJsonl.getBytes(StandardCharsets.UTF_8);
        String manifest="{\"schema\":\"hooklab-dx02-capture.v1\",\"capture_id\":"+
            DiagnosticTimeline.json(id)+",\"status\":"+DiagnosticTimeline.json(state.name())+
            ",\"started_wall_ms\":"+startedWall+
            ",\"duration_elapsed_ms\":"+elapsed(nowElapsed)+
            ",\"research_only\":"+researchOnly+
            ",\"hook_id\":"+DiagnosticTimeline.json(targetHookId)+
            ",\"package\":"+DiagnosticTimeline.json(targetPackage)+
            ",\"source\":"+DiagnosticTimeline.json(source)+
            ",\"marker_count\":"+markers.size()+
            ",\"imported_events\":"+selected.size()+
            ",\"retention_losses_possible\":"+loss+
            ",\"metadata_only\":true,\"runtime_execution_verified\":false,\"raw_logs_included\":false"+
            ",\"events_sha256\":"+DiagnosticTimeline.json(DiagnosticTimeline.sha256(ev))+
            ",\"markers_sha256\":"+DiagnosticTimeline.json(DiagnosticTimeline.sha256(mk))+"}\n";
        return new Report(manifest,eventJsonl,markerJsonl,selected.size(),loss);
    }
    public static long latestOrdinal(DiagnosticTimeline timeline){
        long max=0;for(DiagnosticTimeline.Event e:timeline.filtered(null))max=Math.max(max,e.ordinal);
        return max;
    }
}
