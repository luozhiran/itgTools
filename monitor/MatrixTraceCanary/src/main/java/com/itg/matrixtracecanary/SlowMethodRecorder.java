package com.itg.matrixtracecanary;

import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/** Called by the optional AGP instrumentation plugin; not intended for direct app calls. */
public final class SlowMethodRecorder {
    private static volatile MatrixTraceListener listener;
    private static volatile long thresholdMs = 700;
    private static volatile boolean enabled;
    private static volatile boolean configuredEnabled;
    private static final ThreadLocal<Boolean> reporting = new ThreadLocal<>();

    private SlowMethodRecorder() { }

    static void configure(MatrixTraceConfig config, MatrixTraceListener newListener) {
        listener = newListener;
        thresholdMs = config.getSlowMethodThresholdMs();
        configuredEnabled = config.isSlowMethodEnabled();
        enabled = configuredEnabled;
    }

    static void pause() { enabled = false; }
    static void resume() { enabled = configuredEnabled && listener != null; }

    public static long enter() {
        if (!enabled || Boolean.TRUE.equals(reporting.get())
                || Thread.currentThread() != Looper.getMainLooper().getThread()) {
            return 0L;
        }
        return SystemClock.elapsedRealtimeNanos();
    }

    public static void exit(long startNs, String methodName) {
        if (startNs == 0L || !enabled) {
            return;
        }
        long costMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
        MatrixTraceListener callback = listener;
        if (costMs < thresholdMs || callback == null) {
            return;
        }
        try {
            reporting.set(true);
            callback.onEvent(MatrixTraceEvent.slowMethod(methodName, costMs));
        } catch (RuntimeException error) {
            Log.e("MatrixTraceCanary", "Slow method listener failed", error);
        } finally {
            reporting.remove();
        }
    }
}
