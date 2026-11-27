package com.mikeyphw.hooklab.sdk;

/** Same bounded failure vocabulary as the legacy adapter. */
public final class PackDiagnostics {
    private PackDiagnostics(){}
    public static String failureCategory(Throwable failure){
        if(failure instanceof LinkageError)return "LINKAGE";
        if(failure instanceof SecurityException)return "SECURITY";
        if(failure instanceof RuntimeException)return "RUNTIME";
        if(failure instanceof Error)return "ERROR";
        return "CHECKED";
    }
    public static void rethrowFatal(Throwable failure){if(failure instanceof ThreadDeath)throw (ThreadDeath)failure;if(failure instanceof VirtualMachineError)throw (VirtualMachineError)failure;}
}
