import com.mikeyphw.hooklab.runtime.pack.PackHookTransaction;
import com.mikeyphw.hooklab.sdk.PackHookApi;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual parent-owned transaction behavior, with an explicitly synthetic framework. */
public final class Mod02HookTransactionChecks {
    private static final String ID="wordmaster.night.filter", PREFIX="hooklab.wordmaster.night.";
    private static int count;
    public static void target() { }
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;}
    private static final class Backend implements PackHookTransaction.Backend {
        final List<PackHookApi.Callback> hooks=new ArrayList<>();final List<Integer> removed=new ArrayList<>();int failures;boolean refuse,permit=true;
        public boolean permits(Executable e){return permit;}
        public PackHookTransaction.Handle install(Executable e,String id,PackHookApi.Callback callback){if(refuse)throw new IllegalStateException("fixture registration failure");int i=hooks.size();hooks.add(callback);return ()->removed.add(i);}
        public void log(int priority,String tag,String message){}
        public void verbose(String event){}
        public void failed(String category){failures++;}
    }
    private static final class Chain implements PackHookApi.Chain {
        int calls;RuntimeException original;
        public Object getThisObject(){return null;}
        public Object getArg(int index){return null;}
        public Object proceed(){calls++;if(original!=null)throw original;return "BASE";}
        public Object proceed(Object[] args){return proceed();}
    }
    private static void register(PackHookTransaction t,String id,PackHookApi.Callback callback)throws Exception{
        t.hook(Mod02HookTransactionChecks.class.getDeclaredMethod("target")).setId(id).setExceptionMode(PackHookApi.ExceptionMode.PROTECTIVE).intercept(callback);
    }
    public static void main(String[] args)throws Throwable {
        AtomicBoolean eligible=new AtomicBoolean(true);Backend b=new Backend();PackHookTransaction t=new PackHookTransaction(ID,b,eligible::get);
        register(t,PREFIX+"one",c->c.proceed()+"_PACK");Chain c=new Chain();check("BASE".equals(b.hooks.get(0).intercept(c))&&c.calls==1,"uncommitted pass-through");
        try{register(t,PREFIX+"one",PackHookApi.Chain::proceed);throw new AssertionError("duplicate accepted");}catch(IllegalArgumentException expected){count++;}
        t.commit();check("BASE_PACK".equals(b.hooks.get(0).intercept(new Chain())),"committed callback");
        eligible.set(false);check("BASE".equals(b.hooks.get(0).intercept(new Chain())),"revoked pass-through");eligible.set(true);
        Chain original=new Chain();original.original=new IllegalStateException("host original");try{b.hooks.get(0).intercept(original);throw new AssertionError("host error swallowed");}catch(IllegalStateException e){check(e==original.original&&b.failures==0&&t.active(),"original preserved");}
        t.rollback();check(b.removed.equals(List.of(0))&&!t.active(),"rollback");
        Backend partial=new Backend();PackHookTransaction p=new PackHookTransaction(ID,partial,()->true);register(p,PREFIX+"first",PackHookApi.Chain::proceed);partial.refuse=true;
        try{register(p,PREFIX+"second",PackHookApi.Chain::proceed);throw new AssertionError("registration accepted");}catch(IllegalStateException expected){p.rollback();check(partial.removed.equals(List.of(0)),"partial cleanup");}
        Backend f=new Backend();PackHookTransaction q=new PackHookTransaction(ID,f,()->true);register(q,PREFIX+"a",x->{throw new IllegalArgumentException("callback failure");});register(q,PREFIX+"b",PackHookApi.Chain::proceed);q.commit();
        try{f.hooks.get(0).intercept(new Chain());throw new AssertionError("callback error swallowed");}catch(IllegalArgumentException expected){check(f.failures==1&&!q.active()&&f.removed.equals(List.of(1,0)),"quarantine and reverse cleanup");}
        check("BASE".equals(f.hooks.get(1).intercept(new Chain())),"quarantine bypass");
        Backend denied=new Backend();denied.permit=false;PackHookTransaction d=new PackHookTransaction(ID,denied,()->true);try{register(d,PREFIX+"denied",PackHookApi.Chain::proceed);throw new AssertionError("disallowed executable");}catch(IllegalArgumentException expected){check(denied.hooks.isEmpty(),"denied no registration");}
        System.out.println("MOD02_HOOK_TRANSACTION_CHECKS_PASS "+count);
    }
}
