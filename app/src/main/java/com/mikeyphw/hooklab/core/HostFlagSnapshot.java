package com.mikeyphw.hooklab.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Captured once per verified host dispatch, never exposing preferences or an editor. */
public final class HostFlagSnapshot {
    private static final class Flag {
        final Boolean value;
        final Throwable failure;
        Flag(Boolean value, Throwable failure) { this.value = value; this.failure = failure; }
        boolean value() {
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
            if (value == null) throw new IllegalStateException("unavailable preference");
            return value;
        }
    }
    private final Map<String,Flag> enabled, locked;
    private HostFlagSnapshot(Map<String,Flag> enabled, Map<String,Flag> locked) {
        this.enabled = Collections.unmodifiableMap(new HashMap<>(enabled));
        this.locked = Collections.unmodifiableMap(new HashMap<>(locked));
    }
    private static Flag read(TweakEngine.OptIn reader,String id) {
        try { return new Flag(reader.enabled(id),null); }
        catch (RuntimeException | LinkageError failure) { return new Flag(null,failure); }
    }
    public static HostFlagSnapshot capture(List<? extends TweakEngine.Entry<?>> entries, String pkg,String process,long version,
                                           TweakEngine.OptIn enabled,TweakEngine.OptIn locked) {
        Map<String,Flag> flags=new HashMap<>(), safety=new HashMap<>();
        for (TweakEngine.Entry<?> e:entries) {
            if (!e.spec.eligible(pkg,process,version,true)) continue;
            String id=e.spec.id; Flag lock=read(locked,id); safety.put(id,lock);
            if(Boolean.FALSE.equals(lock.value)) flags.put(id,read(enabled,id));
        }
        return new HostFlagSnapshot(flags,safety);
    }
    private static boolean get(Map<String,Flag> map,String id) {
        Flag flag=map.get(id); if(flag==null) throw new IllegalStateException("preference not captured"); return flag.value();
    }
    public boolean enabled(String id) { return get(enabled,id); }
    public boolean locked(String id) { return get(locked,id); }
}
