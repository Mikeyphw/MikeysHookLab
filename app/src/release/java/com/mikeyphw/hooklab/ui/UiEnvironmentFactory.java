package com.mikeyphw.hooklab.ui;
import com.mikeyphw.hooklab.HookLabApp;
/** Release contains no fixture setter, alternate provider or override selection. */
final class UiEnvironmentFactory {
    private UiEnvironmentFactory() {}
    static UiEnvironment create(HookLabApp app) { return new UiEnvironment(app); }
}
