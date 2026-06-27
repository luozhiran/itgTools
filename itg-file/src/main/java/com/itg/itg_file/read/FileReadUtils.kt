package com.itg.itg_file.read

import android.net.Uri
import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.Future

/**
 * 文件读取工具类
 *
 * 提供多种文件读取方式，涵盖文本、字节、按行读取、大文件分块读取等场景。
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
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
    @JvmStatic
    @JvmOverloads
    fun readText(path: String, charset: Charset = StandardCharsets.UTF_8): String? {
        if (!FileUtils.isFile(path)) return null
        return try {
            File(path).readText(charset)
        } catch (e: IOException) {
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
     * @return [Future]
     *
     * 使用示例:
     * ```kotlin
     * FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
     *     if (error != null) {
     *         Log.e("File", "Read failed", error)
     *     } else {
     *         taskExecutor.main { parseAndDisplay(content!!) }
     *     }
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readTextAsync(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readText(path, charset)
                onResult(result, if (result == null && FileUtils.exists(path)) {
                    IOException("Failed to read file: $path")
                } else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    fun readBytes(path: String): ByteArray? {
        if (!FileUtils.isFile(path)) return null
        return try {
            File(path).readBytes()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取文件为字节数组
     */
    @JvmStatic
    fun readBytesAsync(
        path: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readBytes(path)
                onResult(result, if (result == null && FileUtils.exists(path)) {
                    IOException("Failed to read file: $path")
                } else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    @JvmOverloads
    fun readLines(path: String, charset: Charset = StandardCharsets.UTF_8): List<String>? {
        if (!FileUtils.isFile(path)) return null
        return try {
            File(path).readLines(charset)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取
     */
    @JvmStatic
    @JvmOverloads
    fun readLinesAsync(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = readLines(path, charset)
                onResult(result, if (result == null) IOException("Failed to read: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    @JvmOverloads
    fun readLinesStreaming(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!FileUtils.isFile(path)) return -1
        return try {
            var count = 0
            File(path).bufferedReader(charset).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (!onEachLine(line, count)) break
                    count++
                    line = reader.readLine()
                }
            }
            count
        } catch (e: IOException) {
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
     * @return [Future]
     */
    @JvmStatic
    @JvmOverloads
    fun readLinesStreamingAsync(
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean,
        onComplete: (totalLines: Int, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val count = readLinesStreaming(path, charset, onEachLine)
                onComplete(count, if (count < 0) IOException("Failed to read: $path") else null)
            } catch (e: Exception) {
                onComplete(-1, e)
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
    @JvmStatic
    @JvmOverloads
    fun readBytes(context: android.content.Context, uri: Uri, maxBytes: Int = 10 * 1024 * 1024): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytesWithLimit(maxBytes)
            }
        } catch (e: Exception) {
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
    @JvmStatic
    @JvmOverloads
    fun readBytes(inputStream: InputStream, maxBytes: Int = 0): ByteArray? {
        return try {
            if (maxBytes <= 0) {
                inputStream.readBytes()
            } else {
                inputStream.readBytesWithLimit(maxBytes)
            }
        } catch (e: Exception) {
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
    @JvmStatic
    @JvmOverloads
    fun readText(
        inputStream: InputStream,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = 0
    ): String? {
        val bytes = readBytes(inputStream, maxBytes) ?: return null
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
     * FileReadUtils.readChunks("/sdcard/large.bin", chunkSize = 1024 * 1024) { chunk, index, total ->
     *     uploadChunk(chunk, index)
     *     val progress = (index + 1) * 100 / total
     *     updateProgress(progress)
     *     true  // 继续
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readChunks(
        path: String,
        chunkSize: Int = 64 * 1024,
        onChunk: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean
    ): Long {
        if (!FileUtils.isFile(path)) return -1L

        return try {
            val file = File(path)
            val fileSize = file.length()
            val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
            var totalRead = 0L

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(chunkSize)
                var chunkIndex = 0
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                    totalRead += bytesRead
                    if (!onChunk(chunk, chunkIndex, totalChunks)) break
                    chunkIndex++
                }
            }
            totalRead
        } catch (e: IOException) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 异步分块读取
     */
    @JvmStatic
    @JvmOverloads
    fun readChunksAsync(
        path: String,
        chunkSize: Int = 64 * 1024,
        onChunk: (chunk: ByteArray, chunkIndex: Int, totalChunks: Int) -> Boolean,
        onComplete: (totalBytes: Long, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val total = readChunks(path, chunkSize, onChunk)
                onComplete(total, if (total < 0) IOException("Failed to read: $path") else null)
            } catch (e: Exception) {
                onComplete(-1L, e)
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
    @JvmStatic
    fun readHeadBytes(path: String, numBytes: Int): ByteArray? {
        if (!FileUtils.isFile(path) || numBytes <= 0) return null
        return try {
            RandomAccessFile(path, "r").use { raf ->
                val bytes = ByteArray(minOf(numBytes.toLong(), raf.length()).toInt())
                raf.read(bytes)
                bytes
            }
        } catch (e: IOException) {
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
    @JvmStatic
    fun readTailBytes(path: String, numBytes: Int): ByteArray? {
        if (!FileUtils.isFile(path) || numBytes <= 0) return null
        return try {
            val file = File(path)
            val fileSize = file.length()
            val readSize = minOf(numBytes.toLong(), fileSize).toInt()
            val bytes = ByteArray(readSize)
            RandomAccessFile(path, "r").use { raf ->
                raf.seek(fileSize - readSize)
                raf.read(bytes)
            }
            bytes
        } catch (e: IOException) {
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
    @JvmStatic
    @JvmOverloads
    fun readTailLines(path: String, numLines: Int = 10): List<String>? {
        if (!FileUtils.isFile(path)) return null
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
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 内部方法 ====================

    private fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0
        var bytesRead: Int
        while (read(buffer).also { bytesRead = it } != -1) {
            val remaining = maxBytes - totalRead
            if (remaining <= 0) break
            val toWrite = minOf(bytesRead, remaining)
            output.write(buffer, 0, toWrite)
            totalRead += toWrite
        }
        return output.toByteArray()
    }
}
