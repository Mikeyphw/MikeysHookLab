package com.mikeyphw.hooklab.packs;

import android.content.Context;
import android.view.View;
import com.mikeyphw.hooklab.sdk.ModuleUiResources;
import com.mikeyphw.hooklab.sdk.ModuleConfig;

/** Manager-side native custom UI ABI v1. Only explicitly approved self-authored
 * personal packs may be loaded. Same UID is NOT a security sandbox. Never add
 * credentials to this host. The manager controls lifecycle and sensitive actions.
 * A provider must rebuild from committed settings on each creation. */
public interface ModuleUiProvider {
    interface Host {
        Context themedContext();
        String moduleId();
        ModuleUiResources resources();
        ModuleConfig.Document wordMasterSettings();
        void reviewWordMasterSettings(ModuleConfig.Document draft);
        android.widget.TextView heading(CharSequence text);
        android.widget.Button button(CharSequence text,Runnable action);
    }
    View create(Host host);
    default void resume(){}
    default void pause(){}
    default void destroy(){}
}
