package com.mikeyphw.hooklab.chatgpt;
import org.junit.Test;
import static org.junit.Assert.*;
public final class AsyncCopyTraceTest {
    private static final class Clock implements AsyncCopyTrace.Clock {long t;public long now(){return t;}}
    @Test public void identitySurvivesAsyncContinuationAndRejectsEqualTextWithoutIdentity()throws Exception{
        Clock clock=new Clock();AsyncCopyTrace t=new AsyncCopyTrace(clock);Object code=new Object(),cb=new Object(),ix=new Object(),cont=new Object(),wrap=new Object(),clip=new Object();
        AsyncCopyTrace.Block block=t.block("a\r\nβ\n");t.annotate(block,code,"a\r\nβ\n");AsyncCopyTrace.Render render=t.render(code,"a\r\nβ\n");assertEquals(block.id,render.blockId);t.callback(render,cb,code);AsyncCopyTrace.Action a=t.begin(cb);assertNotNull(a);assertTrue(a.ownsAnnotated(code));t.tag(AsyncCopyTrace.Kind.IXL,ix,a);
        Thread child=new Thread(()->{AsyncCopyTrace.Action v=t.get(AsyncCopyTrace.Kind.IXL,ix);t.tag(AsyncCopyTrace.Kind.IXL,cont,v);t.tag(AsyncCopyTrace.Kind.WRAPPER,wrap,v);t.tag(AsyncCopyTrace.Kind.CLIP,clip,v);});child.start();child.join();assertSame(a,t.get(AsyncCopyTrace.Kind.IXL,cont));assertSame(a,t.get(AsyncCopyTrace.Kind.WRAPPER,wrap));assertEquals("UNBOUND",t.clipboard(a,new Object(),"a\r\nβ\n"));assertEquals("MATCH",t.clipboard(a,clip,"a\r\nβ\n"));assertEquals("UNBOUND",t.clipboard(a,clip,"a\r\nβ\n"));
    }
    @Test public void clipboardLabelCannotSubstituteForAnnotatedObject(){
        AsyncCopyTrace t=new AsyncCopyTrace(()->0L);Object annotated=new Object(),callback=new Object(),continuation=new Object();String label="same";
        t.callback(t.render(annotated,"same"),callback,annotated);AsyncCopyTrace.Action a=t.begin(callback);
        assertNotNull(a);assertFalse(a.ownsAnnotated(label));assertTrue(a.ownsAnnotated(annotated));
        assertNull(t.get(AsyncCopyTrace.Kind.IXL,continuation));
        if(a.ownsAnnotated(annotated))t.tag(AsyncCopyTrace.Kind.IXL,continuation,a);
        assertSame(a,t.get(AsyncCopyTrace.Kind.IXL,continuation));
    }
    @Test public void identicalTextDoesNotMergeBlocks(){Clock c=new Clock();AsyncCopyTrace t=new AsyncCopyTrace(c);Object a=new Object(),b=new Object(),x=new Object(),y=new Object();AsyncCopyTrace.Block p=t.block("same"),q=t.block("same");t.annotate(p,a,"same");t.annotate(q,b,"same");AsyncCopyTrace.Render r=t.render(a,"same"),s=t.render(b,"same");t.callback(r,x,a);t.callback(s,y,b);assertNotEquals(t.begin(x).blockId,t.begin(y).blockId);t.annotate(q,a,"same");assertEquals(0,t.render(a,"same").blockId);}
    @Test public void refusesWrongObjectExpiredAndAmbiguousCallback(){Clock c=new Clock();AsyncCopyTrace t=new AsyncCopyTrace(c);Object a=new Object(),b=new Object(),cb=new Object();AsyncCopyTrace.Render r=t.render(a,"x");t.callback(r,cb,b);assertNull(t.begin(cb));t.callback(r,cb,a);assertNotNull(t.begin(cb));t.callback(t.render(b,"x"),cb,b);assertNull(t.begin(cb));Object cb2=new Object();t.callback(r,cb2,a);c.t=120001;assertNull(t.begin(cb2));assertNull(t.block("\ud800"));}
    @Test public void differentValueIsNotParity(){AsyncCopyTrace t=new AsyncCopyTrace(()->1L);Object c=new Object(),b=new Object(),clip=new Object();t.callback(t.render(c,"a\n"),b,c);AsyncCopyTrace.Action a=t.begin(b);t.tag(AsyncCopyTrace.Kind.CLIP,clip,a);assertEquals("MISMATCH",t.clipboard(a,clip,"a"));}
    @Test public void capsAndCompleteActionLogBudget(){AsyncCopyTrace t=new AsyncCopyTrace(()->0);Object c=new Object(),b=new Object();t.callback(t.render(c,"x"),b,c);for(int i=0;i<20;i++)assertEquals(i<16,t.begin(b).report);Object[] refs=new Object[500];for(int i=0;i<refs.length;i++){refs[i]=new Object();t.annotate(t.block("x"),refs[i],"x");}assertTrue(t.size()<=256);}
}
