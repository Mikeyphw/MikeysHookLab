package com.mikeyphw.hooklab.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Platform-independent eligibility and fault isolation. No hooks without explicit opt-in. */
public final class TweakEngine {
    private TweakEngine() {}
    public interface Installer<T> { void install(T context) throws Exception; }
    public interface OptIn { boolean enabled(String id); }
    public interface FailureSink { void failed(String id, Exception cause); }

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

    /** Returns installed count. Installer and preference failures do not affect other entries. */
    public static <T> int dispatch(List<Entry<T>> entries, String pkg, String process,
                                   long version, OptIn optIn, T context, FailureSink errors) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(optIn, "optIn");
        Objects.requireNonNull(errors, "errors");
        if (pkg == null || process == null || version < 1) return 0;
        int installed = 0;
        for (Entry<T> entry : entries) {
            // Only consult preferences after exact package/process/version filtering.
            if (!entry.spec.eligible(pkg, process, version, true)) continue;
            try {
                if (!optIn.enabled(entry.spec.id)) continue;
                entry.installer.install(context);
                installed++;
            } catch (Exception failure) {
                errors.failed(entry.spec.id, failure);
            }
        }
        return installed;
    }
}
