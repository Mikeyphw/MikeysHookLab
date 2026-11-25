package com.mikeyphw.hooklab.diagnostics;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Pure redacted export writer. SAF controls the destination; no raw input is accepted by event export. */
public final class DiagnosticExport {
    private DiagnosticExport(){}
    private static void write(OutputStream target,String name,byte[] payload,String manifest)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(target)){
            zip.putNextEntry(new ZipEntry(name));zip.write(payload);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
    public static void writeEvents(OutputStream target,List<DiagnosticTimeline.Event> events,int dropped)throws IOException{
        byte[] payload=DiagnosticTimeline.toJsonl(events).getBytes(StandardCharsets.UTF_8);
        Set<String> sources=new LinkedHashSet<>();for(DiagnosticTimeline.Event e:events)sources.add(e.source);
        StringBuilder list=new StringBuilder("[");boolean first=true;
        for(String source:sources){if(!first)list.append(',');first=false;list.append(DiagnosticTimeline.json(source));}list.append(']');
        String manifest="{\"schema\":\"hooklab-dx01-redacted-export.v1\",\"entries\":"+events.size()
            +",\"source_types\":"+list+",\"dropped\":"+dropped+",\"redacted\":true,\"raw_import_lines_included\":false,\"events_sha256\":"
            +DiagnosticTimeline.json(DiagnosticTimeline.sha256(payload))+"}\n";
        write(target,"events.jsonl",payload,manifest);
    }
    /** Accept only the immutable production health projection, not caller-supplied JSON. */
    public static void writeHealth(OutputStream target,com.mikeyphw.hooklab.ui.HookHealthModel.Report report)throws IOException{
        byte[] payload=com.mikeyphw.hooklab.ui.HookHealthModel.redactedJson(report).getBytes(StandardCharsets.UTF_8);
        String manifest="{\"schema\":\"hooklab-uxr01-health-export.v1\",\"redacted\":true,\"runtime_execution_verified\":false,\"report_sha256\":"+DiagnosticTimeline.json(DiagnosticTimeline.sha256(payload))+"}\n";
        write(target,"health.json",payload,manifest);
    }
}
