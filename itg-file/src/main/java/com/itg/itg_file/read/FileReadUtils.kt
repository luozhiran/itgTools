package com.itg.itg_file.read
import com.itg.itg_file.core.FileFailureCallbacks

import android.net.Uri
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 文件读取工具类
 *
 * 提供多种文件读取方式，涵盖文本、字节、按行读取、大文件分块读取等场景。
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 *
 * 核心特性:
 * - 读取为 String（指定编码）
 * - 读取为 ByteArray
 * - 按行读取（适合逐行处理）
 * - 分块读取（适合大文件，避免 OOM）
 * - 从 Content URI 读取（Android 存储框架兼容）
 * - 读取文件头部/尾部若干字节
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object FileReadUtils {

    private const val DEFAULT_BUFFER_SIZE = 8192  // 8KB
    private const val DEFAULT_MAX_IN_MEMORY_BYTES = 10 * 1024 * 1024

    // ==================== 读取为字符串 ====================

    /**
     * 读取文件全部内容为字符串
     *
     * @param path    文件路径
     * @param charset 字符编码，默认 UTF-8
     * @return 文件内容字符串，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val content = FileReadUtils.readText("/sdcard/data.json")
     * content?.let { println(it) }
     *
     * // 指定编码
     * val gbkContent = FileReadUtils.readText("/sdcard/legacy.txt", Charset.forName("GBK"))
     * ```
     */
    private fun readTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileReadUtils.readText 失败：源文件不存在或不是普通文件")
        return try {
            if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "FileReadUtils.readText 失败：大小或深度超出限制")
            File(path).readText(charset)
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取文件为字符串
     *
     * @param path     文件路径
     * @param charset  字符编码
     * @param onResult 回调 (content: String?, error: Throwable?)
     * @return Unit
     *
     * 使用示例:
     * ```kotlin
     * viewModelScope.launch {
     *     val content = FileCoroutines.withIo { FileReadUtils.readText(path) }
     *     content?.let(::parseAndDisplay)
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readTextSuspend(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readText(path, charset, onFailure = { failure = it })
                safeCallback {
                    onResult(result, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 读取为字节数组 ====================

    /**
     * 读取文件全部内容为字节数组
     *
     * @param path 文件路径
     * @return 字节数组，失败返回 null
     *
     * 注意: 大文件 (>50MB) 请使用 [readChunks] 避免 OOM。
     */
    private fun readBytesImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES): ByteArray? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileReadUtils.readBytes 失败：源文件不存在或不是普通文件")
        return try {
            if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "FileReadUtils.readBytes 失败：大小或深度超出限制")
            FileInputStream(File(path)).use { input ->
                if (maxBytes <= 0) input.readBytes() else input.readBytesWithLimit(failureReporter, maxBytes)
            }
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取文件为字节数组
     */
    @JvmStatic
    suspend fun readBytesSuspend(
        path: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readBytes(path, onFailure = { failure = it })
                safeCallback {
                    onResult(result, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 按行读取 ====================

    /**
     * 读取文件所有行
     *
     * @param path    文件路径
     * @param charset 字符编码，默认 UTF-8
     * @return 每行内容的列表，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val lines = FileReadUtils.readLines("/sdcard/log.txt")
     * lines?.forEachIndexed { index, line -> println("Line $index: $line") }
     * ```
     */
    private fun readLinesImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): List<String>? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileReadUtils.readLines 失败：源文件不存在或不是普通文件")
        return try {
            if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "FileReadUtils.readLines 失败：大小或深度超出限制")
            File(path).readLines(charset)
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readLinesSuspend(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readLines(path, charset, onFailure = { failure = it })
                safeCallback {
                    onResult(result, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    /**
     * 逐行读取文件（适合大文件，边读边处理）
     *
     * @param path      文件路径
     * @param charset   字符编码
     * @param onEachLine 每行处理回调，返回 false 可提前终止读取
     * @return 成功读取的行数，失败返回 -1
     *
     * 使用示例:
     * ```kotlin
     * // 读取 CSV 的前 100 行
     * FileReadUtils.readLinesStreaming("/sdcard/large.csv", onEachLine = { line, index ->
     *     processLine(line)
     *     index < 100  // 继续读取（false 停止）
     * })
     * ```
     */
    private fun readLinesStreamingImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!FileUtils.isFile(path)) return failureReporter.fail(-1, "FileReadUtils.readLinesStreaming 失败：源文件不存在或不是普通文件")
        return try {
            var count = 0
            File(path).bufferedReader(charset).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val currentIndex = count
                    count++
                    if (!safeLineCallback(failureReporter, onEachLine, line, currentIndex)) break
                    line = reader.readLine()
                }
            }
            count
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1
        }
    }

    /**
     * 异步逐行读取
     *
     * @param path       文件路径
     * @param charset    编码
     * @param onEachLine 每行处理回调
     * @param onComplete 完成回调 (totalLines: Int, error: Throwable?)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readLinesStreamingSuspend(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean,
        onComplete: (totalLines: Int, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val count = readLinesStreaming(path, charset, onEachLine, onFailure = { failure = it })
                safeCallback {
                    onComplete(count, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onComplete(-1, e) }
            }
        }
    }

    // ==================== 从 URI/InputStream 读取 ====================

    /**
     * 从 Content URI 读取文件内容为字节数组
     *
     * @param context Android Context
     * @param uri     文件 URI
     * @param maxBytes 最大读取字节数（防止读取超大文件），默认 10MB
     * @return 字节数组，失败返回 null
     */
    private fun readBytesImpl(failureReporter: FileFailureCallbacks.Reporter, context: android.content.Context, uri: Uri, maxBytes: Int = 10 * 1024 * 1024): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytesWithLimit(failureReporter, maxBytes)
            }
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 InputStream 读取全部内容为字节数组
     *
     * 注意: 调用方负责关闭 InputStream。
     *
     * @param inputStream 输入流
     * @param maxBytes    最大字节数限制（0 表示无限制）
     * @return 字节数组，失败返回 null
     */
    private fun readBytesImpl(failureReporter: FileFailureCallbacks.Reporter, inputStream: InputStream, maxBytes: Int = 0): ByteArray? {
        return try {
            if (maxBytes <= 0) {
                inputStream.readBytes()
            } else {
                inputStream.readBytesWithLimit(failureReporter, maxBytes)
            }
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 InputStream 读取为字符串
     *
     * @param inputStream 输入流
     * @param charset     编码，默认 UTF-8
     * @param maxBytes    最大字节限制
     * @return 字符串，失败返回 null
     */
    private fun readTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        inputStream: InputStream,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = 0
    ): String? {
        val bytes = readBytesImpl(failureReporter, inputStream, maxBytes) ?: return failureReporter.fail(null, "FileReadUtils.readText 失败：大小或深度超出限制")
        return String(bytes, charset)
    }

    // ==================== 分块读取（大文件） ====================

    /**
     * 分块读取大文件
     *
     * 适用于几百 MB 甚至 GB 级别的文件，每次只读取一个 chunk 到内存。
     *
     * @param path       文件路径
     * @param chunkSize  每块大小（字节），默认 64KB
     * @param onChunk    每块处理回调 (chunkData, chunkIndex, totalChunks)，
     *                   返回 false 可提前终止
     * @return 总读取字节数，失败返回 -1
     *
     * 使用示例:
     * ```kotlin
     * FileReadUtils.readChunks("/sdcard/large.bin", chunkSize = 1024 * 1024, onChunk = { chunk, index, total ->
     *     uploadChunk(chunk, index)
     *     val progress = (index + 1) * 100 / total
     *     updateProgress(progress)
     *     true  // 继续
     * })
     * ```
     */
    private fun readChunksImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        chunkSize: Int = 64 * 1024,
        onChunk: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean
    ): Long {
        if (chunkSize <= 0) return failureReporter.fail(-1L, "FileReadUtils.readChunks 失败：分块大小无效")
        if (!FileUtils.isFile(path)) return failureReporter.fail(-1L, "FileReadUtils.readChunks 失败：源文件不存在或不是普通文件")

        return try {
            val file = File(path)
            val fileSize = file.length()
            val totalChunksLong = if (fileSize == 0L) 0L else ((fileSize - 1L) / chunkSize) + 1L
            val totalChunks = totalChunksLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            var totalRead = 0L

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(chunkSize)
                var chunkIndex = 0
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = buffer.copyOf(bytesRead)
                    totalRead += bytesRead
                    if (!safeChunkCallback(failureReporter, onChunk, chunk, chunkIndex, totalChunks)) break
                    chunkIndex++
                }
            }
            totalRead
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 异步分块读取
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readChunksSuspend(
        path: String,
        chunkSize: Int = 64 * 1024,
        onChunk: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean,
        onComplete: (totalBytes: Long, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val total = readChunks(path, chunkSize, onChunk, onFailure = { failure = it })
                safeCallback {
                    onComplete(total, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onComplete(-1L, e) }
            }
        }
    }

    // ==================== 文件头部/尾部 ====================

    /**
     * 读取文件头部若干字节
     *
     * 适用于读取文件 Magic Number、识别文件类型等场景。
     *
     * @param path      文件路径
     * @param numBytes  读取字节数
     * @return 字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 读取文件头4字节判断类型
     * val header = FileReadUtils.readHeadBytes("/sdcard/file.bin", 4)
     * ```
     */
    private fun readHeadBytesImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, numBytes: Int): ByteArray? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "读取文件头失败：源文件不存在：$path")
        if (numBytes <= 0) return failureReporter.fail(null, "读取文件头失败：字节数必须大于 0")
        return try {
            RandomAccessFile(path, "r").use { raf ->
                val bytes = ByteArray(minOf(numBytes.toLong(), raf.length()).toInt())
                raf.readFully(bytes)
                bytes
            }
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 读取文件尾部若干字节
     *
     * 适用于读取日志文件尾部等场景。
     *
     * @param path     文件路径
     * @param numBytes 读取字节数
     * @return 字节数组，失败返回 null
     */
    private fun readTailBytesImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, numBytes: Int): ByteArray? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "读取文件尾失败：源文件不存在：$path")
        if (numBytes <= 0) return failureReporter.fail(null, "读取文件尾失败：字节数必须大于 0")
        return try {
            val file = File(path)
            val fileSize = file.length()
            val readSize = minOf(numBytes.toLong(), fileSize).toInt()
            val bytes = ByteArray(readSize)
            RandomAccessFile(path, "r").use { raf ->
                raf.seek(fileSize - readSize)
                raf.readFully(bytes)
            }
            bytes
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 读取文件最后 N 行
     *
     * 适用于查看日志文件最新内容。
     *
     * @param path     文件路径
     * @param numLines 行数
     * @return 最后 N 行内容，失败返回 null
     */
    private fun readTailLinesImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, numLines: Int = 10): List<String>? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileReadUtils.readTailLines 失败：源文件不存在或不是普通文件")
        if (numLines <= 0) return emptyList()
        return try {
            val lines = ArrayDeque<String>(numLines)
            File(path).bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (lines.size >= numLines) lines.removeFirst()
                    lines.addLast(line)
                    line = reader.readLine()
                }
            }
            lines.toList()
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    // ==================== 内部方法 ====================

    private fun InputStream.readBytesWithLimit(failureReporter: FileFailureCallbacks.Reporter, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "maxBytes must be non-negative" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0
        while (true) {
            if (totalRead == maxBytes) {
                if (read() != -1) throw IOException("Input exceeds maxBytes=$maxBytes")
                break
            }
            val bytesRead = read(buffer, 0, minOf(buffer.size, maxBytes - totalRead))
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
        }
        return output.toByteArray()
    }

    private fun canReadIntoMemory(failureReporter: FileFailureCallbacks.Reporter, path: String, maxBytes: Int): Boolean {
        if (maxBytes <= 0) return true
        return try {
            File(path).length() <= maxBytes
        } catch (error: Throwable) {
            failureReporter.rethrow(error)
            false
        }
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
        }
    }

    private fun safeLineCallback(failureReporter: FileFailureCallbacks.Reporter,
        callback: (line: String, index: Int) -> Boolean,
        line: String,
        index: Int
    ): Boolean {
        return try {
            failureReporter.invokeUserCallback { callback(line, index) }
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    private fun safeChunkCallback(failureReporter: FileFailureCallbacks.Reporter,
        callback: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean,
        chunk: ByteArray,
        chunkIndex: Int,
        totalChunks: Int
    ): Boolean {
        return try {
            failureReporter.invokeUserCallback { callback(chunk, chunkIndex, totalChunks) }
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** readText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readText(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileReadUtils.readText", "$path", onFailure, { it == null }, null) { failureReporter ->
            readTextImpl(failureReporter, path, charset, maxBytes)
        }

    /** readBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readBytes(
        path: String,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("FileReadUtils.readBytes", "$path", onFailure, { it == null }, null) { failureReporter ->
            readBytesImpl(failureReporter, path, maxBytes)
        }

    /** readLines 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readLines(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): List<String>? =
        FileFailureCallbacks.run("FileReadUtils.readLines", "$path", onFailure, { it == null }, null) { failureReporter ->
            readLinesImpl(failureReporter, path, charset, maxBytes)
        }

    /** readLinesStreaming 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readLinesStreaming(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean,
        onFailure: ((Exception) -> Unit)? = null
    ): Int =
        FileFailureCallbacks.run("FileReadUtils.readLinesStreaming", "$path", onFailure, { it < 0 }, -1) { failureReporter ->
            readLinesStreamingImpl(failureReporter, path, charset, onEachLine)
        }

    /** 保留 Kotlin 尾随 lambda 的调用方式。 */
    @JvmStatic
    @JvmName("readLinesStreamingWithLineCallback")
    fun readLinesStreaming(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int = readLinesStreaming(path, charset, onEachLine, null)

    /** readBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readBytes(
        context: android.content.Context,
        uri: Uri,
        maxBytes: Int = 10 * 1024 * 1024,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("FileReadUtils.readBytes", "$context", onFailure, { it == null }, null) { failureReporter ->
            readBytesImpl(failureReporter, context, uri, maxBytes)
        }

    /** readBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readBytes(
        inputStream: InputStream,
        maxBytes: Int = 0,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("FileReadUtils.readBytes", "$inputStream", onFailure, { it == null }, null) { failureReporter ->
            readBytesImpl(failureReporter, inputStream, maxBytes)
        }

    /** readText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readText(
        inputStream: InputStream,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = 0,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileReadUtils.readText", "$inputStream", onFailure, { it == null }, null) { failureReporter ->
            readTextImpl(failureReporter, inputStream, charset, maxBytes)
        }

    /** readChunks 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readChunks(
        path: String,
        chunkSize: Int = 64 * 1024,
        onChunk: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("FileReadUtils.readChunks", "$path", onFailure, { it < 0L }, -1L) { failureReporter ->
            readChunksImpl(failureReporter, path, chunkSize, onChunk)
        }

    /** 保留 Kotlin 尾随 lambda 的调用方式。 */
    @JvmStatic
    @JvmName("readChunksWithChunkCallback")
    fun readChunks(
        path: String,
        chunkSize: Int = 64 * 1024,
        onChunk: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean
    ): Long = readChunks(path, chunkSize, onChunk, null)

    /** readHeadBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readHeadBytes(
        path: String,
        numBytes: Int,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("FileReadUtils.readHeadBytes", "$path", onFailure, { it == null }, null) { failureReporter ->
            readHeadBytesImpl(failureReporter, path, numBytes)
        }

    /** readTailBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readTailBytes(
        path: String,
        numBytes: Int,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("FileReadUtils.readTailBytes", "$path", onFailure, { it == null }, null) { failureReporter ->
            readTailBytesImpl(failureReporter, path, numBytes)
        }

    /** readTailLines 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readTailLines(
        path: String,
        numLines: Int = 10,
        onFailure: ((Exception) -> Unit)? = null
    ): List<String>? =
        FileFailureCallbacks.run("FileReadUtils.readTailLines", "$path", onFailure, { it == null }, null) { failureReporter ->
            readTailLinesImpl(failureReporter, path, numLines)
        }
}
