package com.mikeyphw.hooklab.diagnostics;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Only explicit SAF CreateDocument destinations; no direct filesystem or log access. */
public final class CaptureSaf {
    private CaptureSaf() { }
    public static void export(ContentResolver resolver, Uri uri,CaptureSession.Report report) throws IOException {
        if(uri==null||!"content".equals(uri.getScheme())||report==null)
            throw new IOException("DX02_EXPLICIT_SAF_URI_REQUIRED");
        try(OutputStream out=resolver.openOutputStream(uri,"w")){
            if(out==null)throw new IOException("DX02_EXPORT_STREAM_UNAVAILABLE");
            try(ZipOutputStream zip=new ZipOutputStream(out)){
                add(zip,"events.jsonl",report.eventsJsonl);
                add(zip,"markers.jsonl",report.markersJsonl);
                add(zip,"manifest.json",report.manifest);
            }
        }
    }
    private static void add(ZipOutputStream zip,String name,String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
