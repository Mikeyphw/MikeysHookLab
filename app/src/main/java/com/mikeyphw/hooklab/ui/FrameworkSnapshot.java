package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.TweakCompatibility;
import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, evidence-only framework state. Unknown is never equivalent to false. */
public final class FrameworkSnapshot {
    public enum Connection { LOADING, DISCONNECTED, UNSUPPORTED, CONNECTED, UNAVAILABLE }

    public static final class Flag {
        public final Boolean enabled;
        public final Boolean locked;
        public final Long installedVersion;

        public Flag(Boolean enabled, Boolean locked, Long installedVersion) {
            this.enabled = enabled;
            this.locked = locked;
            this.installedVersion = installedVersion;
        }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Flag)) return false;
            Flag that = (Flag) value;
            return Objects.equals(enabled, that.enabled)
                    && Objects.equals(locked, that.locked)
                    && Objects.equals(installedVersion, that.installedVersion);
        }
        @Override public int hashCode() {
            return Objects.hash(enabled, locked, installedVersion);
        }
    }

    public final Connection connection;
    public final String frameworkName;
    public final String frameworkVersion;
    public final int apiVersion;
    /** Null means scope has not been verified. */
    public final Set<String> scope;
    public final Map<String, Flag> flags;
    /** Null means the framework preference is unavailable. */
    public final Boolean verboseLogging;

    private FrameworkSnapshot(Connection connection, String name, String version, int api,
                              Set<String> scope, Map<String, Flag> flags, Boolean verbose) {
        this.connection = Objects.requireNonNull(connection);
        this.frameworkName = name;
        this.frameworkVersion = version;
        this.apiVersion = api;
        this.scope = scope == null ? null : Collections.unmodifiableSet(new HashSet<>(scope));
        this.flags = Collections.unmodifiableMap(new HashMap<>(flags));
        this.verboseLogging = verbose;
    }
    public static FrameworkSnapshot loading() {
        return new FrameworkSnapshot(Connection.LOADING, null, null, 0, null,
                Collections.emptyMap(), null);
    }
    public static FrameworkSnapshot disconnected() {
        return new FrameworkSnapshot(Connection.DISCONNECTED, null, null, 0, null,
                Collections.emptyMap(), null);
    }
    public static FrameworkSnapshot unavailable() {
        return new FrameworkSnapshot(Connection.UNAVAILABLE, null, null, 0, null,
                Collections.emptyMap(), null);
    }
    public static FrameworkSnapshot unsupported(int api) {
        return new FrameworkSnapshot(Connection.UNSUPPORTED, null, null, api, null,
                Collections.emptyMap(), null);
    }
    public static FrameworkSnapshot connected(String name, String version, int api,
                                              Set<String> scope, Map<String, Flag> flags,
                                              Boolean verbose) {
        return new FrameworkSnapshot(Connection.CONNECTED, name, version, api, scope, flags, verbose);
    }
    public boolean ready() { return connection == Connection.CONNECTED; }
    public Boolean inScope(String pkg) { return scope == null ? null : scope.contains(pkg); }
    public Flag flag(String id) { return flags.get(id); }
    public TweakCompatibility.State eligibility(TweakSpec spec) {
        if (connection == Connection.LOADING || connection == Connection.UNAVAILABLE)
            return TweakCompatibility.State.SCOPE_UNKNOWN;
        if (!ready()) return TweakCompatibility.State.FRAMEWORK_UNAVAILABLE;
        Boolean approved = inScope(spec.packageName);
        Flag flag = flag(spec.id);
        if (approved == null) return TweakCompatibility.State.SCOPE_UNKNOWN;
        if (!approved) return TweakCompatibility.State.OUT_OF_SCOPE;
        if (flag == null || flag.enabled == null || flag.locked == null)
            return TweakCompatibility.State.SCOPE_UNKNOWN;
        return TweakCompatibility.assess(spec, true, approved, flag.enabled, flag.locked,
                flag.installedVersion);
    }
    @Override public boolean equals(Object value) {
        if (!(value instanceof FrameworkSnapshot)) return false;
        FrameworkSnapshot that = (FrameworkSnapshot) value;
        return connection == that.connection && apiVersion == that.apiVersion
                && Objects.equals(frameworkName, that.frameworkName)
                && Objects.equals(frameworkVersion, that.frameworkVersion)
                && Objects.equals(scope, that.scope)
                && Objects.equals(flags, that.flags)
                && Objects.equals(verboseLogging, that.verboseLogging);
    }
    @Override public int hashCode() {
        return Objects.hash(connection, frameworkName, frameworkVersion, apiVersion,
                scope, flags, verboseLogging);
    }
}
