package com.itg.matrixtracecanary;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.plugin.DefaultPluginListener;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.TraceConfig;

/** Entry point for apps that do not already initialize Tencent Matrix. */
public final class MatrixTraceCanary {
    private static final String TAG = "MatrixTraceCanary";
    private static TracePlugin installedPlugin;
    private static ActivityTimingMonitor activityTimingMonitor;

    private MatrixTraceCanary() { }

    /** Installs Matrix Trace once, on the main thread, preferably in Application.onCreate(). */
    public static void install(Application application, MatrixTraceListener listener) {
        install(application, new MatrixTraceConfig.Builder().build(), listener);
    }

    public static void install(Application application, MatrixTraceConfig config,
                               MatrixTraceListener listener) {
        requireMainThread();
        if (application == null || config == null || listener == null) {
            throw new IllegalArgumentException("application, config and listener are required");
        }
        if (installedPlugin != null) {
            return;
        }
        if (Matrix.isInstalled()) {
            throw new IllegalStateException(
                    "Matrix is already installed; add createPlugin(config) to the existing Matrix.Builder");
        }
        TracePlugin plugin = createPlugin(config);
        Matrix.Builder builder = new Matrix.Builder(application)
                .plugin(plugin)
                .pluginListener(new DefaultPluginListener(application) {
                    @Override
                    public void onReportIssue(Issue issue) {
                        super.onReportIssue(issue);
                        try {
                            listener.onEvent(MatrixTraceEvent.fromIssue(issue));
                        } catch (RuntimeException error) {
                            Log.e(TAG, "Trace event listener failed", error);
                        }
                    }
                });
        Matrix.init(builder.build());
        installedPlugin = plugin;
        configureSlowMethods(config, listener);
        configureActivityTiming(application, config, listener);
        plugin.start();
    }

    /** Creates a plugin for hosts that already assemble a Matrix.Builder themselves. */
    public static TracePlugin createPlugin(MatrixTraceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config == null");
        }
        TraceConfig traceConfig = new TraceConfig.Builder()
                .enableFPS(config.isFpsEnabled())
                .enableAnrTrace(config.isAnrEnabled())
                .enableEvilMethodTrace(false)
                .enableStartup(false)
                .enableSignalAnrTrace(false)
                .isDebug(config.isDebug())
                .isDevEnv(false)
                .build();
        return new TracePlugin(traceConfig);
    }

    public static void start() {
        requireMainThread();
        TracePlugin plugin = requireInstalledPlugin();
        if (!plugin.isPluginStarted()) {
            plugin.start();
        }
        SlowMethodRecorder.resume();
        if (activityTimingMonitor != null) {
            activityTimingMonitor.start();
        }
    }

    public static void stop() {
        requireMainThread();
        TracePlugin plugin = requireInstalledPlugin();
        if (plugin.isPluginStarted()) {
            plugin.stop();
        }
        SlowMethodRecorder.pause();
        if (activityTimingMonitor != null) {
            activityTimingMonitor.stop();
        }
    }

    public static boolean isInstalled() {
        return installedPlugin != null;
    }

    /** Configures the app-class slow-method probe, including for hosts using their own Matrix.Builder. */
    public static void configureSlowMethods(MatrixTraceConfig config, MatrixTraceListener listener) {
        requireMainThread();
        if (config == null || listener == null) {
            throw new IllegalArgumentException("config and listener are required");
        }
        SlowMethodRecorder.configure(config, listener);
    }

    /** Registers Activity first-frame timing, including for hosts using their own Matrix.Builder. */
    public static void configureActivityTiming(Application application, MatrixTraceConfig config,
                                               MatrixTraceListener listener) {
        requireMainThread();
        if (application == null || config == null || listener == null) {
            throw new IllegalArgumentException("application, config and listener are required");
        }
        if (activityTimingMonitor != null) {
            activityTimingMonitor.stop();
        }
        activityTimingMonitor = new ActivityTimingMonitor(application, config, listener);
        activityTimingMonitor.start();
        if (config.isAnrEnabled()) {
            HistoricalAnrReporter.check(application, listener);
        }
    }

    /** Begins explicit timing for Fragment or Compose navigation; finish when the destination is visible. */
    public static PageTransition beginPageTransition(String fromScene, String toScene) {
        requireMainThread();
        if (activityTimingMonitor == null) {
            throw new IllegalStateException("Call install() or configureActivityTiming() first");
        }
        if (fromScene == null || fromScene.isEmpty() || toScene == null || toScene.isEmpty()) {
            throw new IllegalArgumentException("fromScene and toScene are required");
        }
        return new PageTransition(activityTimingMonitor, fromScene, toScene,
                SystemClock.elapsedRealtimeNanos());
    }

    public static final class PageTransition {
        private final ActivityTimingMonitor monitor;
        private final String fromScene;
        private final String toScene;
        private final long startNs;
        private boolean finished;

        private PageTransition(ActivityTimingMonitor monitor, String fromScene,
                               String toScene, long startNs) {
            this.monitor = monitor;
            this.fromScene = fromScene;
            this.toScene = toScene;
            this.startNs = startNs;
        }

        public void finish() {
            requireMainThread();
            if (!finished) {
                finished = true;
                monitor.reportManualPage(fromScene, toScene, startNs);
            }
        }

        public void cancel() {
            requireMainThread();
            finished = true;
        }
    }

    private static TracePlugin requireInstalledPlugin() {
        if (installedPlugin == null) {
            throw new IllegalStateException("Call install() first");
        }
        return installedPlugin;
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Call MatrixTraceCanary on the main thread");
        }
    }
}
