package com.itg.itg_file.hash
import com.itg.itg_file.core.FileFailureCallbacks

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ForwardingSink
import okio.ForwardingSource
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Okio 流式哈希工具类
 *
 * 基于 Okio [okio.HashingSource] / [okio.HashingSink] 实现边读边写边计算哈希。
 * 与 [com.itg.itg_file.hash.FileHashUtils] 的区别在于:
 * - 流式计算: 在读取/写入过程中同时计算哈希，无需额外遍历
 * - 进度+哈希: 一次 I/O 同时获得进度和哈希值
 * - 组合操作: copyAndHash / compressAndHash 等组合操作
 *
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioHashUtils {

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
    private fun hashWhileReadingImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        digest: MessageDigest,
        onRead: ((buffer: Buffer, bytesRead: Long) -> Unit)? = null
    ): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioHashUtils.hashWhileReading 失败：源文件不存在或不是普通文件")

        return try {
            val source = File(path).source()
            val hashingSource = DigestingSource(source, freshDigest(digest))

            hashingSource.use { src ->
                var bytesRead = 0L
                val buffer = Buffer()
                while (true) {
                    val read = src.read(buffer, BUFFER_SIZE)
                    if (read == -1L) break
                    bytesRead += read
                    safeCallback(failureReporter) { onRead?.invoke(buffer.clone(), bytesRead) }
                    buffer.clear()
                }
            }
            hashingSource.hash().toHexString()
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步流式哈希
     */
    @JvmStatic
    suspend fun hashWhileReadingSuspend(
        path: String,
        digest: MessageDigest,
        onRead: ((buffer: Buffer, bytesRead: Long) -> Unit)? = null,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val hash = hashWhileReading(path, digest, onRead, onFailure = { failure = it })
                safeCallback { onResult(hash, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
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
    private fun hashWhileWritingImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        data: ByteArray,
        digest: MessageDigest
    ): Pair<String?, Boolean>? {
        if (path.isBlank()) return failureReporter.fail(null, "OkioHashUtils.hashWhileWriting 失败：输入路径或参数为空")

        var hash: String? = null
        val success = writeToFileAtomically(failureReporter, path) { file ->
            val hashingSink = DigestingSink(file.sink(), freshDigest(digest))
            hashingSink.buffer().use { buffered ->
                buffered.write(data)
            }
            hash = hashingSink.hash().toHexString()
        }
        return if (success) Pair(hash, true) else null
    }

    /**
     * 异步写入时哈希
     */
    @JvmStatic
    @JvmOverloads
    suspend fun hashWhileWritingSuspend(
        path: String,
        data: ByteArray,
        digest: MessageDigest,
        onResult: (String?, Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            try {
                val result = hashWhileWriting(path, data, digest, onFailure)
                if (result != null) {
                    safeCallback { onResult(result.first, result.second) }
                } else {
                    safeCallback { onResult(null, false) }
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, false) }
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
    private fun copyAndHashImpl(failureReporter: FileFailureCallbacks.Reporter,
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Pair<String?, Boolean>? {
        if (!FileUtils.isFile(srcPath)) return failureReporter.fail(null, "OkioHashUtils.copyAndHash 失败：源文件不存在或不是普通文件")

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return Pair(null, false)
            val totalSize = srcFile.length()

            if (destFile.exists() && !overwrite) return Pair(null, false)

            var bytesCopied = 0L
            var lastReport = 0L
            var hash: String? = null

            val success = writeToFileAtomically(failureReporter, destPath, overwrite) { tempFile ->
                val rawSource = srcFile.source()
                val hashingSource = DigestingSource(rawSource, freshDigest(digest))
                val progressSource = object : ForwardingSource(hashingSource) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = super.read(sink, byteCount)
                        if (read != -1L) {
                            bytesCopied += read
                            if (onProgress != null && bytesCopied - lastReport >= BUFFER_SIZE) {
                                safeCallback(failureReporter) { onProgress(bytesCopied, totalSize) }
                                lastReport = bytesCopied
                            }
                        }
                        return read
                    }
                }

                tempFile.sink().buffer().use { sink ->
                    progressSource.buffer().use { source -> sink.writeAll(source) }
                }
                hash = hashingSource.hash().toHexString()
            }

            safeCallback(failureReporter) { onProgress?.invoke(bytesCopied, totalSize) }
            Pair(hash, success)
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            Pair(null, false)
        }
    }

    /**
     * 异步复制并计算哈希
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyAndHashSuspend(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            try {
                val result = copyAndHash(srcPath, destPath, digest, overwrite, onProgress, onFailure)
                if (result != null) {
                    safeCallback { onResult(result.first, result.second) }
                } else {
                    safeCallback { onResult(null, false) }
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, false) }
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
    private fun gzipAndHashImpl(failureReporter: FileFailureCallbacks.Reporter,
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Pair<String?, Boolean>? {
        if (!FileUtils.isFile(srcPath)) return failureReporter.fail(null, "OkioHashUtils.gzipAndHash 失败：源文件不存在或不是普通文件")

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return Pair(null, false)
            val totalSize = srcFile.length()
            var bytesProcessed = 0L
            var lastReport = 0L
            var hash: String? = null

            val success = writeToFileAtomically(failureReporter, destPath) { tempFile ->
                val rawSource = srcFile.source()
                val progressSource = object : ForwardingSource(rawSource) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = super.read(sink, byteCount)
                        if (read != -1L) {
                            bytesProcessed += read
                            if (onProgress != null && bytesProcessed - lastReport >= BUFFER_SIZE) {
                                safeCallback(failureReporter) { onProgress(bytesProcessed, totalSize) }
                                lastReport = bytesProcessed
                            }
                        }
                        return read
                    }
                }

                val gzipSink = okio.GzipSink(tempFile.sink())
                val hashingSink = DigestingSink(gzipSink, freshDigest(digest))

                progressSource.buffer().use { source ->
                    hashingSink.buffer().use { buffered ->
                        buffered.writeAll(source)
                    }
                }
                hash = hashingSink.hash().toHexString()
            }

            safeCallback(failureReporter) { onProgress?.invoke(bytesProcessed, totalSize) }
            Pair(hash, success)
        } catch (e: IOException) {
            failureReporter.rethrow(e)
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
    private fun copyAndVerifyImpl(failureReporter: FileFailureCallbacks.Reporter,
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Triple<String?, Boolean, Boolean>? {
        // 第一步: 复制并计算源文件哈希
        val result = copyAndHashImpl(failureReporter, srcPath, destPath, digest, overwrite, onProgress)
            ?: return failureReporter.fail(Triple(null, false, false), "复制并计算源文件哈希失败：$srcPath")

        val (srcHash, success) = result
        if (!success || srcHash == null) {
            return failureReporter.fail(Triple(null, false, false), "复制并计算源文件哈希未完成：$srcPath")
        }

        // 第二步: 验证目标文件哈希
        val destHash = hashFileImpl(failureReporter, destPath, digest)
        val verified = destHash != null && destHash.equals(srcHash, ignoreCase = true)

        if (!verified) {
            return failureReporter.fail(
                Triple(srcHash, true, false),
                "复制后校验失败：源与目标文件的哈希值不一致：$destPath"
            )
        }
        return Triple(srcHash, true, true)
    }

    // ==================== 便捷哈希方法 ====================

    /**
     * 计算文件哈希（Okio HashingSource 实现）
     *
     * @param path   文件路径
     * @param digest 哈希算法
     * @return 十六进制哈希字符串，失败返回 null
     */
    private fun hashFileImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, digest: MessageDigest): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioHashUtils.hashFile 失败：源文件不存在或不是普通文件")
        return try {
            File(path).source().use { source ->
                val hashingSource = DigestingSource(source, freshDigest(digest))
                hashingSource.buffer().use { buffered ->
                    buffered.drainAll()
                }
                hashingSource.hash().toHexString()
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算哈希
     */
    @JvmStatic
    suspend fun hashFileSuspend(
        path: String,
        digest: MessageDigest,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val hash = hashFile(path, digest, onFailure = { failure = it })
                safeCallback { onResult(hash, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    /**
     * 计算文件哈希（带进度）
     */
    private fun hashFileWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "OkioHashUtils.hashFileWithProgress 失败：源文件不存在或不是普通文件")
        return try {
            val file = File(path)
            val totalSize = file.length()
            var bytesProcessed = 0L
            var lastReport = 0L

            val rawSource = file.source()
            val hashingSource = DigestingSource(rawSource, freshDigest(digest))
            val progressSource = object : ForwardingSource(hashingSource) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesProcessed += read
                        if (onProgress != null && bytesProcessed - lastReport >= BUFFER_SIZE) {
                            safeCallback(failureReporter) { onProgress(bytesProcessed, totalSize) }
                            lastReport = bytesProcessed
                        }
                    }
                    return read
                }
            }

            progressSource.buffer().use { buffered ->
                buffered.drainAll()
            }
            safeCallback(failureReporter) { onProgress?.invoke(bytesProcessed, totalSize) }
            hashingSource.hash().toHexString()
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步带进度哈希
     */
    @JvmStatic
    suspend fun hashFileWithProgressSuspend(
        path: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val hash = hashFileWithProgress(path, digest, onProgress, onFailure = { failure = it })
                safeCallback { onResult(hash, failure) }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 数据哈希 ====================

    /**
     * 计算 ByteString 的哈希
     */
    @JvmStatic
    fun hashByteString(data: ByteString, digest: MessageDigest): String {
        val localDigest = freshDigest(digest)
        localDigest.update(data.asByteBuffer())
        return localDigest.digest().toHexString()
    }

    /**
     * 计算字符串的哈希
     */
    @JvmStatic
    fun hashString(text: String, digest: MessageDigest): String {
        return hashByteString(text.encodeUtf8(), digest)
    }

    // ==================== 内部方法 ====================

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun freshDigest(digest: MessageDigest): MessageDigest =
        MessageDigest.getInstance(digest.algorithm)

    private fun BufferedSource.drainAll() {
        val buffer = Buffer()
        while (read(buffer, BUFFER_SIZE) != -1L) {
            buffer.clear()
        }
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
        }
    }

    private fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        overwrite: Boolean = true,
        writer: (File) -> Unit
    ): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "OkioHashUtils.writeToFileAtomically 失败：输入路径或参数为空")
        var tempFile: File? = null
        return try {
            val destFile = File(path).canonicalFile
            if (destFile.exists()) {
                if (!overwrite) return failureReporter.fail(false, "OkioHashUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
                if (destFile.isDirectory) return failureReporter.fail(false, "OkioHashUtils.writeToFileAtomically 失败：目标路径是目录")
            }
            val parent = ensureParentDirectory(failureReporter, destFile) ?: return failureReporter.fail(false, "OkioHashUtils.writeToFileAtomically 失败：无法创建目标父目录")
            tempFile = File.createTempFile(".${destFile.name}.", ".tmp", parent)
            writer(tempFile)
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "OkioHashUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
            replaceFile(failureReporter, tempFile, destFile)
            tempFile = null
            true
        } catch (e: IOException) {
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

    private fun replaceFile(failureReporter: FileFailureCallbacks.Reporter, tempFile: File, destFile: File) {
        if (tempFile.renameTo(destFile)) return
        if (destFile.exists() && !destFile.delete()) {
            throw IOException("Failed to delete destination: ${destFile.absolutePath}")
        }
        if (!tempFile.renameTo(destFile)) {
            throw IOException("Failed to move temp file to destination: ${destFile.absolutePath}")
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

    private class DigestingSource(
        delegate: okio.Source,
        private val digest: MessageDigest
    ) : ForwardingSource(delegate) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            val offset = sink.size
            val read = super.read(sink, byteCount)
            if (read > 0L) {
                val copy = Buffer()
                sink.copyTo(copy, offset, read)
                digest.update(copy.readByteArray())
            }
            return read
        }

        fun hash(): ByteArray = digest.digest()
    }

    private class DigestingSink(
        delegate: okio.Sink,
        private val digest: MessageDigest
    ) : ForwardingSink(delegate) {
        override fun write(source: Buffer, byteCount: Long) {
            val copy = Buffer()
            source.copyTo(copy, 0L, byteCount)
            digest.update(copy.readByteArray())
            super.write(source, byteCount)
        }

        fun hash(): ByteArray = digest.digest()
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** hashWhileReading 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun hashWhileReading(
        path: String,
        digest: MessageDigest,
        onRead: ((buffer: Buffer, bytesRead: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioHashUtils.hashWhileReading", "$path", onFailure, { it == null }, null) { failureReporter ->
            hashWhileReadingImpl(failureReporter, path, digest, onRead)
        }

    /** hashWhileWriting 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun hashWhileWriting(
        path: String,
        data: ByteArray,
        digest: MessageDigest,
        onFailure: ((Exception) -> Unit)? = null
    ): Pair<String?, Boolean>? =
        FileFailureCallbacks.run("OkioHashUtils.hashWhileWriting", "$path", onFailure, { it == null || !it.second }, null) { failureReporter ->
            hashWhileWritingImpl(failureReporter, path, data, digest)
        }

    /** copyAndHash 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAndHash(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Pair<String?, Boolean>? =
        FileFailureCallbacks.run("OkioHashUtils.copyAndHash", "$srcPath → $destPath", onFailure, { it == null || !it.second }, null) { failureReporter ->
            copyAndHashImpl(failureReporter, srcPath, destPath, digest, overwrite, onProgress)
        }

    /** gzipAndHash 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun gzipAndHash(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Pair<String?, Boolean>? =
        FileFailureCallbacks.run("OkioHashUtils.gzipAndHash", "$srcPath", onFailure, { it == null || !it.second }, null) { failureReporter ->
            gzipAndHashImpl(failureReporter, srcPath, destPath, digest, onProgress)
        }

    /** copyAndVerify 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAndVerify(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Triple<String?, Boolean, Boolean>? =
        FileFailureCallbacks.run("OkioHashUtils.copyAndVerify", "$srcPath → $destPath", onFailure, { it == null || !it.second || !it.third }, null) { failureReporter ->
            copyAndVerifyImpl(failureReporter, srcPath, destPath, digest, overwrite, onProgress)
        }

    /** hashFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun hashFile(
        path: String,
        digest: MessageDigest,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioHashUtils.hashFile", "$path", onFailure, { it == null }, null) { failureReporter ->
            hashFileImpl(failureReporter, path, digest)
        }

    /** hashFileWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun hashFileWithProgress(
        path: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("OkioHashUtils.hashFileWithProgress", "$path", onFailure, { it == null }, null) { failureReporter ->
            hashFileWithProgressImpl(failureReporter, path, digest, onProgress)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("hashWhileWritingSuspendLegacyCallback")
    suspend fun hashWhileWritingSuspend(
        path: String,
        data: ByteArray,
        digest: MessageDigest,
        onResult: (String?, Boolean) -> Unit
    ) = hashWhileWritingSuspend(path = path, data = data, digest = digest, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAndHashSuspendLegacyCallback")
    suspend fun copyAndHashSuspend(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Boolean) -> Unit
    ) = copyAndHashSuspend(srcPath = srcPath, destPath = destPath, digest = digest, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("hashWhileReadingLegacyCallback")
    fun hashWhileReading(
        path: String,
        digest: MessageDigest,
        onRead: ((buffer: Buffer, bytesRead: Long) -> Unit)? = null
    ) = hashWhileReading(path = path, digest = digest, onRead = onRead, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAndHashLegacyCallback")
    fun copyAndHash(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) = copyAndHash(srcPath = srcPath, destPath = destPath, digest = digest, overwrite = overwrite, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("gzipAndHashLegacyCallback")
    fun gzipAndHash(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) = gzipAndHash(srcPath = srcPath, destPath = destPath, digest = digest, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAndVerifyLegacyCallback")
    fun copyAndVerify(
        srcPath: String,
        destPath: String,
        digest: MessageDigest,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) = copyAndVerify(srcPath = srcPath, destPath = destPath, digest = digest, overwrite = overwrite, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("hashFileWithProgressLegacyCallback")
    fun hashFileWithProgress(
        path: String,
        digest: MessageDigest,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) = hashFileWithProgress(path = path, digest = digest, onProgress = onProgress, onFailure = null)
}
