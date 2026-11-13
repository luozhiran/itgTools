package com.itg.itg_file.read
import com.itg.itg_file.core.FileFailureCallbacks

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ForwardingSource
import okio.IOException
import okio.buffer
import okio.source
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Okio 文件读取工具类
 *
 * 基于 Okio [BufferedSource] 实现高效文件读取，相比传统 java.io：
 * - [ByteString]: 不可变字节序列，高效的 hex/base64/UTF-8 转换
 * - [Buffer]: 可变的零拷贝字节缓冲区
 * - 内置超时控制 (source.timeout)
 * - 进度追踪 (ForwardingSource)
 * - Gzip 解压 (Okio.gzip)
 *
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioReadUtils {

    private const val DEFAULT_MAX_IN_MEMORY_BYTES = 10 * 1024 * 1024

    // ==================== 读取为 ByteString ====================

    /**
     * 读取文件为不可变 [ByteString]
     *
     * ByteString 是 Okio 的不可变字节序列，支持:
     * - 零拷贝子序列 (substring)
     * - 高效的 hex / base64 / base64Url 编码
     * - 直接 UTF-8 解码
     *
     * @param path 文件路径
     * @return ByteString，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bytes = OkioReadUtils.readByteString("/sdcard/photo.jpg")
     * println("Hex: ${bytes?.hex()}")
     * println("Base64: ${bytes?.base64()}")
     * println("MD5: ${bytes?.md5()?.hex()}")
     * ```
     */
    private fun readByteStringImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong()): ByteString? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readByteString 失败：源文件不存在或不是普通文件")
        if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "OkioReadUtils.readByteString 失败：大小或深度超出限制")
        return try {
            File(path).source().buffer().use { buffered ->
                buffered.readByteString()
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取为 ByteString
     */
    @JvmStatic
    suspend fun readByteStringSuspend(
        path: String,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readByteString(path, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 读取为字符串 ====================

    /**
     * 读取文件为 UTF-8 字符串
     *
     * 使用 Okio 的 BufferedSource.readUtf8()，比 java.io BufferedReader 更高效。
     *
     * @param path    文件路径
     * @param charset 字符编码，默认 UTF-8
     * @return 字符串，失败返回 null
     */
    private fun readUtf8Impl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong()
    ): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readUtf8 失败：源文件不存在或不是普通文件")
        if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "OkioReadUtils.readUtf8 失败：大小或深度超出限制")
        return try {
            File(path).source().buffer().use { buffered ->
                buffered.readString(charset)
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取为字符串
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readUtf8Suspend(
        path: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readUtf8(path, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 读取到 Buffer ====================

    /**
     * 读取文件到 Okio [Buffer]
     *
     * Buffer 是可变字节缓冲区，适合需要修改数据的场景。
     *
     * @param path 文件路径
     * @return Buffer (可变)，失败返回 null
     */
    private fun readToBufferImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Buffer? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readToBuffer 失败：源文件不存在或不是普通文件")
        return try {
            val buffer = Buffer()
            File(path).source().use { source ->
                buffer.writeAll(source)
            }
            buffer
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取到 Buffer
     */
    @JvmStatic
    suspend fun readToBufferSuspend(
        path: String,
        onResult: (Buffer?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readToBuffer(path, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 按行读取 ====================

    /**
     * 使用 Okio 按行读取文件
     *
     * @param path    文件路径
     * @param charset 字符编码，默认 UTF-8
     * @return 行列表，失败返回 null
     */
    private fun readLinesImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong()
    ): List<String>? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readLines 失败：源文件不存在或不是普通文件")
        if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "OkioReadUtils.readLines 失败：大小或深度超出限制")
        return try {
            File(path).inputStream().bufferedReader(charset).use { reader ->
                reader.readLines()
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取
     */
    @JvmStatic
    suspend fun readLinesSuspend(
        path: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readLines(path, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    /**
     * 流式逐行读取（大文件友好）
     *
     * 边读边处理，不会一次性加载全部内容到内存。
     *
     * @param path       文件路径
     * @param onEachLine 每行回调，返回 false 停止读取
     * @return 读取的行数，失败返回 -1
     */
    private fun readLinesStreamingImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!FileUtils.isFile(path)) return failureReporter.fail(-1, "OkioReadUtils.readLinesStreaming 失败：源文件不存在或不是普通文件")
        return try {
            var count = 0
            File(path).source().buffer().use { buffered ->
                while (true) {
                    val line = buffered.readUtf8Line() ?: break
                    if (!safeLineCallback(failureReporter, line, count, onEachLine)) break
                    count++
                }
            }
            count
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
            -1
        }
    }

    // ==================== 超时控制 ====================

    /**
     * 带超时的文件读取
     *
     * 通过 Okio 的 Timeout 机制实现，比 java.io 更可靠。
     *
     * @param path      文件路径
     * @param timeoutMs 超时毫秒数
     * @return 读取的内容，超时返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 读取网络文件系统上的大文件，30 秒超时
     * val content = OkioReadUtils.readWithTimeout("/mnt/nfs/huge.log", 30_000)
     * ```
     */
    private fun readWithTimeoutImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        timeoutMs: Long,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong()
    ): ByteString? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readWithTimeout 失败：源文件不存在或不是普通文件")
        if (!canReadIntoMemory(failureReporter, path, maxBytes)) return failureReporter.fail(null, "OkioReadUtils.readWithTimeout 失败：大小或深度超出限制")
        return try {
            val source = File(path).source()
            source.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            source.buffer().use { buffered ->
                buffered.readByteString()
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioReadUtils", "Read timed out after ${timeoutMs}ms: $path")
            }
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步超时读取
     */
    @JvmStatic
    suspend fun readWithTimeoutSuspend(
        path: String,
        timeoutMs: Long,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readWithTimeout(path, timeoutMs, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 带进度的读取 ====================

    /**
     * 带进度的文件读取
     *
     * 通过 [ForwardingSource] 拦截读取操作，在每个 segment 读取后回调进度。
     *
     * @param path       文件路径
     * @param chunkSize  每次读取的块大小（用于进度回调间隔）
     * @param onProgress 进度回调 (bytesRead, totalBytes)
     * @return ByteArray，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val data = OkioReadUtils.readWithProgress("/sdcard/large.zip", onProgress = { read, total ->
     *     val percent = read * 100 / total
     *     updateProgressBar(percent.toInt())
     * })
     * ```
     */
    private fun readWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        chunkSize: Long = 8192L,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): ByteArray? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readWithProgress 失败：源文件不存在或不是普通文件")
        if (!canReadIntoMemory(failureReporter, path, DEFAULT_MAX_IN_MEMORY_BYTES.toLong())) return failureReporter.fail(null, "OkioReadUtils.readWithProgress 失败：文件超出内存读取限制")

        return try {
            val file = File(path)
            val totalSize = file.length()
            var bytesRead = 0L
            var lastReport = 0L

            val rawSource = file.source()
            val progressSource = object : ForwardingSource(rawSource) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesRead += read
                        // 控制进度回调频率
                        if (onProgress != null && bytesRead - lastReport >= chunkSize) {
                            safeCallback(failureReporter) { onProgress(bytesRead, totalSize) }
                            lastReport = bytesRead
                        }
                    }
                    return read
                }
            }

            progressSource.buffer().use { buffered ->
                val result = buffered.readByteArray()
                // 最后一次进度回调
                safeCallback(failureReporter) { onProgress?.invoke(bytesRead, totalSize) }
                result
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步带进度读取
     */
    @JvmStatic
    suspend fun readWithProgressSuspend(
        path: String,
        chunkSize: Long = 8192L,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readWithProgress(path, chunkSize, onProgress, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== Gzip 解压 ====================

    /**
     * 读取并解压 Gzip 文件
     *
     * @param path Gzip 压缩文件路径
     * @return 解压后的字节数组，失败返回 null
     */
    private fun readGzipImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): ByteArray? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioReadUtils.readGzip 失败：源文件不存在或不是普通文件")
        return try {
            File(path).source().use { source ->
                okio.GzipSource(source).buffer().use { gzip ->
                    gzip.readByteArrayWithLimit(failureReporter, DEFAULT_MAX_IN_MEMORY_BYTES.toLong())
                }
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取并解压 Gzip
     */
    @JvmStatic
    suspend fun readGzipSuspend(
        path: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readGzip(path, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    /**
     * 读取 Gzip 并解压为字符串
     */
    private fun readGzipAsTextImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, charset: Charset = Charsets.UTF_8): String? {
        val bytes = readGzipImpl(failureReporter, path) ?: return failureReporter.fail(null, "OkioReadUtils.readGzipAsText 失败：解压 gzip 文件失败")
        return String(bytes, charset)
    }

    private fun canReadIntoMemory(failureReporter: FileFailureCallbacks.Reporter, path: String, maxBytes: Long): Boolean {
        return maxBytes <= 0L || File(path).length() <= maxBytes
    }

    private fun BufferedSource.readByteArrayWithLimit(failureReporter: FileFailureCallbacks.Reporter, maxBytes: Long): ByteArray {
        if (maxBytes <= 0L) return readByteArray()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = read(buffer, 8192L)
            if (read == -1L) break
            total += read
            if (total > maxBytes) throw IOException("Input exceeds maxBytes=$maxBytes")
        }
        return buffer.readByteArray()
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
        }
    }

    private fun safeLineCallback(failureReporter: FileFailureCallbacks.Reporter,
        line: String,
        index: Int,
        callback: (line: String, index: Int) -> Boolean
    ): Boolean {
        return try {
            failureReporter.invokeUserCallback { callback(line, index) }
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
            false
        }
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** readByteString 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readByteString(
        path: String,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong(),
        onFailure: ((Exception) -> Unit)? = null
    ): ByteString? =
        FileFailureCallbacks.run("OkioReadUtils.readByteString", "$path", onFailure, { it == null }, null) { failureReporter ->
            readByteStringImpl(failureReporter, path, maxBytes)
        }

    /** readUtf8 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readUtf8(
        path: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong(),
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioReadUtils.readUtf8", "$path", onFailure, { it == null }, null) { failureReporter ->
            readUtf8Impl(failureReporter, path, charset, maxBytes)
        }

    /** readToBuffer 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readToBuffer(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Buffer? =
        FileFailureCallbacks.run("OkioReadUtils.readToBuffer", "$path", onFailure, { it == null }, null) { failureReporter ->
            readToBufferImpl(failureReporter, path)
        }

    /** readLines 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readLines(
        path: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong(),
        onFailure: ((Exception) -> Unit)? = null
    ): List<String>? =
        FileFailureCallbacks.run("OkioReadUtils.readLines", "$path", onFailure, { it == null }, null) { failureReporter ->
            readLinesImpl(failureReporter, path, charset, maxBytes)
        }

    /** readLinesStreaming 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readLinesStreaming(
        path: String,
        onEachLine: (line: String, index: Int) -> Boolean,
        onFailure: ((Exception) -> Unit)? = null
    ): Int =
        FileFailureCallbacks.run("OkioReadUtils.readLinesStreaming", "$path", onFailure, { it < 0 }, -1) { failureReporter ->
            readLinesStreamingImpl(failureReporter, path, onEachLine)
        }

    /** readWithTimeout 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readWithTimeout(
        path: String,
        timeoutMs: Long,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES.toLong(),
        onFailure: ((Exception) -> Unit)? = null
    ): ByteString? =
        FileFailureCallbacks.run("OkioReadUtils.readWithTimeout", "$path", onFailure, { it == null }, null) { failureReporter ->
            readWithTimeoutImpl(failureReporter, path, timeoutMs, maxBytes)
        }

    /** readWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readWithProgress(
        path: String,
        chunkSize: Long = 8192L,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("OkioReadUtils.readWithProgress", "$path", onFailure, { it == null }, null) { failureReporter ->
            readWithProgressImpl(failureReporter, path, chunkSize, onProgress)
        }

    /** readGzip 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readGzip(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("OkioReadUtils.readGzip", "$path", onFailure, { it == null }, null) { failureReporter ->
            readGzipImpl(failureReporter, path)
        }

    /** readGzipAsText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readGzipAsText(
        path: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioReadUtils.readGzipAsText", "$path", onFailure, { it == null }, null) { failureReporter ->
            readGzipAsTextImpl(failureReporter, path, charset)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("readLinesStreamingLegacyCallback")
    fun readLinesStreaming(
        path: String,
        onEachLine: (line: String, index: Int) -> Boolean
    ) = readLinesStreaming(path = path, onEachLine = onEachLine, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("readWithProgressLegacyCallback")
    fun readWithProgress(
        path: String,
        chunkSize: Long = 8192L,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ) = readWithProgress(path = path, chunkSize = chunkSize, onProgress = onProgress, onFailure = null)
}
