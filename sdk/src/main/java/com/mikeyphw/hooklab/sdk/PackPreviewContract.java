package com.mikeyphw.hooklab.sdk;

import java.nio.charset.StandardCharsets;

/** Preview-only boundary shared with the manager; no shell or command execution. */
public final class PackPreviewContract {
    private PackPreviewContract(){}
    public static final String ACTION_PREVIEW="com.mikeyphw.hooklab.termux.PREVIEW";
    public static final String EXTRA_SOURCE="com.mikeyphw.hooklab.termux.SOURCE";
    public static final String EXTRA_REQUEST_ID="com.mikeyphw.hooklab.termux.REQUEST_ID";
    public static final String EXTRA_REQUEST_TIME="com.mikeyphw.hooklab.termux.REQUEST_TIME";
    public static final String EXTRA_REQUEST_WALL="com.mikeyphw.hooklab.termux.REQUEST_WALL";
    public static final int MAX_BYTES=48*1024;
    public static String checkedCommand(String text) {
        if(text==null || text.length()>MAX_BYTES || text.trim().isEmpty())throw new IllegalArgumentException("Empty or oversized command");
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException("Invalid Unicode");
            } else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode");
            if((Character.isISOControl(c)&&c!='\n'&&c!='\t')||Character.getType(c)==Character.FORMAT)
                throw new IllegalArgumentException("Unsupported invisible character");
        }
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Command exceeds 48 KiB UTF-8 limit");
        return text;
    }
}
