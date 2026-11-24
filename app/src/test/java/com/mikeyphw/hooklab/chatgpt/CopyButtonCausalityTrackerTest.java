package com.mikeyphw.hooklab.chatgpt;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CopyButtonCausalityTrackerTest {
    private static final class Clock implements CopyButtonCausalityTracker.Clock {
        long now=10;
        public long nowMs(){return now;}
    }
    @Test public void callbackTaggedOnlyDuringVerifiedRender() {
        Clock clock=new Clock();CopyButtonCausalityTracker t=new CopyButtonCausalityTracker(clock);
        Object ignored=new Object(),marked=new Object();
        assertNull(t.constructed(ignored));
        assertNull(t.enterHandler(ignored));
        CopyButtonCausalityTracker.RenderFrame render=t.beginRender();
        CopyButtonCausalityTracker.Mark mark=t.constructed(marked);
        assertNotNull(mark);assertEquals(render.renderId,mark.renderId);
        t.endRender(render);
        CopyButtonCausalityTracker.InvocationFrame invoke=t.enterHandler(marked);
        assertNotNull(invoke);assertEquals(mark.bindingId,invoke.bindingId);
        assertEquals(mark.renderId,invoke.renderId);
        assertSame(invoke,t.currentHandler());t.exitHandler(invoke);
        assertNull(t.currentHandler());assertNull(t.enterHandler(ignored));
    }
    @Test public void identicalLookingObjectsRemainDistinct() {
        CopyButtonCausalityTracker t=new CopyButtonCausalityTracker(()->4L);
        Object a=new Object(),b=new Object();
        CopyButtonCausalityTracker.RenderFrame r=t.beginRender();
        CopyButtonCausalityTracker.Mark m1=t.constructed(a),m2=t.constructed(b);
        t.endRender(r);assertNotNull(m1);assertNotNull(m2);
        assertNotEquals(m1.bindingId,m2.bindingId);
        assertEquals(m1.renderId,m2.renderId);
    }
    @Test public void expiresWithoutRetainingSource() {
        Clock c=new Clock();CopyButtonCausalityTracker t=new CopyButtonCausalityTracker(c);
        Object a=new Object();CopyButtonCausalityTracker.RenderFrame r=t.beginRender();
        t.constructed(a);t.endRender(r);c.now+=120001;
        assertNull(t.enterHandler(a));assertEquals(0,t.bindingCount());
    }
    @Test public void preventsCrossThreadAttribution() throws Exception {
        CopyButtonCausalityTracker t=new CopyButtonCausalityTracker(()->1L);
        Object a=new Object();CopyButtonCausalityTracker.RenderFrame r=t.beginRender();
        t.constructed(a);t.endRender(r);
        CopyButtonCausalityTracker.InvocationFrame f=t.enterHandler(a);
        final boolean[] separate={false};
        Thread child=new Thread(()->separate[0]=(t.currentHandler()==null));
        child.start();child.join();assertTrue(separate[0]);assertSame(f,t.currentHandler());t.exitHandler(f);
    }
    @Test public void nestedScopesRestoreAndFrameMismatchRefused() {
        CopyButtonCausalityTracker t=new CopyButtonCausalityTracker(()->1L);
        CopyButtonCausalityTracker.RenderFrame a=t.beginRender();
        CopyButtonCausalityTracker.RenderFrame b=t.beginRender();
        try {t.endRender(a);fail("mismatch must refuse");}catch(IllegalStateException expected){}
        t.endRender(b);t.endRender(a);
        Object cb=new Object();assertNull(t.constructed(cb));
    }
    @Test public void boundsBindingsAndLogging() {
        CopyButtonCausalityTracker t=new CopyButtonCausalityTracker(()->1L);
        CopyButtonCausalityTracker.RenderFrame r=t.beginRender();
        for(int i=0;i<200;i++)t.constructed(new Object());
        t.endRender(r);assertTrue(t.bindingCount()<=96);
        for(int i=0;i<500;i++) {
            int result=t.logCount(CopyButtonCausalityTracker.Event.P03_NOT_FUNCTION0,10);
            assertEquals(i<4?i+1:0,result);
        }
        assertEquals(500,t.occurrenceCount(CopyButtonCausalityTracker.Event.P03_NOT_FUNCTION0,10));
        assertEquals(0,t.occurrenceCount(CopyButtonCausalityTracker.Event.P03_NOT_FUNCTION0,14));
    }
}
