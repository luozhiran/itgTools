package com.itg.itg_file.write

import android.content.Context
import android.net.Uri
import android.system.Os
import com.itg.itg_file.core.FileUtils
import com.itg.concurrent.Concurrent
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.Future

/**
 * 文件写入工具类
 *
 * 提供多种文件写入方式，涵盖文本、字节、追加、流写入、大文件分块写入等场景。
 * 所有同步方法直接阻塞执行；异步方法通过 [Concurrent] 在 I/O 线程池执行。
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
    @JvmStatic
    @JvmOverloads
    fun writeText(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8
    ): Boolean {
        return writeToFileAtomically(path) { tempFile ->
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
     * @return [Future]
     */
    @JvmStatic
    @JvmOverloads
    fun writeTextAsync(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return Concurrent.io { safeCallback { onResult(writeText(path, content, charset)) } }
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
    @JvmStatic
    @JvmOverloads
    fun appendText(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8
    ): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (!ensureParentDirectory(file)) return false
            file.appendText(content, charset)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步追加字符串
     */
    @JvmStatic
    @JvmOverloads
    fun appendTextAsync(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return Concurrent.io { safeCallback { onResult(appendText(path, content, charset)) } }
    }

    // ==================== 写入字节数组 ====================

    /**
     * 写入字节数组到文件（覆盖模式）
     *
     * @param path  文件路径
     * @param bytes 字节数组
     * @return true 表示写入成功
     */
    @JvmStatic
    fun writeBytes(path: String, bytes: ByteArray): Boolean {
        return writeToFileAtomically(path) { tempFile ->
            tempFile.writeBytes(bytes)
        }
    }

    /**
     * 异步写入字节数组
     */
    @JvmStatic
    fun writeBytesAsync(
        path: String,
        bytes: ByteArray,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return Concurrent.io { safeCallback { onResult(writeBytes(path, bytes)) } }
    }

    /**
     * 追加字节数组到文件末尾
     */
    @JvmStatic
    fun appendBytes(path: String, bytes: ByteArray): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (!ensureParentDirectory(file)) return false
            file.appendBytes(bytes)
            true
        } catch (e: Exception) {
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
    @JvmStatic
    @JvmOverloads
    fun writeFromStream(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null
    ): Boolean {
        return writeToFileAtomically(path, overwrite) { tempFile ->
            FileOutputStream(tempFile).use { output ->
                copyStream(inputStream, output, onProgress)
            }
        }
    }

    /**
     * 异步从流写入
     */
    @JvmStatic
    fun writeFromStreamAsync(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return Concurrent.io {
            safeCallback { onResult(writeFromStream(path, inputStream, overwrite, onProgress)) }
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
    @JvmStatic
    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun writeToUri(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        mimeType: String? = null
    ): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            outputStream.use { output ->
                output.write(bytes)
                output.flush()
            }
            true
        } catch (e: Exception) {
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
    @JvmStatic
    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun writeStreamToUri(
        context: Context,
        uri: Uri,
        inputStream: InputStream,
        mimeType: String? = null
    ): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            outputStream.use { output ->
                copyStream(inputStream, output)
            }
            true
        } catch (e: Exception) {
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
    @JvmStatic
    @JvmOverloads
    fun writeTextAtomic(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8
    ): Boolean {
        return writeToFileAtomically(path) { tempFile ->
            tempFile.writeText(content, charset)
        }
    }

    /**
     * 异步原子写入
     */
    @JvmStatic
    @JvmOverloads
    fun writeTextAtomicAsync(
        path: String,
        content: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return Concurrent.io { safeCallback { onResult(writeTextAtomic(path, content, charset)) } }
    }

    /**
     * 原子写入字节数组
     *
     * @param path  目标文件路径
     * @param bytes 字节数据
     * @return true 表示写入成功
     */
    @JvmStatic
    fun writeBytesAtomic(path: String, bytes: ByteArray): Boolean {
        return writeToFileAtomically(path) { tempFile ->
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
    @JvmStatic
    @JvmOverloads
    fun writeBytesInChunks(
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null
    ): Boolean {
        if (chunkSize <= 0) return false

        return writeToFileAtomically(path, overwrite) { tempFile ->
            FileOutputStream(tempFile).use { output ->
                var offset = 0
                while (offset < data.size) {
                    val remaining = data.size - offset
                    val size = minOf(chunkSize, remaining)
                    output.write(data, offset, size)
                    offset += size
                    safeProgress(onProgress, offset.toLong(), data.size.toLong())
                }
                output.flush()
            }
        }
    }

    /**
     * 异步分块写入
     */
    @JvmStatic
    fun writeBytesInChunksAsync(
        path: String,
        data: ByteArray,
        chunkSize: Int = 64 * 1024,
        overwrite: Boolean = true,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return Concurrent.io {
            safeCallback { onResult(writeBytesInChunks(path, data, chunkSize, overwrite, onProgress)) }
        }
    }

    // ==================== 内部方法 ====================

    private fun copyStream(
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
            safeProgress(onProgress, totalWritten, -1L)
        }
        output.flush()
    }

    private inline fun writeToFileAtomically(
        path: String,
        overwrite: Boolean = true,
        writer: (File) -> Unit
    ): Boolean {
        if (path.isBlank()) return false

        val destination = File(path).absoluteFile
        if (destination.exists()) {
            if (!overwrite || destination.isDirectory) return false
        }
        if (!ensureParentDirectory(destination)) return false

        var tempFile: File? = null
        return try {
            val parent = destination.parentFile ?: return false
            val prefix = (destination.name.ifBlank { "file" }.take(32) + "_").padEnd(3, '_')
            tempFile = File.createTempFile(prefix, ".tmp", parent)
            writer(tempFile)
            replaceAtomically(tempFile, destination)
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        } finally {
            tempFile?.takeIf { it.exists() }?.delete()
        }
    }

    private fun ensureParentDirectory(file: File): Boolean {
        return try {
            val parent = file.absoluteFile.parentFile ?: return false
            parent.isDirectory || parent.mkdirs() || parent.isDirectory
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    private inline fun safeCallback(callback: () -> Unit) {
        try {
            callback()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun safeProgress(
        callback: ((bytesWritten: Long, estimatedTotal: Long) -> Unit)?,
        bytesWritten: Long,
        estimatedTotal: Long
    ) {
        if (callback == null) return
        safeCallback { callback(bytesWritten, estimatedTotal) }
    }

    private fun replaceAtomically(tempFile: File, destination: File): Boolean {
        return try {
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            Os.rename(tempFile.absolutePath, destination.absolutePath)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
