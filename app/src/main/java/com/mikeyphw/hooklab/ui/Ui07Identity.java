package com.mikeyphw.hooklab.ui;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Display-only local package identity. Never authenticates a running hook.
 * The digest is over APK signing certificate bytes (not the APK or a secret).
 */
public final class Ui07Identity {
    private Ui07Identity() { }
    public static String signerFingerprint(byte[] certificate) {
        if(certificate==null || certificate.length==0)return "";
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(certificate);
            StringBuilder text=new StringBuilder(95);
            for(byte value:hash){
                if(text.length()>0)text.append(':');
                text.append(String.format(Locale.ROOT,"%02X",value & 0xFF));
            }
            return text.toString();
        }catch(NoSuchAlgorithmException e){return "";}
    }
    public static String versionDisplay(String name,long code) {
        String safe=name==null||name.trim().isEmpty()?"unknown":name.replaceAll("[^A-Za-z0-9._+-]","_");
        if(safe.length()>80)safe=safe.substring(0,80);
        return safe+" ("+Math.max(0,code)+")";
    }
}
