package com.mikeyphw.hooklab.ui;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class PendingFrameworkCommandsTest {
    @Test public void pendingSurvivesOwnerReplacementAndRejectsDuplicate() {
        PendingFrameworkCommands ledger=new PendingFrameworkCommands();
        var original=ledger.begin("scope:target");
        assertTrue(original!=null);
        assertNull(ledger.begin("scope:target"));
        assertEquals(Set.of("target"),ledger.keys("scope:"));
        assertTrue(ledger.complete(original));
        assertFalse(ledger.contains("scope:target"));
    }
    @Test public void lateCompletionCannotReleaseSuccessor() {
        PendingFrameworkCommands ledger=new PendingFrameworkCommands();
        var old=ledger.begin("pref:hook");
        assertTrue(ledger.complete(old));
        var next=ledger.begin("pref:hook");
        assertFalse(ledger.complete(old));
        assertTrue(ledger.contains("pref:hook"));
        assertTrue(ledger.complete(next));
    }
    @Test public void independentCommandsAndImmutableSnapshot() {
        PendingFrameworkCommands ledger=new PendingFrameworkCommands();
        var scope=ledger.begin("scope:target");
        var flag=ledger.begin("pref:target");
        var prior=ledger.keys("scope:");
        assertTrue(ledger.complete(flag));
        assertTrue(ledger.contains("scope:target"));
        try {prior.clear();fail("mutable pending projection");}catch(UnsupportedOperationException expected){}
        assertTrue(ledger.complete(scope));
        assertEquals(Set.of("target"),prior);
        assertTrue(ledger.keys("scope:").isEmpty());
        assertNull(ledger.begin(""));assertNull(ledger.begin(null));assertFalse(ledger.complete(null));
    }
    @Test public void concurrentBeginHasExactlyOneWinner() throws Exception {
        PendingFrameworkCommands ledger=new PendingFrameworkCommands();
        CountDownLatch start=new CountDownLatch(1),done=new CountDownLatch(8);
        AtomicInteger winners=new AtomicInteger();
        for(int i=0;i<8;i++)new Thread(()->{try{start.await();if(ledger.begin("scope:p")!=null)winners.incrementAndGet();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{done.countDown();}}).start();
        start.countDown();assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,winners.get());
    }
}
