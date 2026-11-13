package com.itg.itg_file.write
import com.itg.itg_file.core.FileFailureCallbacks

import android.system.ErrnoException
import android.system.Os
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import okio.Buffer
import okio.ByteString
import okio.ForwardingSink
import okio.GzipSink
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Okio 文件写入工具类
 *
 * 基于 Okio [okio.BufferedSink] 实现高效文件写入，相比传统 java.io：
 * - [Buffer]: 零拷贝字节缓冲区，分段写入后一次性 flush
 * - 内置超时控制 (sink.timeout)
 * - 进度追踪 (ForwardingSink)
 * - Gzip 压缩 (Okio.gzip)
 * - 原子写入（写临时文件 → 重命名）
 *
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioWriteUtils {

    // ==================== 写入 ByteString ====================

    /**
     * 写入 [ByteString] 到文件
     *
     * ByteString 可来自 [com.itg.itg_file.read.OkioReadUtils.readByteString] 或手动构建。
     *
     * @param path      文件路径
     * @param byteString 不可变字节序列
     * @return true 表示写入成功
     *
     * 使用示例:
     * ```kotlin
     * val byteStr = ByteString.encodeUtf8("Hello Okio")
     * OkioWriteUtils.writeByteString("/sdcard/hello.txt", byteStr)
     * ```
     */
    private fun writeByteStringImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, byteString: ByteString): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeByteString 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path) { file ->
            file.sink().buffer().use { buffered ->
                buffered.write(byteString)
            }
        }
    }

    /**
     * 异步写入 ByteString
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeByteStringSuspend(
        path: String,
        byteString: ByteString,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeByteString(path, byteString, onFailure = onFailure)) } }
    }

    // ==================== 写入字符串 ====================

    /**
     * 写入字符串到文件（Okio 版本）
     *
     * @param path    文件路径
     * @param content 字符串内容
     * @param charset 字符编码，默认 UTF-8
     * @return true 表示写入成功
     */
    private fun writeUtf8Impl(failureReporter: FileFailureCallbacks.Reporter, path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeUtf8 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path) { file ->
            file.sink().buffer().use { buffered ->
                buffered.writeString(content, charset)
            }
        }
    }

    /**
     * 异步写入字符串
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeUtf8Suspend(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeUtf8(path, content, charset, onFailure = onFailure)) } }
    }

    /**
     * 追加字符串到文件末尾（Okio 版本）
     *
     * @param path    文件路径
     * @param content 追加内容
     * @param charset 编码，默认 UTF-8
     * @return true 表示追加成功
     */
    private fun appendUtf8Impl(failureReporter: FileFailureCallbacks.Reporter, path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.appendUtf8 失败：输入路径或参数为空")
        return try {
            val file = File(path)
            ensureParentDirectory(failureReporter, file) ?: return failureReporter.fail(false, "OkioWriteUtils.appendUtf8 失败：无法创建目标父目录")
            FileOutputStream(file, true).sink().buffer().use { buffered ->
                buffered.writeString(content, charset)
            }
            true
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
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
    suspend fun appendUtf8Suspend(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(appendUtf8(path, content, charset, onFailure = onFailure)) } }
    }

    // ==================== 从 Buffer 写入 ====================

    /**
     * 将 Okio [Buffer] 写入文件
     *
     * @param path   文件路径
     * @param buffer Okio 可变缓冲区
     * @return true 表示写入成功
     */
    private fun writeFromBufferImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, buffer: Buffer): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeFromBuffer 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path) { file ->
            file.sink().buffer().use { buffered ->
                buffered.writeAll(buffer)
            }
        }
    }

    // ==================== 从 InputStream 写入 ====================

    /**
     * 从 InputStream 写入文件（Okio 桥接）
     *
     * @param path        目标文件路径
     * @param inputStream 输入流
     * @param overwrite   是否覆盖
     * @return true 表示写入成功
     */
    private fun writeFromStreamImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true
    ): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeFromStream 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path, overwrite) { file ->
            inputStream.source().buffer().use { source ->
                file.sink().buffer().use { buffered ->
                    buffered.writeAll(source)
                }
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
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeFromStream(path, inputStream, overwrite, onFailure = onFailure)) } }
    }

    // ==================== 超时控制的写入 ====================

    /**
     * 带超时的文件写入
     *
     * 通过 Okio Timeout 机制在写入阶段设置超时。
     *
     * @param path      文件路径
     * @param bytes     要写入的字节
     * @param timeoutMs 超时毫秒数
     * @return true 表示写入成功
     */
    private fun writeWithTimeoutImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, bytes: ByteArray, timeoutMs: Long): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeWithTimeout 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path) { file ->
            val sink = file.sink()
            sink.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            sink.buffer().use { buffered ->
                buffered.write(bytes)
            }
        }
    }

    /**
     * 异步超时写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeWithTimeoutSuspend(
        path: String,
        bytes: ByteArray,
        timeoutMs: Long,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeWithTimeout(path, bytes, timeoutMs, onFailure = onFailure)) } }
    }

    // ==================== 带进度的写入 ====================

    /**
     * 带进度的文件写入
     *
     * 通过 [ForwardingSink] 拦截写入操作，跟踪进度。
     *
     * @param path       文件路径
     * @param data       要写入的数据
     * @param chunkSize  进度回调最小间隔（字节）
     * @param onProgress 进度回调 (bytesWritten, totalBytes)
     * @return true 表示写入成功
     */
    private fun writeWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeWithProgress 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, path) { file ->
            val totalSize = data.size.toLong()
            var bytesWritten = 0L
            var lastReport = 0L

            val rawSink = file.sink()
            val progressSink = object : ForwardingSink(rawSink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    if (onProgress != null && bytesWritten - lastReport >= chunkSize) {
                        safeCallback(failureReporter) { onProgress(bytesWritten, totalSize) }
                        lastReport = bytesWritten
                    }
                }
            }

            progressSink.buffer().use { buffered ->
                buffered.write(data)
                safeCallback(failureReporter) { onProgress?.invoke(bytesWritten, totalSize) }
            }
        }
    }

    /**
     * 异步带进度写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeWithProgressSuspend(
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            safeCallback { onResult(writeWithProgress(path, data, chunkSize, onProgress, onFailure = onFailure)) }
        }
    }

    // ==================== Gzip 压缩写入 ====================

    /**
     * 将数据 Gzip 压缩后写入文件
     *
     * @param path 文件路径（建议以 .gz 结尾）
     * @param data 原始数据
     * @return true 表示压缩写入成功
     *
     * 使用示例:
     * ```kotlin
     * val json = """{"data": "..."}"""
     * OkioWriteUtils.writeGzip("/sdcard/data.json.gz", json.toByteArray())
     * // 解压读取:
     * val decompressed = OkioReadUtils.readGzip("/sdcard/data.json.gz")
     * ```
     */
    private fun writeGzipImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, data: ByteArray): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeGzip 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path) { file ->
            file.sink().use { sink ->
                GzipSink(sink).buffer().use { gzip ->
                    gzip.write(data)
                }
            }
        }
    }

    /**
     * 异步 Gzip 压缩写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeGzipSuspend(
        path: String,
        data: ByteArray,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeGzip(path, data, onFailure = onFailure)) } }
    }

    /**
     * Gzip 压缩字符串并写入
     */
    private fun writeGzipTextImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        return writeGzipImpl(failureReporter, path, content.toByteArray(charset))
    }

    // ==================== 原子写入 ====================

    /**
     * Okio 原子写入
     *
     * 写入临时文件 → flush+close → 原子重命名为目标文件。
     * 比 java.io 原子写入更可靠，因为 Okio 的 flush 保证数据已落盘。
     *
     * @param path 目标文件路径
     * @param data 要写入的数据
     * @return true 表示写入成功
     */
    private fun writeAtomicImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, data: ByteArray): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeAtomic 失败：输入路径或参数为空")
        return writeToFileAtomically(failureReporter, path) { tempFile ->
            tempFile.sink().buffer().use { buffered ->
                buffered.write(data)
            }
        }
    }

    /**
     * 异步原子写入
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeAtomicSuspend(
        path: String,
        data: ByteArray,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(writeAtomic(path, data, onFailure = onFailure)) } }
    }

    /**
     * Okio 原子写入字符串
     */
    private fun writeAtomicUtf8Impl(failureReporter: FileFailureCallbacks.Reporter, path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        return writeAtomicImpl(failureReporter, path, content.toByteArray(charset))
    }

    private fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        overwrite: Boolean = true,
        writer: (File) -> Unit
    ): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioWriteUtils.writeToFileAtomically 失败：输入路径或参数为空")
        var tempFile: File? = null
        return try {
            val destFile = File(path).canonicalFile
            if (destFile.exists()) {
                if (!overwrite) return failureReporter.fail(false, "OkioWriteUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
                if (destFile.isDirectory) return failureReporter.fail(false, "OkioWriteUtils.writeToFileAtomically 失败：目标路径是目录")
            }
            val parent = ensureParentDirectory(failureReporter, destFile) ?: return failureReporter.fail(false, "OkioWriteUtils.writeToFileAtomically 失败：无法创建目标父目录")
            tempFile = File.createTempFile(".${destFile.name}.", ".tmp", parent)
            writer(tempFile)
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "OkioWriteUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
            Os.rename(tempFile.absolutePath, destFile.absolutePath)
            tempFile = null
            true
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

    private fun ensureParentDirectory(failureReporter: FileFailureCallbacks.Reporter, file: File): File? {
        val parent = file.parentFile
            ?: return failureReporter.fail(null, "无法获取目标文件的父目录：${file.path}")
        if (parent.exists()) {
            return if (parent.isDirectory) parent
            else failureReporter.fail(null, "父路径不是目录：${parent.path}")
        }
        return if (parent.mkdirs() || parent.isDirectory) parent
        else failureReporter.fail(null, "无法创建父目录：${parent.path}")
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
        }
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** writeByteString 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeByteString(
        path: String,
        byteString: ByteString,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeByteString", "$path", onFailure, { !it }, false) { failureReporter ->
            writeByteStringImpl(failureReporter, path, byteString)
        }

    /** writeUtf8 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeUtf8(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeUtf8", "$path", onFailure, { !it }, false) { failureReporter ->
            writeUtf8Impl(failureReporter, path, content, charset)
        }

    /** appendUtf8 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun appendUtf8(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.appendUtf8", "$path", onFailure, { !it }, false) { failureReporter ->
            appendUtf8Impl(failureReporter, path, content, charset)
        }

    /** writeFromBuffer 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeFromBuffer(
        path: String,
        buffer: Buffer,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeFromBuffer", "$path", onFailure, { !it }, false) { failureReporter ->
            writeFromBufferImpl(failureReporter, path, buffer)
        }

    /** writeFromStream 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeFromStream(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeFromStream", "$path", onFailure, { !it }, false) { failureReporter ->
            writeFromStreamImpl(failureReporter, path, inputStream, overwrite)
        }

    /** writeWithTimeout 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeWithTimeout(
        path: String,
        bytes: ByteArray,
        timeoutMs: Long,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeWithTimeout", "$path", onFailure, { !it }, false) { failureReporter ->
            writeWithTimeoutImpl(failureReporter, path, bytes, timeoutMs)
        }

    /** writeWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeWithProgress(
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeWithProgress", "$path", onFailure, { !it }, false) { failureReporter ->
            writeWithProgressImpl(failureReporter, path, data, chunkSize, onProgress)
        }

    /** writeGzip 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeGzip(
        path: String,
        data: ByteArray,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeGzip", "$path", onFailure, { !it }, false) { failureReporter ->
            writeGzipImpl(failureReporter, path, data)
        }

    /** writeGzipText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeGzipText(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeGzipText", "$path", onFailure, { !it }, false) { failureReporter ->
            writeGzipTextImpl(failureReporter, path, content, charset)
        }

    /** writeAtomic 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeAtomic(
        path: String,
        data: ByteArray,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeAtomic", "$path", onFailure, { !it }, false) { failureReporter ->
            writeAtomicImpl(failureReporter, path, data)
        }

    /** writeAtomicUtf8 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun writeAtomicUtf8(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioWriteUtils.writeAtomicUtf8", "$path", onFailure, { !it }, false) { failureReporter ->
            writeAtomicUtf8Impl(failureReporter, path, content, charset)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeByteStringSuspendLegacyCallback")
    suspend fun writeByteStringSuspend(
        path: String,
        byteString: ByteString,
        onResult: (Boolean) -> Unit
    ) = writeByteStringSuspend(path = path, byteString = byteString, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeUtf8SuspendLegacyCallback")
    suspend fun writeUtf8Suspend(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (Boolean) -> Unit
    ) = writeUtf8Suspend(path = path, content = content, charset = charset, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("appendUtf8SuspendLegacyCallback")
    suspend fun appendUtf8Suspend(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (Boolean) -> Unit
    ) = appendUtf8Suspend(path = path, content = content, charset = charset, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeFromStreamSuspendLegacyCallback")
    suspend fun writeFromStreamSuspend(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = writeFromStreamSuspend(path = path, inputStream = inputStream, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeWithTimeoutSuspendLegacyCallback")
    suspend fun writeWithTimeoutSuspend(
        path: String,
        bytes: ByteArray,
        timeoutMs: Long,
        onResult: (Boolean) -> Unit
    ) = writeWithTimeoutSuspend(path = path, bytes = bytes, timeoutMs = timeoutMs, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeWithProgressSuspendLegacyCallback")
    suspend fun writeWithProgressSuspend(
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = writeWithProgressSuspend(path = path, data = data, chunkSize = chunkSize, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeGzipSuspendLegacyCallback")
    suspend fun writeGzipSuspend(
        path: String,
        data: ByteArray,
        onResult: (Boolean) -> Unit
    ) = writeGzipSuspend(path = path, data = data, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeAtomicSuspendLegacyCallback")
    suspend fun writeAtomicSuspend(
        path: String,
        data: ByteArray,
        onResult: (Boolean) -> Unit
    ) = writeAtomicSuspend(path = path, data = data, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("writeWithProgressLegacyCallback")
    fun writeWithProgress(
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ) = writeWithProgress(path = path, data = data, chunkSize = chunkSize, onProgress = onProgress, onFailure = null)
}
