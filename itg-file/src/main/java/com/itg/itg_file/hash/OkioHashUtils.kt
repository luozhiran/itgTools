package com.itg.itg_file.hash

import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import okio.Buffer
import okio.ByteString
import okio.FileSystem
import okio.ForwardingSource
import okio.HashingSink
import okio.HashingSource
import okio.IOException
import okio.Okio
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Future

/**
 * Okio 流式哈希工具类
 *
 * 基于 Okio [HashingSource] / [HashingSink] 实现边读边写边计算哈希。
 * 与 [com.itg.itg_file.hash.FileHashUtils] 的区别在于:
 * - 流式计算: 在读取/写入过程中同时计算哈希，无需额外遍历
 * - 进度+哈希: 一次 I/O 同时获得进度和哈希值
 * - 组合操作: copyAndHash / compressAndHash 等组合操作
 *
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioHashUtils {

    private val fileSystem: FileSystem = FileSystem.SYSTEM
    private const val BUFFER_SIZE = 8192L

    // ==================== 哈希计算（流式） ====================

    /**
     * 使用 Okio HashingSource 计算文件哈希
     *
     * 在读取文件的同时计算哈希，适用于需要同时读取内容和计算哈希的场景。
     *
     * @param path      文件路径
     * @param digest    哈希算法，如 MessageDigest.getInstance("SHA-256")
     * @param onRead    读取数据回调 (buffer: Buffer, bytesRead: Long)
     * @return 十六进制哈希字符串，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // SHA-256 + 复制到 buffer
     * val buffer = Buffer()
     * val hash = OkioHashUtils.hashWhileReading("/sdcard/file.bin",
     *     MessageDigest.getInstance("SHA-256"),
     *     onRead = { buf, _ -> buffer.writeAll(buf) })
     * println("SHA256: $hash")
     * // buffer 中已有完整数据
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun hashWhileReading(
        path: String,
        digest: MessageDigest,
        onRead: ((buffer: Buffer, bytesRead: Long) -> Unit)? = null
    ): String? {
        if (!FileUtils.isFile(path)) return null

        return try {
            val source = fileSystem.source(path.toPath())
            val hashingSource = HashingSource(source, digest)

            hashingSource.buffer().use { buffered ->
                var bytesRead = 0L
                while (!buffered.exhausted()) {
                    val before = buffered.buffer.size
                    buffered.skip(BUFFER_SIZE)
                    val read = before - buffered.buffer.size
                    bytesRead += read
                    onRead?.invoke(buffered.buffer, bytesRead)
                }
            }
            hashingSource.hash.toHexString()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步流式哈希
     */
    @JvmStatic
    fun hashWhileReadingAsync(
        path: String,
        digest: MessageDigest,
        onRead: ((buffer: Buffer, bytesRead: Long) -> Unit)? = null,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val hash = hashWhileReading(path, digest, onRead)
                onResult(hash, if (hash == null) IOException("Hash failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
            }
        }
    }

    // ==================== 写入时哈希（流式） ====================

    /**
     * 在写入数据的同时计算哈希
     *
     * 适用于需要边写边算哈希的场景（如边下载边校验）。
     *
     * @param path   目标文件路径
     * @param data   要写入的数据
     * @param digest 哈希算法
     * @return Pair(hash: String, written: Boolean)，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val (hash, ok) = OkioHashUtils.hashWhileWriting("/sdcard/data.bin",
     *     imageBytes, MessageDigest.getInstance("MD5"))
     * if (ok) println("MD5: $hash")
     * ```
     */
    @JvmStatic
    fun hashWhileWriting(
        path: String,
        data: ByteArray,
        digest: MessageDigest
    ): Pair<String?, Boolean>? {
        if (path.isBlank()) return null

        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            val rawSink = fileSystem.sink(path.toPath())
            val hashingSink = HashingSink(rawSink, digest)

            hashingSink.buffer().use { buffered ->
                buffered.write(data)
            }

            val hash = hashingSink.hash.toHexString()
            Pair(hash, true)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步写入时哈希
     */
    @JvmStatic
    fun hashWhileWritingAsync(
        path: String,
        data: ByteArray,
        digest: MessageDigest,
        onResult: (String?, Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = hashWhileWriting(path, data, digest)
                if (result != null) {
                    onResult(result.first, result.second)
                } else {
                    onResult(null, false)
                }
            } catch (e: Exception) {
                onResult(null, false)
            }
        }
    }

    // ==================== 复制并同时计算哈希 ====================

    /**
     * 边复制边计算哈希（一次 I/O 完成两个操作）
     *
     * 这是 Okio 最独特的优势：复制 + 进度 + 哈希 一步到位。
     *
     * @param srcPath   源文件
     * @param destPath  目标文件
     * @param digest    哈希算法
     * @param overwrite 是否覆盖
     * @param onProgress 进度回调 (bytesCopied, totalBytes)
     * @return Pair(hash: String?, success: Boolean)
     *
     * 使用示例:
     * ```kotlin
     * val (hash, ok) = OkioHashUtils.copyAndHash(
     *     "/sdcard/large.iso", "/sdcard/backup/large.iso",
     *     MessageDigest.getInstance("SHA-256"),
     *     onProgress = { copied, total ->
     *         updateProgressBar((copied * 100 / total).toInt())
     *     })
     * if (ok) println("SHA256: $hash")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copyAndHash(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Pair<String?, Boolean>? {
        if (!FileUtils.isFile(srcPath)) return null

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            val totalSize = srcFile.length()

            if (destFile.exists() && !overwrite) return Pair(null, false)
            destFile.parentFile?.mkdirs()

            var bytesCopied = 0L
            var lastReport = 0L

            // 创建带哈希的 Source
            val rawSource = fileSystem.source(srcPath.toPath())
            val hashingSource = HashingSource(rawSource, digest)

            // 包装进度追踪
            val progressSource = object : ForwardingSource(hashingSource) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesCopied += read
                        if (onProgress != null && bytesCopied - lastReport >= BUFFER_SIZE) {
                            onProgress(bytesCopied, totalSize)
                            lastReport = bytesCopied
                        }
                    }
                    return read
                }
            }

            // 直接写入目标文件
            fileSystem.write(destPath.toPath()) { destSink ->
                destSink.writeAll(progressSource.buffer())
            }

            onProgress?.invoke(bytesCopied, totalSize)
            Pair(hashingSource.hash.toHexString(), true)
        } catch (e: IOException) {
            e.printStackTrace()
            Pair(null, false)
        }
    }

    /**
     * 异步复制并计算哈希
     */
    @JvmStatic
    @JvmOverloads
    fun copyAndHashAsync(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = copyAndHash(srcPath, destPath, digest, overwrite, onProgress)
                if (result != null) {
                    onResult(result.first, result.second)
                } else {
                    onResult(null, false)
                }
            } catch (e: Exception) {
                onResult(null, false)
            }
        }
    }

    // ==================== 压缩并计算哈希 ====================

    /**
     * Gzip 压缩并同时计算哈希
     *
     * @param srcPath   源文件
     * @param destPath  压缩输出路径（.gz）
     * @param digest    哈希算法（对压缩后数据计算）
     * @param onProgress 进度回调
     * @return Pair(hash: String?, success: Boolean)
     */
    @JvmStatic
    @JvmOverloads
    fun gzipAndHash(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Pair<String?, Boolean>? {
        if (!FileUtils.isFile(srcPath)) return null

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            val totalSize = srcFile.length()
            destFile.parentFile?.mkdirs()

            var bytesProcessed = 0L
            var lastReport = 0L

            val rawSource = fileSystem.source(srcPath.toPath())
            val progressSource = object : ForwardingSource(rawSource) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesProcessed += read
                        if (onProgress != null && bytesProcessed - lastReport >= BUFFER_SIZE) {
                            onProgress(bytesProcessed, totalSize)
                            lastReport = bytesProcessed
                        }
                    }
                    return read
                }
            }

            val rawSink = fileSystem.sink(destPath.toPath())
            val gzipSink = okio.GzipSink(rawSink)
            val hashingSink = HashingSink(gzipSink, digest)

            hashingSink.buffer().use { buffered ->
                buffered.writeAll(progressSource.buffer())
            }

            onProgress?.invoke(bytesProcessed, totalSize)
            Pair(hashingSink.hash.toHexString(), true)
        } catch (e: IOException) {
            e.printStackTrace()
            Pair(null, false)
        }
    }

    // ==================== 批量复制 + 哈希校验 ====================

    /**
     * 复制文件并验证哈希（确保复制正确性）
     *
     * 复制完成后，对比源和目标文件的哈希值。
     *
     * @param srcPath   源文件
     * @param destPath  目标文件
     * @param digest    哈希算法
     * @param overwrite 是否覆盖
     * @param onProgress 进度回调
     * @return 三元组 (hash: String?, success: Boolean, verified: Boolean)
     */
    @JvmStatic
    @JvmOverloads
    fun copyAndVerify(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Triple<String?, Boolean, Boolean>? {
        // 第一步: 复制并计算源文件哈希
        val result = copyAndHash(srcPath, destPath, digest, overwrite, onProgress)
            ?: return Triple(null, false, false)

        val (srcHash, success) = result
        if (!success || srcHash == null) {
            return Triple(null, false, false)
        }

        // 第二步: 验证目标文件哈希
        val destHash = hashFile(destPath, digest)
        val verified = destHash != null && destHash.equals(srcHash, ignoreCase = true)

        return Triple(srcHash, true, verified)
    }

    // ==================== 便捷哈希方法 ====================

    /**
     * 计算文件哈希（Okio HashingSource 实现）
     *
     * @param path   文件路径
     * @param digest 哈希算法
     * @return 十六进制哈希字符串，失败返回 null
     */
    @JvmStatic
    fun hashFile(path: String, digest: MessageDigest): String? {
        if (!FileUtils.isFile(path)) return null
        return try {
            fileSystem.source(path.toPath()).use { source ->
                val hashingSource = HashingSource(source, digest)
                hashingSource.buffer().use { buffered ->
                    buffered.skip(Long.MAX_VALUE)  // 读取全部
                }
                hashingSource.hash.toHexString()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算哈希
     */
    @JvmStatic
    fun hashFileAsync(
        path: String,
        digest: MessageDigest,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val hash = hashFile(path, digest)
                onResult(hash, if (hash == null) IOException("Hash failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
            }
        }
    }

    /**
     * 计算文件哈希（带进度）
     */
    @JvmStatic
    @JvmOverloads
    fun hashFileWithProgress(
        path: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): String? {
        if (!FileUtils.isFile(path)) return null
        return try {
            val file = File(path)
            val totalSize = file.length()
            var bytesProcessed = 0L
            var lastReport = 0L

            val rawSource = fileSystem.source(path.toPath())
            val hashingSource = HashingSource(rawSource, digest)
            val progressSource = object : ForwardingSource(hashingSource) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesProcessed += read
                        if (onProgress != null && bytesProcessed - lastReport >= BUFFER_SIZE) {
                            onProgress(bytesProcessed, totalSize)
                            lastReport = bytesProcessed
                        }
                    }
                    return read
                }
            }

            progressSource.buffer().use { buffered ->
                buffered.skip(Long.MAX_VALUE)
            }
            onProgress?.invoke(bytesProcessed, totalSize)
            hashingSource.hash.toHexString()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步带进度哈希
     */
    @JvmStatic
    fun hashFileWithProgressAsync(
        path: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val hash = hashFileWithProgress(path, digest, onProgress)
                onResult(hash, if (hash == null) IOException("Hash failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
            }
        }
    }

    // ==================== 数据哈希 ====================

    /**
     * 计算 ByteString 的哈希
     */
    @JvmStatic
    fun hashByteString(data: ByteString, digest: MessageDigest): String {
        digest.update(data.asByteBuffer())
        return digest.digest().toHexString()
    }

    /**
     * 计算字符串的哈希
     */
    @JvmStatic
    fun hashString(text: String, digest: MessageDigest): String {
        return hashByteString(ByteString.encodeUtf8(text), digest)
    }

    // ==================== 内部方法 ====================

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
