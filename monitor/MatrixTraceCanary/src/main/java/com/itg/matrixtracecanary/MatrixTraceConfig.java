package com.itg.matrixtracecanary;

/** Runtime options for Matrix Trace. Method-level tracing requires build-time instrumentation. */
public final class MatrixTraceConfig {
    private final boolean fpsEnabled;
    private final boolean anrEnabled;
    private final boolean debug;
    private final boolean slowMethodEnabled;
    private final long slowMethodThresholdMs;
    private final boolean startupEnabled;
    private final boolean pageSwitchEnabled;

    private MatrixTraceConfig(Builder builder) {
        fpsEnabled = builder.fpsEnabled;
        anrEnabled = builder.anrEnabled;
        debug = builder.debug;
        slowMethodEnabled = builder.slowMethodEnabled;
        slowMethodThresholdMs = builder.slowMethodThresholdMs;
        startupEnabled = builder.startupEnabled;
        pageSwitchEnabled = builder.pageSwitchEnabled;
    }

    public boolean isFpsEnabled() {
        return fpsEnabled;
    }

    public boolean isAnrEnabled() {
        return anrEnabled;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isSlowMethodEnabled() { return slowMethodEnabled; }
    public long getSlowMethodThresholdMs() { return slowMethodThresholdMs; }
    public boolean isStartupEnabled() { return startupEnabled; }
    public boolean isPageSwitchEnabled() { return pageSwitchEnabled; }

    public static final class Builder {
        private boolean fpsEnabled = true;
        private boolean anrEnabled = true;
        private boolean debug;
        private boolean slowMethodEnabled = true;
        private long slowMethodThresholdMs = 700;
        private boolean startupEnabled = true;
        private boolean pageSwitchEnabled = true;

        public Builder fpsEnabled(boolean enabled) {
            fpsEnabled = enabled;
            return this;
        }

        public Builder anrEnabled(boolean enabled) {
            anrEnabled = enabled;
            return this;
        }

        public Builder debug(boolean enabled) {
            debug = enabled;
            return this;
        }

        public Builder slowMethodEnabled(boolean enabled) {
            slowMethodEnabled = enabled;
            return this;
        }

        public Builder slowMethodThresholdMs(long thresholdMs) {
            slowMethodThresholdMs = thresholdMs;
            return this;
        }

        public Builder startupEnabled(boolean enabled) {
            startupEnabled = enabled;
            return this;
        }

        public Builder pageSwitchEnabled(boolean enabled) {
            pageSwitchEnabled = enabled;
            return this;
        }

        public MatrixTraceConfig build() {
            if (!fpsEnabled && !anrEnabled && !slowMethodEnabled
                    && !startupEnabled && !pageSwitchEnabled) {
                throw new IllegalArgumentException("Enable at least one monitor");
            }
            if (slowMethodThresholdMs < 1) {
                throw new IllegalArgumentException("slowMethodThresholdMs must be positive");
            }
            return new MatrixTraceConfig(this);
        }
    }
}
