package com.mikeyphw.hooklab.chatgpt;
/** Production per-composition debounce regression, runnable without Android. */
public final class ChatGptOfferChecks {
    private ChatGptOfferChecks(){}
    private static void check(boolean condition){if(!condition)throw new AssertionError("ChatGPT offer gate");}
    public static int run(){
        Object key=new Object();TermuxOfferGate gate=new TermuxOfferGate(key,"echo fixture",1500);
        check(gate.selected(0)==null);gate.remembered();check("echo fixture".equals(gate.selected(0)));check(gate.selected(500)==null);check(gate.selected(1499)==null);check("echo fixture".equals(gate.selected(1500)));check(gate.selected(1000)==null);gate.dispose();check(gate.selected(3000)==null);gate.remembered();check(gate.selected(4500)==null);
        try{new TermuxOfferGate(key,"echo fixture",400);throw new AssertionError("weaker interval accepted");}catch(IllegalArgumentException expected){}
        return 9;
    }
}
