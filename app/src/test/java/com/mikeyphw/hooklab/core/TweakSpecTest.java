package com.mikeyphw.hooklab.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class TweakSpecTest {
    private final TweakSpec s = new TweakSpec("demo.feature", "org.example.app", 10, 20);
    @Test public void safeDefault() { assertEquals(1, TweakCatalog.all().size()); assertFalse(s.eligible("org.example.app","org.example.app",15,false)); }
    @Test public void exactScope() { assertFalse(s.eligible("org.example.other","org.example.app",15,true)); assertFalse(s.eligible("org.example.app","org.example.app:remote",15,true)); }
    @Test public void bounds() { assertFalse(s.eligible("org.example.app","org.example.app",9,true)); assertFalse(s.eligible("org.example.app","org.example.app",21,true)); assertTrue(s.eligible("org.example.app","org.example.app",10,true)); assertTrue(s.eligible("org.example.app","org.example.app",20,true)); }
    @Test(expected=IllegalArgumentException.class) public void invalidRange() { new TweakSpec("x","org.example.app",10,9); }
}
