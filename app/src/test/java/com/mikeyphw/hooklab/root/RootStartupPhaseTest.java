package com.mikeyphw.hooklab.root;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
public final class RootStartupPhaseTest {
    @Test public void completedPhaseReportsBeginThenOk()throws Exception {
        List<String> events=new ArrayList<>();
        int result=RootStartupPhase.run("AUTH","TIMEOUT","FAIL",50,Runnable::run,()->7,(p,s,t)->events.add(p+"_"+s),false,true);
        assertEquals(7,result);assertEquals(java.util.Arrays.asList("AUTH_BEGIN","AUTH_OK"),events);
    }
    @Test public void timeoutIsUncertainAndDoesNotRetry()throws Exception {
        AtomicInteger calls=new AtomicInteger();CountDownLatch blocked=new CountDownLatch(1);
        try{RootStartupPhase.run("BIND","BIND_TIMEOUT","FAIL",20,c->{Thread t=new Thread(c);t.setDaemon(true);t.start();},
            ()->{calls.incrementAndGet();blocked.await();return null;},(p,s,t)->{},true,true);fail();}
        catch(RootStartupPhase.Failure e){assertEquals("BIND_TIMEOUT",e.code);assertTrue(e.uncertain);assertTrue(calls.get()<=1);}
        finally{blocked.countDown();}
    }
    @Test public void failureAndRejectedExecutorKeepDistinctCode()throws Exception {
        try{RootStartupPhase.run("AUTH","TIMEOUT","AUTH_FAIL",20,Runnable::run,()->{throw new IllegalStateException();},(p,s,t)->{},false,true);fail();}
        catch(RootStartupPhase.Failure e){assertEquals("AUTH_FAIL",e.code);assertFalse(e.uncertain);}
        try{RootStartupPhase.run("BIND","TIMEOUT","BIND_FAIL",20,c->{throw new java.util.concurrent.RejectedExecutionException();},()->null,(p,s,t)->{},true,true);fail();}
        catch(RootStartupPhase.Failure e){assertEquals("BIND_FAIL",e.code);assertTrue(e.uncertain);}
    }
}
