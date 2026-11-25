package com.mikeyphw.hooklab.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import androidx.appcompat.content.res.AppCompatResources;
import com.mikeyphw.hooklab.R;
import java.util.HashSet;
import java.util.Set;

/** Per-Activity bounded cache of independent drawable states; never caches failures. */
final class AppIconProvider {
    enum Availability { AVAILABLE, NOT_INSTALLED, UNKNOWN }
    static final class Entry {
        final String label;
        final Drawable drawable;
        final Availability availability;
        Entry(String label,Drawable drawable,Availability availability){this.label=label;this.drawable=drawable;this.availability=availability;}
    }
    private static final int MAX=32;
    private final Context context;
    private final PackageManager manager;
    private final Set<String> visible;
    private final IconStateCache<Drawable.ConstantState> cache=new IconStateCache<>(MAX);
    private Configuration configuration;
    AppIconProvider(Context context,Set<String> visible){this.context=context;this.manager=context.getPackageManager();this.visible=new HashSet<>(visible);configuration=new Configuration(context.getResources().getConfiguration());}
    void invalidate(String pkg){cache.invalidate(pkg);}
    void clear(){cache.clear();}
    private Entry fallback(String pkg,Availability state){return new Entry(pkg,AppCompatResources.getDrawable(context,R.drawable.ic_hooklab),state);}
    Entry load(String pkg){
        Configuration now=context.getResources().getConfiguration();
        if(!configuration.equals(now)){clear();configuration=new Configuration(now);}
        if(!visible.contains(pkg))return fallback(pkg,Availability.UNKNOWN);
        try{
            PackageInfo info=manager.getPackageInfo(pkg,0);
            ApplicationInfo app=info.applicationInfo;
            if(app==null){cache.invalidate(pkg);return fallback(pkg,Availability.UNKNOWN);}
            long version=info.getLongVersionCode();
            Drawable.ConstantState state=cache.get(pkg,version,info.lastUpdateTime);
            Drawable icon;
            if(state==null){
                icon=manager.getApplicationIcon(app);
                if(icon==null){cache.invalidate(pkg);return fallback(pkg,Availability.UNKNOWN);}
                state=icon.getConstantState();
                if(state!=null){cache.put(pkg,version,info.lastUpdateTime,state);icon=state.newDrawable(context.getResources(),context.getTheme());}
            }else icon=state.newDrawable(context.getResources(),context.getTheme());
            CharSequence label=manager.getApplicationLabel(app);
            return new Entry(label==null||label.toString().trim().isEmpty()?pkg:label.toString(),icon.mutate(),Availability.AVAILABLE);
        }catch(PackageManager.NameNotFoundException failure){cache.invalidate(pkg);return fallback(pkg,Availability.NOT_INSTALLED);}
        catch(RuntimeException failure){cache.invalidate(pkg);return fallback(pkg,Availability.UNKNOWN);}
    }
}
