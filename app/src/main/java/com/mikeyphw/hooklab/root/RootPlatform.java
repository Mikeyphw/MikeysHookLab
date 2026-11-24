package com.mikeyphw.hooklab.root;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import com.topjohnwu.superuser.ipc.RootService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicit, non-daemon diagnostic session, initiated by the companion UI only. */
public final class RootPlatform {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    // libsu 6.0.0 may retain a pending startup after a lost bind. Refuse retries
    // in that process until restart rather than auto-start another root service.
    private static final AtomicBoolean STARTUP_UNCERTAIN = new AtomicBoolean();
    private RootPlatform() {}
    private static String finish(String value) {
        String safe=RootContextCode.checked(value);Log.i("HookLabRoot","ROOT_PLATFORM_RESULT="+safe);return safe;
    }
    public static String inspect(Context context) {
        if(Looper.myLooper()==Looper.getMainLooper())return finish("ROOT_PLATFORM_MAIN_THREAD_REFUSED");
        if(STARTUP_UNCERTAIN.get())return finish("ROOT_PLATFORM_RESTART_REQUIRED");
        if(!ACTIVE.compareAndSet(false,true))return finish("ROOT_PLATFORM_BUSY");
        Handler main=new Handler(Looper.getMainLooper());
        Connection connection=new Connection(main);
        FutureTask<String> call=null;
        try {
            Intent intent=new Intent(context.getApplicationContext(),RootDiagnosticService.class);
            if(!main.post(() -> connection.bind(intent)))return finish("ROOT_PLATFORM_START_FAILED");
            if(!connection.ready.await(15,TimeUnit.SECONDS)) {
                STARTUP_UNCERTAIN.set(true);return finish("ROOT_PLATFORM_AUTH_OR_BIND_TIMEOUT");
            }
            IBinder binder=connection.binder;
            if(binder==null)return finish(connection.failure);
            call=new FutureTask<>(() -> inspectBinder(binder));
            Thread worker=new Thread(call,"hooklab-root-health-ipc");worker.setDaemon(true);worker.start();
            try {return finish(call.get(5,TimeUnit.SECONDS));}
            catch(TimeoutException timeout){STARTUP_UNCERTAIN.set(true);return finish("ROOT_PLATFORM_IPC_TIMEOUT");}
            catch(java.util.concurrent.ExecutionException failure){return finish("ROOT_PLATFORM_IPC_REFUSED");}
        }catch(InterruptedException interrupted){STARTUP_UNCERTAIN.set(true);Thread.currentThread().interrupt();return finish("ROOT_PLATFORM_INTERRUPTED");}
        catch(RuntimeException failure){return finish("ROOT_PLATFORM_START_FAILED");}
        finally {if(call!=null&&!call.isDone())call.cancel(true);connection.close();ACTIVE.set(false);}
    }
    private static String inspectBinder(IBinder binder) throws Exception {
        AtomicBoolean dead=new AtomicBoolean();IBinder.DeathRecipient death=()->dead.set(true);
        binder.linkToDeath(death,0);
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try {
            if(dead.get()||!binder.isBinderAlive())return "ROOT_PLATFORM_BINDER_DIED";
            request.writeInterfaceToken(RootDiagnosticService.DESCRIPTOR);
            if(!binder.transact(RootDiagnosticService.INSPECT,request,reply,0))return "ROOT_PLATFORM_IPC_REFUSED";
            reply.readException();String result=reply.readString();
            if(reply.dataAvail()!=0)return "ROOT_PLATFORM_RESULT_UNVERIFIED";
            return dead.get()?"ROOT_PLATFORM_BINDER_DIED":RootContextCode.checked(result);
        } finally {request.recycle();reply.recycle();try{binder.unlinkToDeath(death,0);}catch(RuntimeException ignored){}}
    }
    private static final class Connection implements ServiceConnection {
        final Handler main;final CountDownLatch ready=new CountDownLatch(1);
        final AtomicBoolean closed=new AtomicBoolean();
        volatile IBinder binder;volatile String failure="ROOT_PLATFORM_BIND_REFUSED";
        boolean bindAttempted; // main thread only
        Connection(Handler h){main=h;}
        void bind(Intent intent){
            if(closed.get())return;
            try{bindAttempted=true;RootService.bind(intent,this);}
            catch(RuntimeException denied){failure="ROOT_PLATFORM_BIND_REFUSED";ready.countDown();}
        }
        private void unbind(){if(bindAttempted)try{RootService.unbind(this);}catch(RuntimeException ignored){}}
        void close(){closed.set(true);main.post(this::unbind);}
        @Override public void onServiceConnected(ComponentName name,IBinder service){
            if(closed.get()){unbind();return;}
            if(service==null){failure="ROOT_PLATFORM_NULL_BINDING";}else binder=service;
            ready.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name){binder=null;failure="ROOT_PLATFORM_BINDER_DIED";ready.countDown();}
        @Override public void onBindingDied(ComponentName name){onServiceDisconnected(name);}
        @Override public void onNullBinding(ComponentName name){binder=null;failure="ROOT_PLATFORM_NULL_BINDING";ready.countDown();}
    }
}
