import com.mikeyphw.hooklab.sdk.ModuleConfig;
import com.mikeyphw.hooklab.sdk.ChatGptConfig;
import com.mikeyphw.hooklab.chatgpt.ChatGptOfferChecks;
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
        ChatGptConfig.Document chat=ChatGptConfig.defaults();ChatGptConfig.Options initial=chat.effective(ChatGptConfig.MODULE,ChatGptConfig.HOOK,ChatGptConfig.TARGET);
        check(initial.showRun&&initial.layoutDiagnostics&&initial.tapIntervalMs==500);
        ChatGptConfig.Options hidden=new ChatGptConfig.Options(false,false,1500);
        ChatGptConfig.Document edited=chat.put("quiet",hidden).choose("quiet");
        check(!edited.effective(ChatGptConfig.MODULE,ChatGptConfig.HOOK,ChatGptConfig.TARGET).showRun);
        check(edited.override("default").effective(ChatGptConfig.MODULE,ChatGptConfig.HOOK,ChatGptConfig.TARGET).showRun);
        check(edited.remove("quiet").active().equals("default"));check(edited.override("quiet").remove("quiet").appOverride().isEmpty());
        check(ChatGptConfig.parse(bytes(edited.canonical())).canonical().equals(edited.canonical()));check(ModuleConfig.parseDocument(bytes(edited.canonical())).module().equals("chatgpt"));
        check(!chat.digest().equals(edited.digest()));check(!ModuleConfig.GROUP.equals(ChatGptConfig.GROUP));
        ChatGptConfig.Frame before=ChatGptConfig.read(Map.of());check(before.current.digest().equals(chat.digest())&&before.previous==null);
        ChatGptConfig.Frame applied=ChatGptConfig.read(ChatGptConfig.replacement(before,edited));check(applied.current.digest().equals(edited.digest())&&applied.previous.digest().equals(chat.digest()));
        check(ChatGptConfig.read(ChatGptConfig.replacement(applied,applied.previous)).current.digest().equals(chat.digest()));
        reject(()->new ChatGptConfig.Options(true,true,400));reject(()->new ChatGptConfig.Options(true,true,2100));reject(()->new ChatGptConfig.Options(true,true,550));
        reject(()->edited.choose("missing"));reject(()->edited.put("bad/profile",hidden));reject(()->edited.override("missing"));
        reject(()->edited.effective("chatgpt","chatgpt.codeblock.copy.probe",ChatGptConfig.TARGET));reject(()->edited.effective("chatgpt",ChatGptConfig.HOOK,"other.app"));
        reject(()->ModuleConfig.parseForModule("wordmaster",bytes(edited.canonical())));reject(()->ModuleConfig.parseForModule("chatgpt",bytes(base.canonical())));
        reject(()->ChatGptConfig.parse(bytes(edited.canonical().replace("\"tap_interval_ms\":1500","\"tap_interval_ms\":1500,\"skip_confirmation\":true"))));
        reject(()->ChatGptConfig.parse(bytes(edited.canonical().replace("\"show_run\":false","\"show_run\":false,\"show_run\":true"))));
        reject(()->ChatGptConfig.read(Map.of(ModuleConfig.CURRENT,edited.canonical())));
        Map<String,String> invalidChat=new TreeMap<>(ChatGptConfig.replacement(before,edited));invalidChat.put(ModuleConfig.GENERATION,"0".repeat(64));reject(()->ChatGptConfig.read(invalidChat));
        check(!new HookSdk.Settings(Map.of(ChatGptConfig.HOOK,true),filter,hidden).chatGptOptions().showRun);
        check(new HookSdk.Settings(Map.of(ChatGptConfig.HOOK,true)).chatGptOptions().tapIntervalMs==500);
        reject(()->new HookSdk.Settings(Map.of("chatgpt.codeblock.copy.probe",true),filter,hidden).chatGptOptions());
        count+=ChatGptOfferChecks.run();
        DiagnosticTimeline t=new DiagnosticTimeline();String sha="a".repeat(64),gen="b".repeat(64);String marker="I/HookLabPack(12345): com.mikeyphw.hooklab: PACK_INSTALLED id=neostore.obtainx.manual module=obtainx version=1.0.0 sha256="+sha+" generation="+gen;
        t.importStream(new ByteArrayInputStream(bytes(marker+"\n"+marker.replace("module=obtainx","module=chatgpt")+"\n"+marker+" token=PRIVATE\n"+marker.replace("version=1.0.0","version=01.0.0"))),"ANDROID_LOGCAT_IMPORT");
        DiagnosticTimeline.Event e=t.filtered(null).get(0);check(e.code.equals("PACK_INSTALLED")&&e.hookId.equals("neostore.obtainx.manual")&&e.moduleId.equals("obtainx")&&e.archiveSha256.equals(sha)&&e.registryGeneration.equals(gen)&&e.correlation.equals("ASSOCIATED"));
        check(t.filtered(null).stream().filter(row->row.code.equals("PACK_INSTALLED")).count()==1);String out=DiagnosticTimeline.toJsonl(t.filtered(null));check(out.contains("\"archive_sha256\":\""+sha+"\"")&&!out.contains("PRIVATE"));System.out.println("MOD03_CONFIG_CHECKS_PASS="+count);
    }
}
