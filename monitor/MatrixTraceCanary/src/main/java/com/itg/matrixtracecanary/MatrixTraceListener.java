package com.itg.matrixtracecanary;

/** Receives Matrix Trace issues on the thread that generated each issue. */
public interface MatrixTraceListener {
    void onEvent(MatrixTraceEvent event);
}
