package com.mikeyphw.hooklab.core;
import java.util.Objects;
public final class TweakSpec {
    public final String id, packageName;
    public final long minVersion, maxVersion;
    public TweakSpec(String id, String packageName, long minVersion, long maxVersion) {
        this.id = Objects.requireNonNull(id);
        this.packageName = Objects.requireNonNull(packageName);
        if (!id.matches("[a-z][a-z0-9_.-]*") || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || minVersion < 1 || maxVersion < minVersion)
            throw new IllegalArgumentException("Invalid tweak identity or version range");
        this.minVersion = minVersion; this.maxVersion = maxVersion;
    }
    public boolean eligible(String loadedPackage, String process, long version, boolean optedIn) {
        // M0 policy allows only the package's main process. Future explicit process allowlists must be reviewed.
        return optedIn && packageName.equals(loadedPackage) && packageName.equals(process)
                && version >= minVersion && version <= maxVersion;
    }
}
