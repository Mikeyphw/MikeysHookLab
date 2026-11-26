package com.mikeyphw.hooklab.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** Process-owned single-flight ledger. A stopped Activity cannot release an uncertain command. */
public final class PendingFrameworkCommands {
    public static final class Ticket {
        private final String key;
        private Ticket(String key){this.key=key;}
    }
    private final Map<String,Ticket> active=new HashMap<>();
    public synchronized Ticket begin(String key){
        if(key==null||key.isEmpty()||active.containsKey(key))return null;
        Ticket t=new Ticket(key);active.put(key,t);return t;
    }
    public synchronized boolean complete(Ticket ticket){
        if(ticket==null||active.get(ticket.key)!=ticket)return false;
        active.remove(ticket.key);return true;
    }
    public synchronized boolean contains(String key){return active.containsKey(key);}
    public synchronized Set<String> keys(String prefix){
        Set<String> keys=new HashSet<>();for(String key:active.keySet())if(key.startsWith(prefix))keys.add(key.substring(prefix.length()));
        return Collections.unmodifiableSet(keys);
    }
}
