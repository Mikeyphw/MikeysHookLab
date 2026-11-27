import com.mikeyphw.hooklab.sdk.ModuleConfig;
import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Production classes exercised on the host JVM; never claims Android/device acceptance. */
public final class Mod03ConfigChecks {
    private static int count;
    private static void check(boolean condition){if(!condition)throw new AssertionError("MOD03 check "+count);count++;}
    private static void reject(Runnable step){try{step.run();throw new AssertionError("accepted invalid config");}catch(IllegalArgumentException|ArithmeticException|IllegalStateException expected){count++;}}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        ModuleConfig.Document base=ModuleConfig.defaults();check(base.effective(ModuleConfig.MODULE,ModuleConfig.HOOK,ModuleConfig.TARGET).color()==0x64010712);
        ModuleConfig.Filter filter=new ModuleConfig.Filter(true,80,"warm");ModuleConfig.Document custom=base.put("evening",filter).choose("evening");check(custom.effective(ModuleConfig.MODULE,ModuleConfig.HOOK,ModuleConfig.TARGET).color()==0x50180c03);
        ModuleConfig.Document override=custom.override("default");check(override.effective(ModuleConfig.MODULE,ModuleConfig.HOOK,ModuleConfig.TARGET).color()==0x64010712);check(custom.remove("evening").active.equals("default"));
        check(ModuleConfig.parse(bytes(override.canonical())).canonical().equals(override.canonical()));check(!base.digest().equals(custom.digest()));
        ModuleConfig.Frame frame=ModuleConfig.read(Map.of());check(frame.current.digest().equals(base.digest())&&frame.previous==null);ModuleConfig.Frame saved=ModuleConfig.read(ModuleConfig.replacement(frame,custom));check(saved.current.digest().equals(custom.digest())&&saved.previous.digest().equals(base.digest()));check(ModuleConfig.read(ModuleConfig.replacement(saved,saved.previous)).current.digest().equals(base.digest()));
        reject(()->new ModuleConfig.Filter(true,255,"warm"));reject(()->new ModuleConfig.Filter(true,80,"invalid"));reject(()->custom.put("bad/password",filter));reject(()->custom.remove("default"));reject(()->custom.choose("unknown"));reject(()->custom.override("unknown"));reject(()->custom.effective("wordmaster",ModuleConfig.HOOK,"other.app"));reject(()->ModuleConfig.parse(bytes(custom.canonical().replace("\"opacity\":80","\"opacity\":80,\"token\":\"private\""))));reject(()->ModuleConfig.parse(bytes(custom.canonical().replace("\"active\":\"evening\"","\"active\":\"evening\",\"active\":\"default\""))));reject(()->ModuleConfig.parse(bytes(custom.canonical().replace("\"module\":\"wordmaster\"","\"module\":\"other\""))));reject(()->ModuleConfig.read(Map.of(ModuleConfig.CURRENT,custom.canonical())));
        Map<String,String> bad=new TreeMap<>(ModuleConfig.replacement(frame,custom));bad.put(ModuleConfig.GENERATION,"0".repeat(64));reject(()->ModuleConfig.read(bad));reject(()->ModuleConfig.read(Map.of(ModuleConfig.CURRENT,custom.canonical(),ModuleConfig.GENERATION,custom.digest(),ModuleConfig.PREVIOUS,"{}")));
        check(new HookSdk.Settings(Map.of(ModuleConfig.HOOK,true),filter).wordMasterFilter().color()==filter.color());check(new HookSdk.Settings(Map.of(ModuleConfig.HOOK,true)).wordMasterFilter().color()==0x64010712);reject(()->new HookSdk.Settings(Map.of("other.hook",true),filter).wordMasterFilter());
        DiagnosticTimeline t=new DiagnosticTimeline();String sha="a".repeat(64),gen="b".repeat(64);String marker="I/HookLabPack(12345): com.mikeyphw.hooklab: PACK_INSTALLED id=neostore.obtainx.manual module=obtainx version=1.0.0 sha256="+sha+" generation="+gen;
        t.importStream(new ByteArrayInputStream(bytes(marker+"\n"+marker.replace("module=obtainx","module=chatgpt")+"\n"+marker+" token=PRIVATE\n"+marker.replace("version=1.0.0","version=01.0.0"))),"ANDROID_LOGCAT_IMPORT");
        DiagnosticTimeline.Event e=t.filtered(null).get(0);check(e.code.equals("PACK_INSTALLED")&&e.hookId.equals("neostore.obtainx.manual")&&e.moduleId.equals("obtainx")&&e.archiveSha256.equals(sha)&&e.registryGeneration.equals(gen)&&e.correlation.equals("ASSOCIATED"));
        check(t.filtered(null).stream().filter(row->row.code.equals("PACK_INSTALLED")).count()==1);String out=DiagnosticTimeline.toJsonl(t.filtered(null));check(out.contains("\"archive_sha256\":\""+sha+"\"")&&!out.contains("PRIVATE"));System.out.println("MOD03_CONFIG_CHECKS_PASS="+count);
    }
}
