package com.mikeyphw.hooklab.ui;

import androidx.lifecycle.ViewModel;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import com.mikeyphw.hooklab.diagnostics.CaptureSession;

/** Activity-scoped, rotation-retained UI state. Never persist imported text, query,
 * pending SAF payloads or event selection across process death. */
public final class DiagnosticWorkspaceState extends ViewModel {
    final String generation=java.util.UUID.randomUUID().toString();
    DiagnosticTimeline.Filter filter=new DiagnosticTimeline.Filter();
    String groupId="";
    String eventId;
    int pageIndex;
    final int[] scroll={0,0,0};
    ImportRequest pendingImport;
    DiagnosticTimeline.Selection pendingExport;
    CaptureSession.Report pendingCaptureExport;
    HookHealthModel.Report pendingHealthExport;
    static final class ImportRequest {
        final String source;
        final CaptureSession capture;
        ImportRequest(String source,CaptureSession capture){this.source=source;this.capture=capture;}
    }
    boolean pickerPending(){return pendingImport!=null||pendingExport!=null||pendingCaptureExport!=null||pendingHealthExport!=null;}
}
