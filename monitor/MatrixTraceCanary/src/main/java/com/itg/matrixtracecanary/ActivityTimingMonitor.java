package com.itg.matrixtracecanary;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.IdentityHashMap;
import java.util.Map;

/** Activity first-frame timing for process startup, foreground return and page switches. */
final class ActivityTimingMonitor implements Application.ActivityLifecycleCallbacks {
    private final Application application;
    private final MatrixTraceConfig config;
    private final MatrixTraceListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Activity, Window.OnFrameMetricsAvailableListener> pendingFrames =
            new IdentityHashMap<>();
    private boolean running;
    private boolean seenFirstFrame;
    private int startedActivities;
    private long foregroundStartNs;
    private long lastPauseNs;
    private String lastPausedScene = "";

    ActivityTimingMonitor(Application application, MatrixTraceConfig config,
                          MatrixTraceListener listener) {
        this.application = application;
        this.config = config;
        this.listener = listener;
    }

    void start() {
        if (running || (!config.isStartupEnabled() && !config.isPageSwitchEnabled())) {
            return;
        }
        running = true;
        application.registerActivityLifecycleCallbacks(this);
    }

    void stop() {
        if (!running) {
            return;
        }
        running = false;
        application.unregisterActivityLifecycleCallbacks(this);
        for (Map.Entry<Activity, Window.OnFrameMetricsAvailableListener> entry
                : pendingFrames.entrySet()) {
            entry.getKey().getWindow().removeOnFrameMetricsAvailableListener(entry.getValue());
        }
        pendingFrames.clear();
        startedActivities = 0;
        foregroundStartNs = 0;
        lastPauseNs = 0;
        lastPausedScene = "";
    }

    boolean isPageSwitchEnabled() {
        return running && config.isPageSwitchEnabled();
    }

    void reportManualPage(String from, String to, long startNs) {
        if (isPageSwitchEnabled()) {
            long costMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
            dispatch(MatrixTraceEvent.timing(MatrixTraceEvent.Kind.PAGE_SWITCH,
                    from, to, Math.max(0, costMs), "manual_mark_to_visible"));
        }
    }

    private void dispatch(MatrixTraceEvent event) {
        try {
            listener.onEvent(event);
        } catch (RuntimeException error) {
            Log.e("MatrixTraceCanary", "Timing listener failed", error);
        }
    }

    private void removeFrameListener(Activity activity) {
        Window.OnFrameMetricsAvailableListener old = pendingFrames.remove(activity);
        if (old != null) {
            activity.getWindow().removeOnFrameMetricsAvailableListener(old);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }

    @Override public void onActivityStarted(Activity activity) {
        if (startedActivities == 0 && seenFirstFrame && config.isStartupEnabled()) {
            foregroundStartNs = SystemClock.elapsedRealtimeNanos();
        }
        startedActivities++;
    }

    @Override public void onActivityResumed(Activity activity) {
        removeFrameListener(activity);
        final String scene = activity.getClass().getName();
        final String from = lastPausedScene;
        final long pageStartNs = lastPauseNs;
        lastPausedScene = "";
        lastPauseNs = 0;

        Window.OnFrameMetricsAvailableListener frameListener = new Window.OnFrameMetricsAvailableListener() {
            @Override public void onFrameMetricsAvailable(Window window, FrameMetrics metrics,
                                                           int droppedReports) {
                if (pendingFrames.get(activity) != this) {
                    return;
                }
                removeFrameListener(activity);
                if (!running) {
                    return;
                }
                long nowNs = SystemClock.elapsedRealtimeNanos();
                boolean firstFrame = !seenFirstFrame;
                seenFirstFrame = true;
                if (firstFrame && config.isStartupEnabled()) {
                    long costMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime();
                    dispatch(MatrixTraceEvent.timing(MatrixTraceEvent.Kind.STARTUP,
                            "", scene, Math.max(0, costMs), "process_start_to_first_frame"));
                } else if (foregroundStartNs != 0 && config.isStartupEnabled()) {
                    long costMs = (nowNs - foregroundStartNs) / 1_000_000L;
                    dispatch(MatrixTraceEvent.timing(MatrixTraceEvent.Kind.STARTUP,
                            "", scene, Math.max(0, costMs), "foreground_start_to_first_frame"));
                }
                foregroundStartNs = 0;
                if (!firstFrame && config.isPageSwitchEnabled() && pageStartNs != 0
                        && !from.isEmpty() && !from.equals(scene)) {
                    long costMs = (nowNs - pageStartNs) / 1_000_000L;
                    dispatch(MatrixTraceEvent.timing(MatrixTraceEvent.Kind.PAGE_SWITCH,
                            from, scene, Math.max(0, costMs), "activity_pause_to_first_frame"));
                }
            }
        };
        pendingFrames.put(activity, frameListener);
        activity.getWindow().addOnFrameMetricsAvailableListener(frameListener, mainHandler);
    }

    @Override public void onActivityPaused(Activity activity) {
        removeFrameListener(activity);
        lastPausedScene = activity.getClass().getName();
        lastPauseNs = SystemClock.elapsedRealtimeNanos();
    }

    @Override public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) {
            lastPauseNs = 0;
            lastPausedScene = "";
        }
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }

    @Override public void onActivityDestroyed(Activity activity) {
        removeFrameListener(activity);
    }
}
