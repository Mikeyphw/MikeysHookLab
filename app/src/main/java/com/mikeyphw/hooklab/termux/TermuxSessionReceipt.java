package com.mikeyphw.hooklab.termux;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import com.mikeyphw.hooklab.R;
import java.util.Map;

/** One-shot, private Termux session-close correlation. Never reads or saves the transcript. */
public final class TermuxSessionReceipt extends BroadcastReceiver {
    private static final String ACTION="com.mikeyphw.hooklab.termux.SESSION_RESULT",PREFS="termux_session_receipts";
    private static final String PREFIX="session:",TIME="time:";
    private static final long MAX_PENDING_AGE_MS=7L*24*60*60*1000;
    private static SharedPreferences ledger(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static boolean active(String s){return "DISPATCHING".equals(s)||"SUBMITTED".equals(s)||"UNCERTAIN".equals(s);}
    static PendingIntent begin(Context c,String id){
        SharedPreferences p=ledger(c);SharedPreferences.Editor edit=p.edit();int outstanding=0;long now=System.currentTimeMillis();
        for(Map.Entry<String,?> e:p.getAll().entrySet())if(e.getKey().startsWith(PREFIX)){
            if(e.getKey().equals(PREFIX+id))return null;
            String old=e.getKey().substring(PREFIX.length());long stamp=p.getLong(TIME+old,0);
            if(e.getValue() instanceof String&&active((String)e.getValue())&&stamp>0&&(now<stamp||now-stamp<=MAX_PENDING_AGE_MS))outstanding++;
            else {edit.remove(e.getKey());edit.remove(TIME+old);}
        }
        if(outstanding>=128||!edit.putString(PREFIX+id,"DISPATCHING").putLong(TIME+id,now).putString("latest","DISPATCHING").commit())return null;
        Intent result=new Intent(c,TermuxSessionReceipt.class).setAction(ACTION).setData(Uri.parse("hooklab-session:"+id));
        return PendingIntent.getBroadcast(c,0,result,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_MUTABLE);
    }
    private static void transition(Context c,String id,String state){
        SharedPreferences p=ledger(c);
        if(active(p.getString(PREFIX+id,"")))p.edit().putString(PREFIX+id,state).putString("latest",state).commit();
    }
    static void submitted(Context c,String id){transition(c,id,"SUBMITTED");}
    static void refused(Context c,String id){transition(c,id,"DISPATCH_REFUSED");}
    static void uncertain(Context c,String id){transition(c,id,"UNCERTAIN");}
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction())||intent.getData()==null||!"hooklab-session".equals(intent.getData().getScheme()))return;
        String id=intent.getData().getSchemeSpecificPart();SharedPreferences p=ledger(c);
        if(id==null||!active(p.getString(PREFIX+id,"")))return;
        String result="INVALID_RESULT";
        try{
            Bundle b=intent.getBundleExtra("result");
            if(b!=null&&b.containsKey("err")){
                int err=b.getInt("err",Integer.MIN_VALUE);
                if(err!=-1&&err!=Integer.MIN_VALUE)result="TERMUX_ERROR:"+err;
                else if(err==-1&&b.containsKey("exitCode")){
                    int code=b.getInt("exitCode",Integer.MIN_VALUE);
                    if(code!=Integer.MIN_VALUE)result="SESSION_EXIT:"+code;
                }
            }
        }catch(RuntimeException invalid){result="INVALID_RESULT";}
        finally{intent.replaceExtras((Bundle)null);}
        transition(c,id,result);
    }
    static String summary(Context c){
        String state=ledger(c).getString("latest","");
        if(state.startsWith("SESSION_EXIT:"))return c.getString(R.string.termux_r06_session_exit,state.substring(13));
        if(state.startsWith("TERMUX_ERROR:"))return c.getString(R.string.termux_r06_session_error,state.substring(13));
        if("INVALID_RESULT".equals(state))return c.getString(R.string.termux_r06_session_invalid);
        if("SUBMITTED".equals(state)||"DISPATCHING".equals(state))return c.getString(R.string.termux_r06_session_pending);
        if("UNCERTAIN".equals(state)||"DISPATCH_REFUSED".equals(state))return c.getString(R.string.termux_r05_dispatch_failed);
        return c.getString(R.string.termux_r06_no_result);
    }
}
