package com.mikeyphw.hooklab.ui;
import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Ui08LegacySelectionTest {
    private final TweakSpec a=new TweakSpec("a.one","org.example.same",1,9);
    private final TweakSpec b=new TweakSpec("b.two","org.example.same",1,9);
    private final TweakSpec c=new TweakSpec("c.three","org.example.other",1,9);
    @Test public void oneRegisteredHookMayMigrate() {
        assertEquals("c.three",Ui08LegacySelection.resolveUnique(Arrays.asList(a,b,c),"org.example.other"));
    }
    @Test public void twoHooksSharingPackageNeverSelectFirst() {
        assertNull(Ui08LegacySelection.resolveUnique(Arrays.asList(a,b,c),"org.example.same"));
        assertNull(Ui08LegacySelection.resolveUnique(Arrays.asList(b,a,c),"org.example.same"));
    }
    @Test public void missingOrUnknownPackageNeverRoutes() {
        assertNull(Ui08LegacySelection.resolveUnique(Arrays.asList(a,c),"org.example.unknown"));
        assertNull(Ui08LegacySelection.resolveUnique(Arrays.asList(a,c),null));
        assertNull(Ui08LegacySelection.resolveUnique(Arrays.asList(a,c),""));
    }
    @Test public void emptyRegistryNeverRoutes() {
        assertNull(Ui08LegacySelection.resolveUnique(List.of(),"org.example.same"));
    }
    @Test(expected=NullPointerException.class) public void nullRegistryFailsClosed() {
        Ui08LegacySelection.resolveUnique(null,"org.example.same");
    }
    @Test(expected=NullPointerException.class) public void nullSpecFailsClosed() {
        Ui08LegacySelection.resolveUnique(Arrays.asList(a,null),"org.example.same");
    }
}
