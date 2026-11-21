package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Platform-independent, fail-closed eligibility, quarantine and fault isolation. */
public final class TweakEngine {
    private TweakEngine() {}
    public interface Installer<T> { void install(T context) throws Exception; }
    public interface OptIn { boolean enabled(String id); }
    public interface FailureSink { void failed(String id, Throwable cause); }
    public interface FaultPolicy {
        boolean quarantined(String id);
        void quarantine(String id) throws Exception;
    }
    public interface StatusSink {
        void recorded(String id, TweakDiagnostics.Outcome outcome, String category);
    }

    public static final class Entry<T> {
        public final TweakSpec spec;
        public final String title;
        public final String description;
        public final Installer<T> installer;
        public Entry(TweakSpec spec, String title, String description, Installer<T> installer) {
            this.spec = Objects.requireNonNull(spec, "spec");
            this.title = Objects.requireNonNull(title, "title");
            this.description = Objects.requireNonNull(description, "description");
            this.installer = Objects.requireNonNull(installer, "installer");
            if (title.trim().isEmpty() || description.trim().isEmpty())
                throw new IllegalArgumentException("Tweak metadata must be nonempty");
        }
    }

    public static <T> List<Entry<T>> immutableCatalog(List<Entry<T>> entries) {
        Objects.requireNonNull(entries, "entries");
        Set<String> ids = new HashSet<>();
        List<Entry<T>> copy = new ArrayList<>();
        for (Entry<T> entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.spec.id)) throw new IllegalArgumentException("Duplicate tweak ID: " + entry.spec.id);
            copy.add(entry);
        }
        return Collections.unmodifiableList(copy);
    }

    /** Legacy entry point for independent M1-M3 tests; runtime uses the hardened overload. */
    public static <T> int dispatch(List<Entry<T>> entries, String pkg, String process,
                                   long version, OptIn optIn, T context, FailureSink errors) {
        return dispatch(entries, pkg, process, version, optIn, context, errors,
                new FaultPolicy() {
                    @Override public boolean quarantined(String id) { return false; }
                    @Override public void quarantine(String id) { }
                }, (id, status, category) -> {});
    }

    /** Each entry is isolated; unsafe policy reads must never authorize a hook. */
    public static <T> int dispatch(List<Entry<T>> entries, String pkg, String process,
                                   long version, OptIn optIn, T context, FailureSink errors,
                                   FaultPolicy policy, StatusSink statuses) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(optIn, "optIn");
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(statuses, "statuses");
        if (pkg == null || process == null || version < 1) return 0;
        int installed = 0;
        for (Entry<T> entry : entries) {
            // Never query remote preferences for unrelated or unsupported apps.
            if (!entry.spec.eligible(pkg, process, version, true)) continue;
            String id = entry.spec.id;
            final boolean blocked;
            try { blocked = policy.quarantined(id); }
            catch (Throwable failure) {
                TweakDiagnostics.rethrowFatal(failure);
                report(errors, id, failure);
                record(statuses, id, TweakDiagnostics.Outcome.QUARANTINE_UNAVAILABLE, "POLICY");
                continue; // fail closed
            }
            if (blocked) {
                record(statuses, id, TweakDiagnostics.Outcome.QUARANTINED, "NONE");
                continue;
            }
            final boolean enabled;
            try { enabled = optIn.enabled(id); }
            catch (Throwable failure) {
                TweakDiagnostics.rethrowFatal(failure);
                report(errors, id, failure);
                record(statuses, id, TweakDiagnostics.Outcome.PREFERENCE_UNAVAILABLE, "PREFERENCE");
                continue; // fail closed; don't quarantine an installer that did not run
            }
            if (!enabled) {
                record(statuses, id, TweakDiagnostics.Outcome.DISABLED, "NONE");
                continue;
            }
            try {
                entry.installer.install(context);
                installed++;
                record(statuses, id, TweakDiagnostics.Outcome.INSTALLED, "NONE");
            } catch (Throwable failure) {
                TweakDiagnostics.rethrowFatal(failure);
                try { policy.quarantine(id); }
                catch (Throwable sinkError) {
                    TweakDiagnostics.rethrowFatal(sinkError);
                    report(errors, id, sinkError);
                }
                report(errors, id, failure);
                record(statuses, id, TweakDiagnostics.Outcome.INSTALL_FAILED,
                        TweakDiagnostics.failureCategory(failure));
            }
        }
        return installed;
    }

    private static void report(FailureSink errors, String id, Throwable failure) {
        try { errors.failed(id, failure); }
        catch (Throwable callbackFailure) { TweakDiagnostics.rethrowFatal(callbackFailure); }
    }

    private static void record(StatusSink statuses, String id, TweakDiagnostics.Outcome event, String category) {
        try { statuses.recorded(id, event, category); }
        catch (Throwable callbackFailure) { TweakDiagnostics.rethrowFatal(callbackFailure); }
    }
}
