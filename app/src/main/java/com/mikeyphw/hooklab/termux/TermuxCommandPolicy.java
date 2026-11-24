package com.mikeyphw.hooklab.termux;

import java.nio.charset.StandardCharsets;

/** TB01 command validation, extended for the native inline action. Never executes code. */
public final class TermuxCommandPolicy {
    public static final String HOME="/data/data/com.termux/files/home";
    public static final String PREFIX="/data/data/com.termux/files/usr/bin/";
    public static final String PERMISSION="com.termux.permission.RUN_COMMAND";
    public static final String ACTION_PREVIEW="com.mikeyphw.hooklab.termux.PREVIEW";
    public static final String ACTION_SETTINGS="com.mikeyphw.hooklab.termux.SETTINGS";
    public static final String EXTRA_SOURCE="com.mikeyphw.hooklab.termux.SOURCE";
    public static final int MAX_BYTES=48*1024;
    private TermuxCommandPolicy() {}
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
    public static String checkedWorkingDirectory(String input) {
        if(input==null||input.trim().isEmpty()||input.trim().equals("~"))return HOME;
        String p=input.trim();if(p.startsWith("~/"))p=HOME+p.substring(1);
        if(!(p.equals(HOME)||p.startsWith(HOME+"/"))||p.length()>1024)throw new IllegalArgumentException("Working directory must be under Termux home");
        for(int i=0;i<p.length();i++)if(Character.isISOControl(p.charAt(i))||Character.getType(p.charAt(i))==Character.FORMAT)throw new IllegalArgumentException("Invalid working directory");
        if(!p.equals(HOME))for(String part:p.substring(HOME.length()+1).split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IllegalArgumentException("Invalid working directory segment");
        return p;
    }
    public static String checkedShell(String name) {
        if(!"zsh".equals(name)&&!"bash".equals(name)&&!"sh".equals(name)&&!"python3".equals(name))throw new IllegalArgumentException("Unsupported interpreter");
        return name;
    }
    public static String[] arguments(String source,String shell) {
        checkedShell(shell);return new String[]{"-c",checkedCommand(source)};
    }
}
