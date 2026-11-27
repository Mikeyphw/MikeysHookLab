import com.mikeyphw.hooklab.runtime.pack.HookPack;
import com.mikeyphw.hooklab.runtime.pack.PackPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Test CLI for the actual independent Java parser, never a second implementation. */
public final class Mod02PackChecks {
    private Mod02PackChecks() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("pack/key required");
        byte[] archive=Files.readAllBytes(Path.of(args[0])), key=Files.readAllBytes(Path.of(args[1]));
        HookPack.Verified pack=HookPack.verify(archive,key);
        if(!"probe".equals(pack.moduleId))PackPolicy.verifyMigration(pack);
        byte[] original=pack.dex(), copy=pack.dex();copy[0]^=1;
        if(!Arrays.equals(original,pack.dex()))throw new AssertionError("mutable verified state");
        java.nio.ByteBuffer buffer=pack.directDex();
        if(!buffer.isDirect()||buffer.position()!=0||buffer.remaining()!=original.length)throw new AssertionError("invalid direct buffer");
        byte[] view=new byte[buffer.remaining()];buffer.get(view);if(!Arrays.equals(view,original))throw new AssertionError("direct buffer mismatch");
        System.out.println("JAVA_PACK_VERIFIED "+pack.moduleId+" "+pack.version+" "+pack.signer+" "+pack.archiveSha256);
    }
}
