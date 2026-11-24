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
import com.topjohnwu.superuser.Shell;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
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
    private static final RootStartupPhase.Observer STAGES=(phase,state,elapsed) ->
        Log.i("HookLabRoot","ROOT_PLATFORM_STAGE="+phase+"_"+state+" elapsed_ms="+elapsed);
    private static final Executor WORKER=command->{
        Thread worker=new Thread(command,"hooklab-root-health-phase");worker.setDaemon(true);worker.start();
    };
    public static String inspect(Context context) {
        if(Looper.myLooper()==Looper.getMainLooper())return finish("ROOT_PLATFORM_MAIN_THREAD_REFUSED");
        if(STARTUP_UNCERTAIN.get())return finish("ROOT_PLATFORM_RESTART_REQUIRED");
        if(!ACTIVE.compareAndSet(false,true))return finish("ROOT_PLATFORM_BUSY");
        Handler main=new Handler(Looper.getMainLooper());
        Connection connection=new Connection(main);
        Executor mainExecutor=command->{if(!main.post(command))throw new RejectedExecutionException();};
        try {
            // Follow the MMRL authorization-before-binding pattern; never set namespace flags.
            Shell shell=RootStartupPhase.run("AUTH","ROOT_PLATFORM_AUTH_TIMEOUT","ROOT_PLATFORM_AUTH_FAILED",
                35000,WORKER,Shell::getShell,STAGES,false,true);
            if(shell==null||!shell.isRoot()){
                STAGES.event("AUTH_ROOT","DENIED",0);return finish("ROOT_PLATFORM_NOT_ROOT");
            }
            STAGES.event("AUTH_ROOT","OK",0);
            Intent intent=new Intent(context.getApplicationContext(),RootDiagnosticService.class);
            Shell.Task task=RootStartupPhase.run("BIND_SETUP","ROOT_PLATFORM_BIND_SETUP_TIMEOUT","ROOT_PLATFORM_BIND_REFUSED",
                5000,mainExecutor,()->connection.prepare(intent,mainExecutor),STAGES,true,false);
            if(task!=null){
                // bindOrTask returns libsu's own launch task; run only that task on the verified shell.
                AtomicBoolean died=new AtomicBoolean();
                Shell.Task observed=new Shell.Task(){
                    @Override public void run(java.io.OutputStream in,java.io.InputStream out,java.io.InputStream err)throws java.io.IOException {
                        task.run(in,out,err);
                    }
                    @Override public void shellDied(){died.set(true);task.shellDied();}
                };
                RootStartupPhase.run("LAUNCH_SUBMISSION","ROOT_PLATFORM_LAUNCH_TIMEOUT","ROOT_PLATFORM_LAUNCH_FAILED",
                    10000,WORKER,()->{if(!shell.isRoot())throw new IllegalStateException();shell.execTask(observed);
                        if(died.get())throw new java.io.IOException();return null;},STAGES,true,true);
            }else STAGES.event("LAUNCH_SUBMISSION","NOT_NEEDED",0);
            // Task submission is not evidence that the remote process started or returned a Binder.
            IBinder binder=RootStartupPhase.run("BIND_CALLBACK","ROOT_PLATFORM_SERVICE_BIND_TIMEOUT","ROOT_PLATFORM_BIND_REFUSED",
                20000,WORKER,()->{connection.ready.await();return connection.binder;},STAGES,true,true);
            if(binder==null){STARTUP_UNCERTAIN.set(true);return finish(connection.failure);}
            return finish(RootStartupPhase.run("IPC","ROOT_PLATFORM_IPC_TIMEOUT","ROOT_PLATFORM_IPC_REFUSED",
                5000,WORKER,()->inspectBinder(binder),STAGES,true,true));
        }catch(RootStartupPhase.Failure failure){if(failure.uncertain)STARTUP_UNCERTAIN.set(true);return finish(failure.code);}
        catch(InterruptedException interrupted){STARTUP_UNCERTAIN.set(true);Thread.currentThread().interrupt();return finish("ROOT_PLATFORM_INTERRUPTED");}
        catch(RuntimeException failure){STARTUP_UNCERTAIN.set(true);return finish("ROOT_PLATFORM_START_FAILED");}
        finally {connection.close();ACTIVE.set(false);}
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
        Shell.Task prepare(Intent intent, Executor callbackExecutor){
            if(closed.get())throw new IllegalStateException("Closed");
            bindAttempted=true;
            return RootService.bindOrTask(intent,callbackExecutor,this);
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
