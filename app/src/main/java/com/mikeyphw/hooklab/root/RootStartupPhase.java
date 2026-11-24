package com.mikeyphw.hooklab.root;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded startup phases; a timed-out task can still have side effects. Never auto-retry. */
final class RootStartupPhase {
    interface Observer { void event(String phase, String state, long elapsedMs); }
    static final class Failure extends Exception {
        final String code; final boolean uncertain;
        Failure(String code, boolean uncertain) { super(code); this.code=code; this.uncertain=uncertain; }
    }
    private RootStartupPhase() {}
    static <T> T run(String name, String timeoutCode, String failureCode, long millis,
                     Executor executor, Callable<T> operation, Observer observer,
                     boolean uncertainOnFailure, boolean interruptWorker) throws Failure, InterruptedException {
        long start=System.nanoTime();
        observer.event(name,"BEGIN",0);
        FutureTask<T> future=new FutureTask<>(operation);
        try {
            executor.execute(future);
            T result=future.get(millis,TimeUnit.MILLISECONDS);
            observer.event(name,"OK",elapsed(start));return result;
        } catch (TimeoutException e) {
            future.cancel(interruptWorker);observer.event(name,"TIMEOUT",elapsed(start));
            throw new Failure(timeoutCode,true);
        } catch (ExecutionException | RuntimeException e) {
            future.cancel(interruptWorker);observer.event(name,"FAILED",elapsed(start));
            if(e instanceof ExecutionException) {
                Throwable cause=e.getCause();
                if(cause instanceof VirtualMachineError)throw (VirtualMachineError)cause;
                if(cause instanceof ThreadDeath)throw (ThreadDeath)cause;
            }
            throw new Failure(failureCode,uncertainOnFailure);
        } catch (InterruptedException e) {
            future.cancel(interruptWorker);observer.event(name,"INTERRUPTED",elapsed(start));throw e;
        }
    }
    private static long elapsed(long start){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));}
}
