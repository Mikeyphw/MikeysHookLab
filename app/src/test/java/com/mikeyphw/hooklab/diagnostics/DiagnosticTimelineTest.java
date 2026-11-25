package com.mikeyphw.hooklab.diagnostics;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public final class DiagnosticTimelineTest {
    private static ByteArrayInputStream stream(String text){return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));}
    @Test public void knownHookLabCodeIsAssociatedNotAuthenticated(){
        DiagnosticTimeline t=new DiagnosticTimeline();
        try{
            t.importStream(stream("2026-11-23T19:00:00Z HookLabVerbose event=NEO_TAP"),"LSPOSED_MODULE");
        }catch(IOException failure){throw new AssertionError(failure);}
        DiagnosticTimeline.Event e=t.filtered(null).get(0);
        assertEquals("NEO_TAP",e.code);assertEquals("ASSOCIATED",e.correlation);
        assertEquals("neostore.obtainx.manual",e.hookId);
        assertEquals("LSPOSED_MODULE",e.source);
        assertTrue(e.utcMillis>0);
    }
    @Test public void arbitraryPrivateLinesNeverEnterOutput() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(stream("Bearer s3cr3t email a@b.com https://example.com/private\npassword=qwerty"),"LSPOSED_FRAMEWORK");
        String json=DiagnosticTimeline.toJsonl(t.filtered(null));
        assertFalse(json.contains("s3cr3t"));assertFalse(json.contains("qwerty"));
        assertFalse(json.contains("a@b.com"));assertFalse(json.contains("example.com"));
        assertEquals(2,t.size());
    }
    @Test public void crossFileSameTextIsNotDeduped() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(stream("scope warning"),"LSPOSED_FRAMEWORK");
        t.importStream(stream("scope warning"),"LSPOSED_MODULE");
        assertEquals(2,t.size());
        assertFalse(t.filtered(null).get(0).id().equals(t.filtered(null).get(1).id()));
    }
    @Test public void unsafeCollectorIsRejected() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        try {t.importStream(stream("any"),"ROOT_LOGCAT");throw new AssertionError("accepted unknown source");}
        catch(IllegalArgumentException expected){assertEquals(0,t.size());}
    }
    @Test public void oversizedImportIsAtomic() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        byte[] huge=new byte[DiagnosticTimeline.MAX_BYTES+1];
        try {t.importStream(new ByteArrayInputStream(huge),"LSPOSED_MODULE");throw new AssertionError("oversized");}
        catch(IOException expected){assertEquals(0,t.size());}
    }
    @Test public void filtersDoNotMutateOriginalStore() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(stream("HookLabVerbose event=MCC_BUTTON_ATTACHED\nother\nHookLabVerbose event=NEO_TAP"),"LSPOSED_MODULE");
        DiagnosticTimeline.Filter f=new DiagnosticTimeline.Filter();f.hookId="mcc.diamond.control";
        List<DiagnosticTimeline.Event> result=t.filtered(f);
        assertEquals(1,result.size());assertEquals("MCC_BUTTON_ATTACHED",result.get(0).code);
        assertEquals(3,t.size());
    }
    @Test public void unknownClockDoesNotInventTimestamp() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(stream("11-23 18:00:00.123 unknown message"),"ANDROID_LOGCAT_IMPORT");
        assertTrue(t.filtered(null).get(0).utcMillis==0L);
        assertEquals("",t.filtered(null).get(0).timestamp);
    }
    @Test public void outputDoesNotSerializeOriginalRawText() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(stream("2026-11-23T19:00:00Z HookLabVerbose event=DROID_TAP cookie=S3CR3T"),"LSPOSED_MODULE");
        String out=DiagnosticTimeline.toJsonl(t.filtered(null));
        assertTrue(out.contains("DROID_TAP"));assertFalse(out.contains("S3CR3T"));
        assertTrue(out.contains("redacted"));
    }
    @Test public void draftFilterAndGroupedTimeBoundsAreIndependent() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(stream("2026-11-23T19:00:00Z WARN HookLabVerbose event=NEO_TAP\n"
            +"2026-11-23T18:00:00Z HookLabVerbose event=DROID_TAP\n"
            +"2026-11-23T20:00:00Z WARN HookLabVerbose event=MCC_BUTTON_ATTACHED\n"
            +"WARN HookLabVerbose event=NEO_TAP"),"LSPOSED_MODULE");
        DiagnosticTimeline.Filter applied=new DiagnosticTimeline.Filter();applied.source="LSPOSED_MODULE";
        DiagnosticTimeline.Filter draft=applied.copy();draft.groupHooks=java.util.Set.of("neostore.obtainx.manual","droidify.obtainx.manual");
        draft.afterUtc=java.time.Instant.parse("2026-11-23T18:30:00Z").toEpochMilli();draft.severity="WARN";
        assertEquals(4,t.filtered(applied).size());assertEquals(1,t.filtered(draft).size());
        assertEquals("NEO_TAP",t.filtered(draft).get(0).code);assertEquals(4,draft.activeCount());
        draft.hookId="mcc.diamond.control";assertTrue(t.filtered(draft).isEmpty());
        assertEquals(1,applied.activeCount());
    }
    @Test public void exactEventIdentityAndSnapshotSurviveEvictionWithoutRebinding() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();t.importStream(stream("same\nsame"),"LSPOSED_MODULE");
        List<DiagnosticTimeline.Event> initial=t.filtered(null);
        assertNotEquals(initial.get(0).id(),initial.get(1).id());
        DiagnosticTimeline.Selection selected=t.selectEvent(initial.get(1).id());assertEquals(initial.get(1).id(),selected.events.get(0).id());
        t.importStream(stream("x\n".repeat(DiagnosticTimeline.MAX_EVENTS)),"LSPOSED_FRAMEWORK");
        assertEquals(DiagnosticTimeline.MAX_EVENTS,t.size());assertEquals(2,t.dropped());
        assertNull(t.find(initial.get(1).id()));assertNull(t.selectEvent(initial.get(1).id()));
        assertEquals(1,selected.events.size());assertEquals(0,selected.dropped);
        assertTrue(selected.jsonl().contains(initial.get(1).id()));
    }
    @Test public void malformedUtf8DoesNotPartiallyImport() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();t.importStream(stream("first"),"LSPOSED_MODULE");
        String before=t.select(null).jsonl();
        try{t.importStream(new ByteArrayInputStream(new byte[]{65,10,(byte)0xc3,40}),"LSPOSED_MODULE");fail();}
        catch(IOException expected){assertEquals(before,t.select(null).jsonl());assertEquals(1,t.size());}
    }
    @Test public void chronologySortsKnownTimesThenStableUnknowns() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();t.importStream(stream("2026-11-23T21:00:00Z later\nunknown\n2026-11-23T18:00:00Z earlier\nother"),"LSPOSED_MODULE");
        List<DiagnosticTimeline.Event> rows=t.filtered(null);assertEquals(3,rows.get(0).lineNumber);assertEquals(1,rows.get(1).lineNumber);
        assertEquals(2,rows.get(2).lineNumber);assertEquals(4,rows.get(3).lineNumber);
    }
}
