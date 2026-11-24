package com.mikeyphw.hooklab.chatgpt;
import org.junit.Test;
import static org.junit.Assert.*;
public final class TermuxOfferGateTest {
    @Test public void matchedActionOneUse(){TermuxOfferGate g=new TermuxOfferGate();long t=g.replace(49,100);assertEquals(49,g.available(t,101));assertEquals(49,g.consume(t,102));assertEquals(0,g.consume(t,103));}
    @Test public void unrelatedWriteAndExpiryRefused(){TermuxOfferGate g=new TermuxOfferGate();long a=g.replace(50,100);long b=g.replace(0,101);assertEquals(0,g.consume(a,102));assertEquals(0,g.consume(b,102));long c=g.replace(51,200);assertEquals(0,g.consume(c,12201));}
    @Test public void staleDismissDoesNotClearNewOffer(){TermuxOfferGate g=new TermuxOfferGate();long a=g.replace(50,100);long b=g.replace(51,101);g.clear(a);assertEquals(51,g.consume(b,102));}
    @Test public void pauseInvalidatesAndClockRegressionRefused(){TermuxOfferGate g=new TermuxOfferGate();long a=g.replace(49,100);assertEquals(0,g.consume(a,99));g.invalidate();assertEquals(0,g.consume(a,101));}
}
