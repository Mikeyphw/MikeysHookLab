package com.mikeyphw.hooklab.mcc;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local single-flight manual MCC control. No automatic operations.
 * Explicit UI confirmation is still required before each write; old Activity
 * instances can detach while a root worker finishes without losing its result.
 */
public final class MccManualOperation {
    public enum Stage { IDLE, RUNNING, COMPLETE }
    @FunctionalInterface public interface Task { String run() throws Exception; }
    @FunctionalInterface public interface Listener { void changed(Snapshot current); }
    public static final class Snapshot {
        public final Stage stage;
        public final boolean inspectionOnly;
        public final String code;
        public final long generation;
        private Snapshot(Stage state,boolean read,String result,long value){
            stage=state;inspectionOnly=read;code=result;generation=value;
        }
    }
    private static final MccManualOperation SHARED = new MccManualOperation();
    public static MccManualOperation shared() { return SHARED; }
    private final Set<Listener> listeners=new CopyOnWriteArraySet<>();
    private volatile Snapshot snapshot=new Snapshot(Stage.IDLE,false,"",0L);
    private MccManualOperation() { }
    public Snapshot snapshot() { return snapshot; }
    public void addListener(Listener listener) {
        Objects.requireNonNull(listener,"listener");
        listeners.add(listener);
        try{listener.changed(snapshot);}catch(RuntimeException ignored){}
    }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    private void notifyListeners(Snapshot value) {
        for(Listener listener:listeners){
            try{listener.changed(value);}catch(RuntimeException ignored){
                // UI observer bugs must never replay or block a root operation.
            }
        }
    }
    public boolean start(boolean readOnly,Task task) {
        Objects.requireNonNull(task,"manual user-requested operation");
        final Snapshot active;
        synchronized(this){
            if(snapshot.stage==Stage.RUNNING)return false;
            active=new Snapshot(Stage.RUNNING,readOnly,"",snapshot.generation+1);
            snapshot=active;
        }
        notifyListeners(active);
        Thread worker=new Thread(() -> {
            String result;
            try { result=task.run(); }
            catch(Exception refused) { result="MCC_MANUAL_OPERATION_FAILED"; }
            if(result==null || result.isEmpty())result="MCC_EMPTY_RESULT";
            // Only a bounded result code is retained; never an exception, path,
            // credential or third-party file contents.
            result=result.replaceAll("[^A-Za-z0-9_:.-]","_");
            if(result.length()>160)result=result.substring(0,160);
            final Snapshot completed;
            synchronized(this){
                if(snapshot.generation!=active.generation || snapshot.stage!=Stage.RUNNING)return;
                completed=new Snapshot(Stage.COMPLETE,readOnly,result,active.generation);
                snapshot=completed;
            }
            notifyListeners(completed);
        },"hooklab-mcc-manual");
        worker.start();
        return true;
    }
}
