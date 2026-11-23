package com.mikeyphw.hooklab.mcc;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;
public final class MccManualOperationTest {
    @Test public void idleDoesNotStartAWorker(){
        MccManualOperation m=MccManualOperation.shared();
        assertFalse(m.snapshot().stage==MccManualOperation.Stage.RUNNING && m.snapshot().generation==0L);
    }
    @Test public void explicitStartAndSingleFlightAcrossRotation() throws Exception{
        MccManualOperation m=MccManualOperation.shared();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        assertTrue(m.start(false,()->{entered.countDown();release.await(4,TimeUnit.SECONDS);return "WRITE_9_VERIFIED";}));
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        assertFalse(m.start(false,()->"BAD_DUPLICATE"));
        assertEquals(MccManualOperation.Stage.RUNNING,m.snapshot().stage);
        AtomicReference<MccManualOperation.Snapshot> restored=new AtomicReference<>();
        MccManualOperation.Listener observer=restored::set;
        m.addListener(observer);assertEquals(MccManualOperation.Stage.RUNNING,restored.get().stage);
        release.countDown();long end=System.currentTimeMillis()+3000;
        while(m.snapshot().stage==MccManualOperation.Stage.RUNNING&&System.currentTimeMillis()<end)Thread.sleep(8);
        assertEquals(MccManualOperation.Stage.COMPLETE,m.snapshot().stage);
        assertEquals("WRITE_9_VERIFIED",m.snapshot().code);
        assertEquals("WRITE_9_VERIFIED",restored.get().code);
        m.removeListener(observer);
    }
    @Test public void nextManualInspectionCanRunAfterCompletion() throws Exception{
        MccManualOperation m=MccManualOperation.shared();
        assertTrue(m.start(true,()->"INSPECT_OK"));
        long end=System.currentTimeMillis()+3000;
        while(m.snapshot().stage==MccManualOperation.Stage.RUNNING&&System.currentTimeMillis()<end)Thread.sleep(8);
        assertTrue(m.snapshot().inspectionOnly);assertEquals("INSPECT_OK",m.snapshot().code);
    }
    @Test public void exceptionsCannotLeakStackOrMessage() throws Exception{
        MccManualOperation m=MccManualOperation.shared();
        assertTrue(m.start(true,()->{throw new IllegalStateException("SECRET_PATH");}));
        long end=System.currentTimeMillis()+3000;
        while(m.snapshot().stage==MccManualOperation.Stage.RUNNING&&System.currentTimeMillis()<end)Thread.sleep(8);
        assertEquals("MCC_MANUAL_OPERATION_FAILED",m.snapshot().code);
    }
    @Test public void unexpectedRawResultIsBoundedAndSanitized() throws Exception{
        MccManualOperation m=MccManualOperation.shared();
        assertTrue(m.start(true,()->"foo\nbar/"+"x".repeat(400)));
        long end=System.currentTimeMillis()+3000;
        while(m.snapshot().stage==MccManualOperation.Stage.RUNNING&&System.currentTimeMillis()<end)Thread.sleep(8);
        assertTrue(m.snapshot().code.length()<=160);assertFalse(m.snapshot().code.contains("/"));
        assertFalse(m.snapshot().code.contains("\n"));
    }
}
