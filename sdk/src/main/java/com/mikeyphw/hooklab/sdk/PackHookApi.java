package com.mikeyphw.hooklab.sdk;

import java.lang.reflect.Executable;

/** Parent-defined registration port. Dynamically loaded code never calls libxposed directly. */
public interface PackHookApi {
    enum ExceptionMode { PROTECTIVE }
    interface Chain {
        Object getThisObject();
        Object getArg(int index);
        Object proceed() throws Throwable;
        Object proceed(Object[] args) throws Throwable;
    }
    interface Callback {Object intercept(Chain chain)throws Throwable;}
    interface Builder {
        Builder setId(String id);
        Builder setExceptionMode(ExceptionMode mode);
        void intercept(Callback callback);
    }
    Builder hook(Executable executable);
    void log(int priority,String tag,String message);
    void verbose(String event);
    /** False after quarantine, revocation or change of the selected generation. */
    boolean active();
}
