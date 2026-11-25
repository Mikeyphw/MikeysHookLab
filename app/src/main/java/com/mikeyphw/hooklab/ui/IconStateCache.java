package com.mikeyphw.hooklab.ui;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Access-ordered, bounded positive cache; package-version/update/config changes invalidate the key. */
final class IconStateCache<T> {
    private final int limit;
    private final LinkedHashMap<String,Slot<T>> values=new LinkedHashMap<>(16,0.75f,true);
    private static final class Slot<T>{final long version,updated;final T value;Slot(long v,long u,T t){version=v;updated=u;value=t;}}
    IconStateCache(int limit){if(limit<1||limit>128)throw new IllegalArgumentException("cache limit");this.limit=limit;}
    T get(String pkg,long version,long updated){Slot<T> slot=values.get(pkg);if(slot==null)return null;if(slot.version!=version||slot.updated!=updated){values.remove(pkg);return null;}return slot.value;}
    void put(String pkg,long version,long updated,T value){values.put(Objects.requireNonNull(pkg),new Slot<>(version,updated,Objects.requireNonNull(value)));while(values.size()>limit){java.util.Iterator<String> it=values.keySet().iterator();it.next();it.remove();}}
    void invalidate(String pkg){values.remove(pkg);}
    void clear(){values.clear();}
    int size(){return values.size();}
}
