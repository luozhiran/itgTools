package com.itg.itg_file

import com.itg.itg_file.cleanup.CleanupAction
import com.itg.itg_file.cleanup.CleanupExecutor
import com.itg.itg_file.cleanup.CleanupRule
import com.itg.itg_file.cleanup.CleanupScheduleMode
import com.itg.itg_file.cleanup.CleanupPermissionRequest
import com.itg.itg_file.cleanup.CleanupTrigger
import com.itg.itg_file.cleanup.FileCleanupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CleanupExecutorTest {

    @Test
    fun clearDirectoryDeletesChildrenButKeepsDirectory() {
        val directory = Files.createTempDirectory("itg-clear-").toFile()
        File(directory, "nested").mkdirs()
        File(directory, "nested/data.txt").writeText("data")
        File(directory, "top.txt").writeText("data")

        val result = CleanupExecutor.execute(
            rule("clear", directory, CleanupAction.CLEAR_DIRECTORY)
        )

        assertTrue(result.success)
        assertEquals(3, result.deletedEntries)
        assertEquals("清理完成，共删除 3 项", result.message)
        assertTrue(directory.isDirectory)
        assertTrue(directory.listFiles().isNullOrEmpty())
        directory.delete()
    }

    @Test
    fun deleteTargetDeletesDirectoryItself() {
        val directory = Files.createTempDirectory("itg-delete-").toFile()
        File(directory, "data.txt").writeText("data")

        val result = CleanupExecutor.execute(
            rule("delete", directory, CleanupAction.DELETE_TARGET)
        )

        assertTrue(result.success)
        assertEquals(2, result.deletedEntries)
        assertFalse(directory.exists())
    }

    @Test
    fun missingTargetIsAnIdempotentSuccess() {
        val target = File(System.getProperty("java.io.tmpdir"), "itg-missing-${System.nanoTime()}")

        val result = CleanupExecutor.execute(
            rule("missing", target, CleanupAction.DELETE_TARGET)
        )

        assertTrue(result.success)
        assertEquals(0, result.deletedEntries)
        assertFalse(result.existedBefore)
        assertEquals("目标不存在，无需清理", result.message)
    }

    @Test
    fun clearDirectoryRejectsAFileTarget() {
        val file = File.createTempFile("itg-clear-file-", ".tmp")

        val result = CleanupExecutor.execute(
            rule("wrong-type", file, CleanupAction.CLEAR_DIRECTORY)
        )

        assertFalse(result.success)
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun cleanupRejectsFileSystemRoot() {
        val root = File.listRoots().first().canonicalFile

        val result = CleanupExecutor.execute(
            rule("root", root, CleanupAction.DELETE_TARGET)
        )

        assertFalse(result.success)
        assertTrue(root.exists())
    }

    @Test
    fun builderRejectsDuplicateKeys() {
        try {
            FileCleanupManager.builder()
                .clearOnAppStart("same", "first")
                .deleteAfterDelay("same", "second", 1L)
            fail("Expected duplicate key to be rejected")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }

    @Test
    fun configSpecifiesTargetActionAndApplicationTrigger() {
        val config = FileCleanupManager.builder()
            .clearOnAppBackground("cache", "/app/cache")
            .deleteOnAppStart("temp", "/app/temp.tmp")
            .build()

        assertEquals("/app/cache", config.rules[0].path)
        assertSame(CleanupAction.CLEAR_DIRECTORY, config.rules[0].action)
        assertSame(CleanupTrigger.OnAppBackground, config.rules[0].trigger)
        assertEquals("/app/temp.tmp", config.rules[1].path)
        assertSame(CleanupAction.DELETE_TARGET, config.rules[1].action)
        assertSame(CleanupTrigger.OnAppStart, config.rules[1].trigger)
    }

    @Test
    fun timedRuleCanRestartItsTimerAfterExecution() {
        val config = FileCleanupManager.builder()
            .clearAfterDelay(
                key = "repeating_cache",
                path = "/app/cache",
                delayMs = 10_000L,
                scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
            )
            .build()

        assertSame(CleanupScheduleMode.RESTART_AFTER_EXECUTION, config.rules.single().scheduleMode)
    }

    @Test
    fun delayedRuleCanKeepItsDeadlineAcrossRestarts() {
        val config = FileCleanupManager.builder()
            .clearAfterDelay(
                key = "persistent_delay",
                path = "/app/cache",
                delayMs = 10_000L,
                persistAcrossRestarts = true
            )
            .build()

        val trigger = config.rules.single().trigger as CleanupTrigger.AfterDelay
        assertTrue(trigger.persistAcrossRestarts)
        assertEquals(10_000L, trigger.delayMs)
    }

    @Test
    fun absoluteTimeRuleRejectsRestartMode() {
        try {
            FileCleanupManager.builder().add(
                CleanupRule(
                    key = "invalid_repeat",
                    path = "/app/cache",
                    action = CleanupAction.CLEAR_DIRECTORY,
                    trigger = CleanupTrigger.AtTimeMillis(1L),
                    scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
                )
            )
            fail("Expected absolute time repeat mode to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun permissionRequestCanOnlyBeResolvedOnce() {
        var retries = 0
        var cancellations = 0
        val request = CleanupPermissionRequest(
            key = "protected_file",
            path = "/storage/protected.txt",
            suggestedPermissions = listOf("permission"),
            requiresSpecialSettings = false,
            reason = "denied",
            retryAction = { retries++; true },
            cancelAction = { cancellations++; true }
        )

        assertTrue(request.retry())
        assertFalse(request.retry())
        assertFalse(request.cancel())
        assertEquals(1, retries)
        assertEquals(0, cancellations)
    }

    private fun rule(key: String, file: File, action: CleanupAction): CleanupRule {
        return CleanupRule(
            key = key,
            path = file.absolutePath,
            action = action,
            trigger = CleanupTrigger.OnAppStart
        )
    }
}
