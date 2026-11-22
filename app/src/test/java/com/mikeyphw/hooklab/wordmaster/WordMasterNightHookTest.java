package com.mikeyphw.hooklab.wordmaster;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure host-version guards; the native Unity surface requires device acceptance. */
public final class WordMasterNightHookTest {
    @Test public void acceptsExactApk() {
        assertTrue(WordMasterNightHook.matches("com.fugo.wowsolitaire", 56240403L, "0.6.10"));
    }
    @Test public void refusesNewerOrOlderBuilds() {
        assertFalse(WordMasterNightHook.matches("com.fugo.wowsolitaire", 56240404L, "0.6.10"));
        assertFalse(WordMasterNightHook.matches("com.fugo.wowsolitaire", 56240402L, "0.6.10"));
        assertFalse(WordMasterNightHook.matches("com.fugo.wowsolitaire", 56240403L, "0.6.11"));
    }
    @Test public void refusesOtherAppsOrNull() {
        assertFalse(WordMasterNightHook.matches("com.fugo.other", 56240403L, "0.6.10"));
        assertFalse(WordMasterNightHook.matches(null, 56240403L, "0.6.10"));
        assertFalse(WordMasterNightHook.matches("com.fugo.wowsolitaire", 56240403L, null));
    }
}
