package com.mikeyphw.hooklab.sdk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Bounded, verified pack resource namespace; no extraction, Android resource IDs or executable assets. */
public final class ModuleUiResources {
    public static final int MAX_BYTES=65536, MAX_ASSET=16384;
    private final String module;
    private final Map<String,Map<String,String>> locales;
    private final Map<String,Asset> assets;
    private ModuleUiResources(String module,Map<String,Map<String,String>> locales,Map<String,Asset> assets){this.module=module;this.locales=Collections.unmodifiableMap(locales);this.assets=Collections.unmodifiableMap(assets);}
    private static void require(boolean ok){if(!ok)throw new IllegalArgumentException("invalid module UI resources");}
    private static Map<?,?> map(Object v){require(v instanceof Map<?,?>);return (Map<?,?>)v;}
    private static String string(Object v){require(v instanceof String);return (String)v;}
    private static String name(Object v){String s=string(v);require(s.matches("[a-z][a-z0-9_.-]{0,63}")&&!s.contains(".."));return s;}
    private static String text(Object v){String s=string(v);require(!s.isEmpty()&&s.getBytes(StandardCharsets.UTF_8).length<=512&&s.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'));return s;}
    public static final class Asset {
        public final String type;private final byte[] bytes;
        private Asset(String type,byte[] bytes){this.type=type;this.bytes=bytes.clone();}
        public byte[] bytes(){return bytes.clone();}
    }
    public static ModuleUiResources empty(String module){require(module!=null&&module.matches("[a-z][a-z0-9_]{0,63}"));return new ModuleUiResources(module,Collections.emptyMap(),Collections.emptyMap());}
    public static ModuleUiResources parse(String module,byte[] raw){
        require(raw!=null&&raw.length>0&&raw.length<=MAX_BYTES&&module!=null&&module.matches("[a-z][a-z0-9_]{0,63}"));Map<?,?> root=map(HookSdk.StrictJson.read(raw));
        require(root.keySet().equals(Set.of("schema","module_id","locales","assets"))&&"hooklab.module.ui-resources.v1".equals(root.get("schema"))&&module.equals(root.get("module_id")));
        Map<?,?> languages=map(root.get("locales")),files=map(root.get("assets"));require(languages.size()<=16&&files.size()<=16&&languages.containsKey("en"));
        Map<String,Map<String,String>> translations=new TreeMap<>();
        for(Map.Entry<?,?> row:languages.entrySet()){String lang=string(row.getKey());require(lang.matches("[a-z]{2,3}(-[A-Z]{2})?"));Map<?,?> entries=map(row.getValue());require(entries.size()<=64);Map<String,String> values=new TreeMap<>();for(Map.Entry<?,?> e:entries.entrySet())values.put(name(e.getKey()),text(e.getValue()));translations.put(lang,Collections.unmodifiableMap(values));}
        for(Map<String,String> values:translations.values())require(translations.get("en").keySet().containsAll(values.keySet()));
        Map<String,Asset> assets=new TreeMap<>();
        for(Map.Entry<?,?> row:files.entrySet()){Map<?,?> f=map(row.getValue());require(f.keySet().equals(Set.of("type","data")));String type=string(f.get("type")),encoded=string(f.get("data"));byte[] bytes=Base64.getDecoder().decode(encoded);require(bytes.length>0&&bytes.length<=MAX_ASSET&&Base64.getEncoder().encodeToString(bytes).equals(encoded));
            if("text/plain".equals(type)){try{String value=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();require(value.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'));}catch(java.nio.charset.CharacterCodingException bad){throw new IllegalArgumentException("invalid text asset",bad);}}
            else if("image/png".equals(type)){byte[] header={(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82};require(bytes.length>=33&&Arrays.equals(header,Arrays.copyOf(bytes,16)));ByteBuffer b=ByteBuffer.wrap(bytes);int w=b.getInt(16),h=b.getInt(20);require(w>0&&h>0&&w<=512&&h<=512);}
            else throw new IllegalArgumentException("unsupported UI asset type");assets.put(name(row.getKey()),new Asset(type,bytes));}
        return new ModuleUiResources(module,translations,assets);
    }
    public String moduleId(){return module;}
    public String translation(String namespace,String key,Locale locale,String fallback){require(module.equals(namespace)&&locale!=null);name(key);String language=locale.getLanguage(),country=locale.getCountry();for(String tag:new String[]{language+(country.isEmpty()?"":"-"+country),language,"en"}){Map<String,String> values=locales.get(tag);if(values!=null&&values.containsKey(key))return values.get(key);}return fallback;}
    public Asset asset(String namespace,String key){require(module.equals(namespace));return assets.get(name(key));}
}
