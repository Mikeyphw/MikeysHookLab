import com.mikeyphw.hooklab.sdk.HookSdk;
import com.mikeyphw.hooklab.sdk.HookSdk.*;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry;
import com.mikeyphw.hooklab.runtime.BuiltinRegistry.RegistryGeneration;
import com.mikeyphw.hooklab.core.*;
import com.mikeyphw.hooklab.probe.ProbeProtocol;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Mod01Checks {
    private static int count;
    private static void check(boolean b,String label){if(!b)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    private static void reject(Runnable r,String label){try{r.run();}catch(RuntimeException expected){check(true,label);return;}throw new AssertionError("accepted "+label);}
    private static final class Port implements RegistryGeneration.Publisher {
        String value; boolean allow=true, readback=true;int writes;
        public String read(){return value;}
        public boolean write(String g){writes++;if(allow&&readback)value=g;return allow;}
    }
    public static void main(String[] args) throws Exception {
        BuiltinRegistry registry=BuiltinRegistry.current();String gen=registry.generation;
        check(registry.hooks().size()==6&&registry.modules().size()==4,"exact cardinality");
        check(registry.hooks().stream().map(h->h.id).toList().equals(List.of("mcc.diamond.control","neostore.obtainx.manual","droidify.obtainx.manual","wordmaster.night.filter","chatgpt.codeblock.copy.probe","chatgpt.codeblock.termux.handoff")),"stable hook ID order");
        check(registry.hook("neostore.obtainx.manual").minVersion==1304&&registry.hook("droidify.obtainx.manual").maxVersion==780&&registry.hook("chatgpt.codeblock.copy.probe").maxVersion==2632128,"exact target guards");
        reject(()->registry.hooks().clear(),"immutable hooks");reject(()->registry.modules().clear(),"immutable modules");
        HookDescriptor h=registry.hook("neostore.obtainx.manual");String pkg=h.packageName;
        check(h.eligible(pkg,pkg,1304,true,true,true),"eligible target");
        check(!h.eligible(pkg,pkg+":remote",1304,true,true,true),"secondary process refused");
        check(!h.eligible(pkg,pkg,1303,true,true,true)&&!h.eligible(pkg,pkg,1305,true,true,true),"version boundaries");
        check(!h.eligible(pkg,pkg,1304,false,true,true)&&!h.eligible(pkg,pkg,1304,true,false,true)&&!h.eligible(pkg,pkg,1304,true,true,false),"scope opt-in safety required");
        byte[] raw=Files.readAllBytes(Path.of(args[0]));String text=new String(raw,StandardCharsets.UTF_8);
        check(BuiltinRegistry.read(raw).generation.equals(gen),"same bytes same generation");
        byte[] mutated=raw.clone();BuiltinRegistry copy=BuiltinRegistry.read(mutated);mutated[0]=0;check(copy.generation.equals(gen),"defensive byte copy");
        reject(()->BuiltinRegistry.read(text.replace("\"sdk_max\": 1","\"sdk_max\": 0").getBytes(StandardCharsets.UTF_8)),"incompatible registry");
        reject(()->BuiltinRegistry.read(text.replace("droidify.obtainx.manual","neostore.obtainx.manual").getBytes(StandardCharsets.UTF_8)),"duplicate hook refused");
        reject(()->BuiltinRegistry.read(text.replace("\"module\": \"mcc\"","\"module\": \"unknown\"").getBytes(StandardCharsets.UTF_8)),"missing module refused");
        Port p=new Port();check(!RegistryGeneration.matches(gen,p),"missing generation");p.value="0".repeat(64);p.allow=false;check(!RegistryGeneration.publish(gen,p),"write failure");p.allow=true;p.readback=false;check(!RegistryGeneration.publish(gen,p),"readback failure");p.readback=true;check(RegistryGeneration.publish(gen,p)&&RegistryGeneration.matches(gen,p),"publish and readback");int writes=p.writes;check(RegistryGeneration.publish(gen,p)&&writes==p.writes,"idempotent publication");check(!RegistryGeneration.matches(gen,()->{throw new IllegalStateException();}),"unavailable transport");
        AtomicInteger countInstall=new AtomicInteger();TweakEngine.Entry<Object> entry=new TweakEngine.Entry<>(new TweakSpec(h.id,pkg,1304,1304),"title","description",c->countInstall.incrementAndGet());List<TweakEngine.Entry<Object>> entries=List.of(entry);
        AtomicInteger calls=new AtomicInteger();HostFlagSnapshot flags=HostFlagSnapshot.capture(entries,pkg,pkg,1304,id->{calls.incrementAndGet();return true;},id->false);
        check(flags.enabled(h.id)&&flags.enabled(h.id)&&calls.get()==1&&!flags.locked(h.id),"immutable snapshot");
        reject(()->flags.enabled("unknown"),"missing setting refused");
        HostFlagSnapshot blocked=HostFlagSnapshot.capture(entries,pkg,pkg,1304,id->{throw new AssertionError("blocked read");},id->true);check(blocked.locked(h.id),"locked flag no opt-in read");
        check(TweakEngine.dispatch(entries,pkg,pkg,1304,flags::enabled,new Object(),(id,e)->{})==1&&countInstall.get()==1,"engine authorized dispatch");
        check(TweakEngine.dispatch(entries,pkg,pkg+":remote",1304,id->{throw new AssertionError("unrelated preference read");},new Object(),(id,e)->{})==0,"engine unrelated process no read");
        Map<String,Boolean> settings=new HashMap<>();settings.put(ProbeProtocol.ID,true);HookSdk.Settings snapshot=new HookSdk.Settings(settings);settings.clear();check(snapshot.enabled(ProbeProtocol.ID)&&!snapshot.enabled("unknown"),"SDK setting snapshot defaults off");
        check(ProbeProtocol.eligible(ProbeProtocol.TARGET,ProbeProtocol.TARGET,1,true,true,true)&&!ProbeProtocol.eligible(ProbeProtocol.TARGET,ProbeProtocol.TARGET,1,true,true,false),"probe target signer gate");
        byte[] dex=new byte[112];System.arraycopy(new byte[]{'d','e','x','\n'},0,dex,0,4);String sha=ProbeProtocol.digest(dex);check(ProbeProtocol.matches(dex,ProbeProtocol.read(new ByteArrayInputStream(dex)),sha),"owned bytes readback");byte[] bad=dex.clone();bad[100]=1;check(!ProbeProtocol.matches(dex,bad,sha),"altered payload refused");
        try{ProbeProtocol.read(new ByteArrayInputStream(new byte[ProbeProtocol.MAX_BYTES+1]));throw new AssertionError("oversized");}catch(IOException expected){check(true,"oversize payload refused");}
        reject(()->ProbeProtocol.file("../file"),"path refused");
        ByteBuffer old=ByteBuffer.wrap(dex).asReadOnlyBuffer();
        try{old.array();throw new AssertionError("old heap buffer unexpectedly accessible");}
        catch(ReadOnlyBufferException expected){check(true,"old Android backing-array failure reproduced");}
        byte[] remote=dex.clone();ByteBuffer direct=ProbeProtocol.verifiedDexBuffer(dex,remote,sha);
        check(direct.isDirect()&&!direct.isReadOnly()&&direct.position()==0&&direct.limit()==dex.length,"ART-compatible private direct buffer bounds");
        remote[0]=0;byte[] copied=new byte[dex.length];direct.get(copied);
        check(Arrays.equals(dex,copied),"loader buffer owns exact verified bytes");
        reject(()->ProbeProtocol.verifiedDexBuffer(dex,remote,sha),"modified transport rejected before buffer allocation");
        reject(()->ProbeProtocol.verifiedDexBuffer(dex,dex,"0".repeat(64)),"wrong digest rejected before buffer allocation");
        try(URLClassLoader loader=new URLClassLoader(new URL[]{Path.of(args[2]).toUri().toURL()},HookSdk.class.getClassLoader())){
            Class<?> type=Class.forName(ProbeProtocol.ENTRY,false,loader);check(type.getClassLoader()==loader&&HookSdk.Entrypoint.class.isAssignableFrom(type),"independent JVM payload SDK identity");
            HookSdk.Entrypoint ep=type.asSubclass(HookSdk.Entrypoint.class).getDeclaredConstructor().newInstance();
            List<HookSdk.Marker> markers=new ArrayList<>();List<HookSdk.Event> events=new ArrayList<>();
            ep.install(new HookSdk.Context(ProbeProtocol.TARGET,ProbeProtocol.TARGET,1,gen,loader,snapshot,events::add,(id,marker)->{if(!ProbeProtocol.ID.equals(id))throw new IllegalArgumentException();markers.add(marker);}));
            check(markers.size()==1&&"MOD01_PROBE_OK".equals(markers.get(0).after("BASE"))&&events.contains(HookSdk.Event.PROBE_READY),"actual isolated benign entrypoint");
            try{ep.install(new HookSdk.Context(ProbeProtocol.TARGET,ProbeProtocol.TARGET,1,gen,loader,new HookSdk.Settings(Map.of()),events::add,(id,marker)->{throw new AssertionError("disabled registration");}));throw new AssertionError("disabled entrypoint allowed");}catch(IllegalArgumentException|IllegalStateException expected){check(true,"disabled payload refused");}
        }
        int vectors=0;
        for(String line:Files.readAllLines(Path.of(args[1],"corpus.tsv"),StandardCharsets.US_ASCII)){
            String[] row=line.split("\t",-1);if(row.length!=3)throw new IllegalArgumentException("vector row");
            boolean accepted;try{ContractReader.validate(Base64.getDecoder().decode(row[2]));accepted=true;}catch(IllegalArgumentException expected){accepted=false;}
            check(accepted==row[0].equals("valid"),"shared "+row[0]+"/"+row[1]);vectors++;
        }check(vectors==33,"vector roster");System.out.println("MOD01_CHECKS_PASS "+count);
    }
}
