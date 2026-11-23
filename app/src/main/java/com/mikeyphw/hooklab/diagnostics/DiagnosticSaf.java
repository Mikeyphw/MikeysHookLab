package com.mikeyphw.hooklab.diagnostics;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Explicit Android SAF only. No root, READ_LOGS, logcat process or hidden storage. */
public final class DiagnosticSaf {
    private DiagnosticSaf() { }
    public static DiagnosticTimeline.ImportReceipt importDocument(
            ContentResolver resolver,Uri uri,DiagnosticTimeline store,String source) throws IOException {
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("DX01_SAF_URI_REQUIRED");
        try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IOException("DX01_IMPORT_STREAM_UNAVAILABLE");
            return store.importStream(in,source);
        }
    }
    public static void exportRedactedZip(ContentResolver resolver,Uri uri,
            List<DiagnosticTimeline.Event> events,int dropped) throws IOException {
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("DX01_EXPORT_SAF_URI_REQUIRED");
        String jsonl=DiagnosticTimeline.toJsonl(events);
        byte[] payload=jsonl.getBytes(StandardCharsets.UTF_8);
        String digest=DiagnosticTimeline.sha256(payload);
        Set<String> sources=new LinkedHashSet<>();
        for(DiagnosticTimeline.Event e:events)sources.add(e.source);
        StringBuilder list=new StringBuilder("[");
        boolean first=true;for(String s:sources){if(!first)list.append(',');first=false;list.append(DiagnosticTimeline.json(s));}
        list.append(']');
        String manifest="{\"schema\":\"hooklab-dx01-redacted-export.v1\",\"entries\":"+events.size()+
            ",\"source_types\":"+list+",\"dropped\":"+dropped+
            ",\"redacted\":true,\"raw_import_lines_included\":false,\"events_sha256\":"+
            DiagnosticTimeline.json(digest)+"}\n";
        try(OutputStream target=resolver.openOutputStream(uri,"w")){
            if(target==null)throw new IOException("DX01_EXPORT_STREAM_UNAVAILABLE");
            try(ZipOutputStream zip=new ZipOutputStream(target)){
                zip.putNextEntry(new ZipEntry("events.jsonl"));zip.write(payload);zip.closeEntry();
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
        }
    }
}
