package com.mikeyphw.hooklab.obtainx;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure policy adapted from NeoStoreObtainX PR #12's SourceMapper, ProbePolicy,
 * and ObtainXSeedPolicy. Host metadata is read-only and checked again on tap.
 * This class does not create Intents, call a network API or modify an app.
 */
public final class SourcePolicy {
    private SourcePolicy() { }
    public static final class Snapshot {
        public final String packageName;
        public final String repositoryAddress;
        public final String preferredUrl;
        public final String overrideSource;
        public final String productSourceSite;
        private Snapshot(String pkg, String repo, String url, String override, String source) {
            packageName=pkg; repositoryAddress=repo; preferredUrl=url;
            overrideSource=override; productSourceSite=source;
        }
        @Override public boolean equals(Object object) {
            if (!(object instanceof Snapshot)) return false;
            Snapshot s=(Snapshot)object;
            return Objects.equals(packageName,s.packageName) &&
                Objects.equals(repositoryAddress,s.repositoryAddress) &&
                Objects.equals(preferredUrl,s.preferredUrl) &&
                Objects.equals(overrideSource,s.overrideSource) &&
                Objects.equals(productSourceSite,s.productSourceSite);
        }
        @Override public int hashCode() {
            return Objects.hash(packageName,repositoryAddress,preferredUrl,overrideSource,productSourceSite);
        }
    }
    private static final class Mapping {
        final String url, override;
        Mapping(String u, String o) { url=u;override=o; }
    }
    public static boolean packageId(String id) {
        return id!=null && id.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
            && id.length()<=255;
    }
    private static boolean unsafe(String text) {
        if (text==null || text.isEmpty() || text.length()>2048) return true;
        for (int i=0;i<text.length();i++) if (Character.isISOControl(text.charAt(i))) return true;
        return false;
    }
    private static URI cleanHttps(String raw) {
        if (unsafe(raw)) return null;
        try {
            URI u=new URI(raw.trim());
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null ||
                u.getRawUserInfo()!=null || u.getRawQuery()!=null ||
                u.getRawFragment()!=null || u.isOpaque()) return null;
            if (u.getPort() < -1 || u.getPort() > 65535) return null;
            return u;
        } catch (Exception error) { return null; }
    }
    private static String trimTrailingSlash(String path) {
        while (path.endsWith("/") && !path.isEmpty()) path=path.substring(0,path.length()-1);
        return path;
    }
    private static Mapping mapStore(String pkg, String repository) {
        if (!packageId(pkg)) return null;
        URI uri=cleanHttps(repository);
        if (uri==null) return null;
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        String path=trimTrailingSlash(uri.getRawPath()==null?"":uri.getRawPath());
        if (path.endsWith("/index.xml")) path=path.substring(0,path.length()-10);
        boolean standardPort=uri.getPort()==-1 || uri.getPort()==443;
        String id=URLEncoder.encode(pkg,StandardCharsets.UTF_8);
        if (host.equals("f-droid.org") || host.equals("www.f-droid.org")) {
            if (!path.equals("/repo") || !standardPort) return null;
            return new Mapping("https://f-droid.org/packages/"+id,null);
        }
        if (host.equals("apt.izzysoft.de")) {
            if (!path.equals("/fdroid/repo") || !standardPort) return null;
            return new Mapping("https://apt.izzysoft.de/fdroid/index/apk/"+id,"IzzyOnDroid");
        }
        // Custom F-Droid repository URLs retain their exact HTTPS origin/port/path.
        // Reject path traversal and encoded separators before construction.
        if (path.contains("%") || path.contains("\\") || path.contains("/../") ||
            path.endsWith("/..") || path.contains("/./") || path.endsWith("/.")) return null;
        String port=uri.getPort()==-1?"":":"+uri.getPort();
        return new Mapping("https://"+host+port+path+"?appId="+id,"FDroidRepo");
    }
    private static String upstream(String raw) {
        URI uri=cleanHttps(raw);
        if (uri==null || (uri.getPort()!=-1 && uri.getPort()!=443)) return null;
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        String rawPath=uri.getRawPath()==null?"":uri.getRawPath();
        String[] bits=rawPath.split("/");
        List<String> segments=new ArrayList<>();
        for (String p:bits) {
            if (p.isEmpty()) continue;
            if (p.equals(".") || p.equals("..") || p.contains("%") ||
                !p.matches("[A-Za-z0-9_.~-]+")) return null;
            segments.add(p);
        }
        boolean two=segments.size()==2 && !segments.contains("issues") &&
            !segments.contains("pull") && !segments.contains("tree") && !segments.contains("blob");
        boolean valid;
        switch (host) {
            case "github.com": case "codeberg.org": valid=two;break;
            case "gitlab.com":
                valid=segments.size()>=2 && segments.size()<=7 &&
                    Collections.disjoint(segments,Arrays.asList("-","issues","merge_requests","tree","blob"));
                break;
            case "git.sr.ht": valid=segments.size()==2 && segments.get(0).startsWith("~");break;
            case "sourceforge.net": case "www.sourceforge.net":
                valid=segments.size()==2 && segments.get(0).equals("projects");break;
            default: valid=false;
        }
        if (!valid) return null;
        return "https://"+host+"/"+String.join("/",segments);
    }
    /** All version/source/route validation must be done by the host probe first. */
    public static Snapshot ready(String active, String viewModel, String product,
                                 Long productRepoId, Long repoId, String repo,
                                 String auth, String source) {
        if (!packageId(active) || !active.equals(viewModel) || !active.equals(product)
            || productRepoId==null || repoId==null || !productRepoId.equals(repoId)
            || auth==null || !auth.isEmpty()) return null;
        Mapping map=mapStore(active,repo);
        if (map==null) return null;
        String preferred=upstream(source);
        if (preferred!=null) return new Snapshot(active,repo,preferred,null,source);
        return new Snapshot(active,repo,map.url,map.override,source);
    }
    public static boolean same(Snapshot shown, Snapshot live) {
        return shown!=null && shown.equals(live) && prepare(live,live.packageName)!=null;
    }
    public static String prepare(Snapshot snapshot, String liveRoute) {
        if (snapshot==null || !packageId(snapshot.packageName) ||
            !snapshot.packageName.equals(liveRoute)) return null;
        Snapshot again=ready(snapshot.packageName,snapshot.packageName,snapshot.packageName,
            1L,1L,snapshot.repositoryAddress,"",snapshot.productSourceSite);
        if (!snapshot.equals(again) || unsafe(snapshot.preferredUrl)) return null;
        String settings=(snapshot.overrideSource==null)?"{}":
            "{\"appIdOrName\":"+quote(snapshot.packageName)+"}";
        StringBuilder json=new StringBuilder("{\"id\":").append(quote(snapshot.packageName));
        json.append(",\"url\":").append(quote(snapshot.preferredUrl));
        json.append(",\"author\":\"\",\"name\":").append(quote(snapshot.packageName));
        json.append(",\"latestVersion\":\"\",\"preferredApkIndex\":-1");
        json.append(",\"additionalSettings\":").append(quote(settings));
        if (snapshot.overrideSource!=null) {
            if (!snapshot.overrideSource.equals("FDroidRepo") &&
                !snapshot.overrideSource.equals("IzzyOnDroid")) return null;
            json.append(",\"overrideSource\":").append(quote(snapshot.overrideSource));
        }
        return json.append('}').toString();
    }
    public static String quote(String input) {
        StringBuilder out=new StringBuilder("\"");
        for (int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            switch (c) {
                case '"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                case '\b':out.append("\\b");break;
                case '\f':out.append("\\f");break;
                default:
                    if (c<0x20 || c==0x2028 || c==0x2029)
                        out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
