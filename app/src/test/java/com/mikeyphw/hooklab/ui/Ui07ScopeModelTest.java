package com.mikeyphw.hooklab.ui;
import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Ui07ScopeModelTest {
    private static final TweakSpec ONE=new TweakSpec("hook.one","org.example.app",1,5);
    private static final TweakSpec TWO=new TweakSpec("hook.two","org.example.app",1,5);
    private static final TweakSpec THREE=new TweakSpec("hook.three","org.example.other",1,5);
    private static List<TweakSpec> all(){return Arrays.asList(ONE,TWO,THREE);}
    @Test public void dedupesSharedPackageWithoutDuplicatingButtons(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(all(),true,Set.of("org.example.app"),Set.of());
        assertEquals(2,result.targets.size());assertEquals("org.example.app",result.targets.get(0).packageName);
        assertEquals(1,result.reviewedApproved);assertTrue(result.targets.get(0).canRemove());
        assertTrue(result.targets.get(1).canRequest());
    }
    @Test public void disconnectedAlwaysFailsClosedEvenWithCachedApprovals(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(all(),false,Set.of("org.example.app"),Set.of());
        assertEquals(-1,result.reviewedApproved);assertEquals(-1,result.otherApproved);
        for(Ui07ScopeModel.Row row:result.targets){assertEquals(Ui07ScopeModel.State.FRAMEWORK_UNAVAILABLE,row.state);
            assertFalse(row.canRequest());assertFalse(row.canRemove());}
    }
    @Test public void unknownScopeIsNotDeniedScope(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(all(),true,null,Set.of());
        assertEquals(Ui07ScopeModel.State.SCOPE_UNKNOWN,result.targets.get(0).state);
        assertFalse(result.targets.get(0).canRequest());assertFalse(result.targets.get(0).canRemove());
    }
    @Test public void pendingBlocksRequestAndRemove(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(all(),true,Set.of("org.example.app"),Set.of("org.example.app"));
        assertEquals(Ui07ScopeModel.State.PENDING,result.targets.get(0).state);
        assertFalse(result.targets.get(0).canRequest());assertFalse(result.targets.get(0).canRemove());
    }
    @Test public void unrelatedScopeEntriesAreCountedButNotActionable(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(all(),true,
            Set.of("org.example.app","org.unrelated.private"),Set.of());
        assertEquals(1,result.otherApproved);assertEquals(2,result.targets.size());
        for(Ui07ScopeModel.Row row:result.targets)assertFalse(row.packageName.equals("org.unrelated.private"));
    }
    @Test public void emptyReviewedRegistryDoesNotFabricateActions(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(List.of(),true,
            Set.of("org.unrelated.private"),Set.of());
        assertEquals(0,result.targets.size());assertEquals(1,result.otherApproved);
    }
    @Test public void inputCollectionsAreNotMutableThroughModel(){
        Ui07ScopeModel.Listing result=Ui07ScopeModel.project(all(),true,Set.of(),Set.of());
        try{result.targets.clear();fail();}catch(UnsupportedOperationException expected){}
    }
}
