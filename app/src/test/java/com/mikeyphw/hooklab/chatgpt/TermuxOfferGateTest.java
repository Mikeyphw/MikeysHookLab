package com.mikeyphw.hooklab.chatgpt;
import org.junit.Test;
import static org.junit.Assert.*;
public final class TermuxOfferGateTest {
 @Test public void activatesOnlyOnCommit(){Object k=new Object();TermuxOfferGate b=new TermuxOfferGate(k,"echo first\n");assertNull(b.selected(100));b.remembered();assertEquals("echo first\n",b.selected(100));assertNull(b.selected(599));assertEquals("echo first\n",b.selected(600));}
 @Test public void abandonedAndForgottenCallbacksAreNeverReactivated(){Object k=new Object();TermuxOfferGate b=new TermuxOfferGate(k,"a");b.dispose();b.remembered();assertFalse(b.owns(k));assertNull(b.selected(100));TermuxOfferGate c=new TermuxOfferGate(k,"b");c.remembered();c.dispose();assertNull(c.selected(600));c.remembered();assertNull(c.selected(1000));}
 @Test public void identicalTextDoesNotShareOwnership(){Object a=new Object(),b=new Object();TermuxOfferGate x=new TermuxOfferGate(a,"echo x"),y=new TermuxOfferGate(b,"echo x");x.remembered();y.remembered();assertTrue(x.owns(a));assertFalse(x.owns(b));assertTrue(y.owns(b));x.dispose();assertEquals("echo x",y.selected(10));assertNull(x.selected(10));}
 @Test public void uncommittedReplacementCannotLaunch(){TermuxOfferGate old=new TermuxOfferGate(new Object(),"old"),next=new TermuxOfferGate(new Object(),"new");old.remembered();assertNull(next.selected(0));next.dispose();assertEquals("old",old.selected(0));assertNull(next.selected(600));}
 @Test public void recycledGenerationDropsOldContent(){TermuxOfferGate old=new TermuxOfferGate(new Object(),"old"),next=new TermuxOfferGate(new Object(),"new");old.remembered();old.dispose();next.remembered();assertNull(old.selected(0));assertEquals("new",next.selected(0));}
}
