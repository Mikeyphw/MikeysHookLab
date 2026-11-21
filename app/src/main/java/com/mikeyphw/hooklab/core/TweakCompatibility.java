package com.mikeyphw.hooklab.core;

import java.util.Objects;

/** Pure, explicitly three-valued compatibility assessment for UI and tests. */
public final class TweakCompatibility {
    private TweakCompatibility() {}

    public enum State {
        FRAMEWORK_UNAVAILABLE, SCOPE_UNKNOWN, OUT_OF_SCOPE, QUARANTINED,
        DISABLED, VERSION_UNVERIFIED, VERSION_UNSUPPORTED, ELIGIBLE
    }

    /** A null scope or version is unknown, never misrepresented as absent/compatible. */
    public static State assess(TweakSpec spec, boolean frameworkReady, Boolean inScope,
                               boolean enabled, boolean quarantined, Long verifiedVersion) {
        Objects.requireNonNull(spec, "spec");
        if (!frameworkReady) return State.FRAMEWORK_UNAVAILABLE;
        if (inScope == null) return State.SCOPE_UNKNOWN;
        if (!inScope) return State.OUT_OF_SCOPE;
        if (quarantined) return State.QUARANTINED;
        if (!enabled) return State.DISABLED;
        if (verifiedVersion == null || verifiedVersion < 1) return State.VERSION_UNVERIFIED;
        return verifiedVersion >= spec.minVersion && verifiedVersion <= spec.maxVersion
                ? State.ELIGIBLE : State.VERSION_UNSUPPORTED;
    }
}
