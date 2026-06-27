package com.itg.itg_file.write

import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.FileSystem
import okio.ForwardingSink
import okio.IOException
import okio.Okio
import okio.Path.Companion.toPath
import okio.Sink
import okio.Timeout
import okio.buffer
import okio.use
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Okio 文件写入工具类
 *
 * 基于 Okio [BufferedSink] 实现高效文件写入，相比传统 java.io：
 * - [Buffer]: 零拷贝字节缓冲区，分段写入后一次性 flush
 * - 内置超时控制 (sink.timeout)
 * - 进度追踪 (ForwardingSink)
 * - Gzip 压缩 (Okio.gzip)
 * - 原子写入（写临时文件 → 重命名）
 *
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioWriteUtils {

    private val fileSystem: FileSystem = FileSystem.SYSTEM

    // ==================== 写入 ByteString ====================

    /**
     * 写入 [ByteString] 到文件
     *
     * ByteString 可来自 [OkioReadUtils.readByteString] 或手动构建。
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
    @JvmStatic
    fun writeByteString(path: String, byteString: ByteString): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            fileSystem.sink(path.toPath()).use { sink ->
                sink.buffer().use { buffered ->
                    buffered.write(byteString)
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步写入 ByteString
     */
    @JvmStatic
    fun writeByteStringAsync(
        path: String,
        byteString: ByteString,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(writeByteString(path, byteString)) }
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
    @JvmStatic
    @JvmOverloads
    fun writeUtf8(path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            fileSystem.write(path.toPath()) {
                writeString(content, charset)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步写入字符串
     */
    @JvmStatic
    @JvmOverloads
    fun writeUtf8Async(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(writeUtf8(path, content, charset)) }
    }

    /**
     * 追加字符串到文件末尾（Okio 版本）
     *
     * @param path    文件路径
     * @param content 追加内容
     * @param charset 编码，默认 UTF-8
     * @return true 表示追加成功
     */
    @JvmStatic
    @JvmOverloads
    fun appendUtf8(path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            fileSystem.appendingSink(path.toPath()).use { sink ->
                sink.buffer().use { buffered ->
                    buffered.writeString(content, charset)
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步追加字符串
     */
    @JvmStatic
    fun appendUtf8Async(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(appendUtf8(path, content, charset)) }
    }

    // ==================== 从 Buffer 写入 ====================

    /**
     * 将 Okio [Buffer] 写入文件
     *
     * @param path   文件路径
     * @param buffer Okio 可变缓冲区
     * @return true 表示写入成功
     */
    @JvmStatic
    fun writeFromBuffer(path: String, buffer: Buffer): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            fileSystem.write(path.toPath()) {
                writeAll(buffer)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
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
    @JvmStatic
    @JvmOverloads
    fun writeFromStream(
        path: String,
        inputStream: InputStream,
        overwrite: Boolean = true
    ): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (file.exists() && !overwrite) return false
            file.parentFile?.mkdirs()

            val source = Okio.source(inputStream).buffer()
            fileSystem.write(path.toPath()) { sink ->
                sink.writeAll(source)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
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
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(writeFromStream(path, inputStream, overwrite)) }
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
    @JvmStatic
    fun writeWithTimeout(path: String, bytes: ByteArray, timeoutMs: Long): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            fileSystem.sink(path.toPath()).use { sink ->
                sink.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                sink.buffer().use { buffered ->
                    buffered.write(bytes)
                }
            }
            true
        } catch (e: IOException) {
            if (e is java.io.InterruptedIOException) {
                android.util.Log.w("OkioWriteUtils", "Write timed out after ${timeoutMs}ms: $path")
            }
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步超时写入
     */
    @JvmStatic
    fun writeWithTimeoutAsync(
        path: String,
        bytes: ByteArray,
        timeoutMs: Long,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(writeWithTimeout(path, bytes, timeoutMs)) }
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
    @JvmStatic
    @JvmOverloads
    fun writeWithProgress(
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (path.isBlank()) return false

        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            val totalSize = data.size.toLong()
            var bytesWritten = 0L
            var lastReport = 0L

            val rawSink = fileSystem.sink(path.toPath())
            val progressSink = object : ForwardingSink(rawSink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    if (onProgress != null && bytesWritten - lastReport >= chunkSize) {
                        onProgress(bytesWritten, totalSize)
                        lastReport = bytesWritten
                    }
                }
            }

            progressSink.buffer().use { buffered ->
                buffered.write(data)
                onProgress?.invoke(bytesWritten, totalSize)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步带进度写入
     */
    @JvmStatic
    fun writeWithProgressAsync(
        path: String,
        data: ByteArray,
        chunkSize: Long = 8192L,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(writeWithProgress(path, data, chunkSize, onProgress))
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
    @JvmStatic
    fun writeGzip(path: String, data: ByteArray): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            fileSystem.sink(path.toPath()).use { sink ->
                okio.GzipSink(sink).buffer().use { gzip ->
                    gzip.write(data)
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步 Gzip 压缩写入
     */
    @JvmStatic
    fun writeGzipAsync(
        path: String,
        data: ByteArray,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(writeGzip(path, data)) }
    }

    /**
     * Gzip 压缩字符串并写入
     */
    @JvmStatic
    @JvmOverloads
    fun writeGzipText(path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        return writeGzip(path, content.toByteArray(charset))
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
    @JvmStatic
    fun writeAtomic(path: String, data: ByteArray): Boolean {
        if (path.isBlank()) return false
        return try {
            val destFile = File(path)
            destFile.parentFile?.mkdirs()
            val tmpFile = File.createTempFile(
                destFile.nameWithoutExtension + "_",
                ".tmp",
                destFile.parentFile
            )

            // 写入临时文件
            fileSystem.write(tmpFile.absolutePath.toPath()) {
                write(data)
            }

            // 原子移动
            fileSystem.atomicMove(tmpFile.absolutePath.toPath(), path.toPath())
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步原子写入
     */
    @JvmStatic
    fun writeAtomicAsync(
        path: String,
        data: ByteArray,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(writeAtomic(path, data)) }
    }

    /**
     * Okio 原子写入字符串
     */
    @JvmStatic
    @JvmOverloads
    fun writeAtomicUtf8(path: String, content: String, charset: Charset = Charsets.UTF_8): Boolean {
        return writeAtomic(path, content.toByteArray(charset))
    }
}
