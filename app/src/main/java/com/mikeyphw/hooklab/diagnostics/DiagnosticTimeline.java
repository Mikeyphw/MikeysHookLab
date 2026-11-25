package com.mikeyphw.hooklab.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, in-memory, imported-evidence-only diagnostics. Never captures other
 * applications' logs implicitly and never persists arbitrary raw log lines.
 */
public final class DiagnosticTimeline {
    public static final int MAX_BYTES=2*1024*1024, MAX_LINES=10000, MAX_EVENTS=4000;
    private static final Pattern ISO=Pattern.compile("\\b(20[0-9]{2}-[0-9]{2}-[0-9]{2}T[0-9:.]+(?:Z|[+-][0-9:]+))\\b");
    private static final Pattern HOOK=Pattern.compile("(?:^|\\s)event=([A-Z][A-Z0-9_]{1,63})(?:\\b|$)");
    private static final Pattern PREFIX=Pattern.compile("(password|token|authorization|bearer|cookie|secret)",Pattern.CASE_INSENSITIVE);
    private final List<Event> events=new ArrayList<>();
    private long serial;
    private int dropped;

    public static final class Event {
        public final String source, collector, fileSha256, code, severity, hookId, packageName;
        public final String correlation, message, timestamp;
        public final long utcMillis, ordinal;
        public final int lineNumber;
        private Event(String source,String collector,String sha,String code,String severity,
                String hookId,String pkg,String corr,String message,String timestamp,
                long utc,long ordinal,int line) {
            this.source=source;this.collector=collector;this.fileSha256=sha;this.code=code;
            this.severity=severity;this.hookId=hookId;this.packageName=pkg;
            this.correlation=corr;this.message=message;this.timestamp=timestamp;
            this.utcMillis=utc;this.ordinal=ordinal;this.lineNumber=line;
        }
        public String id() {return fileSha256+":"+lineNumber+":"+ordinal;}
    }
    public static final class Filter {
        public String query="",source="",severity="",packageName="",hookId="";
        public long afterUtc=0,beforeUtc=0;
        public Set<String> groupHooks=Collections.emptySet();
        public Filter copy(){Filter f=new Filter();f.query=query;f.source=source;f.severity=severity;
            f.packageName=packageName;f.hookId=hookId;f.afterUtc=afterUtc;f.beforeUtc=beforeUtc;
            f.groupHooks=Collections.unmodifiableSet(new LinkedHashSet<>(groupHooks));return f;}
        public int activeCount(){return (query.trim().isEmpty()?0:1)+(source.isEmpty()?0:1)+(severity.isEmpty()?0:1)
            +(packageName.isEmpty()?0:1)+(hookId.isEmpty()?0:1)+(afterUtc==0?0:1)+(beforeUtc==0?0:1)+(groupHooks.isEmpty()?0:1);}

    }
    public static final class ImportReceipt {
        public final String sha256,source;
        public final int accepted,lines,dropped;
        private ImportReceipt(String sha,String source,int accepted,int lines,int dropped){
            this.sha256=sha;this.source=source;this.accepted=accepted;this.lines=lines;this.dropped=dropped;
        }
    }
    private static final Set<String> SOURCES;
    static {
        Set<String> set=new LinkedHashSet<>();
        set.add("LSPOSED_MODULE");set.add("LSPOSED_FRAMEWORK");
        set.add("ANDROID_LOGCAT_IMPORT");
        SOURCES=Collections.unmodifiableSet(set);
    }
    public static boolean acceptedSource(String name){return SOURCES.contains(name);}
    private static String sha(byte[] value){
        try {byte[] h=MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder sb=new StringBuilder(64);
            for(byte b:h)sb.append(String.format(Locale.ROOT,"%02x",b & 255));
            return sb.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    /** Atomic bounded import. Never mutate existing events for malformed/oversized input. */
    public ImportReceipt importStream(InputStream stream,String source) throws IOException {
        if(!acceptedSource(source) || stream==null)throw new IllegalArgumentException("Unapproved collector");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        byte[] chunk=new byte[8192];int n;
        while((n=stream.read(chunk))!=-1){
            if(n==0)continue;
            if(buffer.size()+n>MAX_BYTES)throw new IOException("DX01_IMPORT_EXCEEDS_LIMIT");
            buffer.write(chunk,0,n);
        }
        byte[] raw=buffer.toByteArray();
        String digest=sha(raw);String decoded=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(raw)).toString();
        String[] lines=decoded.split("\\r?\\n",-1);
        if(lines.length>MAX_LINES)throw new IOException("DX01_TOO_MANY_LINES");
        synchronized(this){
        int accepted=0, lost=0;
        for(int i=0;i<lines.length;i++){
            if(lines[i].isEmpty())continue;
            Event event=normalize(lines[i],source,digest,i+1,++serial);
            if(events.size()==MAX_EVENTS){events.remove(0);lost++;dropped++;}
            events.add(event);accepted++;
        }
        return new ImportReceipt(digest,source,accepted,lines.length,lost);
        }
    }
    private static Event normalize(String raw,String source,String digest,int line,long ordinal){
        Matcher tm=ISO.matcher(raw);String timestamp="";long millis=0;
        if(tm.find()){
            try {timestamp=tm.group(1);millis=Instant.parse(timestamp).toEpochMilli();}
            catch(DateTimeParseException ignored){timestamp="";millis=0;}
        }
        String code="FRAMEWORK_LINE",hook="",pkg="",confidence="UNCLASSIFIED";
        String severity="INFO";
        String upper=raw.toUpperCase(Locale.ROOT);
        if(upper.contains("ERROR")||upper.contains("EXCEPTION"))severity="ERROR";
        else if(upper.contains("WARN"))severity="WARN";
        Matcher event=HOOK.matcher(raw);
        if(raw.contains("HookLabVerbose")&&event.find()){
            String candidate=event.group(1);
            if(knownEvent(candidate)){
                code=candidate;confidence="ASSOCIATED";
                if(code.startsWith("NEO_")){hook="neostore.obtainx.manual";pkg="com.machiav3lli.fdroid";}
                else if(code.startsWith("DROID_")){hook="droidify.obtainx.manual";pkg="com.looker.droidify";}
                else if(code.startsWith("MCC_")){hook="mcc.diamond.control";pkg="br.art.code.meucronogramacapilar";}
            }
        } else if(upper.contains("EXCEPTION")){code="EXCEPTION_REPORTED";}
        else if(upper.contains("SCOPE")){code="SCOPE_REPORTED";}
        // Never keep raw third-party text, tokens, paths, URLs, account or clipboard data.
        String message=code.equals("FRAMEWORK_LINE")?"Unclassified imported log line (contents withheld)":
            "Reported diagnostic code: "+code;
        return new Event(source,"SAF_IMPORT",digest,code,severity,hook,pkg,confidence,
                message,timestamp,millis,ordinal,line);
    }
    /** Reviewed fixed event-code vocabulary from HookLab's opt-in VerboseLogs. */
    private static boolean knownEvent(String c){
        return c.matches("(?:HOST_DISPATCH_READY|TWEAK_DISPATCH_OUTCOME|MCC_(?:HOOK_INSTALLED|BUTTON_ATTACHED|CONTROL_OPEN|CONTROL_OPEN_REFUSED)|NEO_(?:HOOK_INSTALLED|PAGE_READY|PAGE_WAITING|POLL_REFUSED|TAP|TAP_REFUSED|PREVIEW_REQUESTED|PREVIEW_REFUSED)|DROID_(?:HOOK_INSTALLED|ATTACH_ATTEMPT|TOOLBAR_ADDED|TOOLBAR_MISSING|TAP|SOURCE_REFUSED|PREVIEW_REQUESTED|PREVIEW_REFUSED))");
    }
    public synchronized List<Event> filtered(Filter filter){
        Filter f=filter==null?new Filter():filter;
        List<Event> result=new ArrayList<>();
        for(Event e:events){
            if(!f.source.isEmpty()&&!f.source.equals(e.source))continue;
            if(!f.severity.isEmpty()&&!f.severity.equals(e.severity))continue;
            if(!f.packageName.isEmpty()&&!f.packageName.equals(e.packageName))continue;
            if(!f.hookId.isEmpty()&&!f.hookId.equals(e.hookId))continue;
            if(!f.groupHooks.isEmpty()&&!f.groupHooks.contains(e.hookId))continue;
            if(f.afterUtc>0 && (e.utcMillis==0||e.utcMillis<f.afterUtc))continue;
            if(f.beforeUtc>0 && (e.utcMillis==0||e.utcMillis>f.beforeUtc))continue;
            String q=f.query==null?"":f.query.trim().toLowerCase(Locale.ROOT);
            if(!q.isEmpty()&&!((e.code+" "+e.message+" "+e.packageName+" "+e.hookId+" "+e.source).toLowerCase(Locale.ROOT).contains(q)))continue;
            result.add(e);
        }
        // Unknown source clocks remain in stable import order, not fabricated wall-time order.
        result.sort(Comparator.<Event>comparingLong(e->e.utcMillis==0?Long.MAX_VALUE:e.utcMillis)
                .thenComparingLong(e->e.ordinal));
        return Collections.unmodifiableList(result);
    }
    public synchronized Event find(String id){
        if(id==null)return null;for(Event e:events)if(e.id().equals(id))return e;return null;
    }
    /** Snapshot the exact selection and retention count under the same store lock. */
    public synchronized Selection select(Filter filter){return new Selection(filtered(filter),dropped,events.size());}
    public synchronized Selection selectEvent(String id){Event e=find(id);return e==null?null:new Selection(Collections.singletonList(e),dropped,events.size());}
    public static final class Selection {
        public final List<Event> events;public final int dropped,total;
        private Selection(List<Event> events,int dropped,int total){this.events=Collections.unmodifiableList(new ArrayList<>(events));this.dropped=dropped;this.total=total;}
        public String jsonl(){return toJsonl(events);}
    }
    public synchronized int size(){return events.size();}
    public synchronized int dropped(){return dropped;}
    public static String json(String s){
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            if(ch=='"'||ch=='\\')out.append('\\').append(ch);
            else if(ch=='\n')out.append("\\n");
            else if(ch=='\r')out.append("\\r");
            else if(ch=='\t')out.append("\\t");
            else if(ch<32)out.append(' ');else out.append(ch);
        }return out.append('"').toString();
    }
    public static String toJson(Event e){
        return "{\"schema_version\":1,\"event_id\":"+json(e.id())+
          ",\"source\":"+json(e.source)+",\"collector\":"+json(e.collector)+
          ",\"import_file_sha256\":"+json(e.fileSha256)+
          ",\"line\":"+e.lineNumber+",\"timestamp\":"+json(e.timestamp)+
          ",\"utc_millis\":"+e.utcMillis+",\"severity\":"+json(e.severity)+
          ",\"event_code\":"+json(e.code)+",\"message_redacted\":"+json(e.message)+
          ",\"hook_id\":"+json(e.hookId)+",\"package\":"+json(e.packageName)+
          ",\"correlation\":"+json(e.correlation)+"}";
    }
    public static String toJsonl(List<Event> events){
        StringBuilder sb=new StringBuilder();for(Event e:events)sb.append(toJson(e)).append('\n');
        return sb.toString();
    }
    public static String sha256(byte[] data){return sha(data);}
}
