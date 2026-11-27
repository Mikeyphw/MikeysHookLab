package com.mikeyphw.hooklab.qa;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mikeyphw.hooklab.packs.VerifiedPackLoader;
import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import com.mikeyphw.hooklab.sdk.HookSdk;
import java.io.File;
import java.io.FileInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Loads independently built, signed real migration DEX. No third-party host or framework is invoked. */
@RunWith(AndroidJUnit4.class)
public final class Mod02PackArtTest {
    private static byte[] bytes(File path,int max)throws Exception {
        try(FileInputStream in=new FileInputStream(path)){return HookPack.readBounded(in,max);}
    }
    @Test public void verifiedMigratedDex()throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();File dir=new File(context.getFilesDir(),"mod02-test");
        byte[] pub=bytes(new File(dir,"publisher-test.der"),2048);
        for(String module:PackPolicy.MODULES){
            byte[] archive=bytes(new File(dir,module+".hookpack"),HookPack.MAX_ARCHIVE);
            HookPack.Verified pack=HookPack.verify(archive,pub);assertEquals(module,pack.moduleId);assertEquals("test",pack.channel);
            Class<? extends HookSdk.Entrypoint> entry=VerifiedPackLoader.load(pack);assertEquals(pack.entrypoint,entry.getName());
            assertNotSame(HookSdk.class.getClassLoader(),entry.getClassLoader());assertSame(HookSdk.class,entry.getClassLoader().loadClass(HookSdk.class.getName()));assertNotNull(entry.getDeclaredConstructor().newInstance());
            archive[archive.length/2]^=1;
            try{HookPack.verify(archive,pub);fail("tampered archive accepted");}catch(IllegalArgumentException|java.security.GeneralSecurityException expected){/* fail closed */}
        }
    }
}
