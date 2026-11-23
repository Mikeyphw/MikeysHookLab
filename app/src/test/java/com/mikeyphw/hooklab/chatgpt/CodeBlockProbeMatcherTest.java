package com.mikeyphw.hooklab.chatgpt;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CodeBlockProbeMatcherTest {
    private static final class Clock implements CodeBlockProbeMatcher.Clock {
        long ms=1000;
        public long nowMs() { return ms; }
    }
    @Test public void preservesShellSyntaxAndTrailingNewlines() {
        Clock clock=new Clock();
        CodeBlockProbeMatcher matcher=new CodeBlockProbeMatcher(clock);
        String code="  printf '%s\\n' \"$HOME\" \"$(uname -m)\"\r\n"
                +"echo 'literal `backticks` and | ; \" quotes'\n\n";
        matcher.observePair("zsh",code);
        CodeBlockProbeMatcher.Evidence exact=matcher.compareCopiedText(code);
        assertEquals(CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY,exact.result);
        assertEquals(1,exact.parameterSlot);
        assertEquals(code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,exact.utf8Length);
        assertEquals(3,exact.lineFeeds);
        assertEquals(1,exact.carriageReturns);
        assertEquals(2,exact.trailingNewlines);
        assertEquals(CodeBlockProbeMatcher.Result.UNMATCHED,matcher.compareCopiedText(code.trim()).result);
        assertEquals(CodeBlockProbeMatcher.Result.UNMATCHED,matcher.compareCopiedText(code.replace("\r\n","\n")).result);
    }
    @Test public void doesNotLogOrRetainContentAsPlaintext() {
        Clock clock=new Clock(); CodeBlockProbeMatcher matcher=new CodeBlockProbeMatcher(clock);
        matcher.observePair("zsh","echo 密码 🐧\n");
        CodeBlockProbeMatcher.Evidence e=matcher.compareCopiedText("echo 密码 🐧\n");
        assertEquals(CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY,e.result);
        assertTrue(e.ephemeralToken.matches("[0-9a-f]{16}"));
        assertFalse(e.ephemeralToken.contains("密码"));
    }
    @Test public void rejectsAmbiguityAndUnpairedSurrogates() {
        Clock clock=new Clock(); CodeBlockProbeMatcher matcher=new CodeBlockProbeMatcher(clock);
        matcher.observePair("same","same");
        assertEquals(CodeBlockProbeMatcher.Result.AMBIGUOUS_SLOTS,matcher.compareCopiedText("same").result);
        assertEquals(CodeBlockProbeMatcher.Result.UNSAFE_TEXT,matcher.compareCopiedText("bad\ud800").result);
    }
    @Test public void boundsCandidatesAndExpiresOldRenderings() {
        Clock clock=new Clock(); CodeBlockProbeMatcher matcher=new CodeBlockProbeMatcher(clock);
        for(int i=0;i<600;i++)matcher.observePair("lang-"+i,"echo "+i);
        assertTrue(matcher.candidateCount()<=128);
        assertEquals(CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY,matcher.compareCopiedText("echo 599").result);
        clock.ms+=2*60*1000L+1;
        assertEquals(CodeBlockProbeMatcher.Result.UNMATCHED,matcher.compareCopiedText("echo 599").result);
    }
    @Test public void emptyStringAndUnicodeAreDistinct() {
        CodeBlockProbeMatcher m=new CodeBlockProbeMatcher(()->1);
        m.observePair("zsh","");
        assertEquals(CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY,m.compareCopiedText("").result);
        assertEquals(CodeBlockProbeMatcher.Result.UNMATCHED,m.compareCopiedText("\n").result);
        m.observePair("zsh","olá\n");
        assertEquals(CodeBlockProbeMatcher.Result.MATCH_VALUE_ONLY,m.compareCopiedText("olá\n").result);
    }
}
