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
