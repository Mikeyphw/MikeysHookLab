package com.mikeyphw.hooklab.obtainx;

import org.junit.Test;
import static org.junit.Assert.*;

/** Ported behavior of upstream NeoStoreObtainX M2/M5 source and seed contracts. */
public class SourcePolicyTest {
    private static final String APP="com.example.demo";
    private static SourcePolicy.Snapshot ready(String repo,String source) {
        return SourcePolicy.ready(APP,APP,APP,3L,3L,repo,"",source);
    }
    @Test public void officialFdroid() {
        SourcePolicy.Snapshot s=ready("https://f-droid.org/repo",null);
        assertNotNull(s);assertEquals("https://f-droid.org/packages/"+APP,s.preferredUrl);
        assertNull(s.overrideSource);
        assertTrue(SourcePolicy.prepare(s,APP).contains("\"additionalSettings\":\"{}\""));
    }
    @Test public void izzyUsesItsOwnAdapter() {
        SourcePolicy.Snapshot s=ready("https://apt.izzysoft.de/fdroid/repo","https://unsupported.example/app");
        assertNotNull(s);assertEquals("IzzyOnDroid",s.overrideSource);
        assertTrue(s.preferredUrl.endsWith("/"+APP));
        assertTrue(SourcePolicy.prepare(s,APP).contains("\"overrideSource\":\"IzzyOnDroid\""));
    }
    @Test public void customRepositoryNotSubstituted() {
        SourcePolicy.Snapshot s=ready("https://example.org/fdroid/repo/index.xml",null);
        assertNotNull(s);assertEquals("FDroidRepo",s.overrideSource);
        assertEquals("https://example.org/fdroid/repo?appId="+APP,s.preferredUrl);
        assertTrue(SourcePolicy.prepare(s,APP).contains("appIdOrName"));
    }
    @Test public void preferredGithubSource() {
        SourcePolicy.Snapshot s=ready("https://f-droid.org/repo","https://github.com/example/project");
        assertNotNull(s);assertEquals("https://github.com/example/project",s.preferredUrl);
        assertNull(s.overrideSource);
    }
    @Test public void rejectGithubIssuesAsSourceButFallbackToStore() {
        SourcePolicy.Snapshot s=ready("https://f-droid.org/repo","https://github.com/example/project/issues");
        assertNotNull(s);assertEquals("https://f-droid.org/packages/"+APP,s.preferredUrl);
    }
    @Test public void rejectCredentialedRepository() {
        assertNull(SourcePolicy.ready(APP,APP,APP,1L,1L,"https://f-droid.org/repo","token",null));
        assertNull(ready("https://user:secret@example.org/repo",null));
    }
    @Test public void rejectMismatchedIdentityAndRepository() {
        assertNull(SourcePolicy.ready(APP,"com.example.other",APP,1L,1L,"https://f-droid.org/repo","",null));
        assertNull(SourcePolicy.ready(APP,APP,APP,1L,2L,"https://f-droid.org/repo","",null));
    }
    @Test public void rejectUnsafeOfficialRepositoryPath() {
        assertNull(ready("https://f-droid.org/archive/repo",null));
        assertNull(ready("https://apt.izzysoft.de/fdroid/repo?token=a",null));
        assertNull(ready("http://f-droid.org/repo",null));
    }
    @Test public void rejectChangedRoute() {
        SourcePolicy.Snapshot s=ready("https://f-droid.org/repo",null);
        assertNull(SourcePolicy.prepare(s,"com.other.app"));
    }
    @Test public void snapshotsRejectStaleSelectedSource() {
        SourcePolicy.Snapshot a=ready("https://f-droid.org/repo",null);
        SourcePolicy.Snapshot b=ready("https://apt.izzysoft.de/fdroid/repo",null);
        assertFalse(SourcePolicy.same(a,b));
        assertTrue(SourcePolicy.same(a,a));
    }
    @Test public void rejectMaliciousPathAndPorts() {
        assertNull(ready("https://f-droid.org:8443/repo",null));
        assertNull(ready("https://example.org/repo/%2e%2e/private",null));
    }
    @Test public void JSONQuoteEscapesControlAndQuotes() {
        assertEquals("\"a\\\"b\\\\c\\n\"",SourcePolicy.quote("a\"b\\c\n"));
    }
}
