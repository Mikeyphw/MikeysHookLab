package com.mikeyphw.hooklab.core;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry.RegistryGeneration;
import com.mikeyphw.hooklab.sdk.HookSdk.HookDescriptor;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ModularCoreTest {
    @Test public void realCatalogMatchesAllManifestMembers(){
        BuiltinRegistry registry=BuiltinRegistry.current();assertEquals(6,TweakCatalog.entries().size());
        for(TweakEngine.Entry<HookContext> e:TweakCatalog.entries()){
            HookDescriptor h=registry.hook(e.spec.id);assertNotNull(h);assertEquals(h.packageName,e.spec.packageName);assertEquals(h.minVersion,e.spec.minVersion);assertEquals(h.maxVersion,e.spec.maxVersion);assertEquals(h.title,e.title);assertEquals(h.description,e.description);
        }
        assertEquals(4,HookGroupCatalog.groups(TweakCatalog.all()).size());
    }
    @Test public void staleAndUnavailableGenerationsRefused(){
        String generation=BuiltinRegistry.current().generation;
        assertFalse(RegistryGeneration.matches(generation,()->null));assertFalse(RegistryGeneration.matches(generation,()->"0".repeat(64)));assertFalse(RegistryGeneration.matches(generation,()->{throw new IllegalStateException();}));assertTrue(RegistryGeneration.matches(generation,()->generation));
    }
}
