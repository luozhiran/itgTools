package com.itg.itg_file.read

import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.FileSystem
import okio.ForwardingSource
import okio.IOException
import okio.Okio
import okio.Path.Companion.toPath
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.Future
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
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioReadUtils {

    private val fileSystem: FileSystem = FileSystem.SYSTEM

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
    @JvmStatic
    fun readByteString(path: String): ByteString? {
        if (!FileUtils.isFile(path)) return null
        return try {
            fileSystem.source(path.toPath()).use { source ->
                source.buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取为 ByteString
     */
    @JvmStatic
    fun readByteStringAsync(
        path: String,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readByteString(path)
                onResult(result, if (result == null) IOException("Read failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    @JvmOverloads
    fun readUtf8(path: String, charset: Charset = Charsets.UTF_8): String? {
        if (!FileUtils.isFile(path)) return null
        return try {
            fileSystem.source(path.toPath()).use { source ->
                source.buffer().use { buffered ->
                    buffered.readString(charset)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取为字符串
     */
    @JvmStatic
    @JvmOverloads
    fun readUtf8Async(
        path: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readUtf8(path, charset)
                onResult(result, if (result == null) IOException("Read failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    fun readToBuffer(path: String): Buffer? {
        if (!FileUtils.isFile(path)) return null
        return try {
            val buffer = Buffer()
            fileSystem.source(path.toPath()).use { source ->
                buffer.writeAll(source)
            }
            buffer
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取到 Buffer
     */
    @JvmStatic
    fun readToBufferAsync(
        path: String,
        onResult: (Buffer?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readToBuffer(path)
                onResult(result, if (result == null) IOException("Read failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    @JvmOverloads
    fun readLines(path: String, charset: Charset = Charsets.UTF_8): List<String>? {
        if (!FileUtils.isFile(path)) return null
        return try {
            val lines = mutableListOf<String>()
            fileSystem.source(path.toPath()).use { source ->
                source.buffer().use { buffered ->
                    while (true) {
                        val line = buffered.readUtf8Line() ?: break
                        lines.add(line)
                    }
                }
            }
            lines
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取
     */
    @JvmStatic
    fun readLinesAsync(
        path: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readLines(path, charset)
                onResult(result, if (result == null) IOException("Read failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    fun readLinesStreaming(
        path: String,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!FileUtils.isFile(path)) return -1
        return try {
            var count = 0
            fileSystem.source(path.toPath()).use { source ->
                source.buffer().use { buffered ->
                    while (true) {
                        val line = buffered.readUtf8Line() ?: break
                        if (!onEachLine(line, count)) break
                        count++
                    }
                }
            }
            count
        } catch (e: IOException) {
            e.printStackTrace()
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
    @JvmStatic
    fun readWithTimeout(path: String, timeoutMs: Long): ByteString? {
        if (!FileUtils.isFile(path)) return null
        return try {
            fileSystem.source(path.toPath()).use { source ->
                source.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                source.buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: IOException) {
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioReadUtils", "Read timed out after ${timeoutMs}ms: $path")
            }
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步超时读取
     */
    @JvmStatic
    fun readWithTimeoutAsync(
        path: String,
        timeoutMs: Long,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readWithTimeout(path, timeoutMs)
                onResult(result, if (result == null) IOException("Read failed/timed out: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    @JvmOverloads
    fun readWithProgress(
        path: String,
        chunkSize: Long = 8192L,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): ByteArray? {
        if (!FileUtils.isFile(path)) return null

        return try {
            val file = File(path)
            val totalSize = file.length()
            var bytesRead = 0L
            var lastReport = 0L

            val rawSource = fileSystem.source(path.toPath())
            val progressSource = object : ForwardingSource(rawSource) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesRead += read
                        // 控制进度回调频率
                        if (onProgress != null && bytesRead - lastReport >= chunkSize) {
                            onProgress(bytesRead, totalSize)
                            lastReport = bytesRead
                        }
                    }
                    return read
                }
            }

            progressSource.buffer().use { buffered ->
                val result = buffered.readByteArray()
                // 最后一次进度回调
                onProgress?.invoke(bytesRead, totalSize)
                result
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步带进度读取
     */
    @JvmStatic
    fun readWithProgressAsync(
        path: String,
        chunkSize: Long = 8192L,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readWithProgress(path, chunkSize, onProgress)
                onResult(result, if (result == null) IOException("Read failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    fun readGzip(path: String): ByteArray? {
        if (!FileUtils.isFile(path)) return null
        return try {
            fileSystem.source(path.toPath()).use { source ->
                okio.GzipSource(source).buffer().use { gzip ->
                    val buffer = Buffer()
                    buffer.writeAll(gzip)
                    buffer.readByteArray()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取并解压 Gzip
     */
    @JvmStatic
    fun readGzipAsync(
        path: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readGzip(path)
                onResult(result, if (result == null) IOException("Gzip read failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
            }
        }
    }

    /**
     * 读取 Gzip 并解压为字符串
     */
    @JvmStatic
    fun readGzipAsText(path: String, charset: Charset = Charsets.UTF_8): String? {
        val bytes = readGzip(path) ?: return null
        return String(bytes, charset)
    }
}
