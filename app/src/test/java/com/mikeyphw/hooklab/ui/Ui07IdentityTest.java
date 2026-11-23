package com.mikeyphw.hooklab.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Ui07IdentityTest {
    @Test public void knownDigestIsStandardSha256Hex(){
        assertEquals("BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD",
            Ui07Identity.signerFingerprint(new byte[]{'a','b','c'}));
    }
    @Test public void missingCertificateIsUnverified(){
        assertEquals("",Ui07Identity.signerFingerprint(null));
        assertEquals("",Ui07Identity.signerFingerprint(new byte[0]));
    }
    @Test public void versionCannotExposeUserSuppliedControlCharacters(){
        assertEquals("0.7.1 (10)",Ui07Identity.versionDisplay("0.7.1",10));
        assertEquals("unknown (0)",Ui07Identity.versionDisplay(null,-15));
        assertFalse(Ui07Identity.versionDisplay("secret\nline",12).contains("\n"));
    }
}
