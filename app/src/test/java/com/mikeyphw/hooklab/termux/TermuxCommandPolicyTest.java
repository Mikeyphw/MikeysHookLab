package com.mikeyphw.hooklab.termux;
import org.junit.Test;
import static org.junit.Assert.*;
public final class TermuxCommandPolicyTest {
 @Test public void exactMultilineArguments(){String s="printf '%s\\n' 'a,b'\n  echo '💡'\n";for(String sh:new String[]{"zsh","bash","sh","python3"}){String[] a=TermuxCommandPolicy.arguments(s,sh);assertEquals(2,a.length);assertEquals("-c",a[0]);assertEquals(s,a[1]);}}
 @Test public void directoryRules(){assertEquals(TermuxCommandPolicy.HOME,TermuxCommandPolicy.checkedWorkingDirectory("~"));assertEquals(TermuxCommandPolicy.HOME+"/Code",TermuxCommandPolicy.checkedWorkingDirectory("~/Code"));for(String s:new String[]{"~/../other","~/a//b","/data/data/com.termux/files/home2","/sdcard/Download"}){try{TermuxCommandPolicy.checkedWorkingDirectory(s);fail();}catch(IllegalArgumentException expected){}}}
 @Test public void rejectsNonReviewedInputs(){for(String s:new String[]{""," \n","a\u0000b","a\u202eb","a\ud800","x".repeat(TermuxCommandPolicy.MAX_BYTES+1)}){try{TermuxCommandPolicy.checkedCommand(s);fail();}catch(IllegalArgumentException expected){}}try{TermuxCommandPolicy.checkedShell("/system/bin/su");fail();}catch(IllegalArgumentException expected){}}
}
