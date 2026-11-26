package com.mikeyphw.hooklab.probe;

import com.mikeyphw.hooklab.sdk.HookSdk;
import dalvik.system.InMemoryDexClassLoader;

/** Fixed APK-owned debug fixture only. No general external pack entrypoint. */
public final class ProbeDexLoader {
    private ProbeDexLoader() {}
    public static HookSdk.Entrypoint loadVerified(byte[] expected,byte[] observed,String claimed) throws ReflectiveOperationException {
        ClassLoader parent=HookSdk.class.getClassLoader();
        InMemoryDexClassLoader dex=new InMemoryDexClassLoader(ProbeProtocol.verifiedDexBuffer(expected,observed,claimed),parent);
        Class<?> type=Class.forName(ProbeProtocol.ENTRY,false,dex);
        if(type.getClassLoader()!=dex||!HookSdk.Entrypoint.class.isAssignableFrom(type))
            throw new IllegalStateException("SDK class identity");
        return type.asSubclass(HookSdk.Entrypoint.class).getDeclaredConstructor().newInstance();
    }
}
