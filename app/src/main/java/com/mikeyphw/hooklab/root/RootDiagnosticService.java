package com.mikeyphw.hooklab.root;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.system.Os;
import com.topjohnwu.superuser.ipc.RootService;
import java.io.BufferedReader;
import java.io.FileReader;

/** Private libsu IPC service; exactly one fixed health transaction. */
public final class RootDiagnosticService extends RootService {
    static final String DESCRIPTOR = "com.mikeyphw.hooklab.root.health.v1";
    static final int INSPECT = IBinder.FIRST_CALL_TRANSACTION;
    private static String readLink(String path) {
        try { return Os.readlink(path); } catch (Exception unavailable) { return null; }
    }
    private static String readFixed(String path, String prefix) {
        // Called only for our own proc metadata, not another application's files.
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            for (int i=0;i<128;i++) {
                String line=r.readLine();
                if(line==null || line.length()>4096) return null;
                if(prefix==null) return line.trim();
                if(line.startsWith(prefix)) return line.substring(prefix.length()).trim();
            }
        } catch (Exception unavailable) { return null; }
        return null;
    }
    @Override public IBinder onBind(Intent intent) {
        final int allowedUid = getApplicationInfo().uid;
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if(code==INTERFACE_TRANSACTION) { if(reply!=null)reply.writeString(DESCRIPTOR);return reply!=null; }
                // Binder may add transport flags (e.g. FLAG_COLLECT_NOTED_APP_OPS).
                // Only reject asynchronous calls, unknown transactions and missing replies.
                if(code!=INSPECT || reply==null || (flags & IBinder.FLAG_ONEWAY)!=0) {
                    Log.w("HookLabRoot","ROOT_PLATFORM_IPC_SERVER=TRANSACTION_REFUSED");return false;
                }
                try { data.enforceInterface(DESCRIPTOR); }
                catch(SecurityException invalid) { Log.w("HookLabRoot","ROOT_PLATFORM_IPC_SERVER=DESCRIPTOR_REFUSED");throw invalid; }
                if(data.dataAvail()!=0) {
                    Log.w("HookLabRoot","ROOT_PLATFORM_IPC_SERVER=PAYLOAD_REFUSED");throw new SecurityException("Unexpected root diagnostic payload");
                }
                if(allowedUid<10000 || Binder.getCallingUid()!=allowedUid) {
                    Log.w("HookLabRoot","ROOT_PLATFORM_IPC_SERVER=CALLER_REFUSED");throw new SecurityException("Root diagnostic caller refused");
                }
                final int pid=Binder.getCallingPid();
                String result="ROOT_PLATFORM_NOT_ROOT";
                if(Os.getuid()==0) {
                    String self=readLink("/proc/self/ns/mnt");
                    result="ROOT_PLATFORM_U"+RootContextCode.id(Os.getuid())
                        +"_G"+RootContextCode.id(Os.getgid())
                        +"_K"+RootContextCode.capability(readFixed("/proc/self/status","CapEff:"))
                        +"_I"+RootContextCode.compare(self,readLink("/proc/1/ns/mnt"))
                        +"_C"+RootContextCode.compare(self,pid>0?readLink("/proc/"+pid+"/ns/mnt"):null)
                        +"_S"+RootContextCode.security(readFixed("/proc/self/attr/current",null));
                }
                reply.writeNoException();reply.writeString(RootContextCode.checked(result));
                Log.i("HookLabRoot","ROOT_PLATFORM_IPC_SERVER="+(RootContextCode.isContext(result)?"CONTEXT_RETURNED":"NOT_ROOT"));return true;
            }
        };
    }
}
