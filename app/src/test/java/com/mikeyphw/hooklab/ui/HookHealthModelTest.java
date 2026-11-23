package com.mikeyphw.hooklab.ui;

import static org.junit.Assert.*;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import org.junit.Test;

public final class HookHealthModelTest {
    private static final TweakSpec NEO=new TweakSpec("neostore.obtainx.manual","com.machiav3lli.fdroid",1,1304);
    private static final TweakSpec OTHER=new TweakSpec("other.manual","com.machiav3lli.fdroid",1,1304);
    private static FrameworkSnapshot connected(Boolean enable,Boolean lock,Long version,boolean scope){
        Map<String,FrameworkSnapshot.Flag> flags=new HashMap<>();
        flags.put(NEO.id,new FrameworkSnapshot.Flag(enable,lock,version));
        flags.put(OTHER.id,new FrameworkSnapshot.Flag(enable,lock,version));
        return FrameworkSnapshot.connected("LSPosed","1",102,
            scope?Collections.singleton(NEO.packageName):Collections.emptySet(),flags,false);
    }
    private static DiagnosticTimeline imported(String...lines) throws Exception {
        DiagnosticTimeline timeline=new DiagnosticTimeline();
        for(String line:lines)timeline.importStream(new ByteArrayInputStream(
                line.getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        return timeline;
    }
    @Test public void eligibleConfigurationDoesNotAssertRuntimeExecution() throws Exception {
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,false,100L,true),new DiagnosticTimeline());
        assertTrue(r.configurationEligible());assertFalse(r.executionVerified());
        assertEquals("NO_IMPORTED_SOURCE",r.collectorGap);
        assertEquals(HookHealthModel.Advice.IMPORT_EVIDENCE,r.advice);
        assertTrue(HookHealthModel.redactedJson(r).contains("\"runtime_execution_verified\":false"));
    }
    @Test public void disconnectedFrameworkCannotBecomeActiveFromLogs() throws Exception {
        HookHealthModel.Report r=HookHealthModel.project(NEO,FrameworkSnapshot.disconnected(),
            imported("2026-11-23T12:00:00Z HookLabVerbose event=NEO_TAP"));
        assertEquals(HookHealthModel.Axis.PROBLEM,r.framework);
        assertEquals(HookHealthModel.Axis.UNKNOWN,r.optIn);
        assertEquals(HookHealthModel.Advice.CONNECT_FRAMEWORK,r.advice);
        assertFalse(r.executionVerified());
    }
    @Test public void lockedAndUnsupportedStatesRemainIndependent() {
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,true,99999L,true),new DiagnosticTimeline());
        assertEquals(HookHealthModel.Axis.PROBLEM,r.quarantine);
        assertEquals(HookHealthModel.Axis.PROBLEM,r.version);
        assertEquals(HookHealthModel.Advice.INSPECT_SAFETY_LOCK,r.advice);
    }
    @Test public void voluntaryOptOutIsNotAHealthFailure() {
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(false,false,100L,true),new DiagnosticTimeline());
        assertEquals(HookHealthModel.Axis.OPTED_OUT,r.optIn);
        assertEquals(HookHealthModel.Advice.ENABLE_OPT_IN,r.advice);
        assertFalse(r.configurationEligible());
    }
    @Test public void unknownVersionNeverAssumesCompatibility() {
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,false,null,true),new DiagnosticTimeline());
        assertEquals(HookHealthModel.Axis.UNKNOWN,r.version);
        assertEquals(HookHealthModel.Advice.VERIFY_TARGET_VERSION,r.advice);
    }
    @Test public void missingScopeNeedsUserReviewNotImplicitGrant() {
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,false,100L,false),new DiagnosticTimeline());
        assertEquals(HookHealthModel.Axis.PROBLEM,r.scope);
        assertEquals(HookHealthModel.Advice.REVIEW_SCOPE,r.advice);
    }
    @Test public void samePackageNeverBorrowEventsFromAnotherHook() throws Exception {
        DiagnosticTimeline t=imported("2026-11-23T12:00:00Z HookLabVerbose event=NEO_TAP");
        HookHealthModel.Report r=HookHealthModel.project(OTHER,connected(true,false,100L,true),t);
        assertEquals(0,r.matchingImports);
        assertEquals("NO_EXACT_HOOK_ASSOCIATION",r.collectorGap);
    }
    @Test public void exactHookEvidenceIsSourcedButNeverAuthenticated() throws Exception {
        DiagnosticTimeline t=imported("2026-11-23T12:00:00Z HookLabVerbose event=NEO_TAP");
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,false,100L,true),t);
        assertEquals(1,r.matchingImports);
        assertEquals("LSPOSED_MODULE",r.sourceSummary);
        assertEquals(1,r.recent.get(0).lineNumber);
        assertTrue(HookHealthModel.redactedJson(r).contains("import_file_sha256"));
        assertFalse(r.executionVerified());
    }
    @Test public void unaffiliatedErrorsNotAttributedToSpecificHook() throws Exception {
        DiagnosticTimeline t=imported("EXCEPTION in unknown package with Bearer SECRET_123");
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,false,100L,true),t);
        assertEquals(0,r.reportedErrors);assertEquals(1,r.unrelatedErrors);
        assertEquals(0,r.matchingImports);
        assertFalse(HookHealthModel.redactedJson(r).contains("SECRET_123"));
    }
    @Test public void retentionLossIsNotSilentlyDropped() throws Exception {
        DiagnosticTimeline t=new DiagnosticTimeline();
        StringBuilder s=new StringBuilder();
        for(int n=0;n<DiagnosticTimeline.MAX_EVENTS+1;n++)s.append("log line\n");
        t.importStream(new ByteArrayInputStream(s.toString().getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        HookHealthModel.Report r=HookHealthModel.project(NEO,connected(true,false,100L,true),t);
        assertTrue(r.retainedDropped>0);
        assertEquals("NO_EXACT_HOOK_ASSOCIATION",r.collectorGap);
    }
    @Test public void loadingFrameworkAndAbsentFlagsRemainUnknown() {
        HookHealthModel.Report r=HookHealthModel.project(NEO,FrameworkSnapshot.loading(),new DiagnosticTimeline());
        assertEquals(HookHealthModel.Axis.UNKNOWN,r.framework);
        assertEquals(HookHealthModel.Axis.UNKNOWN,r.scope);
        assertEquals(HookHealthModel.Axis.UNKNOWN,r.quarantine);
    }
}
