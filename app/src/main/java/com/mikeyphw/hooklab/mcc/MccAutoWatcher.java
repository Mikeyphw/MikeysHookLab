package com.mikeyphw.hooklab.mcc;

import android.content.Context;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import com.mikeyphw.hooklab.core.HookContext;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/** Target process observes only; any root write is performed by the separate HookLab Activity. */
public final class MccAutoWatcher {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final long POLL_MS = 1200, QUIET_MS = 750, COOLDOWN_MS = 15000;
    private static Handler handler;
    private static File directory;
    private static FileObserver observer;
    private static long zeroFirstSeen, lastRequest;
    private MccAutoWatcher() { }

    private static File uniqueBalance() {
        if (directory == null || !directory.isDirectory() || Files.isSymbolicLink(directory.toPath())) return null;
        File[] files = directory.listFiles((d,name) -> MccAutoPolicy.allowedName(name));
        if (files == null || files.length != 1 || !files[0].isFile() ||
                Files.isSymbolicLink(files[0].toPath())) return null;
        return files[0];
    }

    private static void poll() {
        File file = uniqueBalance();
        if (file == null || file.length() != 1) { zeroFirstSeen = 0; return; }
        try {
            if (!MccAutoPolicy.exactZero(Files.readAllBytes(file.toPath()))) {
                zeroFirstSeen = 0;
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (zeroFirstSeen == 0) { zeroFirstSeen = now; return; }
            if (now-zeroFirstSeen < QUIET_MS || now-lastRequest < COOLDOWN_MS) return;
            if (MccDiamondUi.requestAutomatic()) lastRequest = now;
        } catch (Exception ex) {
            lastRequest = SystemClock.elapsedRealtime();
            Log.w("HookLabMCC", "Auto-refill observation unavailable: " + ex.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private static void startObserver() {
        if (observer != null || directory == null || !directory.isDirectory()) return;
        try {
            observer = new FileObserver(directory.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
                @Override public void onEvent(int event, String filename) {
                    if (!MccAutoPolicy.allowedName(filename)) return;
                    Handler h = handler;
                    if (h != null) h.post(MccAutoWatcher::poll);
                }
            };
            observer.startWatching();
        } catch (RuntimeException ex) {
            Log.w("HookLabMCC", "Observer unavailable; using polling");
        }
    }

    public static void install(HookContext ctx) throws Exception {
        if (!MccIds.PACKAGE.equals(ctx.packageName)) throw new SecurityException("Scope mismatch");
        // Auto mode requires a foreground Activity; it adds the manual button too.
        MccDiamondUi.install(ctx);
        if (!STARTED.compareAndSet(false, true)) return;
        Context c = ctx.appContext(Context.class);
        directory = new File(c.getApplicationInfo().dataDir, "databases");
        HandlerThread thread = new HandlerThread("hooklab-mcc-zero-observer");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(new Runnable() {
            @Override public void run() {
                startObserver();
                poll();
                handler.postDelayed(this, POLL_MS);
            }
        });
    }
}
