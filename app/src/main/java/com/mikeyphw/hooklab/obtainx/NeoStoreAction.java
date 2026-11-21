package com.mikeyphw.hooklab.obtainx;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/**
 * Neo Store's native 48dp header action. This does not mutate host Compose state.
 * Semantic anchoring is preferred; pinned 1.3.3 geometry is a narrow fallback.
 */
final class NeoStoreAction {
    interface Click { void click(Object route, SourcePolicy.Snapshot snapshot); }
    private WeakReference<ImageButton> button=new WeakReference<>(null);
    private Object route;
    private SourcePolicy.Snapshot shown;
    private long lastTap;
    private static int dp(Activity a,int v) {
        return Math.round(v*a.getResources().getDisplayMetrics().density);
    }
    private static boolean imeVisible(FrameLayout root) {
        WindowInsets insets=root.getRootWindowInsets();
        // Cannot prove keyboard absence: avoid unsafe header action placement.
        if (insets==null) return true;
        if (Build.VERSION.SDK_INT>=30) return insets.isVisible(WindowInsets.Type.ime());
        return insets.getSystemWindowInsetBottom()>insets.getStableInsetBottom();
    }
    static boolean visible(Activity a) {
        return a!=null && !a.isFinishing() && !a.isDestroyed() &&
            a.hasWindowFocus() && !a.isInPictureInPictureMode();
    }
    private static Rect sourceSemantics(Activity a,FrameLayout root) {
        int id=a.getResources().getIdentifier("source_code","string",ObtainXIds.NEO);
        if (id==0) return null;
        String desired=a.getString(id);
        AccessibilityNodeInfo first=root.createAccessibilityNodeInfo();
        if (first==null) return null;
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
        nodes.add(first);
        int[] origin=new int[2];root.getLocationOnScreen(origin);
        try {
            for (int count=0;!nodes.isEmpty()&&count<240;count++) {
                AccessibilityNodeInfo node=nodes.removeFirst();
                try {
                    if (desired.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())) {
                        Rect r=new Rect();node.getBoundsInScreen(r);
                        if (r.width()>=dp(a,18) && r.width()<=dp(a,64) &&
                            r.height()>=dp(a,18) && r.height()<=dp(a,64) &&
                            r.top>=origin[1] && r.bottom<=origin[1]+dp(a,210)) return r;
                    }
                    for(int i=0;i<Math.min(50,node.getChildCount());i++) {
                        AccessibilityNodeInfo child=node.getChild(i);
                        if (child!=null) nodes.addLast(child);
                    }
                } finally { node.recycle(); }
            }
        } catch (RuntimeException ignored) { return null; }
        finally { while(!nodes.isEmpty()) nodes.removeFirst().recycle(); }
        return null;
    }
    private static Rect fallback(Activity a,FrameLayout root) {
        if (root.getWidth()<dp(a,365)||root.getHeight()<dp(a,160)) return null;
        int[] origin=new int[2];root.getLocationOnScreen(origin);
        WindowInsets insets=root.getRootWindowInsets();
        int status=Build.VERSION.SDK_INT>=30 ? insets.getInsets(WindowInsets.Type.statusBars()).top
            : insets.getStableInsetTop();
        int uncovered=Math.max(0,status-origin[1]);
        int cx=origin[0]+(root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL
            ? dp(a,76) : root.getWidth()-dp(a,76));
        int cy=origin[1]+uncovered+dp(a,36), h=dp(a,20);
        Rect r=new Rect(cx-h,cy-h,cx+h,cy+h);
        return r.left>=origin[0] && r.right<=origin[0]+root.getWidth() &&
            r.top>=origin[1] && r.bottom<=origin[1]+root.getHeight() &&
            r.bottom<=origin[1]+uncovered+dp(a,90) ? r:null;
    }
    private static boolean position(Activity a,FrameLayout root,ImageButton b,Rect source) {
        int[] offset=new int[2];root.getLocationOnScreen(offset);
        int size=dp(a,48), gap=dp(a,8);
        int xScreen=root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL
            ? source.right+gap : source.left-gap-size;
        int x=xScreen-offset[0],y=source.centerY()-size/2-offset[1];
        if (x<dp(a,66)||y<0||x+size>root.getWidth()||
            y+size>root.getHeight()||imeVisible(root)) return false;
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)b.getLayoutParams();
        if (lp.leftMargin!=x||lp.topMargin!=y||lp.gravity!=(Gravity.TOP|Gravity.LEFT)) {
            lp.gravity=Gravity.TOP|Gravity.LEFT;
            lp.leftMargin=x;lp.topMargin=y;b.setLayoutParams(lp);
        }
        return true;
    }
    void render(Activity a,Object freshRoute,SourcePolicy.Snapshot snapshot,Click onClick) {
        if (!visible(a)||freshRoute==null||snapshot==null) {clear();return;}
        View content=a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)||!content.isShown()) {clear();return;}
        FrameLayout root=(FrameLayout)content;
        if (root.getWidth()<dp(a,230)||root.getHeight()<dp(a,160)||imeVisible(root)) {clear();return;}
        Rect r;
        try {r=sourceSemantics(a,root);} catch (RuntimeException fail){r=null;}
        if (r==null) r=fallback(a,root);
        if (r==null) {clear();return;}
        ImageButton existing=button.get();
        if(existing!=null&&existing.getParent()==root&&route==freshRoute&&shown.equals(snapshot)) {
            if(!position(a,root,existing,r))clear();
            return;
        }
        clear();
        ImageButton icon=new ImageButton(a);
        icon.setTag("hooklab.obtainx.neo.action");
        icon.setContentDescription("Open this app in ObtainX; import preview only");
        Drawable logo=null;
        try {logo=a.getPackageManager().getApplicationIcon(ObtainXIds.OBTAIN_X).mutate();}
        catch (PackageManager.NameNotFoundException | SecurityException ignored) { }
        if (logo!=null) icon.setImageDrawable(logo);
        else icon.setImageResource(android.R.drawable.stat_sys_download_done);
        icon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(a,9),dp(a,9),dp(a,9),dp(a,9));
        GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);
        int night=a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        bg.setColor(night==android.content.res.Configuration.UI_MODE_NIGHT_YES?Color.rgb(45,39,35):Color.rgb(235,235,235));
        icon.setBackground(bg);
        icon.setFocusable(true);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        route=freshRoute;shown=snapshot;button=new WeakReference<>(icon);
        final Object shownRoute=freshRoute;
        final SourcePolicy.Snapshot bound=snapshot;
        WeakReference<Activity> weakActivity=new WeakReference<>(a);
        icon.setOnClickListener(v->{
            Activity host=weakActivity.get();
            if(!visible(host)||button.get()!=icon||route!=shownRoute||
                !icon.isAttachedToWindow()||!icon.isShown()||imeVisible(root))return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastTap<1000)return;
            lastTap=now;
            onClick.click(shownRoute,bound);
        });
        root.addView(icon,new FrameLayout.LayoutParams(dp(a,48),dp(a,48),Gravity.TOP|Gravity.LEFT));
        if(!position(a,root,icon,r))clear();
    }
    void result(Activity a,ObtainXBridge.Outcome o) {
        if(o!=ObtainXBridge.Outcome.DISPATCHED_FOR_PREVIEW)
            Toast.makeText(a,"ObtainX preview unavailable: "+o.name(),Toast.LENGTH_SHORT).show();
    }
    void clear() {
        ImageButton icon=button.get();
        if(icon!=null){
            icon.setOnClickListener(null);
            if(icon.getParent() instanceof ViewGroup)((ViewGroup)icon.getParent()).removeView(icon);
        }
        button=new WeakReference<>(null);route=null;shown=null;
    }
}
