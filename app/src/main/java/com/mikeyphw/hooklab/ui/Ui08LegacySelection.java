package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.List;
import java.util.Objects;

/** Legacy package-only selection is safe only for an unambiguous registered hook.
 * Never route an old package state to the first matching hook in a multi-hook app.
 */
public final class Ui08LegacySelection {
    private Ui08LegacySelection() { }
    public static String resolveUnique(List<TweakSpec> registered, String oldPackage) {
        Objects.requireNonNull(registered, "registered");
        if (oldPackage == null || oldPackage.isEmpty()) return null;
        String unique = null;
        for (TweakSpec spec : registered) {
            Objects.requireNonNull(spec, "registered spec");
            if (!oldPackage.equals(spec.packageName)) continue;
            if (unique != null) return null;
            unique = spec.id;
        }
        return unique;
    }
}
