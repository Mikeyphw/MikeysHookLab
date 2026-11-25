package com.mikeyphw.hooklab.diagnostics;

import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.ui.FrameworkSnapshot;
import com.mikeyphw.hooklab.ui.HookHealthModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DiagnosticExportTest {
    private static DiagnosticTimeline store() throws IOException {
        DiagnosticTimeline t=new DiagnosticTimeline();
        t.importStream(new ByteArrayInputStream("HookLabVerbose event=NEO_TAP\npassword=HIDDEN_PRIVATE\n".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");return t;
    }
    private static Map<String,byte[]> unzip(ByteArrayOutputStream bytes)throws IOException{
        Map<String,byte[]> result=new HashMap<>();
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            ZipEntry entry;while((entry=in.getNextEntry())!=null)assertNull(result.put(entry.getName(),in.readAllBytes()));
        }return result;
    }
    @Test public void selectedZipUsesExactSnapshotAndProvenance()throws Exception{
        DiagnosticTimeline t=store();DiagnosticTimeline.Selection selected=t.selectEvent(t.filtered(null).get(0).id());
        t.importStream(new ByteArrayInputStream("later".getBytes(StandardCharsets.UTF_8)),"LSPOSED_FRAMEWORK");
        ByteArrayOutputStream out=new ByteArrayOutputStream();DiagnosticExport.writeEvents(out,selected.events,selected.dropped);
        Map<String,byte[]> files=unzip(out);assertEquals(Set.of("events.jsonl","manifest.json"),files.keySet());
        assertEquals(selected.jsonl(),new String(files.get("events.jsonl"),StandardCharsets.UTF_8));
        String manifest=new String(files.get("manifest.json"),StandardCharsets.UTF_8);
        assertTrue(manifest.contains(DiagnosticTimeline.sha256(files.get("events.jsonl"))));assertTrue(manifest.contains("\"entries\":1"));
        assertFalse(new String(files.get("events.jsonl"),StandardCharsets.UTF_8).contains("HIDDEN_PRIVATE"));
    }
    @Test public void typedHealthZipIsImmutableAndHashBound()throws Exception{
        DiagnosticTimeline t=store();TweakSpec hook=new TweakSpec("neostore.obtainx.manual","com.machiav3lli.fdroid",1,100);
        HookHealthModel.Report report=HookHealthModel.project(hook,FrameworkSnapshot.disconnected(),t);
        t.importStream(new ByteArrayInputStream("HookLabVerbose event=NEO_TAP".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        ByteArrayOutputStream out=new ByteArrayOutputStream();DiagnosticExport.writeHealth(out,report);Map<String,byte[]> files=unzip(out);
        assertEquals(Set.of("health.json","manifest.json"),files.keySet());
        assertEquals(HookHealthModel.redactedJson(report),new String(files.get("health.json"),StandardCharsets.UTF_8));
        assertTrue(new String(files.get("manifest.json"),StandardCharsets.UTF_8).contains(DiagnosticTimeline.sha256(files.get("health.json"))));
        assertEquals(1,report.matchingImports);assertFalse(report.executionVerified());
    }
    @Test public void deniedWriteCannotReportSuccessfulExport()throws Exception{
        OutputStream denied=new OutputStream(){@Override public void write(int value)throws IOException{throw new IOException("denied");}};
        try{DiagnosticExport.writeEvents(denied,store().filtered(null),0);fail();}catch(IOException expected){assertEquals("denied",expected.getMessage());}
    }
}
