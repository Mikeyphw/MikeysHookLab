package com.mikeyphw.hooklab.mcc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Root executes only under HookLab's UID, never from the hooked MCC process. */
public final class MccRefillWorker {
    private static final int TIMEOUT_SECONDS = 20;
    private MccRefillWorker() { }

    /** Strictly resolve MCC's installed data directory for the current Android user. */
    @SuppressWarnings("deprecation")
    private static String installedDataDir(Context context) {
        try {
            ApplicationInfo info=context.getPackageManager().getApplicationInfo(MccIds.PACKAGE,0);
            return MccRefillCommand.checkedDataDir(info.dataDir);
        } catch (PackageManager.NameNotFoundException | SecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static String relaunch(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getPackageManager().getLaunchIntentSenderForPackage(MccIds.PACKAGE)
                    .sendIntent(context,0,null,null,null);
            } else {
                Intent intent=context.getPackageManager().getLaunchIntentForPackage(MccIds.PACKAGE);
                if (intent == null) return "RELAUNCH_UNAVAILABLE";
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return "RELAUNCH_REQUESTED_NOT_VERIFIED";
        } catch (Throwable error) {
            return "RELAUNCH_FAILED_"+error.getClass().getSimpleName();
        }
    }

    private static String failure(int code) {
        switch (code) {
            case 51: return "BALANCE_FILE_NOT_FOUND";
            case 52: return "UNSAFE_STORAGE_REFUSED";
            case 53: return "MULTIPLE_BALANCE_FILES_REFUSED";
            case 54: case 55: return "UNKNOWN_BALANCE_FORMAT_REFUSED";
            case 56: return "UNRECOGNIZED_ACCOUNT_FILE_REFUSED";
            case 57: return "BALANCE_CHANGED_DURING_OPERATION";
            case 58: return "STORAGE_DIRECTORY_UNAVAILABLE_FROM_HOOKLAB_ROOT";
            case 59: return "WRITE_VERIFICATION_FAILED";
            case 70: return "ROOT_NOT_GRANTED";
            default: return "ROOT_COMMAND_EXIT_"+code;
        }
    }

    private static String run(Context context, boolean probe) {
        String dir=installedDataDir(context);
        if (dir == null) return "MCC_DATA_DIRECTORY_UNVERIFIED";
        Process process=null;
        try {
            process=new ProcessBuilder("su","-c",MccRefillCommand.script(dir,probe))
                .redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT_SECONDS,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "ROOT_COMMAND_TIMEOUT";
            }
            int exit=process.exitValue();
            boolean ready=false, written=false, already=false, fallback=false;
            int primaryVisible=-1, aliasVisible=-1;
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                for (int i=0; i<40 && (line=reader.readLine())!=null; i++) {
                    if ("MCC_STORAGE_READY".equals(line)) ready=true;
                    if ("MCC_BALANCE_WRITE_VERIFIED".equals(line)) written=true;
                    if ("MCC_BALANCE_ALREADY_NINE".equals(line)) already=true;
                    if ("MCC_STORAGE_FALLBACK_USED".equals(line)) fallback=true;
                    if (line.matches("MCC_SCAN_VISIBLE_PRIMARY=[0-3]"))
                        primaryVisible=line.charAt(line.length()-1)-'0';
                    if (line.matches("MCC_SCAN_VISIBLE_ALIAS=[0-6]"))
                        aliasVisible=line.charAt(line.length()-1)-'0';
                    // Ignore all other root output. Never expose account file paths or data.
                }
            }
            if (exit!=0) {
                String code=failure(exit);
                if ((exit==51 || exit==58) && primaryVisible>=0 && aliasVisible>=0)
                    return code+"_P"+primaryVisible+"_A"+aliasVisible;
                return code;
            }
            if (probe) return ready ? (fallback ? "BALANCE_FILE_READY_USER0_ALIAS" : "BALANCE_FILE_READY") : "ROOT_PROBE_UNVERIFIED";
            if (already) return "ALREADY_9_NO_RESTART";
            if (written) return "WRITE_9_VERIFIED_"+(fallback ? "USER0_ALIAS_" : "")+relaunch(context);
            return "ROOT_RESULT_UNVERIFIED";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "ROOT_INTERRUPTED";
        } catch (Exception ex) {
            return "ROOT_UNAVAILABLE_"+ex.getClass().getSimpleName();
        } finally {
            if (process!=null) process.destroy();
        }
    }

    public static synchronized String inspect(Context context) { return run(context,true); }
    public static synchronized String setNine(Context context) { return run(context,false); }
}
