package com.itg.matrixtracecanary;

import com.tencent.matrix.report.Issue;
import com.tencent.matrix.trace.config.SharePluginInfo;

import org.json.JSONObject;

/** Immutable snapshot of a Matrix issue. Callback threads are chosen by Matrix. */
public final class MatrixTraceEvent {
    public enum Kind { FPS, BLOCK, ANR, SLOW_METHOD, STARTUP, PAGE_SWITCH, OTHER }

    private final Kind kind;
    private final String tag;
    private final String scene;
    private final long costMs;
    private final double fps;
    private final String threadStack;
    private final String contentJson;
    private final String methodName;
    private final String fromScene;
    private final String measurement;

    private MatrixTraceEvent(Kind kind, String tag, String scene, long costMs,
                             double fps, String threadStack, String contentJson,
                             String methodName, String fromScene, String measurement) {
        this.kind = kind;
        this.tag = tag;
        this.scene = scene;
        this.costMs = costMs;
        this.fps = fps;
        this.threadStack = threadStack;
        this.contentJson = contentJson;
        this.methodName = methodName;
        this.fromScene = fromScene;
        this.measurement = measurement;
    }

    /** Converts an issue emitted by the TracePlugin. Also useful with an existing Matrix listener. */
    public static MatrixTraceEvent fromIssue(Issue issue) {
        if (issue == null) {
            throw new IllegalArgumentException("issue == null");
        }
        JSONObject content = issue.getContent();
        if (content == null) {
            content = new JSONObject();
        }
        String tag = issue.getTag() == null ? "" : issue.getTag();
        String detail = content.optString(SharePluginInfo.ISSUE_STACK_TYPE, "");
        Kind kind;
        if (SharePluginInfo.TAG_PLUGIN_FPS.equals(tag)) {
            kind = Kind.FPS;
        } else if (SharePluginInfo.TAG_PLUGIN_STARTUP.equals(tag)) {
            kind = Kind.STARTUP;
        } else if ("ANR".equals(detail) || "SIGNAL_ANR".equals(detail)) {
            kind = Kind.ANR;
        } else if (SharePluginInfo.TAG_PLUGIN_EVIL_METHOD.equals(tag)) {
            kind = Kind.BLOCK;
        } else {
            kind = Kind.OTHER;
        }
        return new MatrixTraceEvent(
                kind,
                tag,
                content.optString(SharePluginInfo.ISSUE_SCENE, ""),
                content.optLong(SharePluginInfo.ISSUE_COST, -1L),
                content.optDouble(SharePluginInfo.ISSUE_FPS, Double.NaN),
                content.optString(SharePluginInfo.ISSUE_THREAD_STACK, ""),
                content.toString(), "", "", "");
    }

    static MatrixTraceEvent slowMethod(String methodName, long costMs) {
        JSONObject content = new JSONObject();
        try {
            content.put("method", methodName);
            content.put("cost", costMs);
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
        return new MatrixTraceEvent(Kind.SLOW_METHOD, "Itg_SlowMethod", "", costMs,
                Double.NaN, "", content.toString(), methodName, "", "method_exit");
    }

    static MatrixTraceEvent timing(Kind kind, String fromScene, String scene,
                                   long costMs, String measurement) {
        JSONObject content = new JSONObject();
        try {
            content.put("fromScene", fromScene);
            content.put("scene", scene);
            content.put("cost", costMs);
            content.put("measurement", measurement);
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
        String tag = kind == Kind.STARTUP ? "Itg_Startup" : "Itg_PageSwitch";
        return new MatrixTraceEvent(kind, tag, scene, costMs, Double.NaN, "",
                content.toString(), "", fromScene, measurement);
    }

    static MatrixTraceEvent historicalAnr(long timestampMs, String processName,
                                          String description) {
        JSONObject content = new JSONObject();
        try {
            content.put("timestampMs", timestampMs);
            content.put("processName", processName);
            content.put("description", description);
            content.put("measurement", "historical_exit_reason_anr");
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
        return new MatrixTraceEvent(Kind.ANR, "Itg_HistoricalAnr", "", -1L,
                Double.NaN, "", content.toString(), "", "",
                "historical_exit_reason_anr");
    }

    public Kind getKind() { return kind; }
    public String getTag() { return tag; }
    public String getScene() { return scene; }
    /** Returns -1 when Matrix did not provide a cost. */
    public long getCostMs() { return costMs; }
    /** Returns NaN when Matrix did not provide FPS. */
    public double getFps() { return fps; }
    public String getThreadStack() { return threadStack; }
    /** Complete Matrix issue payload for storage or custom analysis. */
    public String getContentJson() { return contentJson; }
    /** Fully qualified method name for SLOW_METHOD events; empty for Matrix issues. */
    public String getMethodName() { return methodName; }
    /** Previous page for PAGE_SWITCH events. */
    public String getFromScene() { return fromScene; }
    /** Timing definition, such as process_start_to_first_frame. */
    public String getMeasurement() { return measurement; }
}
