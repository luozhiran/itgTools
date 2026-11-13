package com.itg.itg_file.resource
import com.itg.itg_file.core.FileFailureCallbacks

import android.content.Context
import android.content.res.Resources
import android.system.ErrnoException
import android.system.Os
import com.itg.itg_file.core.FileCoroutines
import okio.Buffer
import okio.ByteString
import okio.ForwardingSource
import okio.GzipSource
import okio.IOException
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Okio Android Assets / Raw 资源读写工具类
 *
 * 基于 Okio [Buffer] / [BufferedSource] 实现高效的资源读写，相比 [AssetUtils]：
 * - [ByteString]: 不可变字节序列，高效的 hex/base64/UTF-8 转换
 * - [Buffer]: 零拷贝可变字节缓冲区
 * - 内置超时控制 (source.timeout)
 * - 进度追踪 (ForwardingSource)
 * - Gzip 解压（assets 中存储 .gz 文件时直接解压读取）
 *
 * **重要说明:**
 * - `assets/` 和 `res/raw/` 是 APK 内置的只读资源，运行时无法直接修改。
 * - "写入" 操作指将内置资源复制到文件系统，以便后续修改或外部使用。
 * - 所有方法需要 [Context] 参数。
 *
 * 核心特性:
 * - Assets 读取为 ByteString / Buffer / String
 * - Assets 复制到文件系统（Okio BufferedSink 高效写入）
 * - Assets 分块读取 + 进度回调
 * - Raw 资源读取为 ByteString / Buffer
 * - Raw 资源复制到文件系统
 * - Gzip 压缩 Assets 的解压读取
 * - 超时控制的资源读取
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioAssetUtils {

    private const val DEFAULT_BUFFER_SIZE = 8192L  // 8KB
    private const val DEFAULT_MAX_IN_MEMORY_BYTES = 10L * 1024L * 1024L
    private const val UNKNOWN_SIZE = -1L

    // ==================== Assets → ByteString ====================

    /**
     * 读取 assets 文件为不可变 [ByteString]
     *
     * ByteString 是 Okio 的不可变字节序列，支持:
     * - 零拷贝子序列 (substring)
     * - 高效的 hex / base64 / base64Url 编码
     * - 直接 UTF-8 解码
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @return ByteString，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bytes = OkioAssetUtils.readAssetByteString(context, "images/photo.jpg")
     * println("Base64: ${bytes?.base64()}")
     * println("MD5: ${bytes?.md5()?.hex()}")
     * ```
     */
    private fun readAssetByteStringImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): ByteString? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetByteString 失败：assets 文件不存在：$assetPath")
        if (!canReadAssetIntoMemory(failureReporter, context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetByteString 失败：assets 文件超出内存读取限制")
        return try {
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readByteString()
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
     * 异步读取 assets 为 ByteString
     */
    @JvmStatic
    suspend fun readAssetByteStringSuspend(
        context: Context,
        assetPath: String,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetByteString(appContext, assetPath, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    // ==================== Assets → String ====================

    /**
     * 读取 assets 文件为 UTF-8 字符串（Okio 实现）
     *
     * 使用 Okio 的 BufferedSource.readUtf8()，比 java.io BufferedReader 更高效。
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param charset   字符编码，默认 UTF-8
     * @return 字符串，失败返回 null
     */
    private fun readAssetUtf8Impl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8
    ): String? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetUtf8 失败：assets 文件不存在：$assetPath")
        if (!canReadAssetIntoMemory(failureReporter, context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetUtf8 失败：assets 文件超出内存读取限制")
        return try {
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readString(charset)
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
     * 异步读取 assets 为字符串
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readAssetUtf8Suspend(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetUtf8(appContext, assetPath, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    // ==================== Assets → Buffer ====================

    /**
     * 读取 assets 文件到 Okio [Buffer]
     *
     * Buffer 是可变字节缓冲区，适合需要修改数据的场景。
     * 通过 writeAll 将 InputStream 数据一次性零拷贝写入 Buffer。
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @return Buffer (可变)，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val buffer = OkioAssetUtils.readAssetToBuffer(context, "data/template.txt")
     * buffer?.let {
     *     it.writeUtf8("-- APPENDED --")
     *     // 写入到文件系统
     * }
     * ```
     */
    private fun readAssetToBufferImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): Buffer? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetToBuffer 失败：assets 文件不存在：$assetPath")
        if (!canReadAssetIntoMemory(failureReporter, context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetToBuffer 失败：assets 文件超出内存读取限制")
        return try {
            val buffer = Buffer()
            context.assets.open(assetPath).use { stream ->
                stream.source().use { source ->
                    buffer.writeAll(source)
                }
            }
            buffer
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
     * 异步读取 assets 到 Buffer
     */
    @JvmStatic
    suspend fun readAssetToBufferSuspend(
        context: Context,
        assetPath: String,
        onResult: (Buffer?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetToBuffer(appContext, assetPath, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    // ==================== Assets 按行读取 ====================

    /**
     * 使用 Okio 按行读取 assets 文件
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param charset   字符编码，默认 UTF-8
     * @return 行列表，失败返回 null
     */
    private fun readAssetLinesImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES
    ): List<String>? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetLines 失败：assets 文件不存在：$assetPath")
        return try {
            context.assets.open(assetPath).use { stream ->
                val bytes = stream.source().buffer().use { it.readByteArrayWithLimit(failureReporter, maxBytes) }
                String(bytes, charset).lineSequence().toList()
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
     * 异步按行读取 assets（Okio）
     */
    @JvmStatic
    suspend fun readAssetLinesSuspend(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetLines(appContext, assetPath, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 流式逐行读取 assets（Okio，大文件友好）
     *
     * @param context    Android Context
     * @param assetPath  assets 文件路径
     * @param onEachLine 每行回调，返回 false 停止读取
     * @return 读取的行数，失败返回 -1
     */
    private fun readAssetLinesStreamingImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(-1, "OkioAssetUtils.readAssetLinesStreaming 失败：assets 文件不存在：$assetPath")
        return try {
            var count = 0
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { buffered ->
                    while (true) {
                        val line = buffered.readUtf8Line() ?: break
                        if (!safeLineCallback(failureReporter, line, count, onEachLine)) break
                        count++
                    }
                }
            }
            count
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1
        }
    }

    // ==================== Assets 复制到文件系统（Okio 高效写入） ====================

    /**
     * 使用 Okio 将 assets 复制到文件系统
     *
     * 与 [AssetUtils.copyAssetToFile] 相比，使用 Okio [BufferedSink] 获得更好的写入性能。
     *
     * @param context    Android Context
     * @param assetPath  assets 源路径
     * @param destPath   目标文件路径
     * @param overwrite  是否覆盖，默认 true
     * @return true 表示复制成功
     *
     * 使用示例:
     * ```kotlin
     * OkioAssetUtils.copyAssetToFile(context, "models/model.tflite",
     *     "/sdcard/MyApp/model.tflite")
     * ```
     */
    private fun copyAssetToFileImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(false, "OkioAssetUtils.copyAssetToFile 失败：assets 文件不存在：$assetPath")
        if (destPath.isBlank()) return failureReporter.fail(false, "OkioAssetUtils.copyAssetToFile 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { tempFile ->
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        }
    }

    /**
     * 异步复制 assets 到文件系统（Okio）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyAssetToFileSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyAssetToFile(appContext, assetPath, destPath, overwrite, onFailure = onFailure)) }
        }
    }

    /**
     * 使用 Okio 将 assets 复制到文件系统（带进度回调）
     *
     * 通过 [ForwardingSource] 拦截读取操作，实现进度追踪。
     *
     * @param context     Android Context
     * @param assetPath   assets 源路径
     * @param destPath    目标路径
     * @param overwrite   是否覆盖
     * @param chunkSize   进度回调最小间隔（字节）
     * @param onProgress  进度回调 (bytesCopied, totalBytes)
     * @return true 表示复制成功
     */
    private fun copyAssetToFileWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(false, "OkioAssetUtils.copyAssetToFileWithProgress 失败：assets 文件不存在：$assetPath")
        if (destPath.isBlank()) return failureReporter.fail(false, "OkioAssetUtils.copyAssetToFileWithProgress 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { tempFile ->
            val totalSize = getAssetSizeFast(failureReporter, context, assetPath)
            var bytesCopied = 0L
            var lastReport = 0L

            context.assets.open(assetPath).use { stream ->
                val rawSource: Source = stream.source()
                val progressSource = object : ForwardingSource(rawSource) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = super.read(sink, byteCount)
                        if (read != -1L) {
                            bytesCopied += read
                            if (onProgress != null && bytesCopied - lastReport >= chunkSize) {
                                safeCallback(failureReporter) { onProgress(bytesCopied, totalSize) }
                                lastReport = bytesCopied
                            }
                        }
                        return read
                    }
                }

                progressSource.buffer().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }

                safeCallback(failureReporter) { onProgress?.invoke(bytesCopied, totalSize) }
            }
        }
    }

    /**
     * 异步带进度复制 assets（Okio）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyAssetToFileWithProgressSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback {
                onResult(copyAssetToFileWithProgress(appContext, assetPath, destPath, overwrite, chunkSize, onProgress, onFailure = onFailure))
            }
        }
    }

    // ==================== Assets 超时控制 ====================

    /**
     * 带超时的 assets 文件读取
     *
     * 通过 Okio 的 Timeout 机制实现，比 java.io 更可靠。
     * 适用于从压缩 APK 中读取大文件时需要超时保护的场景。
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param timeoutMs 超时毫秒数
     * @return ByteString，超时或失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 读取大型 assets 文件，30 秒超时
     * val data = OkioAssetUtils.readAssetWithTimeout(context, "large_data.bin", 30_000)
     * ```
     */
    private fun readAssetWithTimeoutImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String, timeoutMs: Long): ByteString? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetWithTimeout 失败：assets 文件不存在：$assetPath")
        if (!canReadAssetIntoMemory(failureReporter, context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetWithTimeout 失败：assets 文件超出内存读取限制")
        return try {
            context.assets.open(assetPath).use { stream ->
                val source = stream.source()
                source.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                source.buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioAssetUtils", "Read timed out after ${timeoutMs}ms: $assetPath")
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
     * 异步超时读取 assets
     */
    @JvmStatic
    suspend fun readAssetWithTimeoutSuspend(
        context: Context,
        assetPath: String,
        timeoutMs: Long,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetWithTimeout(appContext, assetPath, timeoutMs, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    // ==================== Assets 分块读取（带进度） ====================

    /**
     * 分块读取 assets 文件（带进度回调）
     *
     * 适合大型 assets 文件，每次只读取一个 chunk 到内存。
     *
     * @param context    Android Context
     * @param assetPath  assets 文件路径
     * @param chunkSize  每块大小（字节），用于进度回调间隔
     * @param onProgress 进度回调 (bytesRead, totalBytes)
     * @return ByteArray，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val data = OkioAssetUtils.readAssetWithProgress(context, "huge_model.bin",
     *     onProgress = { read, total ->
     *         updateProgressBar((read * 100 / total).toInt())
     *     })
     * ```
     */
    private fun readAssetWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): ByteArray? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetWithProgress 失败：assets 文件不存在：$assetPath")

        return try {
            if (!canReadAssetIntoMemory(failureReporter, context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetWithProgress 失败：assets 文件超出内存读取限制")
            val totalSize = getAssetSizeFast(failureReporter, context, assetPath)
            var bytesRead = 0L
            var lastReport = 0L

            context.assets.open(assetPath).use { stream ->
                val rawSource = stream.source()
                val progressSource = object : ForwardingSource(rawSource) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = super.read(sink, byteCount)
                        if (read != -1L) {
                            bytesRead += read
                            if (onProgress != null && bytesRead - lastReport >= chunkSize) {
                                safeCallback(failureReporter) { onProgress(bytesRead, totalSize) }
                                lastReport = bytesRead
                            }
                        }
                        return read
                    }
                }

                progressSource.buffer().use { buffered ->
                    val result = buffered.readByteArrayWithLimit(failureReporter, DEFAULT_MAX_IN_MEMORY_BYTES)
                    safeCallback(failureReporter) { onProgress?.invoke(bytesRead, totalSize) }
                    result
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
     * 异步分块读取 assets（带进度）
     */
    @JvmStatic
    suspend fun readAssetWithProgressSuspend(
        context: Context,
        assetPath: String,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetWithProgress(appContext, assetPath, chunkSize, onProgress, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    // ==================== Assets Gzip 解压 ====================

    /**
     * 读取并解压 Gzip 压缩的 assets 文件
     *
     * 当 assets 中存放 .gz 压缩文件以节省 APK 体积时，可直接解压读取。
     *
     * @param context   Android Context
     * @param assetPath assets 中的 gzip 文件路径
     * @return 解压后的字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // assets/data.json.gz → 直接解压读取
     * val json = OkioAssetUtils.readAssetGzip(context, "data/data.json.gz")
     * val text = json?.let { String(it) }
     * ```
     */
    private fun readAssetGzipImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): ByteArray? {
        if (!AssetUtils.assetExists(context, assetPath)) return failureReporter.fail(null, "OkioAssetUtils.readAssetGzip 失败：assets 文件不存在：$assetPath")
        return try {
            context.assets.open(assetPath).use { stream ->
                stream.source().use { raw ->
                    GzipSource(raw).buffer().use { gzip ->
                        gzip.readByteArrayWithLimit(failureReporter, DEFAULT_MAX_IN_MEMORY_BYTES)
                    }
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
     * 异步读取并解压 assets Gzip
     */
    @JvmStatic
    suspend fun readAssetGzipSuspend(
        context: Context,
        assetPath: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetGzip(appContext, assetPath, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 读取 Gzip 压缩的 assets 并解压为字符串
     *
     * @param context   Android Context
     * @param assetPath assets 中的 gzip 文件路径
     * @param charset   字符编码，默认 UTF-8
     * @return 解压后的字符串，失败返回 null
     */
    private fun readAssetGzipAsTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8
    ): String? {
        val bytes = readAssetGzipImpl(failureReporter, context, assetPath) ?: return failureReporter.fail(null, "OkioAssetUtils.readAssetGzipAsText 失败：解压 assets gzip 失败")
        return String(bytes, charset)
    }

    // ==================== Raw 资源 → ByteString ====================

    /**
     * 读取 res/raw 资源为不可变 [ByteString]（Okio 实现）
     *
     * @param context Android Context
     * @param resId   R.raw.xxx 资源 ID
     * @return ByteString，失败返回 null
     */
    private fun readRawByteStringImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int): ByteString? {
        if (!canReadRawIntoMemory(failureReporter, context, resId)) return failureReporter.fail(null, "OkioAssetUtils.readRawByteString 失败：raw 资源超出内存读取限制")
        return try {
            context.resources.openRawResource(resId).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
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
     * 异步读取 raw 为 ByteString
     */
    @JvmStatic
    suspend fun readRawByteStringSuspend(
        context: Context,
        resId: Int,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readRawByteString(appContext, resId, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    // ==================== Raw 资源 → String / Buffer ====================

    /**
     * 读取 res/raw 资源为字符串（Okio 实现）
     *
     * @param context Android Context
     * @param resId   R.raw.xxx 资源 ID
     * @param charset 字符编码，默认 UTF-8
     * @return 字符串，失败返回 null
     */
    private fun readRawUtf8Impl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        charset: Charset = Charsets.UTF_8
    ): String? {
        if (!canReadRawIntoMemory(failureReporter, context, resId)) return failureReporter.fail(null, "OkioAssetUtils.readRawUtf8 失败：raw 资源超出内存读取限制")
        return try {
            context.resources.openRawResource(resId).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readString(charset)
                }
            }
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
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
     * 异步读取 raw 为字符串
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readRawUtf8Suspend(
        context: Context,
        resId: Int,
        charset: Charset = Charsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readRawUtf8(appContext, resId, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 读取 res/raw 资源到 Okio [Buffer]
     *
     * @param context Android Context
     * @param resId   R.raw.xxx 资源 ID
     * @return Buffer (可变)，失败返回 null
     */
    private fun readRawToBufferImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int): Buffer? {
        if (!canReadRawIntoMemory(failureReporter, context, resId)) return failureReporter.fail(null, "OkioAssetUtils.readRawToBuffer 失败：raw 资源超出内存读取限制")
        return try {
            val buffer = Buffer()
            context.resources.openRawResource(resId).use { stream ->
                stream.source().use { source ->
                    buffer.writeAll(source)
                }
            }
            buffer
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
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

    // ==================== Raw 资源复制到文件系统（Okio） ====================

    /**
     * 使用 Okio 将 raw 资源复制到文件系统
     *
     * @param context    Android Context
     * @param resId      R.raw.xxx 资源 ID
     * @param destPath   目标文件路径
     * @param overwrite  是否覆盖，默认 true
     * @return true 表示复制成功
     */
    private fun copyRawToFileImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (destPath.isBlank()) return failureReporter.fail(false, "OkioAssetUtils.copyRawToFile 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { tempFile ->
            context.resources.openRawResource(resId).use { stream ->
                stream.source().buffer().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        }
    }

    /**
     * 异步复制 raw 到文件系统（Okio）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyRawToFileSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyRawToFile(appContext, resId, destPath, overwrite, onFailure = onFailure)) }
        }
    }

    /**
     * 使用 Okio 将 raw 资源复制到文件系统（带进度回调）
     *
     * @param context     Android Context
     * @param resId       R.raw.xxx 资源 ID
     * @param destPath    目标路径
     * @param overwrite   是否覆盖
     * @param chunkSize   进度回调最小间隔（字节）
     * @param onProgress  进度回调 (bytesCopied, totalBytes)
     * @return true 表示复制成功
     */
    private fun copyRawToFileWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (destPath.isBlank()) return failureReporter.fail(false, "OkioAssetUtils.copyRawToFileWithProgress 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { tempFile ->
            val totalSize = getRawSizeFast(failureReporter, context, resId)
            var bytesCopied = 0L
            var lastReport = 0L

            context.resources.openRawResource(resId).use { stream ->
                val rawSource: Source = stream.source()
                val progressSource = object : ForwardingSource(rawSource) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = super.read(sink, byteCount)
                        if (read != -1L) {
                            bytesCopied += read
                            if (onProgress != null && bytesCopied - lastReport >= chunkSize) {
                                safeCallback(failureReporter) { onProgress(bytesCopied, totalSize) }
                                lastReport = bytesCopied
                            }
                        }
                        return read
                    }
                }

                progressSource.buffer().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }

                safeCallback(failureReporter) { onProgress?.invoke(bytesCopied, totalSize) }
            }
        }
    }

    /**
     * 异步带进度复制 raw 资源（Okio）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyRawToFileWithProgressSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback {
                onResult(copyRawToFileWithProgress(appContext, resId, destPath, overwrite, chunkSize, onProgress, onFailure = onFailure))
            }
        }
    }

    // ==================== Raw 资源超时控制 ====================

    /**
     * 带超时的 raw 资源读取
     *
     * @param context   Android Context
     * @param resId     R.raw.xxx 资源 ID
     * @param timeoutMs 超时毫秒数
     * @return ByteString，超时或失败返回 null
     */
    private fun readRawWithTimeoutImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int, timeoutMs: Long): ByteString? {
        if (!canReadRawIntoMemory(failureReporter, context, resId)) return failureReporter.fail(null, "OkioAssetUtils.readRawWithTimeout 失败：raw 资源超出内存读取限制")
        return try {
            context.resources.openRawResource(resId).use { stream ->
                val source = stream.source()
                source.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                source.buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioAssetUtils", "Read timed out after ${timeoutMs}ms: resId=$resId")
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
     * 异步超时读取 raw 资源
     */
    @JvmStatic
    suspend fun readRawWithTimeoutSuspend(
        context: Context,
        resId: Int,
        timeoutMs: Long,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readRawWithTimeout(appContext, resId, timeoutMs, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
                safeCallback { onResult(null, t) }
            }
        }
    }

    private fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        destPath: String,
        overwrite: Boolean,
        writer: (File) -> Unit
    ): Boolean {
        if (destPath.isBlank()) return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：输入路径或参数为空")
        var tempFile: File? = null
        return try {
            val destFile = File(destPath).canonicalFile
            if (destFile.exists()) {
                if (!overwrite) return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
                if (destFile.isDirectory) return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：目标路径是目录")
            }

            val parent = destFile.parentFile ?: return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：无法获取目标文件的父目录")
            if (parent.exists()) {
                if (!parent.isDirectory) return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：父路径不是目录：${parent.path}")
            } else if (!parent.mkdirs()) {
                return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：无法创建父目录：${parent.path}")
            }

            tempFile = File.createTempFile(".${destFile.name}.", ".tmp", parent)
            writer(tempFile)
            FileOutputStream(tempFile, true).use { it.fd.sync() }

            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "OkioAssetUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
            Os.rename(tempFile.absolutePath, destFile.absolutePath)
            tempFile = null
            true
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: ErrnoException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } finally {
            tempFile?.delete()
        }
    }

    private fun getAssetSizeFast(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { descriptor -> descriptor.length }
        } catch (e: IOException) {
            // 压缩的 assets 没有可用文件描述符，继续按流读取。
            UNKNOWN_SIZE
        }
    }

    private fun getRawSizeFast(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int): Long {
        return try {
            context.resources.openRawResourceFd(resId).use { descriptor -> descriptor.length }
        } catch (e: Resources.NotFoundException) {
            UNKNOWN_SIZE
        } catch (e: IOException) {
            // 压缩资源无法使用 openRawResourceFd，继续按流读取。
            UNKNOWN_SIZE
        }
    }

    private fun canReadAssetIntoMemory(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): Boolean {
        val size = getAssetSizeFast(failureReporter, context, assetPath)
        return size == UNKNOWN_SIZE || size <= DEFAULT_MAX_IN_MEMORY_BYTES
    }

    private fun canReadRawIntoMemory(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int): Boolean {
        val size = getRawSizeFast(failureReporter, context, resId)
        return size == UNKNOWN_SIZE || size <= DEFAULT_MAX_IN_MEMORY_BYTES
    }

    private fun okio.BufferedSource.readByteArrayWithLimit(failureReporter: FileFailureCallbacks.Reporter, maxBytes: Long): ByteArray {
        if (maxBytes <= 0L) return readByteArray()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = read(buffer, DEFAULT_BUFFER_SIZE)
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

    /** readAssetByteString 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetByteString(
        context: Context,
        assetPath: String,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteString? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetByteString", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetByteStringImpl(failureReporter, context, assetPath)
        }

    /** readAssetUtf8 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetUtf8(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetUtf8", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetUtf8Impl(failureReporter, context, assetPath, charset)
        }

    /** readAssetToBuffer 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetToBuffer(
        context: Context,
        assetPath: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Buffer? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetToBuffer", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetToBufferImpl(failureReporter, context, assetPath)
        }

    /** readAssetLines 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetLines(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): List<String>? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetLines", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetLinesImpl(failureReporter, context, assetPath, charset, maxBytes)
        }

    /** readAssetLinesStreaming 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetLinesStreaming(
        context: Context,
        assetPath: String,
        onEachLine: (line: String, index: Int) -> Boolean,
        onFailure: ((Exception) -> Unit)? = null
    ): Int =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetLinesStreaming", "$assetPath", onFailure, { it < 0 }, -1) { failureReporter ->
            readAssetLinesStreamingImpl(failureReporter, context, assetPath, onEachLine)
        }

    /** copyAssetToFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFile(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioAssetUtils.copyAssetToFile", "$assetPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyAssetToFileImpl(failureReporter, context, assetPath, destPath, overwrite)
        }

    /** copyAssetToFileWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileWithProgress(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioAssetUtils.copyAssetToFileWithProgress", "$assetPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyAssetToFileWithProgressImpl(failureReporter, context, assetPath, destPath, overwrite, chunkSize, onProgress)
        }

    /** readAssetWithTimeout 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetWithTimeout(
        context: Context,
        assetPath: String,
        timeoutMs: Long,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteString? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetWithTimeout", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetWithTimeoutImpl(failureReporter, context, assetPath, timeoutMs)
        }

    /** readAssetWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetWithProgress(
        context: Context,
        assetPath: String,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetWithProgress", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetWithProgressImpl(failureReporter, context, assetPath, chunkSize, onProgress)
        }

    /** readAssetGzip 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetGzip(
        context: Context,
        assetPath: String,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetGzip", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetGzipImpl(failureReporter, context, assetPath)
        }

    /** readAssetGzipAsText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetGzipAsText(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioAssetUtils.readAssetGzipAsText", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetGzipAsTextImpl(failureReporter, context, assetPath, charset)
        }

    /** readRawByteString 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readRawByteString(
        context: Context,
        resId: Int,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteString? =
        FileFailureCallbacks.run("OkioAssetUtils.readRawByteString", "$resId", onFailure, { it == null }, null) { failureReporter ->
            readRawByteStringImpl(failureReporter, context, resId)
        }

    /** readRawUtf8 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readRawUtf8(
        context: Context,
        resId: Int,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioAssetUtils.readRawUtf8", "$resId", onFailure, { it == null }, null) { failureReporter ->
            readRawUtf8Impl(failureReporter, context, resId, charset)
        }

    /** readRawToBuffer 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readRawToBuffer(
        context: Context,
        resId: Int,
        onFailure: ((Exception) -> Unit)? = null
    ): Buffer? =
        FileFailureCallbacks.run("OkioAssetUtils.readRawToBuffer", "$resId", onFailure, { it == null }, null) { failureReporter ->
            readRawToBufferImpl(failureReporter, context, resId)
        }

    /** copyRawToFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFile(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioAssetUtils.copyRawToFile", "$resId → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyRawToFileImpl(failureReporter, context, resId, destPath, overwrite)
        }

    /** copyRawToFileWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileWithProgress(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioAssetUtils.copyRawToFileWithProgress", "$resId → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyRawToFileWithProgressImpl(failureReporter, context, resId, destPath, overwrite, chunkSize, onProgress)
        }

    /** readRawWithTimeout 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readRawWithTimeout(
        context: Context,
        resId: Int,
        timeoutMs: Long,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteString? =
        FileFailureCallbacks.run("OkioAssetUtils.readRawWithTimeout", "$resId", onFailure, { it == null }, null) { failureReporter ->
            readRawWithTimeoutImpl(failureReporter, context, resId, timeoutMs)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetToFileSuspendLegacyCallback")
    suspend fun copyAssetToFileSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = copyAssetToFileSuspend(context = context, assetPath = assetPath, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetToFileWithProgressSuspendLegacyCallback")
    suspend fun copyAssetToFileWithProgressSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = copyAssetToFileWithProgressSuspend(context = context, assetPath = assetPath, destPath = destPath, overwrite = overwrite, chunkSize = chunkSize, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyRawToFileSuspendLegacyCallback")
    suspend fun copyRawToFileSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = copyRawToFileSuspend(context = context, resId = resId, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyRawToFileWithProgressSuspendLegacyCallback")
    suspend fun copyRawToFileWithProgressSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = copyRawToFileWithProgressSuspend(context = context, resId = resId, destPath = destPath, overwrite = overwrite, chunkSize = chunkSize, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("readAssetLinesStreamingLegacyCallback")
    fun readAssetLinesStreaming(
        context: Context,
        assetPath: String,
        onEachLine: (line: String, index: Int) -> Boolean
    ) = readAssetLinesStreaming(context = context, assetPath = assetPath, onEachLine = onEachLine, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetToFileWithProgressLegacyCallback")
    fun copyAssetToFileWithProgress(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) = copyAssetToFileWithProgress(context = context, assetPath = assetPath, destPath = destPath, overwrite = overwrite, chunkSize = chunkSize, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("readAssetWithProgressLegacyCallback")
    fun readAssetWithProgress(
        context: Context,
        assetPath: String,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ) = readAssetWithProgress(context = context, assetPath = assetPath, chunkSize = chunkSize, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyRawToFileWithProgressLegacyCallback")
    fun copyRawToFileWithProgress(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) = copyRawToFileWithProgress(context = context, resId = resId, destPath = destPath, overwrite = overwrite, chunkSize = chunkSize, onProgress = onProgress, onFailure = null)
}
