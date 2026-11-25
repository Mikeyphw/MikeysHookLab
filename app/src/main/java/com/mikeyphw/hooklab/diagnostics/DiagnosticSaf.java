package com.mikeyphw.hooklab.diagnostics;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

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
        try(OutputStream target=resolver.openOutputStream(uri,"w")){
            if(target==null)throw new IOException("DX01_EXPORT_STREAM_UNAVAILABLE");
            DiagnosticExport.writeEvents(target,events,dropped);
        }
    }
    public static void exportHealthZip(ContentResolver resolver,Uri uri,com.mikeyphw.hooklab.ui.HookHealthModel.Report report) throws IOException {
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("DX03_EXPORT_SAF_URI_REQUIRED");
        try(OutputStream target=resolver.openOutputStream(uri,"w")){
            if(target==null)throw new IOException("DX03_EXPORT_STREAM_UNAVAILABLE");
            DiagnosticExport.writeHealth(target,report);
        }
    }
}
