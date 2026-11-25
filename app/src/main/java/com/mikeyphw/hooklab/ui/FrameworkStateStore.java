package com.mikeyphw.hooklab.ui;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.mikeyphw.hooklab.core.TweakDiagnostics;
import com.mikeyphw.hooklab.core.TweakEngine;
import com.mikeyphw.hooklab.core.TweakKeys;
import com.mikeyphw.hooklab.core.HookContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single background owner for LSPosed binder reads and confirmed preference writes. */
public final class FrameworkStateStore {
    public interface Observer { void onSnapshot(FrameworkSnapshot snapshot); }
    public interface Completion { void complete(boolean confirmed); }

    private final UiEnvironment environment;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "hooklab-framework-state");
        t.setDaemon(true);
        return t;
    });
    // These are touched exclusively on the main thread.
    private Observer observer;
    private volatile long generation;
    private volatile boolean closed;
    private long settledGeneration = -1;
    private long lifecycleEpoch;
    private UiEnvironment.Port publishedPort;

    FrameworkStateStore(UiEnvironment environment) { this.environment = java.util.Objects.requireNonNull(environment); }

    boolean ownsReadySnapshot(UiEnvironment.Port expected) {
        return expected != null && expected == publishedPort && expected == environment.currentService();
    }

    public void start(Observer listener) {
        if (closed) return;
        observer = listener;
        refresh();
    }
    public void stop() {
        generation++;
        lifecycleEpoch++;
        publishedPort = null;
        observer = null;
    }
    public void close() {
        stop();
        closed = true;
        worker.shutdown(); // Never interrupt a partially committed framework preference write.
    }
    private void publish(FrameworkSnapshot next) {
        if (observer != null) observer.onSnapshot(next);
    }

    /** Replaces a stale view immediately; only the latest generation may publish. */
    public void refresh() {
        if (observer == null || closed) return;
        long ticket = ++generation;
        UiEnvironment.Port bound = environment.currentService();
        publishedPort = null;
        publish(FrameworkSnapshot.loading());
        // A stuck framework binder must not leave the UI spinning forever.
        main.postDelayed(() -> {
            if (!closed && observer != null && ticket == generation && settledGeneration != ticket)
                publish(FrameworkSnapshot.unavailable());
        }, 8000L);
        worker.execute(() -> {
            if (closed || ticket != generation) return; // skip queued stale binder work
            FrameworkSnapshot result;
            try { result = read(bound); }
            catch (RuntimeException | LinkageError failure) {
                result = FrameworkSnapshot.unavailable();
            }
            FrameworkSnapshot resolved = result;
            main.post(() -> {
                if (closed || observer == null || ticket != generation) return;
                if (bound != environment.currentService()) {
                    refresh();
                    return;
                }
                settledGeneration = ticket;
                publishedPort = resolved.ready() ? bound : null;
                publish(resolved);
            });
        });
    }

    private FrameworkSnapshot read(UiEnvironment.Port bound) {
        if (bound == null) return FrameworkSnapshot.disconnected();
        final int api;
        try { api = bound.getApiVersion(); }
        catch (RuntimeException | LinkageError error) { return FrameworkSnapshot.unavailable(); }
        if (api < 102) return FrameworkSnapshot.unsupported(api);
        String name = null, version = null;
        try {
            name = bound.getFrameworkName();
            version = bound.getFrameworkVersion();
        } catch (RuntimeException | LinkageError ignored) {
            // Connection and scope can still be queried independently.
        }
        Set<String> scope = null;
        try { scope = new HashSet<>(bound.getScope()); }
        catch (RuntimeException | LinkageError ignored) { /* unknown is distinct from empty */ }
        SharedPreferences flags = null, safety = null;
        try { flags = bound.getRemotePreferences(TweakKeys.GROUP); }
        catch (RuntimeException | LinkageError ignored) { /* preferences unavailable */ }
        try { safety = bound.getRemotePreferences(TweakDiagnostics.GROUP); }
        catch (RuntimeException | LinkageError ignored) { /* safety unavailable */ }
        Map<String, FrameworkSnapshot.Flag> states = new HashMap<>();
        for (TweakEngine.Entry<HookContext> entry : environment.entries()) {
            Boolean enabled = null, locked = null;
            if (flags != null) {
                try { enabled = flags.getBoolean(TweakKeys.enabled(entry.spec.id), false); }
                catch (RuntimeException | LinkageError ignored) { /* unreadable: no false assumption */ }
            }
            if (safety != null) {
                try { locked = safety.getBoolean(TweakDiagnostics.quarantineKey(entry.spec.id), false); }
                catch (RuntimeException | LinkageError ignored) { /* unreadable: no false assumption */ }
            }
            Long installed = environment.installedVersion(entry.spec);
            states.put(entry.spec.id, new FrameworkSnapshot.Flag(enabled, locked, installed));
        }
        Boolean verbose = null;
        if (flags != null) {
            try { verbose = flags.getBoolean(TweakKeys.VERBOSE_LSPOSED_LOGS, false); }
            catch (RuntimeException | LinkageError ignored) { /* keep unknown */ }
        }
        return FrameworkSnapshot.connected(name, version, api, scope, states, verbose);
    }

    private boolean isReady(UiEnvironment.Port expected) {
        try {
            return expected != null && expected == environment.currentService()
                    && expected.getApiVersion() >= 102;
        } catch (RuntimeException | LinkageError error) { return false; }
    }
    private void completed(UiEnvironment.Port expected, long epoch, boolean success, Completion cb) {
        main.post(() -> {
            if (closed || observer == null || epoch != lifecycleEpoch) return;
            boolean current = expected == environment.currentService();
            cb.complete(success && current);
            refresh(); // Re-read framework truth; never optimistically flip the switch.
        });
    }
    private void write(UiEnvironment.Port expected, String group, String key, boolean value, Completion cb) {
        if (observer == null || closed || !ownsReadySnapshot(expected)) { cb.complete(false); return; }
        long epoch = lifecycleEpoch;
        worker.execute(() -> {
            boolean saved = false;
            if (isReady(expected)) {
                try {
                    SharedPreferences prefs = expected.getRemotePreferences(group);
                    saved = prefs != null && prefs.edit().putBoolean(key, value).commit()
                            && prefs.getBoolean(key, !value) == value;
                } catch (RuntimeException | LinkageError ignored) { saved = false; }
            }
            completed(expected, epoch, saved, cb);
        });
    }
    public void writeTweak(UiEnvironment.Port expected, String id, boolean enabled, Completion cb) {
        write(expected, TweakKeys.GROUP, TweakKeys.enabled(id), enabled, cb);
    }
    public void writeSafetyLock(UiEnvironment.Port expected, String id, boolean locked, Completion cb) {
        write(expected, TweakDiagnostics.GROUP, TweakDiagnostics.quarantineKey(id), locked, cb);
    }
    public void writeVerbose(UiEnvironment.Port expected, boolean enabled, Completion cb) {
        write(expected, TweakKeys.GROUP, TweakKeys.VERBOSE_LSPOSED_LOGS, enabled, cb);
    }
    public void requestScope(UiEnvironment.Port expected, String pkg, Completion cb) {
        if (observer == null || closed || !ownsReadySnapshot(expected) || !environment.knowsPackage(pkg)) {
            cb.complete(false); return;
        }
        long epoch = lifecycleEpoch;
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
        Completion single = success -> { if (once.compareAndSet(false, true)) completed(expected, epoch, success, cb); };
        worker.execute(() -> {
            if (!isReady(expected)) { single.complete(false); return; }
            try {
                expected.requestScope(Collections.singletonList(pkg), new UiEnvironment.ScopeListener() {
                    @Override public void onScopeRequestApproved(List<String> approved) {
                        single.complete(approved != null && approved.contains(pkg));
                    }
                    @Override public void onScopeRequestFailed(String reason) {
                        single.complete(false);
                    }
                });
            } catch (RuntimeException | LinkageError error) { single.complete(false); }
        });
    }
    public void removeScope(UiEnvironment.Port expected, String pkg, Completion cb) {
        if (observer == null || closed || !ownsReadySnapshot(expected) || !environment.knowsPackage(pkg)) {
            cb.complete(false); return;
        }
        long epoch = lifecycleEpoch;
        worker.execute(() -> {
            boolean requested = false;
            if (isReady(expected)) {
                try {
                    expected.removeScope(Collections.singletonList(pkg));
                    requested = true; // Request submitted; snapshot later verifies actual scope.
                } catch (RuntimeException | LinkageError ignored) { /* request failed */ }
            }
            completed(expected, epoch, requested, cb);
        });
    }
}
