package com.mikeyphw.hooklab.chatgpt;

/** One composition slot owns one generation. No Activity, global text cache or clipboard. */
final class TermuxOfferGate {
    private Object identity;
    private String source;
    private boolean active,disposed;
    private long lastTap=Long.MIN_VALUE;
    private final int minimumInterval;
    TermuxOfferGate(Object identity,String source){this(identity,source,500);}
    TermuxOfferGate(Object identity,String source,int minimumInterval){
        if(minimumInterval<500||minimumInterval>2000||minimumInterval%100!=0)throw new IllegalArgumentException("Invalid tap interval");
        this.minimumInterval=minimumInterval;
        if(identity==null||source==null)throw new IllegalArgumentException("Missing source binding");
        this.identity=identity;this.source=source;
    }
    synchronized boolean owns(Object object){return !disposed&&identity==object;}
    synchronized void remembered(){if(!disposed)active=true;}
    synchronized void dispose(){active=false;disposed=true;identity=null;source=null;}
    synchronized String selected(long now){
        if(!active||disposed||source==null||now<0||(lastTap!=Long.MIN_VALUE&&(now<lastTap||now-lastTap<minimumInterval)))return null;
        lastTap=now;return source;
    }
}
