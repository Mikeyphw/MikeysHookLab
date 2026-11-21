package com.mikeyphw.hooklab.mcc;

import org.junit.Test;
import static org.junit.Assert.*;

public class MccAutoPolicyTest {
    @Test public void zeroMustBeExactlyOneAsciiByte() {
        assertTrue(MccAutoPolicy.exactZero(new byte[]{'0'}));
        assertFalse(MccAutoPolicy.exactZero(new byte[0]));
        assertFalse(MccAutoPolicy.exactZero(new byte[]{'0','\n'}));
        assertFalse(MccAutoPolicy.exactZero(new byte[]{'9'}));
        assertFalse(MccAutoPolicy.exactZero(null));
    }
    @Test public void filenameMustBeAccountLocalAndSafe() {
        assertTrue(MccAutoPolicy.allowedName("abcDEF_123-DiamondBalance"));
        assertFalse(MccAutoPolicy.allowedName("a-DiamondBalance"));
        assertFalse(MccAutoPolicy.allowedName("../../other-DiamondBalance"));
        assertFalse(MccAutoPolicy.allowedName("abcDEF_123-DiamondBalance-backup"));
        assertFalse(MccAutoPolicy.allowedName(null));
    }
}
