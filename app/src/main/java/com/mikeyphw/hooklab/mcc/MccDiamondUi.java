package com.mikeyphw.hooklab.mcc;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.mikeyphw.hooklab.core.HookContext;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adaptation of MCC-VIP-Lab's Flutter diamond button to API 102 hook chains. */
public final class MccDiamondUi {
    private static final String MARKER = "hooklab.mcc.diamond.button";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile WeakReference<Activity> current = new WeakReference<>(null);
    private MccDiamondUi() { }

    private static int dp(Activity a, int n) {
        return (int)(n * a.getResources().getDisplayMetrics().density + .5f);
    }
    private static boolean isFlutter(Activity a) {
        for (Class<?> c = a.getClass(); c != null; c = c.getSuperclass()) {
            if ("io.flutter.embedding.android.FlutterActivity".equals(c.getName()) ||
                    "io.flutter.embedding.android.FlutterFragmentActivity".equals(c.getName())) return true;
        }
        return false;
    }

    /** Always uses an explicit component; root is requested only in the module process. */
    private static void openControl(Activity activity, boolean auto) {
        try {
            Intent i = new Intent(MccRefillCommand.ACTION_FROM_TARGET);
            i.setComponent(new ComponentName("com.mikeyphw.hooklab",
                    "com.mikeyphw.hooklab.mcc.MccControlActivity"));
            i.putExtra(MccRefillCommand.EXTRA_AUTO, auto);
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setShareIdentityEnabled(true);
                activity.startActivity(i, options.toBundle());
            } else {
                activity.startActivity(i); // user confirmation only on older Android
            }
        } catch (RuntimeException ex) {
            Toast.makeText(activity, "HookLab diamond control unavailable", Toast.LENGTH_LONG).show();
        }
    }

    static boolean requestAutomatic() {
        Activity a = current.get();
        if (a == null || a.isFinishing() || a.isDestroyed() || !isFlutter(a)) return false;
        a.runOnUiThread(() -> {
            if (!a.isFinishing() && !a.isDestroyed()) openControl(a, true);
        });
        return true;
    }

    private static View flutterView(View root) {
        ArrayDeque<View> todo = new ArrayDeque<>();
        todo.add(root);
        for (int count=0; !todo.isEmpty() && count<90; count++) {
            View node = todo.removeFirst();
            if ("io.flutter.embedding.android.FlutterView".equals(node.getClass().getName())) return node;
            if (node instanceof ViewGroup) {
                ViewGroup group = (ViewGroup)node;
                for (int i=0; i<group.getChildCount() && i<50; i++) todo.add(group.getChildAt(i));
            }
        }
        return null;
    }

    private static boolean diamondLabel(CharSequence chars) {
        if (chars == null) return false;
        String text = chars.toString().toLowerCase(Locale.ROOT);
        return text.contains("diamond") || text.contains("diamante") || text.contains("gem")
            || text.contains("💎") || text.contains("♦");
    }

    private static Rect diamondAnchor(FrameLayout root) {
        View flutter = flutterView(root);
        if (flutter == null) return null;
        AccessibilityNodeProvider provider = flutter.getAccessibilityNodeProvider();
        if (provider == null) return null;
        try {
            AccessibilityNodeInfo node = provider.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID);
            if (node == null) return null;
            ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
            pending.add(node);
            int[] offset = new int[2];
            root.getLocationOnScreen(offset);
            Rect visible = new Rect(0, 0, root.getWidth(), root.getHeight());
            for (int count=0; !pending.isEmpty() && count<110; count++) {
                AccessibilityNodeInfo item = pending.removeFirst();
                if (diamondLabel(item.getText()) || diamondLabel(item.getContentDescription())) {
                    Rect bounds = new Rect();
                    item.getBoundsInScreen(bounds);
                    bounds.offset(-offset[0], -offset[1]);
                    if (bounds.width()>0 && bounds.height()>0 && Rect.intersects(bounds, visible))
                        return bounds;
                }
                for (int i=0; i<item.getChildCount() && i<25; i++) {
                    try {
                        AccessibilityNodeInfo child = item.getChild(i);
                        if (child != null) pending.add(child);
                    } catch (RuntimeException ignored) { }
                }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static void place(Activity a, FrameLayout root, Button b) {
        if (!b.isAttachedToWindow() || root.getWidth() <= 0) return;
        Rect near = diamondAnchor(root);
        int width = b.getWidth() > 0 ? b.getWidth() : dp(a, 68);
        int height = b.getHeight() > 0 ? b.getHeight() : dp(a, 38);
        // Anchor to the right side of the diamond indicator, clamp to visible bounds.
        int margin = dp(a, 8);
        int x = near == null ? root.getWidth() - width - margin : near.right + margin;
        int y = near == null ? dp(a, 68) : near.top + (near.height() - height)/2;
        x = Math.max(margin, Math.min(x, Math.max(margin, root.getWidth()-width-margin)));
        y = Math.max(margin, Math.min(y, Math.max(margin, root.getHeight()-height-margin)));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)b.getLayoutParams();
        if (lp.leftMargin != x || lp.topMargin != y) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = x;
            lp.topMargin = y;
            b.setLayoutParams(lp);
        }
    }

    private static void attach(Activity a) {
        if (a.isFinishing() || a.isDestroyed() || !isFlutter(a)) return;
        current = new WeakReference<>(a);
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout)decor;
        if (root.findViewWithTag(MARKER) != null) return;
        Button b = new Button(a);
        b.setTag(MARKER);
        b.setText("♦ 9");
        b.setContentDescription("Open diamond refill control");
        b.setTextSize(13f);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(37, 66, 103)));
        b.setElevation(dp(a, 4));
        root.addView(b, new FrameLayout.LayoutParams(dp(a, 68), dp(a, 38),
                Gravity.TOP | Gravity.END));
        b.setOnClickListener(v -> openControl(a, false));
        Runnable reposition = new Runnable() {
            @Override public void run() {
                if (!b.isAttachedToWindow()) return;
                place(a, root, b);
                b.postDelayed(this, 1100L);
            }
        };
        b.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { b.removeCallbacks(reposition); }
        });
        b.post(reposition);
    }

    public static void install(HookContext ctx) throws Exception {
        if (!MccIds.PACKAGE.equals(ctx.packageName)) throw new SecurityException("Scope mismatch");
        if (!INSTALLED.compareAndSet(false, true)) return;
        XposedInterface framework = ctx.framework(XposedInterface.class);
        try {
            framework.hook(Activity.class.getDeclaredMethod("onResume"))
                .setId("hooklab.mcc.diamond.activity_resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity a = (Activity)chain.getThisObject();
                    if (a != null) a.runOnUiThread(() -> { try { attach(a); } catch (RuntimeException ignored) { } });
                    return result;
                });
            framework.hook(Activity.class.getDeclaredMethod("onPause"))
                .setId("hooklab.mcc.diamond.activity_pause")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity a = (Activity)chain.getThisObject();
                    if (a != null && a == current.get()) current = new WeakReference<>(null);
                    return chain.proceed();
                });
        } catch (Throwable t) {
            INSTALLED.set(false);
            if (t instanceof Exception) throw (Exception)t;
            if (t instanceof Error) throw (Error)t;
            throw new IllegalStateException(t);
        }
    }
}
