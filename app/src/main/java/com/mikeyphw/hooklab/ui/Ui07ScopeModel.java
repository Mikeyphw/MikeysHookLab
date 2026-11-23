package com.mikeyphw.hooklab.ui;

import com.mikeyphw.hooklab.core.TweakSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only UI projection of the native framework scope snapshot.
 * Never writes preferences, mutates LSPosed scope or derives authorization.
 */
public final class Ui07ScopeModel {
    private Ui07ScopeModel() { }
    public enum State { FRAMEWORK_UNAVAILABLE, SCOPE_UNKNOWN, APPROVED, NEEDS_APPROVAL, PENDING }
    public static final class Row {
        public final String packageName;
        public final State state;
        private Row(String pkg, State value) { packageName=pkg; state=value; }
        public boolean canRequest() { return state==State.NEEDS_APPROVAL; }
        public boolean canRemove() { return state==State.APPROVED; }
    }
    public static final class Listing {
        public final List<Row> targets;
        /** -1 means that no trusted scope snapshot exists. */
        public final int reviewedApproved, otherApproved;
        private Listing(List<Row> rows,int approved,int other){
            targets=Collections.unmodifiableList(new ArrayList<>(rows));
            reviewedApproved=approved;otherApproved=other;
        }
    }
    public static Listing project(List<TweakSpec> reviewed, boolean connected,
            Set<String> approved, Set<String> pending) {
        Objects.requireNonNull(reviewed,"reviewed hooks");
        Objects.requireNonNull(pending,"pending requests");
        Set<String> packages=new LinkedHashSet<>();
        for(TweakSpec spec:reviewed){
            Objects.requireNonNull(spec,"hook");
            packages.add(spec.packageName);
        }
        // Even if a disconnected framework leaves old data in memory, never
        // interpret those cached values as live approval.
        boolean verified=connected&&approved!=null;
        ArrayList<Row> rows=new ArrayList<>();
        int count=0;
        for(String pkg:packages){
            State state=!connected?State.FRAMEWORK_UNAVAILABLE:
                    !verified?State.SCOPE_UNKNOWN:
                    pending.contains(pkg)?State.PENDING:
                    approved.contains(pkg)?State.APPROVED:State.NEEDS_APPROVAL;
            if(verified&&approved.contains(pkg))count++;
            rows.add(new Row(pkg,state));
        }
        int extras=0;
        if(verified)for(String pkg:approved)if(!packages.contains(pkg))extras++;
        return new Listing(rows,verified?count:-1,verified?extras:-1);
    }
}
