package com.mikeyphw.hooklab.termux;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TermuxCommandPolicyTest {
    @Test public void preservesMultilineCommandsExactly() {
        String value = "  echo hello\nprint -r -- 'one,two'\n";
        assertEquals(value, TermuxCommandPolicy.checkedCommand(value));
    }
    @Test public void preservesUnicodeAndQuotes() {
        String value = "printf '%s' 'á\n漢字'";
        assertEquals(value, TermuxCommandPolicy.checkedCommand(value));
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmpty() { TermuxCommandPolicy.checkedCommand(" \n "); }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsNul() { TermuxCommandPolicy.checkedCommand("echo x\u0000"); }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizeUtf8() {
        TermuxCommandPolicy.checkedCommand("á".repeat(TermuxCommandPolicy.MAX_BYTES / 2 + 1));
    }
    @Test public void acceptsExactByteLimit() {
        String input = "x".repeat(TermuxCommandPolicy.MAX_BYTES);
        assertEquals(input, TermuxCommandPolicy.checkedCommand(input));
    }
    @Test public void defaultsToHome() {
        assertEquals(TermuxCommandPolicy.HOME, TermuxCommandPolicy.checkedWorkingDirectory(""));
        assertEquals(TermuxCommandPolicy.HOME, TermuxCommandPolicy.checkedWorkingDirectory("~"));
    }
    @Test public void resolvesHomeSubdirectories() {
        assertEquals(TermuxCommandPolicy.HOME + "/Code/My Repo",
                TermuxCommandPolicy.checkedWorkingDirectory("~/Code/My Repo"));
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsTraversal() { TermuxCommandPolicy.checkedWorkingDirectory("~/Code/../private"); }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutsideTermuxHome() { TermuxCommandPolicy.checkedWorkingDirectory("/sdcard/Download"); }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsPrefixConfusion() {
        TermuxCommandPolicy.checkedWorkingDirectory(TermuxCommandPolicy.HOME + "other");
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsControlCharacters() { TermuxCommandPolicy.checkedWorkingDirectory("~/Code\nother"); }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptySegments() { TermuxCommandPolicy.checkedWorkingDirectory("~/Code//Repo"); }
}
