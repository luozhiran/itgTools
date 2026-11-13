package com.itg.itg_file.write
import com.itg.itg_file.core.FileFailureCallbacks

import android.content.Context
import android.net.Uri
import android.system.Os
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 文件写入工具类
 *
 * 提供多种文件写入方式，涵盖文本、字节、追加、流写入、大文件分块写入等场景。
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 *
 * 核心特性:
 * - 写入字符串（覆盖/追加）
 * - 写入字节数组
 * - 从 InputStream 写入文件
 * - 写入到 Content URI
 * - 分块写入（适合大文件，带进度回调）
 * - 原子写入（写临时文件 → 重命名，防止写入中断导致文件损坏）
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object FileWriteUtils {

    private const val DEFAULT_BUFFER_SIZE = 8192  // 8KB

    // ==================== 写入字符串 ====================

    /**
     * 写入字符串到文件（覆盖模式）
     *
     * 自动创建父目录。如果文件已存在则覆盖。
     *
     * @param path    文件路径
     * @param content 要写入的字符串
     * @param charset 字符编码，默认 UTF-8
     * @return true 表示写入成功
     *
     * 使用示例:
     * ```kotlin
     * FileWriteUtils.writeText("/sdcard/data.json", """{"name": "test"}""")
     * ```
     */
    private fun writeTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8
    ): Boolean {
        return writeToFileAtomically(failureReporter, path) { tempFile ->
            tempFile.writeText(content, charset)
        }
    }

    /**
     * 异步写入字符串
     *
     * @param path     文件路径
     * @param content  内容
     * @param charset  编码
     * @param onResult 回调 (success: Boolean)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeTextSuspend(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeText(path, content, charset, onFailure = onFailure)) } }
    }

    /**
     * 追加字符串到文件末尾
     *
     * 如果文件不存在则创建。
     *
     * @param path    文件路径
     * @param content 要追加的内容
     * @param charset 字符编码
     * @return true 表示追加成功
     *
     * 使用示例:
     * ```kotlin
     * FileWriteUtils.appendText("/sdcard/log.txt", "[INFO] App started\n")
     * ```
     */
    private fun appendTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8
    ): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "FileWriteUtils.appendText 失败：输入路径或参数为空")
        return try {
            val file = File(path)
            if (!ensureParentDirectory(failureReporter, file)) return failureReporter.fail(false, "FileWriteUtils.appendText 失败：无法创建目标父目录")
            file.appendText(content, charset)
            true
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步追加字符串
     */
    @JvmStatic
    @JvmOverloads
    suspend fun appendTextSuspend(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(appendText(path, content, charset, onFailure = onFailure)) } }
    }

    // ==================== 写入字节数组 ====================

    /**
     * 写入字节数组到文件（覆盖模式）
     *
     * @param path  文件路径
     * @param bytes 字节数组
     * @return true 表示写入成功
     */
    private fun writeBytesImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, bytes: ByteArray): Boolean {
        return writeToFileAtomically(failureReporter, path) { tempFile ->
            tempFile.writeBytes(bytes)
        }
    }

    /**
     * 异步写入字节数组
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeBytesSuspend(
        path: String,
        bytes: ByteArray,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeBytes(path, bytes, onFailure = onFailure)) } }
    }

    /**
     * 追加字节数组到文件末尾
     */
    private fun appendBytesImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, bytes: ByteArray): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "FileWriteUtils.appendBytes 失败：输入路径或参数为空")
        return try {
            val file = File(path)
            if (!ensureParentDirectory(failureReporter, file)) return failureReporter.fail(false, "FileWriteUtils.appendBytes 失败：无法创建目标父目录")
            file.appendBytes(bytes)
            true
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    // ==================== 从流写入 ====================

    /**
     * 从 InputStream 写入文件
     *
     * 注意: 调用方负责关闭 InputStream。
     *
     * @param path        目标文件路径
     * @param inputStream 输入流
     * @param overwrite   是否覆盖已存在文件
     * @param onProgress  进度回调 (bytesWritten, estimatedTotal)，
     *                    estimatedTotal 可能在非文件流场景为 -1
     * @return true 表示写入成功
     */
    private fun writeFromStreamImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null
    ): Boolean {
        return writeToFileAtomically(failureReporter, path, overwrite) { tempFile ->
            FileOutputStream(tempFile).use { output ->
                copyStream(failureReporter, inputStream, output, onProgress)
            }
        }
    }

    /**
     * 异步从流写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeFromStreamSuspend(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            safeCallback { onResult(writeFromStream(path, inputStream, overwrite, onProgress, onFailure = onFailure)) }
        }
    }

    // ==================== 写入到 Content URI ====================

    /**
     * 将字节数组写入 Content URI
     *
     * 适用于 Android 10+ 的 MediaStore / SAF 写入。
     *
     * @param context   Android Context
     * @param uri       目标 URI
     * @param bytes     字节数组
     * @param mimeType  MIME 类型（用于 ContentResolver），可选
     * @return true 表示写入成功
     *
     * 使用示例:
     * ```kotlin
     * val success = FileWriteUtils.writeToUri(context, uri, imageBytes, "image/jpeg")
     * ```
     */
    @Suppress("UNUSED_PARAMETER")
    private fun writeToUriImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        mimeType: String? = null
    ): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: return failureReporter.fail(false, "写入 URI 失败：无法打开目标输出流：$uri")
            outputStream.use { output ->
                output.write(bytes)
                output.flush()
            }
            true
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 从 InputStream 写入 Content URI
     *
     * @param context      Android Context
     * @param uri          目标 URI
     * @param inputStream  输入流
     * @param mimeType     MIME 类型
     * @return true 表示写入成功
     */
    @Suppress("UNUSED_PARAMETER")
    private fun writeStreamToUriImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        uri: Uri,
        inputStream: InputStream,
        mimeType: String? = null
    ): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: return failureReporter.fail(false, "写入 URI 失败：无法打开目标输出流：$uri")
            outputStream.use { output ->
                copyStream(failureReporter, inputStream, output)
            }
            true
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    // ==================== 原子写入 ====================

    /**
     * 原子写入字符串
     *
     * 先写入临时文件，成功后重命名为目标文件，
     * 防止写入过程中进程崩溃导致文件损坏。
     *
     * @param path    目标文件路径
     * @param content 写入内容
     * @param charset 编码
     * @return true 表示写入成功
     *
     * 使用示例:
     * ```kotlin
     * // 安全地写入配置文件
     * FileWriteUtils.writeTextAtomic("/data/config.json", configJson)
     * ```
     */
    private fun writeTextAtomicImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8
    ): Boolean {
        return writeToFileAtomically(failureReporter, path) { tempFile ->
            tempFile.writeText(content, charset)
        }
    }

    /**
     * 异步原子写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeTextAtomicSuspend(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeTextAtomic(path, content, charset, onFailure = onFailure)) } }
    }

    /**
     * 原子写入字节数组
     *
     * @param path  目标文件路径
     * @param bytes 字节数据
     * @return true 表示写入成功
     */
    private fun writeBytesAtomicImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, bytes: ByteArray): Boolean {
        return writeToFileAtomically(failureReporter, path) { tempFile ->
            tempFile.writeBytes(bytes)
        }
    }

    // ==================== 大文件分块写入 ====================

    /**
     * 分块写入字节数组到文件
     *
     * 适用于超大文件写入，带进度回调，逐块写入避免一次性消耗过多内存。
     *
     * @param path       目标文件路径
     * @param data       完整数据
     * @param chunkSize  每块大小，默认 64KB
     * @param overwrite  是否覆盖
     * @param onProgress 进度回调 (bytesWritten, totalBytes)
     * @return true 表示写入成功
     *
     * 使用示例:
     * ```kotlin
     * FileWriteUtils.writeBytesInChunks("/sdcard/big_file.bin", hugeData,
     *     onProgress = { written, total ->
     *         updateProgressBar((written * 100 / total).toInt())
     *     })
     * ```
     */
    private fun writeBytesInChunksImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null
    ): Boolean {
        if (chunkSize <= 0) return failureReporter.fail(false, "FileWriteUtils.writeBytesInChunks 失败：分块大小无效")

        return writeToFileAtomically(failureReporter, path, overwrite) { tempFile ->
            FileOutputStream(tempFile).use { output ->
                var offset = 0
                while (offset < data.size) {
                    val remaining = data.size - offset
                    val size = minOf(chunkSize, remaining)
                    output.write(data, offset, size)
                    offset += size
                    safeProgress(failureReporter, onProgress, offset.toLong(), data.size.toLong())
                }
                output.flush()
            }
        }
    }

    /**
     * 异步分块写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeBytesInChunksSuspend(
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            safeCallback { onResult(writeBytesInChunks(path, data, chunkSize, overwrite, onProgress, onFailure = onFailure)) }
        }
    }

    // ==================== 内部方法 ====================

    private fun copyStream(failureReporter: FileFailureCallbacks.Reporter,
        input: InputStream,
        output: OutputStream,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalWritten = 0L
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalWritten += bytesRead
            safeProgress(failureReporter, onProgress, totalWritten, -1L)
        }
        output.flush()
    }

    private inline fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        overwrite: Boolean = true,
        writer: (File) -> Unit
    ): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "FileWriteUtils.writeToFileAtomically 失败：输入路径或参数为空")

        val destination = File(path).absoluteFile
        if (destination.exists()) {
            if (!overwrite) return failureReporter.fail(false, "FileWriteUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
            if (destination.isDirectory) return failureReporter.fail(false, "FileWriteUtils.writeToFileAtomically 失败：目标路径是目录")
        }
        if (!ensureParentDirectory(failureReporter, destination)) return failureReporter.fail(false, "FileWriteUtils.writeToFileAtomically 失败：无法创建目标父目录")

        var tempFile: File? = null
        return try {
            val parent = destination.parentFile ?: return failureReporter.fail(false, "FileWriteUtils.writeToFileAtomically 失败：无法获取目标文件的父目录")
            val prefix = (destination.name.ifBlank { "file" }.take(32) + "_").padEnd(3, '_')
            tempFile = File.createTempFile(prefix, ".tmp", parent)
            writer(tempFile)
            replaceAtomically(failureReporter, tempFile, destination)
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } finally {
            tempFile?.takeIf { it.exists() }?.delete()
        }
    }

    private fun ensureParentDirectory(failureReporter: FileFailureCallbacks.Reporter, file: File): Boolean {
        return try {
            val parent = file.absoluteFile.parentFile ?: return failureReporter.fail(false, "FileWriteUtils.ensureParentDirectory 失败：无法获取目标文件的父目录")
            parent.isDirectory || parent.mkdirs() || parent.isDirectory
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
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

    private fun safeProgress(failureReporter: FileFailureCallbacks.Reporter,
        callback: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)?,
        bytesWritten: Long,
        estimatedTotal: Long
    ) {
        if (callback == null) return
        safeCallback(failureReporter) { callback(bytesWritten, estimatedTotal) }
    }

    private fun replaceAtomically(failureReporter: FileFailureCallbacks.Reporter, tempFile: File, destination: File): Boolean {
        return try {
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            Os.rename(tempFile.absolutePath, destination.absolutePath)
            true
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** writeText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeText(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeText", "$path", onFailure, { !it }, false) { failureReporter ->
            writeTextImpl(failureReporter, path, content, charset)
        }

    /** appendText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun appendText(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.appendText", "$path", onFailure, { !it }, false) { failureReporter ->
            appendTextImpl(failureReporter, path, content, charset)
        }

    /** writeBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeBytes(
        path: String,
        bytes: ByteArray,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeBytes", "$path", onFailure, { !it }, false) { failureReporter ->
            writeBytesImpl(failureReporter, path, bytes)
        }

    /** appendBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun appendBytes(
        path: String,
        bytes: ByteArray,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.appendBytes", "$path", onFailure, { !it }, false) { failureReporter ->
            appendBytesImpl(failureReporter, path, bytes)
        }

    /** writeFromStream 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeFromStream(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeFromStream", "$path", onFailure, { !it }, false) { failureReporter ->
            writeFromStreamImpl(failureReporter, path, inputStream, overwrite, onProgress)
        }

    /** writeToUri 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeToUri(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        mimeType: String? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeToUri", "$context", onFailure, { !it }, false) { failureReporter ->
            writeToUriImpl(failureReporter, context, uri, bytes, mimeType)
        }

    /** writeStreamToUri 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeStreamToUri(
        context: Context,
        uri: Uri,
        inputStream: InputStream,
        mimeType: String? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeStreamToUri", "$inputStream", onFailure, { !it }, false) { failureReporter ->
            writeStreamToUriImpl(failureReporter, context, uri, inputStream, mimeType)
        }

    /** writeTextAtomic 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeTextAtomic(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeTextAtomic", "$path", onFailure, { !it }, false) { failureReporter ->
            writeTextAtomicImpl(failureReporter, path, content, charset)
        }

    /** writeBytesAtomic 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeBytesAtomic(
        path: String,
        bytes: ByteArray,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeBytesAtomic", "$path", onFailure, { !it }, false) { failureReporter ->
            writeBytesAtomicImpl(failureReporter, path, bytes)
        }

    /** writeBytesInChunks 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeBytesInChunks(
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileWriteUtils.writeBytesInChunks", "$path", onFailure, { !it }, false) { failureReporter ->
            writeBytesInChunksImpl(failureReporter, path, data, chunkSize, overwrite, onProgress)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeTextSuspendLegacyCallback")
    suspend fun writeTextSuspend(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit
    ) = writeTextSuspend(path = path, content = content, charset = charset, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("appendTextSuspendLegacyCallback")
    suspend fun appendTextSuspend(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit
    ) = appendTextSuspend(path = path, content = content, charset = charset, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeBytesSuspendLegacyCallback")
    suspend fun writeBytesSuspend(
        path: String,
        bytes: ByteArray,
        onResult: (Boolean) -> Unit
    ) = writeBytesSuspend(path = path, bytes = bytes, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeFromStreamSuspendLegacyCallback")
    suspend fun writeFromStreamSuspend(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = writeFromStreamSuspend(path = path, inputStream = inputStream, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeTextAtomicSuspendLegacyCallback")
    suspend fun writeTextAtomicSuspend(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit
    ) = writeTextAtomicSuspend(path = path, content = content, charset = charset, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeBytesInChunksSuspendLegacyCallback")
    suspend fun writeBytesInChunksSuspend(
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = writeBytesInChunksSuspend(path = path, data = data, chunkSize = chunkSize, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeFromStreamLegacyCallback")
    fun writeFromStream(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null
    ) = writeFromStream(path = path, inputStream = inputStream, overwrite = overwrite, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeBytesInChunksLegacyCallback")
    fun writeBytesInChunks(
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null
    ) = writeBytesInChunks(path = path, data = data, chunkSize = chunkSize, overwrite = overwrite, onProgress = onProgress, onFailure = null)
}
