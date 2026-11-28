package com.mikeyphw.hooklab.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Typed, restart-bound settings for the exact reviewed ChatGPT Termux handoff.
 * Settings cannot bypass source checks or foreground validation, or change existing execution consent. */
public final class ChatGptConfig {
    public static final String MODULE="chatgpt",HOOK="chatgpt.codeblock.termux.handoff",TARGET="com.openai.chatgpt";
    public static final String GROUP="hooklab.config.chatgpt.v1";
    public static final int MIN_INTERVAL_MS=500,MAX_INTERVAL_MS=2000;
    private ChatGptConfig(){}
    public static final class Options {
        public final boolean showRun,layoutDiagnostics;
        public final int tapIntervalMs;
        public Options(boolean showRun,boolean layoutDiagnostics,int tapIntervalMs){
            ModuleConfig.require(tapIntervalMs>=MIN_INTERVAL_MS&&tapIntervalMs<=MAX_INTERVAL_MS&&tapIntervalMs%100==0);
            this.showRun=showRun;this.layoutDiagnostics=layoutDiagnostics;this.tapIntervalMs=tapIntervalMs;
        }
        String json(){return "{\"show_run\":"+showRun+",\"layout_diagnostics\":"+layoutDiagnostics+",\"tap_interval_ms\":"+tapIntervalMs+"}";}
        static Options read(Object value){Map<?,?> o=ModuleConfig.object(value,"show_run","layout_diagnostics","tap_interval_ms");ModuleConfig.require(o.get("show_run") instanceof Boolean&&o.get("layout_diagnostics") instanceof Boolean&&o.get("tap_interval_ms") instanceof Long);return new Options((Boolean)o.get("show_run"),(Boolean)o.get("layout_diagnostics"),Math.toIntExact((Long)o.get("tap_interval_ms")));}
    }
    public static Options defaultsOptions(){return new Options(true,true,MIN_INTERVAL_MS);}
    public static final class Document implements ModuleConfig.ProfileDocument<Document,Options> {
        private final ModuleConfig.Profiles<Options> data;
        public Document(Map<String,Options> profiles,String active,String override){this(new ModuleConfig.Profiles<>(profiles,active,override));}
        private Document(ModuleConfig.Profiles<Options> data){this.data=data;}
        public String module(){return MODULE;} public Map<String,Options> profiles(){return data.values;} public String active(){return data.active;} public String appOverride(){return data.override;}
        public Options effective(String module,String hook,String pkg){ModuleConfig.require(MODULE.equals(module)&&HOOK.equals(hook)&&TARGET.equals(pkg));return data.effective();}
        public Document put(String profile,Options value){return new Document(data.put(profile,value));}
        public Document choose(String profile){return new Document(data.choose(profile));}
        public Document override(String profile){return new Document(data.override(profile));}
        public Document remove(String profile){return new Document(data.remove(profile));}
        public String canonical(){return data.canonical(MODULE,HOOK,TARGET,Options::json);}
        public String digest(){return ModuleConfig.sha(canonical());}
    }
    public static Document defaults(){return new Document(Collections.singletonMap("default",defaultsOptions()),"default","");}
    public static Document parse(byte[] data){return new Document(ModuleConfig.parseProfiles(data,MODULE,HOOK,TARGET,Options::read));}
    private static Document readCanonical(Object value){String text=ModuleConfig.string(value);Document d=parse(text.getBytes(StandardCharsets.UTF_8));ModuleConfig.require(d.canonical().equals(text));return d;}
    public static final class Frame {
        public final Document current,previous;
        private Frame(Document current,Document previous){this.current=current;this.previous=previous;}
        public boolean same(Frame other){return current.canonical().equals(other.current.canonical())&&(previous==null?other.previous==null:other.previous!=null&&previous.canonical().equals(other.previous.canonical()));}
    }
    public static Frame read(Map<String,?> state){
        ModuleConfig.require(state!=null);if(state.isEmpty())return new Frame(defaults(),null);
        ModuleConfig.require(state.keySet().equals(Set.of(ModuleConfig.CURRENT,ModuleConfig.GENERATION,ModuleConfig.PREVIOUS)));
        Document current=readCanonical(state.get(ModuleConfig.CURRENT));ModuleConfig.require(current.digest().equals(state.get(ModuleConfig.GENERATION)));
        String previous=ModuleConfig.string(state.get(ModuleConfig.PREVIOUS));return new Frame(current,previous.isEmpty()?null:readCanonical(previous));
    }
    public static Map<String,String> replacement(Frame before,Document current){
        ModuleConfig.require(before!=null&&current!=null);Map<String,String> result=new TreeMap<>();result.put(ModuleConfig.CURRENT,current.canonical());result.put(ModuleConfig.GENERATION,current.digest());result.put(ModuleConfig.PREVIOUS,before.current.canonical());return Collections.unmodifiableMap(result);
    }
}
