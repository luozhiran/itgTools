package com.itg.itg_file

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.hash.FileHashUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.write.FileWriteUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream

class FileFailureCallbackTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun writeFailureReportsOnceWithOperationAndTarget() {
        val target = File(temp.root, "missing-parent/file.txt")
        val blocker = File(temp.root, "missing-parent")
        blocker.writeText("not a directory")
        val failures = mutableListOf<Exception>()

        assertFalse(FileUtils.createFile(onFailure = { failures.add(it) }, path = target.path))
        assertEquals(1, failures.size)
        assertTrue(failures.single().message.orEmpty().contains("无法创建父目录"))
        assertTrue(failures.single().message.orEmpty().contains(blocker.path))
    }

    @Test
    fun missingReadReportsFailureAndSuccessDoesNot() {
        val target = File(temp.root, "missing.txt")
        var failure: Exception? = null

        assertNull(FileReadUtils.readText(onFailure = { failure = it }, path = target.path))
        assertNotNull(failure)
        target.writeText("ok")
        failure = null
        assertEquals("ok", FileReadUtils.readText(onFailure = { failure = it }, path = target.path))
        assertNull(failure)
    }

    @Test
    fun nullableFinalCallbackKeepsSingleCallStyle() {
        val missing = File(temp.root, "missing.txt")
        var failure: Exception? = null

        assertEquals(-1L, FileUtils.getSize(missing.path, null))
        assertEquals(-1L, FileUtils.getSize(missing.path) { failure = it })
        assertTrue(failure?.message.orEmpty().contains("不存在"))
    }

    @Test
    fun readStreamPassesThroughOriginalIoException() {
        val sourceError = IOException("读取第一个数据块失败")
        val stream = object : InputStream() {
            override fun read(): Int = throw sourceError
        }
        var failure: Exception? = null

        assertNull(FileReadUtils.readBytes(onFailure = { failure = it }, inputStream = stream))
        assertSame(sourceError, failure)
    }

    @Test
    fun copyReportsTheFailedCheck() {
        val source = File(temp.root, "source.txt")
        val destination = File(temp.root, "destination.txt")
        var failure: Exception? = null

        assertFalse(FileUtils.copy(onFailure = { failure = it }, srcPath = source.path, destPath = destination.path))
        assertTrue(failure?.message.orEmpty().contains("源文件不存在"))

        source.writeText("source")
        destination.writeText("existing")
        failure = null
        assertFalse(FileUtils.copy(
            onFailure = { failure = it },
            srcPath = source.path,
            destPath = destination.path,
            overwrite = false
        ))
        assertTrue(failure?.message.orEmpty().contains("目标文件已存在且禁止覆盖"))
    }

    @Test
    fun originalExceptionReachesFailureCallback() {
        val source = File(temp.root, "source.txt")
        source.writeText("content")
        val destination = File(temp.root, "destination.txt")
        var failure: Exception? = null

        assertFalse(FileUtils.copyWithProgress(
            onFailure = { failure = it },
            srcPath = source.path,
            destPath = destination.path,
            onProgress = { _, _ -> throw IllegalStateException("progress callback failed") }
        ))
        assertEquals("progress callback failed", failure?.message)
        assertFalse(destination.exists())
    }

    @Test
    fun writeReportsParentDirectoryFailure() {
        val blocker = File(temp.root, "blocked-parent")
        blocker.writeText("file")
        val target = File(blocker, "output.txt")
        var failure: Exception? = null

        assertFalse(FileWriteUtils.writeText(
            onFailure = { failure = it },
            path = target.path,
            content = "data"
        ))
        assertTrue(failure?.message.orEmpty().contains("父目录"))
    }

    @Test
    fun callbackExceptionDoesNotReplaceFailureReturn() {
        val target = File(temp.root, "missing.txt")
        assertFalse(FileUtils.delete(onFailure = { throw IllegalStateException("callback failed") }, path = target.path))
    }

    @Test
    fun failureCallbackCanCallLegacyApiWithoutInheritingReporter() {
        val target = File(temp.root, "missing.txt")
        var legacyResult = true

        assertFalse(FileUtils.delete(onFailure = {
            legacyResult = FileUtils.delete(target.path)
        }, path = target.path))
        assertFalse(legacyResult)
    }

    @Test
    fun lineCallbackCanCallLegacyApiWithoutInheritingReporter() {
        val source = File(temp.root, "source.txt")
        source.writeText("content\n")
        val missing = File(temp.root, "missing.txt")
        var legacyResult = true
        var failure: Exception? = null

        assertEquals(1, FileReadUtils.readLinesStreaming(
            onFailure = { failure = it },
            path = source.path,
            onEachLine = { _, _ ->
                legacyResult = FileUtils.delete(missing.path)
                true
            }
        ))
        assertFalse(legacyResult)
        assertNull(failure)
    }

    @Test
    fun asynchronousMissingReadIncludesError() = runBlocking {
        val target = File(temp.root, "missing.txt")
        var result: String? = "initial"
        var error: Throwable? = null

        FileReadUtils.readTextSuspend(target.path) { content, failure ->
            result = content
            error = failure
        }
        assertNull(result)
        assertNotNull(error)
        assertTrue(error?.message.orEmpty().contains("不存在"))
    }

    @Test
    fun asynchronousCreateReportsTheFailedStep() = runBlocking {
        val blocker = File(temp.root, "blocked-parent")
        blocker.writeText("file")
        val target = File(blocker, "output.txt")
        var result: Boolean? = null
        var failure: Exception? = null

        FileUtils.createFileSuspend(
            path = target.path,
            onResult = { result = it },
            onFailure = { failure = it }
        )
        assertEquals(false, result)
        assertTrue(failure?.message.orEmpty().contains("无法创建父目录"))
    }

    @Test
    fun legacyTrailingCallbacksStillCompileAndRun() = runBlocking {
        val source = File(temp.root, "source.txt")
        source.writeText("content")
        var size = -1L
        var progress = 0L

        FileUtils.getSizeSuspend(source.path) { size = it }
        assertEquals(source.length(), size)
        assertNotNull(FileHashUtils.hashFileWithProgress(source.path) { count, _ ->
            progress = count
        })
        assertEquals(source.length(), progress)
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalErrorIsNeverConvertedToFailureCallback() {
        val stream = object : InputStream() {
            override fun read(): Int = throw OutOfMemoryError("fatal read error")
        }
        FileReadUtils.readBytes(stream, onFailure = { throw AssertionError("must not be called") })
    }
}
