package com.itg.matrixtracecanary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.tencent.matrix.report.Issue;

import org.json.JSONObject;
import org.junit.Test;

public class MatrixTraceEventTest {
    @Test
    public void fpsIssueKeepsFullPayload() throws Exception {
        Issue issue = new Issue();
        issue.setTag("Trace_FPS");
        issue.setContent(new JSONObject()
                .put("scene", "HomeActivity")
                .put("fps", 47.5)
                .put("dropSum", new JSONObject().put("DROPPED_HIGH", 3)));

        MatrixTraceEvent event = MatrixTraceEvent.fromIssue(issue);

        assertEquals(MatrixTraceEvent.Kind.FPS, event.getKind());
        assertEquals("HomeActivity", event.getScene());
        assertEquals(47.5, event.getFps(), 0.001);
        assertEquals(-1L, event.getCostMs());
        assertTrue(event.getContentJson().contains("DROPPED_HIGH"));
    }

    @Test
    public void anrIssueExposesMainThreadStack() throws Exception {
        Issue issue = new Issue();
        issue.setTag("Trace_EvilMethod");
        issue.setContent(new JSONObject()
                .put("detail", "ANR")
                .put("cost", 5000)
                .put("threadStack", "main at Example.block()"));

        MatrixTraceEvent event = MatrixTraceEvent.fromIssue(issue);

        assertEquals(MatrixTraceEvent.Kind.ANR, event.getKind());
        assertEquals(5000L, event.getCostMs());
        assertEquals("main at Example.block()", event.getThreadStack());
    }

    @Test
    public void disabledConfigIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MatrixTraceConfig.Builder()
                .fpsEnabled(false).anrEnabled(false).slowMethodEnabled(false)
                .startupEnabled(false).pageSwitchEnabled(false).build());
    }

    @Test
    public void slowMethodEventIncludesNameAndDuration() {
        MatrixTraceEvent event = MatrixTraceEvent.slowMethod("example.Home#load()V", 901);

        assertEquals(MatrixTraceEvent.Kind.SLOW_METHOD, event.getKind());
        assertEquals("example.Home#load()V", event.getMethodName());
        assertEquals(901L, event.getCostMs());
        assertTrue(event.getContentJson().contains("example.Home#load()V"));
    }

    @Test
    public void slowMethodThresholdMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new MatrixTraceConfig.Builder()
                .slowMethodThresholdMs(0).build());
    }

    @Test
    public void startupAndPageSwitchEventsKeepMeasurement() {
        MatrixTraceEvent startup = MatrixTraceEvent.timing(
                MatrixTraceEvent.Kind.STARTUP, "", "Home", 523,
                "process_start_to_first_frame");
        MatrixTraceEvent page = MatrixTraceEvent.timing(
                MatrixTraceEvent.Kind.PAGE_SWITCH, "Home", "Details", 218,
                "activity_pause_to_first_frame");

        assertEquals(MatrixTraceEvent.Kind.STARTUP, startup.getKind());
        assertEquals("process_start_to_first_frame", startup.getMeasurement());
        assertEquals(523L, startup.getCostMs());
        assertEquals(MatrixTraceEvent.Kind.PAGE_SWITCH, page.getKind());
        assertEquals("Home", page.getFromScene());
        assertEquals("Details", page.getScene());
        assertEquals(218L, page.getCostMs());
    }

    @Test
    public void historicalAnrIsDistinguishedFromSuspectedAnr() {
        MatrixTraceEvent event = MatrixTraceEvent.historicalAnr(
                123456L, "example:main", "Input dispatching timed out");

        assertEquals(MatrixTraceEvent.Kind.ANR, event.getKind());
        assertEquals("historical_exit_reason_anr", event.getMeasurement());
        assertEquals(-1L, event.getCostMs());
        assertTrue(event.getContentJson().contains("123456"));
    }
}
