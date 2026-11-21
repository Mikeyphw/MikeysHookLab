package com.mikeyphw.hooklab.obtainx;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/** Import preview only, on a verified explicit user click. No ObtainX database API. */
public final class ObtainXBridge {
    private ObtainXBridge() { }
    public enum Outcome { DISPATCHED_FOR_PREVIEW, UNSAFE_SOURCE, OBTAINX_UNAVAILABLE, LAUNCH_REFUSED }
    public static Outcome launch(Activity activity, SourcePolicy.Snapshot snapshot, String route) {
        String json=SourcePolicy.prepare(snapshot,route);
        if (json==null) return Outcome.UNSAFE_SOURCE;
        Uri uri=Uri.parse("obtainium://app/"+Uri.encode(json));
        if (!"obtainium".equals(uri.getScheme()) || !"app".equals(uri.getHost()))
            return Outcome.UNSAFE_SOURCE;
        Intent intent=new Intent(Intent.ACTION_VIEW,uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage(ObtainXIds.OBTAIN_X);
        try {
            ResolveInfo resolved=activity.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved==null || resolved.activityInfo==null ||
                !ObtainXIds.OBTAIN_X.equals(resolved.activityInfo.packageName) ||
                !resolved.activityInfo.exported || !resolved.activityInfo.enabled)
                return Outcome.OBTAINX_UNAVAILABLE;
            activity.startActivity(intent);
            return Outcome.DISPATCHED_FOR_PREVIEW; // Not proof that ObtainX imported the app.
        } catch (ActivityNotFoundException | SecurityException error) {
            return Outcome.LAUNCH_REFUSED;
        }
    }
}
