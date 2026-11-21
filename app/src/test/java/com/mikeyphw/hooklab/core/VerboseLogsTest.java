package com.mikeyphw.hooklab.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VerboseLogsTest {
    @Test public void defaultOffNeverEmits() {
        assertFalse(VerboseLogs.gate(false,5000L,0L,3000L));
        assertFalse(VerboseLogs.gate(false,9000L,3000L,3000L));
    }
    @Test public void firstEventAndRateLimit() {
        assertTrue(VerboseLogs.gate(true,5000L,0L,3000L));
        assertFalse(VerboseLogs.gate(true,7999L,5000L,3000L));
        assertTrue(VerboseLogs.gate(true,8000L,5000L,3000L));
    }
    @Test public void monotonicResetDoesNotLockOutLogs() {
        assertTrue(VerboseLogs.gate(true,100L,9900L,3000L));
    }
    @Test public void fixedEventVocabularyCannotContainPrivateMetadata() {
        for (VerboseLogs.Event e:VerboseLogs.Event.values()) {
            assertTrue(e.name().matches("[A-Z][A-Z0-9_]*"));
        }
        assertTrue(VerboseLogs.Event.values().length >= 15);
    }
}
