package com.mikeyphw.hooklab.diagnostics;

import static org.junit.Assert.*;
import com.mikeyphw.hooklab.core.TweakSpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class CaptureSessionTest {
    private static final TweakSpec HOOK=new TweakSpec("neostore.obtainx.manual","com.machiav3lli.fdroid",1,1304);
    private static DiagnosticTimeline timeline(String... lines) throws Exception {
        DiagnosticTimeline d=new DiagnosticTimeline();
        for(String s:lines)d.importStream(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        return d;
    }
    @Test public void boundedTimeoutClosesWithoutBackgroundWork() throws Exception {
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        assertEquals(CaptureSession.State.ACTIVE,s.expireIfNeeded(101,0));
        assertEquals(CaptureSession.State.EXPIRED,s.expireIfNeeded(100+CaptureSession.MAX_DURATION_MS,0));
        assertEquals(CaptureSession.State.EXPIRED,s.stop(100+CaptureSession.MAX_DURATION_MS+1,0));
        assertEquals(CaptureSession.State.EXPIRED,s.state());
    }
    @Test public void researchIsStrictAndCannotProduceHookMetadata() {
        try {CaptureSession.forResearch("../../token","LSPOSED_MODULE",1,1700000000000L,0,0);fail();}
        catch(IllegalArgumentException expected) { }
        CaptureSession r=CaptureSession.forResearch("com.example.research","LSPOSED_FRAMEWORK",1,1700000000000L,0,0);
        assertTrue(r.researchOnly);assertEquals("",r.targetHookId);
        assertTrue(r.probeSummary().contains("injection=DISABLED"));
    }
    @Test public void unreviewedCollectorRejected() {
        try {CaptureSession.forHook(HOOK,"ANDROID_LOGCAT_LIVE",1,1700000000000L,0,0);fail();}
        catch(IllegalArgumentException expected) { }
    }
    @Test public void ordinalOnlyEventsWithinSessionAndSourceAreIncluded() throws Exception {
        DiagnosticTimeline d=timeline("old HookLabVerbose event=NEO_TAP");
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,
            CaptureSession.latestOrdinal(d),d.dropped());
        d.importStream(new ByteArrayInputStream("now HookLabVerbose event=NEO_TAP".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        d.importStream(new ByteArrayInputStream("wrong source".getBytes(StandardCharsets.UTF_8)),"LSPOSED_FRAMEWORK");
        assertTrue(s.registerImport("LSPOSED_MODULE",1,2,250));
        s.mark(CaptureSession.MarkerCode.REPRODUCE_ACTION,300,CaptureSession.latestOrdinal(d));
        s.stop(400,CaptureSession.latestOrdinal(d));
        d.importStream(new ByteArrayInputStream("later HookLabVerbose event=NEO_TAP".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        CaptureSession.Report report=s.report(d,500);
        assertEquals(1,report.observed);
        assertEquals(2,s.markersCount());
        assertTrue(report.eventsJsonl.contains("\"event_code\":\"NEO_TAP\""));
        assertTrue(report.manifest.contains("\"runtime_execution_verified\":false"));
        assertTrue(report.markersJsonl.contains("REPRODUCE_ACTION"));
    }
    @Test public void cancellationClearsMarkersAndBlocksReport() throws Exception {
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        s.mark(CaptureSession.MarkerCode.APP_RESTARTED,101,0);s.cancel();
        assertEquals(0,s.markersCount());
        try {s.report(new DiagnosticTimeline(),103);fail();}
        catch(IllegalStateException expected) { }
    }
    @Test public void markerLimitAndNonmonotonicClockFailClosed() throws Exception {
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        for(int n=0;n<CaptureSession.MAX_MARKERS;n++)
            s.mark(CaptureSession.MarkerCode.PERMISSION_PROMPT,101+n,0);
        try {s.mark(CaptureSession.MarkerCode.PERMISSION_PROMPT,200,0);fail();}
        catch(IllegalStateException expected) { }
        assertEquals(CaptureSession.State.INTERRUPTED,s.expireIfNeeded(99,0));
    }
    @Test public void reportNeverRetainsRawSecrets() throws Exception {
        DiagnosticTimeline d=new DiagnosticTimeline();
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        d.importStream(new ByteArrayInputStream("Authorization Bearer SECRET_123 path /data/private".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        assertTrue(s.registerImport("LSPOSED_MODULE",0,CaptureSession.latestOrdinal(d),101));
        s.stop(102,CaptureSession.latestOrdinal(d));
        CaptureSession.Report report=s.report(d,103);
        assertFalse(report.eventsJsonl.contains("SECRET_123"));
        assertFalse(report.manifest.contains("SECRET_123"));
        assertTrue(report.eventsJsonl.contains("contents withheld"));
    }
    @Test public void unrelatedImportsAndExpiredImportsNeverEnterReport() throws Exception {
        DiagnosticTimeline d=new DiagnosticTimeline();
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        d.importStream(new ByteArrayInputStream("other source".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        // This import was not initiated inside the Capture UI; exclude it.
        d.importStream(new ByteArrayInputStream("authorized import".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        assertTrue(s.registerImport("LSPOSED_MODULE",1,2,200));
        d.importStream(new ByteArrayInputStream("too late".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        assertFalse(s.registerImport("LSPOSED_MODULE",2,3,100+CaptureSession.MAX_DURATION_MS));
        assertEquals(1,s.report(d,100+CaptureSession.MAX_DURATION_MS+1).observed);
    }
    @Test public void stoppedSessionCannotAcceptMarkersOrReopen() throws Exception {
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        s.stop(200,0);
        try {s.mark(CaptureSession.MarkerCode.REPRODUCE_ACTION,201,0);fail();}
        catch(IllegalStateException expected) { }
        assertEquals(CaptureSession.State.STOPPED,s.stop(201,0));
        assertEquals(100,s.elapsed(900));
    }
    @Test public void unknownTimestampDoesNotBecomeInventedTimestamp() throws Exception {
        DiagnosticTimeline d=timeline("not a timestamp HookLabVerbose event=NEO_TAP");
        CaptureSession s=CaptureSession.forHook(HOOK,"LSPOSED_MODULE",100,1700000000000L,0,0);
        assertTrue(s.registerImport("LSPOSED_MODULE",0,CaptureSession.latestOrdinal(d),100));
        s.stop(101,CaptureSession.latestOrdinal(d));
        assertEquals(0,d.filtered(null).get(0).utcMillis);
        assertTrue(s.report(d,102).eventsJsonl.contains("\"utc_millis\":0"));
    }
}
