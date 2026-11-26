package com.mikeyphw.hooklab;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArraySet;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Framework binder is the only activation evidence; never use module self-hooks to spoof it. */
public final class HookLabApp extends Application implements XposedServiceHelper.OnServiceListener {
    private volatile XposedService service;
    private final com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline diagnosticTimeline =
            new com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline();
    public com.mikeyphw.hooklab.diagnostics.DiagnosticTimeline diagnostics() {return diagnosticTimeline;}
    // One serialized import/export lane for the process, including Activity recreation.
    private final java.util.concurrent.ExecutorService diagnosticWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final CopyOnWriteArraySet<Runnable> diagnosticObservers=new CopyOnWriteArraySet<>();
    public void diagnosticTask(Runnable task){diagnosticWorker.execute(task);}
    public void addDiagnosticObserver(Runnable task){diagnosticObservers.add(task);}
    public void removeDiagnosticObserver(Runnable task){diagnosticObservers.remove(task);}
    public void diagnosticChanged(){ui.post(()->{for(Runnable observer:diagnosticObservers)observer.run();});}

    // DX-02: only user-directed, redacted SAF imports are associated with a session.
    // A stale process-death marker reports interruption, never invents old events.
    private volatile com.mikeyphw.hooklab.diagnostics.CaptureSession captureSession;
    private final java.util.concurrent.atomic.AtomicBoolean captureImportRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    public com.mikeyphw.hooklab.diagnostics.CaptureSession capture() {return captureSession;}
    public boolean captureImportRunning(){return captureImportRunning.get();}
    public boolean beginCaptureImport(){return captureImportRunning.compareAndSet(false,true);}
    public void endCaptureImport(){captureImportRunning.set(false);}
    public synchronized boolean startCapture(com.mikeyphw.hooklab.diagnostics.CaptureSession next){
        if(next==null || captureImportRunning.get() || (captureSession!=null &&
                captureSession.state()==com.mikeyphw.hooklab.diagnostics.CaptureSession.State.ACTIVE))return false;
        // Persist the interruption marker before publishing an active session.
        if(!getSharedPreferences("hooklab.dx02.capture", MODE_PRIVATE).edit().putBoolean("unfinished",true).commit())return false;
        captureSession=next;
        return true;
    }
    public void closeCapture(){
        getSharedPreferences("hooklab.dx02.capture", MODE_PRIVATE).edit().putBoolean("unfinished",false).apply();
    }
    public boolean interruptedCapture(){
        return captureSession==null && getSharedPreferences("hooklab.dx02.capture", MODE_PRIVATE)
                .getBoolean("unfinished",false);
    }
    // Serialize binder work across Activity recreation and external UI launches.
    private final java.util.concurrent.ExecutorService frameworkWorker=java.util.concurrent.Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"hooklab-framework-state");thread.setDaemon(true);return thread;
    });
    private final com.mikeyphw.hooklab.ui.PendingFrameworkCommands frameworkCommands=new com.mikeyphw.hooklab.ui.PendingFrameworkCommands();
    public void frameworkTask(Runnable task){frameworkWorker.execute(task);}
    public com.mikeyphw.hooklab.ui.PendingFrameworkCommands frameworkCommands(){return frameworkCommands;}
    public void frameworkCommandsChanged(){notifyObservers();}
    private final CopyOnWriteArraySet<Runnable> observers = new CopyOnWriteArraySet<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        // No credential-protected preferences or UI work here: service may bind before unlock.
        XposedServiceHelper.registerListener(this);
    }
    @Override public void onServiceBind(XposedService connected) {
        service = connected;
        notifyObservers();
    }
    @Override public void onServiceDied(XposedService dead) {
        if (service == dead) {
            service = null;
            notifyObservers();
        }
    }
    public XposedService currentService() { return service; }
    public void addObserver(Runnable listener) { observers.add(listener); }
    public void removeObserver(Runnable listener) { observers.remove(listener); }
    private void notifyObservers() {
        ui.post(() -> { for (Runnable observer : observers) observer.run(); });
    }
}
