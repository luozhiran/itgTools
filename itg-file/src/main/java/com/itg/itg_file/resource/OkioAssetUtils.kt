package com.itg.itg_file.resource

import android.content.Context
import android.content.res.Resources
import com.itg.concurrent.Concurrent
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
import java.nio.charset.Charset
import java.util.concurrent.Future
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
    @JvmStatic
    fun readAssetByteString(context: Context, assetPath: String): ByteString? {
        if (!AssetUtils.assetExists(context, assetPath)) return null
        if (!canReadAssetIntoMemory(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 assets 为 ByteString
     */
    @JvmStatic
    fun readAssetByteStringAsync(
        context: Context,
        assetPath: String,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetByteString(appContext, assetPath)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    @JvmOverloads
    fun readAssetUtf8(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8
    ): String? {
        if (!AssetUtils.assetExists(context, assetPath)) return null
        if (!canReadAssetIntoMemory(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readString(charset)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 assets 为字符串
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetUtf8Async(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetUtf8(appContext, assetPath, charset)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    fun readAssetToBuffer(context: Context, assetPath: String): Buffer? {
        if (!AssetUtils.assetExists(context, assetPath)) return null
        if (!canReadAssetIntoMemory(context, assetPath)) return null
        return try {
            val buffer = Buffer()
            context.assets.open(assetPath).use { stream ->
                stream.source().use { source ->
                    buffer.writeAll(source)
                }
            }
            buffer
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 assets 到 Buffer
     */
    @JvmStatic
    fun readAssetToBufferAsync(
        context: Context,
        assetPath: String,
        onResult: (Buffer?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetToBuffer(appContext, assetPath)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    @JvmOverloads
    fun readAssetLines(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = DEFAULT_MAX_IN_MEMORY_BYTES
    ): List<String>? {
        if (!AssetUtils.assetExists(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                val bytes = stream.source().buffer().use { it.readByteArrayWithLimit(maxBytes) }
                String(bytes, charset).lineSequence().toList()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取 assets（Okio）
     */
    @JvmStatic
    fun readAssetLinesAsync(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetLines(appContext, assetPath, charset)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    fun readAssetLinesStreaming(
        context: Context,
        assetPath: String,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!AssetUtils.assetExists(context, assetPath)) return -1
        return try {
            var count = 0
            context.assets.open(assetPath).use { stream ->
                stream.source().buffer().use { buffered ->
                    while (true) {
                        val line = buffered.readUtf8Line() ?: break
                        if (!safeLineCallback(line, count, onEachLine)) break
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
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFile(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (!AssetUtils.assetExists(context, assetPath)) return false
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { tempFile ->
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
    fun copyAssetToFileAsync(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            safeCallback { onResult(copyAssetToFile(appContext, assetPath, destPath, overwrite)) }
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
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileWithProgress(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!AssetUtils.assetExists(context, assetPath)) return false
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { tempFile ->
            val totalSize = getAssetSizeFast(context, assetPath)
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
                                safeCallback { onProgress(bytesCopied, totalSize) }
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

                safeCallback { onProgress?.invoke(bytesCopied, totalSize) }
            }
        }
    }

    /**
     * 异步带进度复制 assets（Okio）
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileWithProgressAsync(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            safeCallback {
                onResult(copyAssetToFileWithProgress(appContext, assetPath, destPath, overwrite, chunkSize, onProgress))
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
    @JvmStatic
    fun readAssetWithTimeout(context: Context, assetPath: String, timeoutMs: Long): ByteString? {
        if (!AssetUtils.assetExists(context, assetPath)) return null
        if (!canReadAssetIntoMemory(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                val source = stream.source()
                source.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                source.buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: IOException) {
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioAssetUtils", "Read timed out after ${timeoutMs}ms: $assetPath")
            }
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步超时读取 assets
     */
    @JvmStatic
    fun readAssetWithTimeoutAsync(
        context: Context,
        assetPath: String,
        timeoutMs: Long,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetWithTimeout(appContext, assetPath, timeoutMs)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed/timed out: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    @JvmOverloads
    fun readAssetWithProgress(
        context: Context,
        assetPath: String,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): ByteArray? {
        if (!AssetUtils.assetExists(context, assetPath)) return null

        return try {
            if (!canReadAssetIntoMemory(context, assetPath)) return null
            val totalSize = getAssetSizeFast(context, assetPath)
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
                                safeCallback { onProgress(bytesRead, totalSize) }
                                lastReport = bytesRead
                            }
                        }
                        return read
                    }
                }

                progressSource.buffer().use { buffered ->
                    val result = buffered.readByteArrayWithLimit(DEFAULT_MAX_IN_MEMORY_BYTES)
                    safeCallback { onProgress?.invoke(bytesRead, totalSize) }
                    result
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步分块读取 assets（带进度）
     */
    @JvmStatic
    fun readAssetWithProgressAsync(
        context: Context,
        assetPath: String,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetWithProgress(appContext, assetPath, chunkSize, onProgress)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    fun readAssetGzip(context: Context, assetPath: String): ByteArray? {
        if (!AssetUtils.assetExists(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                stream.source().use { raw ->
                    GzipSource(raw).buffer().use { gzip ->
                        gzip.readByteArrayWithLimit(DEFAULT_MAX_IN_MEMORY_BYTES)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取并解压 assets Gzip
     */
    @JvmStatic
    fun readAssetGzipAsync(
        context: Context,
        assetPath: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readAssetGzip(appContext, assetPath)
                safeCallback { onResult(result, if (result == null) IOException("Gzip read failed: $assetPath") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    @JvmOverloads
    fun readAssetGzipAsText(
        context: Context,
        assetPath: String,
        charset: Charset = Charsets.UTF_8
    ): String? {
        val bytes = readAssetGzip(context, assetPath) ?: return null
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
    @JvmStatic
    fun readRawByteString(context: Context, resId: Int): ByteString? {
        if (!canReadRawIntoMemory(context, resId)) return null
        return try {
            context.resources.openRawResource(resId).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 raw 为 ByteString
     */
    @JvmStatic
    fun readRawByteStringAsync(
        context: Context,
        resId: Int,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readRawByteString(appContext, resId)
                safeCallback { onResult(result, if (result == null) IOException("Read raw failed: $resId") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    @JvmOverloads
    fun readRawUtf8(
        context: Context,
        resId: Int,
        charset: Charset = Charsets.UTF_8
    ): String? {
        if (!canReadRawIntoMemory(context, resId)) return null
        return try {
            context.resources.openRawResource(resId).use { stream ->
                stream.source().buffer().use { buffered ->
                    buffered.readString(charset)
                }
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 raw 为字符串
     */
    @JvmStatic
    @JvmOverloads
    fun readRawUtf8Async(
        context: Context,
        resId: Int,
        charset: Charset = Charsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readRawUtf8(appContext, resId, charset)
                safeCallback { onResult(result, if (result == null) IOException("Read raw failed: $resId") else null) }
            } catch (t: Throwable) {
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
    @JvmStatic
    fun readRawToBuffer(context: Context, resId: Int): Buffer? {
        if (!canReadRawIntoMemory(context, resId)) return null
        return try {
            val buffer = Buffer()
            context.resources.openRawResource(resId).use { stream ->
                stream.source().use { source ->
                    buffer.writeAll(source)
                }
            }
            buffer
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
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
    @JvmStatic
    @JvmOverloads
    fun copyRawToFile(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { tempFile ->
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
    fun copyRawToFileAsync(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            safeCallback { onResult(copyRawToFile(appContext, resId, destPath, overwrite)) }
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
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileWithProgress(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { tempFile ->
            val totalSize = getRawSizeFast(context, resId)
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
                                safeCallback { onProgress(bytesCopied, totalSize) }
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

                safeCallback { onProgress?.invoke(bytesCopied, totalSize) }
            }
        }
    }

    /**
     * 异步带进度复制 raw 资源（Okio）
     */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileWithProgressAsync(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        chunkSize: Long = DEFAULT_BUFFER_SIZE,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            safeCallback {
                onResult(copyRawToFileWithProgress(appContext, resId, destPath, overwrite, chunkSize, onProgress))
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
    @JvmStatic
    fun readRawWithTimeout(context: Context, resId: Int, timeoutMs: Long): ByteString? {
        if (!canReadRawIntoMemory(context, resId)) return null
        return try {
            context.resources.openRawResource(resId).use { stream ->
                val source = stream.source()
                source.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                source.buffer().use { buffered ->
                    buffered.readByteString()
                }
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioAssetUtils", "Read timed out after ${timeoutMs}ms: resId=$resId")
            }
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步超时读取 raw 资源
     */
    @JvmStatic
    fun readRawWithTimeoutAsync(
        context: Context,
        resId: Int,
        timeoutMs: Long,
        onResult: (ByteString?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return Concurrent.io {
            try {
                val result = readRawWithTimeout(appContext, resId, timeoutMs)
                safeCallback { onResult(result, if (result == null) IOException("Read raw failed/timed out: $resId") else null) }
            } catch (t: Throwable) {
                safeCallback { onResult(null, t) }
            }
        }
    }

    private fun writeToFileAtomically(
        destPath: String,
        overwrite: Boolean,
        writer: (File) -> Unit
    ): Boolean {
        if (destPath.isBlank()) return false
        var tempFile: File? = null
        return try {
            val destFile = File(destPath).canonicalFile
            if (destFile.exists()) {
                if (!overwrite || destFile.isDirectory) return false
            }

            val parent = destFile.parentFile ?: return false
            if (parent.exists()) {
                if (!parent.isDirectory) return false
            } else if (!parent.mkdirs()) {
                return false
            }

            tempFile = File.createTempFile(".${destFile.name}.", ".tmp", parent)
            writer(tempFile)

            if (destFile.exists() && !overwrite) return false
            if (!tempFile.renameTo(destFile)) {
                if (destFile.exists() && (!destFile.delete() || !tempFile.renameTo(destFile))) {
                    throw IOException("Failed to replace destination: ${destFile.absolutePath}")
                } else if (!destFile.exists() && !tempFile.renameTo(destFile)) {
                    throw IOException("Failed to move temp file to destination: ${destFile.absolutePath}")
                }
            }
            tempFile = null
            true
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        } finally {
            tempFile?.delete()
        }
    }

    private fun getAssetSizeFast(context: Context, assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { descriptor -> descriptor.length }
        } catch (e: IOException) {
            UNKNOWN_SIZE
        }
    }

    private fun getRawSizeFast(context: Context, resId: Int): Long {
        return try {
            context.resources.openRawResourceFd(resId).use { descriptor -> descriptor.length }
        } catch (e: Resources.NotFoundException) {
            UNKNOWN_SIZE
        } catch (e: IOException) {
            UNKNOWN_SIZE
        }
    }

    private fun canReadAssetIntoMemory(context: Context, assetPath: String): Boolean {
        val size = getAssetSizeFast(context, assetPath)
        return size == UNKNOWN_SIZE || size <= DEFAULT_MAX_IN_MEMORY_BYTES
    }

    private fun canReadRawIntoMemory(context: Context, resId: Int): Boolean {
        val size = getRawSizeFast(context, resId)
        return size == UNKNOWN_SIZE || size <= DEFAULT_MAX_IN_MEMORY_BYTES
    }

    private fun okio.BufferedSource.readByteArrayWithLimit(maxBytes: Long): ByteArray {
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

    private inline fun safeCallback(callback: () -> Unit) {
        try {
            callback()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private inline fun safeLineCallback(
        line: String,
        index: Int,
        callback: (line: String, index: Int) -> Boolean
    ): Boolean {
        return try {
            callback(line, index)
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }
}
