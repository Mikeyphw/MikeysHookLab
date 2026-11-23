package com.mikeyphw.hooklab.chatgpt;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CodeBlockActionMatcherTest {
    private static class Clock implements CodeBlockActionMatcher.Clock {
        long now=100L;
        public long nowMs(){return now;}
    }
    @Test public void sameTextTwoDistinctCallbacksHaveSeparateIdentity() {
        Clock c=new Clock();CodeBlockActionMatcher m=new CodeBlockActionMatcher(c);
        Object first=new Object(),second=new Object();
        assertTrue(m.bind(first,10,"echo 'same'\n"));
        assertTrue(m.bind(second,10,"echo 'same'\n"));
        CodeBlockActionMatcher.Frame f=m.enter(first);
        CodeBlockActionMatcher.Evidence a=m.matchClipboardText("echo 'same'\n");
        m.exit(f);
        f=m.enter(second);
        CodeBlockActionMatcher.Evidence b=m.matchClipboardText("echo 'same'\n");
        m.exit(f);
        assertEquals(CodeBlockActionMatcher.Outcome.BOUND_VALUE_MATCH,a.outcome);
        assertEquals(CodeBlockActionMatcher.Outcome.BOUND_VALUE_MATCH,b.outcome);
        assertNotEquals(a.bindingId,b.bindingId);
    }
    @Test public void rejectsCallbackReuseForDifferentCode() {
        CodeBlockActionMatcher m=new CodeBlockActionMatcher(()->10L);
        Object target=new Object();
        assertTrue(m.bind(target,10,"one"));
        assertFalse(m.bind(target,10,"two"));
        CodeBlockActionMatcher.Frame f=m.enter(target);
        assertEquals(CodeBlockActionMatcher.Outcome.SHARED_CALLBACK_AMBIGUOUS,m.matchClipboardText("one").outcome);
        m.exit(f);
    }
    @Test public void noSynchronousCallbackMeansNoProof() {
        CodeBlockActionMatcher m=new CodeBlockActionMatcher(()->10L);
        m.bind(new Object(),10,"echo hello");
        assertEquals(CodeBlockActionMatcher.Outcome.NO_BOUND_CALLBACK,m.matchClipboardText("echo hello").outcome);
    }
    @Test public void sourceMismatchesAreNotNormalized() {
        CodeBlockActionMatcher m=new CodeBlockActionMatcher(()->10L);Object o=new Object();
        assertTrue(m.bind(o,14,"echo $HOME\r\n\n"));
        CodeBlockActionMatcher.Frame f=m.enter(o);
        assertEquals(CodeBlockActionMatcher.Outcome.BOUND_VALUE_MATCH,m.matchClipboardText("echo $HOME\r\n\n").outcome);
        assertEquals(CodeBlockActionMatcher.Outcome.BOUND_VALUE_MISMATCH,m.matchClipboardText("echo $HOME\n\n").outcome);
        assertEquals(CodeBlockActionMatcher.Outcome.BOUND_VALUE_MISMATCH,m.matchClipboardText("echo $HOME\r\n").outcome);
        m.exit(f);
    }
    @Test public void expiredBindingsCannotAuthorizeAction() {
        Clock c=new Clock();CodeBlockActionMatcher m=new CodeBlockActionMatcher(c);Object o=new Object();
        assertTrue(m.bind(o,10,"x"));c.now+=120001;
        assertNull(m.enter(o));assertEquals(0,m.bindingCount());
    }
    @Test public void malformedUnicodeRejected() {
        CodeBlockActionMatcher m=new CodeBlockActionMatcher(()->10L);
        assertFalse(m.bind(new Object(),10,"\uD800"));
    }
    @Test public void unwoundCallbackNoLongerMatches() {
        CodeBlockActionMatcher m=new CodeBlockActionMatcher(()->10L);Object o=new Object();
        m.bind(o,10,"value");CodeBlockActionMatcher.Frame f=m.enter(o);
        assertEquals(CodeBlockActionMatcher.Outcome.BOUND_VALUE_MATCH,m.matchClipboardText("value").outcome);
        m.exit(f);
        assertEquals(CodeBlockActionMatcher.Outcome.NO_BOUND_CALLBACK,m.matchClipboardText("value").outcome);
    }
}
