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

/** Single background owner for LSPosed binder reads and confirmed preference writes. */
public final class FrameworkStateStore {
    public interface Observer { void onSnapshot(FrameworkSnapshot snapshot); }
    public interface Completion { void complete(boolean confirmed); }

    private final UiEnvironment environment;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<PendingFrameworkCommands.Ticket, Completion> completions=new HashMap<>();
    private Runnable readTimeout;
    // These are touched exclusively on the main thread.
    private Observer observer;
    private volatile long generation;
    private volatile boolean closed;
    private long settledGeneration = -1;
    private volatile long lifecycleEpoch;
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
        completions.clear();
        if(readTimeout!=null){main.removeCallbacks(readTimeout);readTimeout=null;}
        publishedPort = null;
        observer = null;
    }
    public void close() {
        stop();
        closed = true;
        // The process worker stays alive to settle any already submitted command.
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
        if(readTimeout!=null)main.removeCallbacks(readTimeout);
        readTimeout=()-> {
            if (!closed && observer != null && ticket == generation && settledGeneration != ticket)
                publish(FrameworkSnapshot.unavailable());
        };
        main.postDelayed(readTimeout,8000L);
        environment.app.frameworkTask(() -> {
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
                if(readTimeout!=null){main.removeCallbacks(readTimeout);readTimeout=null;}
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
        // This is metadata reconciliation, not a hook/scope change. No host-side writes.
        if (bound != environment.currentService() || !environment.publishRegistry(bound))
            return FrameworkSnapshot.unavailable();
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
    private PendingFrameworkCommands pending(){return environment.app.frameworkCommands();}
    public boolean pendingPreference(String id){return pending().contains("pref:"+id);}
    public boolean pendingScope(String pkg){return pending().contains("scope:"+pkg);}
    public Set<String> pendingScope(){return pending().keys("scope:");}
    private PendingFrameworkCommands.Ticket begin(UiEnvironment.Port expected,String key,Completion cb){
        if(observer==null||closed||!ownsReadySnapshot(expected)){cb.complete(false);return null;}
        PendingFrameworkCommands.Ticket ticket=pending().begin(key);
        if(ticket!=null){completions.put(ticket,cb);environment.app.frameworkCommandsChanged();}
        return ticket;
    }
    private void completed(UiEnvironment.Port expected,long epoch,PendingFrameworkCommands.Ticket ticket,boolean success){
        main.post(()->{
            if(!pending().complete(ticket))return; // stale completion cannot clear a successor
            Completion cb=completions.remove(ticket);
            if(!closed&&observer!=null&&epoch==lifecycleEpoch&&cb!=null){
                boolean current=expected==environment.currentService();cb.complete(success&&current);
            }
            // Notify the currently attached Activity even when the original one was stopped.
            environment.app.frameworkCommandsChanged();
        });
    }
    private boolean canStart(UiEnvironment.Port expected,long epoch){return !closed&&epoch==lifecycleEpoch&&isReady(expected);}
    private void write(UiEnvironment.Port expected,String operation,String group,String key,boolean value,Completion cb){
        PendingFrameworkCommands.Ticket ticket=begin(expected,"pref:"+operation,cb);if(ticket==null)return;
        long epoch=lifecycleEpoch;
        environment.app.frameworkTask(()->{
            boolean saved=false;
            if(canStart(expected,epoch))try{
                SharedPreferences prefs=expected.getRemotePreferences(group);
                saved=prefs!=null&&prefs.edit().putBoolean(key, value).commit()&&prefs.getBoolean(key, !value) == value;
            }catch(RuntimeException|LinkageError error){saved=false;}
            completed(expected,epoch,ticket,saved);
        });
    }
    public void writeTweak(UiEnvironment.Port expected,String id,boolean enabled,Completion cb){
        write(expected,id,TweakKeys.GROUP,TweakKeys.enabled(id),enabled,cb);
    }
    public void writeSafetyLock(UiEnvironment.Port expected, String id, boolean locked, Completion cb){
        write(expected,"lock:"+id,TweakDiagnostics.GROUP,TweakDiagnostics.quarantineKey(id),locked,cb);
    }
    public void writeVerbose(UiEnvironment.Port expected,boolean enabled,Completion cb){
        write(expected, "verbose", TweakKeys.GROUP, TweakKeys.VERBOSE_LSPOSED_LOGS, enabled, cb);
    }
    public void requestScope(UiEnvironment.Port expected,String pkg,Completion cb){
        if(!environment.knowsPackage(pkg)){cb.complete(false);return;}
        PendingFrameworkCommands.Ticket ticket=begin(expected,"scope:"+pkg,cb);if(ticket==null)return;
        long epoch=lifecycleEpoch;
        java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean();
        Completion single=success->{
            if(!once.compareAndSet(false, true))return;
            environment.app.frameworkTask(()->{
                boolean confirmed=false;
                if(success&&isReady(expected))try{confirmed=expected.getScope().contains(pkg);}catch(RuntimeException|LinkageError ignored){}
                completed(expected,epoch,ticket,confirmed);
            });
        };
        environment.app.frameworkTask(()->{
            if(!canStart(expected,epoch)){single.complete(false);return;}
            try{
                expected.requestScope(Collections.singletonList(pkg),new UiEnvironment.ScopeListener(){
                    @Override public void onScopeRequestApproved(List<String> approved){single.complete(approved!=null&&approved.contains(pkg));}
                    @Override public void onScopeRequestFailed(String reason){single.complete(false);}
                });
            }catch(RuntimeException|LinkageError error){single.complete(false);}
        });
        // No timeout releases a request whose remote effect is uncertain. It stays pending
        // until the framework callback or process loss, never automatically resubmitted.
    }
    public void removeScope(UiEnvironment.Port expected,String pkg,Completion cb){
        if(!environment.knowsPackage(pkg)){cb.complete(false);return;}
        PendingFrameworkCommands.Ticket ticket=begin(expected,"scope:"+pkg,cb);if(ticket==null)return;
        long epoch=lifecycleEpoch;
        environment.app.frameworkTask(()->{
            boolean confirmed=false;
            if(canStart(expected,epoch))try{
                expected.removeScope(Collections.singletonList(pkg));confirmed=!expected.getScope().contains(pkg);
            }catch(RuntimeException|LinkageError ignored){}
            completed(expected,epoch,ticket,confirmed);
        });
    }
}
