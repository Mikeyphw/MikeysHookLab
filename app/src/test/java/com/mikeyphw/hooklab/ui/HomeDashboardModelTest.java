package com.mikeyphw.hooklab.ui;

import static org.junit.Assert.*;
import com.mikeyphw.hooklab.core.HookGroupCatalog;
import com.mikeyphw.hooklab.core.TweakSpec;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class HomeDashboardModelTest {
    private static List<TweakSpec> specs(){return Arrays.asList(
        new TweakSpec("neostore.obtainx.manual","com.machiav3lli.fdroid",1,10),
        new TweakSpec("droidify.obtainx.manual","com.machiav3lli.fdroid",1,10),
        new TweakSpec("mcc.diamond.control","br.art.code.meucronogramacapilar",1,10));}
    private static Map<String,FrameworkSnapshot.Flag> flags(){
        Map<String,FrameworkSnapshot.Flag> map=new HashMap<>();
        for(TweakSpec spec:specs())
            map.put(spec.id,new FrameworkSnapshot.Flag(Boolean.TRUE,Boolean.FALSE,5L));
        return map;
    }
    @Test public void countsUniquePackagesNotHookCount(){
        HomeDashboardModel.Summary s=HomeDashboardModel.project(specs(),
            HookGroupCatalog.groups(specs()),
            FrameworkSnapshot.connected("LSPosed","test",102,
                new HashSet<>(Arrays.asList("com.machiav3lli.fdroid","br.art.code.meucronogramacapilar")),
                flags(),null),new DiagnosticTimeline());
        assertEquals(3,s.hookCount);assertEquals(2,s.groupCount);
        assertEquals(2,s.packageCount);assertEquals(2,s.scopedPackageCount);
        assertEquals(3,s.eligibleConfigurations);assertNull(s.latestImported);
    }
    @Test public void disconnectedIsUnknownNotDisabledOrRunning(){
        HomeDashboardModel.Summary s=HomeDashboardModel.project(specs(),HookGroupCatalog.groups(specs()),
            FrameworkSnapshot.disconnected(),new DiagnosticTimeline());
        assertEquals(-1,s.scopedPackageCount);assertEquals(3,s.unknown);
        assertEquals(0,s.problems);assertEquals(0,s.eligibleConfigurations);
        assertNull(s.attentionHookId);
    }
    @Test public void missingScopeIsUnknown(){
        HomeDashboardModel.Summary s=HomeDashboardModel.project(specs(),HookGroupCatalog.groups(specs()),
            FrameworkSnapshot.connected("LSPosed","test",102,null,flags(),null),new DiagnosticTimeline());
        assertEquals(-1,s.scopedPackageCount);assertEquals(3,s.unknown);
    }
    @Test public void outOfScopeIsActionableButNotInjected(){
        HomeDashboardModel.Summary s=HomeDashboardModel.project(specs(),HookGroupCatalog.groups(specs()),
            FrameworkSnapshot.connected("LSPosed","test",102,Collections.emptySet(),flags(),null),
            new DiagnosticTimeline());
        assertEquals(3,s.problems);assertEquals(0,s.eligibleConfigurations);
        assertEquals("neostore.obtainx.manual",s.attentionHookId);
    }
    @Test public void voluntaryOptOutIsNotAProblem(){
        Map<String,FrameworkSnapshot.Flag> values=flags();
        values.put("mcc.diamond.control",new FrameworkSnapshot.Flag(false,false,5L));
        HomeDashboardModel.Summary s=HomeDashboardModel.project(specs(),HookGroupCatalog.groups(specs()),
            FrameworkSnapshot.connected("LSPosed","test",102,
                new HashSet<>(Arrays.asList("com.machiav3lli.fdroid","br.art.code.meucronogramacapilar")),
                values,null),new DiagnosticTimeline());
        assertEquals(1,s.disabled);assertEquals(0,s.problems);
        assertEquals(2,s.eligibleConfigurations);
    }
    @Test public void latestImportPreservesProvenanceNotRunProof() throws Exception {
        DiagnosticTimeline d=new DiagnosticTimeline();
        d.importStream(new ByteArrayInputStream("2026-11-23T00:00:00Z HookLabVerbose event=NEO_TAP".getBytes(StandardCharsets.UTF_8)),"LSPOSED_MODULE");
        d.importStream(new ByteArrayInputStream("unclassified private line".getBytes(StandardCharsets.UTF_8)),"LSPOSED_FRAMEWORK");
        HomeDashboardModel.Summary s=HomeDashboardModel.project(specs(),HookGroupCatalog.groups(specs()),
            FrameworkSnapshot.disconnected(),d);
        assertEquals(2,s.importedCount);
        assertEquals("LSPOSED_FRAMEWORK",s.latestImported.source);
        assertEquals("UNCLASSIFIED",s.latestImported.correlation);
        assertEquals(0,s.latestImported.utcMillis);
    }
    @Test public void unknownAndBadGroupMembershipAreRejected(){
        try {HomeDashboardModel.project(specs(),Collections.emptyList(),FrameworkSnapshot.disconnected(),
                new DiagnosticTimeline());fail("missing groups allowed");}
        catch(IllegalArgumentException expected){ }
        try {HomeDashboardModel.project(Arrays.asList(specs().get(0),specs().get(0)),
                HookGroupCatalog.groups(specs()),FrameworkSnapshot.disconnected(),
                new DiagnosticTimeline());fail("duplicate ID allowed");}
        catch(IllegalArgumentException expected){ }
    }
    @Test public void emptyRegistryAndNoImportAreExplicit(){
        HomeDashboardModel.Summary s=HomeDashboardModel.project(Collections.emptyList(),
            Collections.emptyList(),FrameworkSnapshot.loading(),new DiagnosticTimeline());
        assertEquals(0,s.groupCount);assertEquals(0,s.hookCount);
        assertEquals(-1,s.scopedPackageCount);assertNull(s.latestImported);
    }
}
