package com.mikeyphw.hooklab.chatgpt;

/** One expiring, one-use affordance; only numeric identity, never clipboard text. */
final class TermuxOfferGate {
    static final long TTL_MS=12000L;
    private long generation,action,created;
    synchronized long replace(long actionId,long now){generation++;action=actionId>0?actionId:0;created=now;return generation;}
    synchronized boolean current(long token){return token==generation;}
    synchronized long available(long token,long now){return current(token)&&action>0&&now>=created&&now-created<=TTL_MS?action:0;}
    synchronized long consume(long token,long now){long result=available(token,now);if(result!=0){action=0;generation++;}return result;}
    synchronized void clear(long token){if(current(token)){action=0;generation++;}}
    synchronized void invalidate(){action=0;generation++;}
}
