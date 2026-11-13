package com.itg.itg_file

import com.itg.itg_file.archive.ZipUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InterruptedIOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun extractsNestedFilesOnlyAfterSuccess() {
        val archive = createZip("data/config.json" to "{\"ok\":true}", "empty/" to "")
        val destination = File(temp.root, "output")

        assertTrue(ZipUtils.extract(archive.path, destination.path))
        assertEquals("{\"ok\":true}", File(destination, "data/config.json").readText())
        assertTrue(File(destination, "empty").isDirectory)
    }

    @Test
    fun extractsThroughSuspendApi() = runBlocking {
        val archive = createZip("async.txt" to "content")
        val destination = File(temp.root, "async-output")

        assertTrue(ZipUtils.extractSuspend(archive.path, destination.path))
        assertEquals("content", File(destination, "async.txt").readText())
    }

    @Test
    fun asynchronousFailureCallsBackOnIoThread() = runBlocking {
        val callerThread = Thread.currentThread()
        var callbackThread: Thread? = null
        var failure: Exception? = null

        assertFalse(ZipUtils.extractSuspend("", "", onFailure = {
            callbackThread = Thread.currentThread()
            failure = it
        }))
        assertTrue(failure is IllegalArgumentException)
        assertTrue(callbackThread != callerThread)
    }

    @Test
    fun rejectsTraversalAndLeavesNoPublishedDirectory() {
        val archive = createZip("../outside.txt" to "bad", "valid.txt" to "good")
        val destination = File(temp.root, "output")

        assertFalse(ZipUtils.extract(archive.path, destination.path))
        assertFalse(destination.exists())
        assertFalse(File(temp.root, "outside.txt").exists())
        assertTrue(temp.root.listFiles().orEmpty().none { it.name.startsWith(".output.") })
    }

    @Test
    fun rejectsOversizedArchiveWithoutPublishingPartialFiles() {
        val archive = createZip("first.txt" to "1234", "second.txt" to "5678")
        val destination = File(temp.root, "output")

        assertFalse(ZipUtils.extract(archive.path, destination.path, maxUncompressedBytes = 5))
        assertFalse(destination.exists())
        assertTrue(temp.root.listFiles().orEmpty().none { it.name.startsWith(".output.") })
    }

    @Test
    fun existingDestinationIsPreserved() {
        val archive = createZip("new.txt" to "new")
        val destination = temp.newFolder("output")
        File(destination, "old.txt").writeText("old")

        assertFalse(ZipUtils.extract(archive.path, destination.path))
        assertEquals("old", File(destination, "old.txt").readText())
        assertFalse(File(destination, "new.txt").exists())
    }

    @Test
    fun rejectsNamesThatCollideAfterCaseOrUnicodeNormalization() {
        val archive = createZip("A.txt" to "first", "a.txt" to "second")
        val destination = File(temp.root, "output")

        assertFalse(ZipUtils.extract(archive.path, destination.path))
        assertFalse(destination.exists())

        val unicodeArchive = createZip("caf\u00e9.txt" to "first", "cafe\u0301.txt" to "second")
        assertFalse(ZipUtils.extract(unicodeArchive.path, destination.path))
        assertFalse(destination.exists())
    }

    @Test
    fun reportsThreadInterruptionThroughCallbackWithoutPublishingFiles() {
        val archive = createZip("file.txt" to "content")
        val destination = File(temp.root, "output")
        var failure: Exception? = null

        try {
            Thread.currentThread().interrupt()
            assertFalse(ZipUtils.extract(archive.path, destination.path, onFailure = { failure = it }))
            assertTrue(failure is InterruptedIOException)
            assertTrue(Thread.currentThread().isInterrupted)
            assertFalse(destination.exists())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun reportsValidationFailureThroughCallback() {
        var failure: Exception? = null

        assertFalse(ZipUtils.extract("", "", onFailure = { failure = it }))
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun callbackExceptionDoesNotReplaceFailureResult() {
        assertFalse(ZipUtils.extract("", "", onFailure = { throw IllegalStateException("UI failed") }))
    }

    private fun createZip(vararg entries: Pair<String, String>): File {
        val archive = temp.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith('/')) zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return archive
    }
}
