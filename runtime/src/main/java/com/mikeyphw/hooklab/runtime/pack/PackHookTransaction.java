package com.mikeyphw.hooklab.runtime.pack;

import com.mikeyphw.hooklab.sdk.PackDiagnostics;
import com.mikeyphw.hooklab.sdk.PackHookApi;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Parent-owned hook registration. Until commit, every installed callback is pass-through. */
public final class PackHookTransaction implements PackHookApi {
    public interface Handle {void unhook();}
    public interface Backend {
        boolean permits(Executable executable);
        Handle install(Executable executable,String id,Callback callback);
        void log(int priority,String tag,String message);
        void verbose(String event);
        void failed(String category);
    }
    private final Backend backend;
    private final BooleanSupplier eligibility;
    private final String prefix;
    private final List<Handle> handles=new ArrayList<>();
    private final Set<String> registrations=new HashSet<>();
    private final AtomicBoolean enabled=new AtomicBoolean(),failed=new AtomicBoolean();
    private boolean sealed;
    public PackHookTransaction(String hookId,Backend backend,BooleanSupplier eligibility){
        PackPolicy.hook(hookId);this.backend=java.util.Objects.requireNonNull(backend);this.eligibility=java.util.Objects.requireNonNull(eligibility);
        switch(hookId){
            case "neostore.obtainx.manual":prefix="hooklab.obtainx.neo.";break;
            case "droidify.obtainx.manual":prefix="hooklab.obtainx.droidify.";break;
            case "wordmaster.night.filter":prefix="hooklab.wordmaster.night.";break;
            case "chatgpt.codeblock.copy.probe":prefix="hooklab.cgt01.p06.";break;
            case "chatgpt.codeblock.termux.handoff":prefix="hooklab.cgt01.p08.";break;
            default:throw new IllegalArgumentException("unreviewed registration");
        }
    }
    @Override public Builder hook(Executable executable){
        if(executable==null||!backend.permits(executable))throw new IllegalArgumentException("hook executable refused");
        return new Builder(){private String id;private boolean protective,used;
            @Override public Builder setId(String value){id=value;return this;}
            @Override public Builder setExceptionMode(ExceptionMode mode){protective=mode==ExceptionMode.PROTECTIVE;return this;}
            @Override public void intercept(Callback callback){if(used||!protective)throw new IllegalArgumentException("invalid hook builder");used=true;register(executable,id,callback);}
        };
    }
    private synchronized void register(Executable executable,String id,Callback callback){
        if(sealed||failed.get()||handles.size()>=64||id==null||!id.startsWith(prefix)||!id.matches("[a-z][a-z0-9_.-]{1,127}")||callback==null||!registrations.add(executable.toGenericString()+"\n"+id))throw new IllegalArgumentException("registration refused");
        Handle handle=backend.install(executable,id,chain->invoke(callback,chain));
        if(handle==null)throw new IllegalStateException("missing registration handle");handles.add(handle);
    }
    public synchronized void commit(){
        if(sealed||failed.get()||handles.isEmpty()||!eligible())throw new IllegalStateException("registration commit refused");sealed=true;enabled.set(true);
    }
    private boolean eligible(){try{return eligibility.getAsBoolean();}catch(RuntimeException|LinkageError e){return false;}}
    @Override public boolean active(){return enabled.get()&&!failed.get()&&eligible();}
    /** SDK callbacks are protected by the real framework. Never swallow original host exceptions. */
    private Object invoke(Callback callback,Chain chain)throws Throwable {
        if(!active())return chain.proceed();
        class Tracked implements Chain {
            Throwable original;
            @Override public Object getThisObject(){return chain.getThisObject();}
            @Override public Object getArg(int i){return chain.getArg(i);}
            @Override public Object proceed()throws Throwable{try{return chain.proceed();}catch(Throwable e){original=e;throw e;}}
            @Override public Object proceed(Object[] args)throws Throwable{try{return chain.proceed(args);}catch(Throwable e){original=e;throw e;}}
        }
        Tracked tracked=new Tracked();try{return callback.intercept(tracked);}catch(Throwable e){PackDiagnostics.rethrowFatal(e);if(e!=tracked.original){rollback();report(PackDiagnostics.failureCategory(e));}throw e;}
    }
    private void report(String category){try{backend.failed(category);}catch(RuntimeException|LinkageError ignored){/* diagnostics cannot hide failure */}}
    public synchronized void rollback(){
        enabled.set(false);failed.set(true);sealed=true;
        for(int i=handles.size()-1;i>=0;i--)try{handles.get(i).unhook();}catch(Throwable e){PackDiagnostics.rethrowFatal(e);report("UNHOOK_"+PackDiagnostics.failureCategory(e));}
        handles.clear();
    }
    @Override public void log(int priority,String tag,String message){backend.log(priority,tag,message);}
    @Override public void verbose(String event){backend.verbose(event);}
}
