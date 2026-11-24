package com.mikeyphw.hooklab.chatgpt;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.PopupWindow;
import com.mikeyphw.hooklab.core.HookContext;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** CGT01-P07: separately opted-in post-Copy action; no clipboard reads, code extras or execution. */
public final class ChatGptTermuxHandoff {
    public static final String TWEAK_ID="chatgpt.codeblock.termux.handoff";
    private static final String TAG="HookLabCGT01";
    private static final AtomicBoolean ENABLED=new AtomicBoolean();
    private static final TermuxOfferGate GATE=new TermuxOfferGate();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> foreground=new WeakReference<>(null);
    private static PopupWindow popup;
    private ChatGptTermuxHandoff(){}
    public static void install(HookContext context)throws Exception {
        if(!ChatGptCodeProbe.PACKAGE.equals(context.packageName)||!context.packageName.equals(context.processName)
                ||context.versionCode!=ChatGptCodeProbe.VERSION_CODE||!ChatGptCodeProbe.active())
            throw new IllegalStateException("Enable the exact-build Copy diagnostic first");
        XposedInterface xp=context.framework(XposedInterface.class);
        xp.hook(Activity.class.getDeclaredMethod("onPostResume"))
            .setId("hooklab.cgt01.p07.resume").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain->{Object result=chain.proceed();if(ENABLED.get())try{
                Activity activity=(Activity)chain.getThisObject();if(ChatGptCodeProbe.PACKAGE.equals(activity.getPackageName())){
                    GATE.invalidate();dismiss();foreground=new WeakReference<>(activity);}
            }catch(RuntimeException error){log("UI_REFUSED",0);}return result;});
        xp.hook(Activity.class.getDeclaredMethod("onPause"))
            .setId("hooklab.cgt01.p07.pause").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain->{if(ENABLED.get())try{if(foreground.get()==chain.getThisObject()){
                GATE.invalidate();dismiss();foreground.clear();}
            }catch(RuntimeException error){log("UI_REFUSED",0);}return chain.proceed();});
        ENABLED.set(true);log("ENABLED",0);
    }
    /** Called only after the original clipboard method returns; 0 invalidates previous offer. */
    static void clipboardResult(long matchedActionId){
        if(!ENABLED.get())return;
        final long token=GATE.replace(matchedActionId,SystemClock.elapsedRealtime());
        MAIN.post(()->{
            if(!GATE.current(token))return;
            dismiss();long action=GATE.available(token,SystemClock.elapsedRealtime());
            if(action==0)return;
            Activity host=foreground.get();
            if(!ready(host)){GATE.clear(token);log("NO_FOREGROUND",action);return;}
            try{show(host,token,action);}catch(RuntimeException error){GATE.clear(token);dismiss();log("UI_REFUSED",action);}
        });
    }
    private static boolean ready(Activity host){return host!=null&&!host.isFinishing()&&!host.isDestroyed()
        &&host.hasWindowFocus()&&host.getWindow().getDecorView().isAttachedToWindow();}
    private static void show(Activity host,long token,long action){
        View anchor=host.getWindow().getDecorView();
        Button button=new Button(host);button.setText("Open in Termux · paste manually");
        button.setContentDescription("Open Termux to paste and review the copied code. Nothing is executed.");
        button.setAllCaps(false);button.setMinHeight(dp(host,48));
        PopupWindow offer=new PopupWindow(button,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,false);
        offer.setTouchable(true);offer.setOutsideTouchable(true);offer.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        offer.setElevation(dp(host,8));offer.setOnDismissListener(()->{if(popup==offer)popup=null;GATE.clear(token);});
        button.setOnClickListener(v->{
            if(!ready(host)||foreground.get()!=host){GATE.clear(token);dismiss();log("STALE_REFUSED",action);return;}
            long selected=GATE.consume(token,SystemClock.elapsedRealtime());dismiss();
            if(selected==0){log("STALE_REFUSED",action);return;}
            // Deliberate second user gesture. No shell, permission, raw code or intent extras.
            Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName("com.termux","com.termux.app.TermuxActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try{host.startActivity(intent);log("OPEN_REQUESTED",selected);}
            catch(RuntimeException error){log("OPEN_REFUSED",selected);android.widget.Toast.makeText(host,"Termux could not be opened",android.widget.Toast.LENGTH_SHORT).show();}
        });
        WindowInsets insets=anchor.getRootWindowInsets();int bottom=dp(host,24);
        if(insets!=null){if(android.os.Build.VERSION.SDK_INT>=30)bottom+=insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            else bottom+=insets.getStableInsetBottom();}
        popup=offer;offer.showAtLocation(anchor,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,bottom);
        log("OFFER_SHOWN",action);
        MAIN.postDelayed(()->{if(popup==offer){GATE.clear(token);dismiss();log("OFFER_EXPIRED",action);}},TermuxOfferGate.TTL_MS);
    }
    private static void dismiss(){PopupWindow old=popup;popup=null;if(old!=null)try{if(old.isShowing())old.dismiss();}catch(RuntimeException ignored){}}
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static void log(String code,long action){Log.i(TAG,"CGT01_P07_"+code+" action_id="+action+" execution=NONE");}
}
