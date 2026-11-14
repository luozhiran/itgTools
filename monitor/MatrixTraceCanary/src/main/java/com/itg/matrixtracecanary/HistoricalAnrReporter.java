package com.itg.matrixtracecanary;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.List;

/** Reports system-confirmed ANR exits from previous processes on Android 11+. */
final class HistoricalAnrReporter {
    private static final String PREFS = "itg_matrix_trace_canary";
    private static final String LAST_ANR_TIME = "last_anr_timestamp_ms";

    private HistoricalAnrReporter() { }

    static void check(Application application, MatrixTraceListener listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        new Thread(() -> readAndReport(application, listener), "Matrix-AnrExit").start();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void readAndReport(Application application, MatrixTraceListener listener) {
        try {
            ActivityManager manager = (ActivityManager)
                    application.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return;
            }
            SharedPreferences prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long lastReported = prefs.getLong(LAST_ANR_TIME, 0L);
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(
                    application.getPackageName(), 0, 10);
            // Android returns newest first; deliver unseen records in chronological order.
            for (int i = exits.size() - 1; i >= 0; i--) {
                ApplicationExitInfo exit = exits.get(i);
                long timestamp = exit.getTimestamp();
                if (exit.getReason() != ApplicationExitInfo.REASON_ANR
                        || timestamp <= lastReported) {
                    continue;
                }
                String processName = exit.getProcessName() == null ? "" : exit.getProcessName();
                String description = exit.getDescription() == null ? "" : exit.getDescription();
                try {
                    listener.onEvent(MatrixTraceEvent.historicalAnr(
                            timestamp, processName, description));
                } catch (RuntimeException callbackError) {
                    Log.e("MatrixTraceCanary", "Historical ANR listener failed", callbackError);
                }
                lastReported = timestamp;
                prefs.edit().putLong(LAST_ANR_TIME, lastReported).apply();
            }
        } catch (RuntimeException error) {
            Log.e("MatrixTraceCanary", "Reading historical ANRs failed", error);
        }
    }
}
