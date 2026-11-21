package com.mikeyphw.hooklab.mcc;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Module-UID-only root worker. Never invoked inside an LSPosed hooked app process. */
public final class MccRefillWorker {
    private static final int TIMEOUT_SECONDS = 20;
    private MccRefillWorker() { }

    private static String launch(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getPackageManager().getLaunchIntentSenderForPackage(MccIds.PACKAGE)
                    .sendIntent(context, 0, null, null, null);
            } else {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(MccIds.PACKAGE);
                if (intent == null) return "LAUNCH_UNAVAILABLE";
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return "LAUNCH_DISPATCHED_NOT_VERIFIED";
        } catch (Throwable ex) {
            return "LAUNCH_FAILED_" + ex.getClass().getSimpleName();
        }
    }

    public static synchronized String execute(Context ctx, boolean onlyWhenZero) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", MccRefillCommand.script(onlyWhenZero))
                .redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "ROOT_COMMAND_TIMEOUT";
            }
            int code = process.exitValue();
            boolean verified = false, skipped = false;
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                for (int count = 0; count < 100 && (line = lines.readLine()) != null; count++) {
                    if ("MCC_REFILL_WRITE_VERIFIED".equals(line)) verified = true;
                    if ("MCC_REFILL_AUTO_SKIP_NONZERO".equals(line)) skipped = true;
                }
            }
            if (verified && code == 0) return "WRITE_9_VERIFIED_" + launch(ctx);
            if (onlyWhenZero && skipped && code == 63) return "AUTO_SKIPPED_BALANCE_NOT_ZERO";
            return code == 70 ? "ROOT_UID_NOT_ZERO" : "REFILL_COMMAND_EXIT_" + code;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "REFILL_INTERRUPTED";
        } catch (Throwable ex) {
            Log.w("HookLabMCC", "Refill unavailable: " + ex.getClass().getSimpleName());
            return "REFILL_UNAVAILABLE_" + ex.getClass().getSimpleName();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
