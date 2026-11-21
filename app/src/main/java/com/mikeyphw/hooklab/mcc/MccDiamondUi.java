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
import com.mikeyphw.hooklab.core.VerboseLogs;
import io.github.libxposed.api.XposedInterface;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single user-initiated MCC diamond button. No watcher, polling or automatic navigation. */
public final class MccDiamondUi {
    private static final String MARKER = "hooklab.mcc.manual.diamond";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private MccDiamondUi() { }

    private static int dp(Activity activity, int n) {
        return (int)(n * activity.getResources().getDisplayMetrics().density + .5f);
    }

    private static boolean isFlutter(Activity activity) {
        for (Class<?> cls = activity.getClass(); cls != null; cls = cls.getSuperclass()) {
            String name = cls.getName();
            if ("io.flutter.embedding.android.FlutterActivity".equals(name) ||
                    "io.flutter.embedding.android.FlutterFragmentActivity".equals(name)) return true;
        }
        return false;
    }

    private static boolean isDiamondLabel(CharSequence value) {
        if (value == null) return false;
        String label = value.toString().toLowerCase(Locale.ROOT);
        return label.contains("diamond") || label.contains("diamante") ||
               label.contains("gem") || label.contains("💎") || label.contains("♦");
    }

    private static View flutterView(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        for (int seen=0; !queue.isEmpty() && seen<100; seen++) {
            View next = queue.removeFirst();
            if ("io.flutter.embedding.android.FlutterView".equals(next.getClass().getName()))
                return next;
            if (next instanceof ViewGroup) {
                ViewGroup group = (ViewGroup)next;
                for (int i=0; i<Math.min(group.getChildCount(),40); i++)
                    queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }

    /** Optional Flutter accessibility anchoring; unavailable semantics safely use fallback. */
    private static int[] diamondAnchor(FrameLayout root) {
        View flutter = flutterView(root);
        if (flutter == null) return null;
        AccessibilityNodeProvider provider = flutter.getAccessibilityNodeProvider();
        if (provider == null) return null;
        try {
            AccessibilityNodeInfo start = provider.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID);
            if (start == null) return null;
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(start);
            int[] origin = new int[2];
            root.getLocationOnScreen(origin);
            Rect viewport = new Rect(0, 0, root.getWidth(), root.getHeight());
            for (int seen=0; !queue.isEmpty() && seen<140; seen++) {
                AccessibilityNodeInfo node = queue.removeFirst();
                if (isDiamondLabel(node.getText()) || isDiamondLabel(node.getContentDescription())) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    bounds.offset(-origin[0], -origin[1]);
                    if (bounds.width()>0 && bounds.height()>0 && Rect.intersects(viewport,bounds))
                        return new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom};
                }
                for (int i=0; i<Math.min(node.getChildCount(),24); i++) {
                    try {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(child);
                    } catch (RuntimeException ignored) { }
                }
            }
        } catch (RuntimeException ignored) { /* No semantic tree: use fallback. */ }
        return null;
    }

    private static void place(Activity activity, FrameLayout root, Button button) {
        if (!button.isAttachedToWindow() || root.getWidth()<=0 || root.getHeight()<=0) return;
        int buttonWidth = button.getWidth()>0 ? button.getWidth() : dp(activity,68);
        int buttonHeight = button.getHeight()>0 ? button.getHeight() : dp(activity,38);
        int[] xy = MccDiamondPlacement.position(root.getWidth(), root.getHeight(),
            buttonWidth, buttonHeight, dp(activity,8), dp(activity,68), diamondAnchor(root));
        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams)button.getLayoutParams();
        if (layout.leftMargin != xy[0] || layout.topMargin != xy[1] ||
                layout.gravity != (Gravity.TOP | Gravity.START)) {
            layout.gravity = Gravity.TOP | Gravity.START;
            layout.leftMargin = xy[0];
            layout.topMargin = xy[1];
            button.setLayoutParams(layout);
        }
    }

    /** A tap ONLY opens HookLab's visible confirmation screen; no root side effect. */
    private static void openControl(Activity activity) {
        VerboseLogs.emit(VerboseLogs.Event.MCC_CONTROL_OPEN);
        try {
            Intent intent = new Intent(MccRefillCommand.ACTION_FROM_TARGET);
            intent.setComponent(new ComponentName("com.mikeyphw.hooklab",
                    "com.mikeyphw.hooklab.mcc.MccControlActivity"));
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setShareIdentityEnabled(true);
                activity.startActivity(intent, options.toBundle());
            } else {
                activity.startActivity(intent);
            }
        } catch (RuntimeException ex) {
            VerboseLogs.emit(VerboseLogs.Event.MCC_CONTROL_OPEN_REFUSED);
            Toast.makeText(activity, "HookLab diamond control unavailable", Toast.LENGTH_LONG).show();
        }
    }

    private static void attach(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() ||
                !isFlutter(activity)) return;
        View view = activity.getWindow().getDecorView();
        if (!(view instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout)view;
        View existing = root.findViewWithTag(MARKER);
        if (existing instanceof Button) {
            Button button = (Button)existing;
            button.post(() -> place(activity,root,button));
            return;
        }
        Button button = new Button(activity);
        button.setTag(MARKER);
        button.setText("♦ 9");
        button.setContentDescription("Open manual diamond control in HookLab");
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(37,66,103)));
        button.setElevation(dp(activity,4));
        root.addView(button,new FrameLayout.LayoutParams(dp(activity,68),dp(activity,38),
                Gravity.TOP | Gravity.END));
        VerboseLogs.emit(VerboseLogs.Event.MCC_BUTTON_ATTACHED);
        button.setOnClickListener(v -> openControl(activity));
        // Flutter semantics may arrive after the Activity is resumed. Bounded retries
        // reposition the button; no recurring watcher or foreground navigation loop.
        button.post(() -> place(activity,root,button));
        button.postDelayed(() -> place(activity,root,button),450);
        button.postDelayed(() -> place(activity,root,button),1400);
    }

    /** Compatibility only: no automatic handoff is permitted in this release. */
    @Deprecated static boolean requestAutomatic() { return false; }

    public static void install(HookContext ctx) throws Exception {
        if (!MccIds.PACKAGE.equals(ctx.packageName)) throw new SecurityException("MCC scope mismatch");
        if (!INSTALLED.compareAndSet(false,true)) return;
        XposedInterface framework = ctx.framework(XposedInterface.class);
        try {
            framework.hook(Activity.class.getDeclaredMethod("onResume"))
                .setId("hooklab.mcc.manual.resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = (Activity)chain.getThisObject();
                    if (activity != null) activity.runOnUiThread(() -> {
                        try { attach(activity); } catch (RuntimeException ignored) { }
                    });
                    return result;
                });
            framework.hook(Activity.class.getDeclaredMethod("onWindowFocusChanged", boolean.class))
                .setId("hooklab.mcc.manual.window_focus")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (Boolean.TRUE.equals(chain.getArg(0))) {
                        Activity activity = (Activity)chain.getThisObject();
                        if (activity != null) activity.runOnUiThread(() -> {
                            try { attach(activity); } catch (RuntimeException ignored) { }
                        });
                    }
                    return result;
                });
            VerboseLogs.emit(VerboseLogs.Event.MCC_HOOK_INSTALLED);
        } catch (Throwable failure) {
            INSTALLED.set(false);
            if (failure instanceof Exception) throw (Exception)failure;
            if (failure instanceof Error) throw (Error)failure;
            throw new IllegalStateException(failure);
        }
    }
}
