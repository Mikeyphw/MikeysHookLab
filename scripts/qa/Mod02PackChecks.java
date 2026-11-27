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
        if(pack.hasUi()){
            if(!pack.directUiDex().isDirect()||pack.directUiDex().remaining()==0||!pack.uiEntrypoint().equals("com.mikeyphw.hooklab.moduleui."+pack.moduleId+".Provider"))throw new AssertionError("UI DEX identity");
            if("wordmaster".equals(pack.moduleId)){
                try{PackPolicy.requireUiAdmission(pack,"0".repeat(64));throw new AssertionError("wrong approval accepted");}catch(IllegalArgumentException expected){}
                if("personal".equals(pack.channel))PackPolicy.requireUiAdmission(pack,pack.archiveSha256);
                else try{PackPolicy.requireUiAdmission(pack,pack.archiveSha256);throw new AssertionError("test UI admitted");}catch(IllegalArgumentException expected){}
            }
            if("probe".equals(pack.moduleId)){
                if(!"Ajuda".equals(pack.uiResources.translation("probe","help",java.util.Locale.forLanguageTag("pt-BR"),"missing"))||!"Settings".equals(pack.uiResources.translation("probe","label",java.util.Locale.forLanguageTag("pt-BR"),"missing")))throw new AssertionError("resource locale fallback");
                byte[] asset=pack.uiResources.asset("probe","guide").bytes();if(!"hello".equals(new String(asset,java.nio.charset.StandardCharsets.UTF_8)))throw new AssertionError("resource bytes");asset[0]^=1;
                if(pack.uiResources.asset("probe","guide").bytes()[0]!='h')throw new AssertionError("mutable asset");
                try{pack.uiResources.asset("other","guide");throw new AssertionError("foreign resource namespace accepted");}catch(IllegalArgumentException expected){}
                System.out.println("JAVA_PACK_UI_RESOURCES_PASS");
            }
        }
        System.out.println("JAVA_PACK_VERIFIED "+pack.moduleId+" "+pack.version+" "+pack.signer+" "+pack.archiveSha256);
    }
}
