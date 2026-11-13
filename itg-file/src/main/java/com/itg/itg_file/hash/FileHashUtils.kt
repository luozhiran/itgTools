package com.itg.itg_file.hash
import com.itg.itg_file.core.FileFailureCallbacks

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/**
 * 文件哈希/校验工具类
 *
 * 提供多种哈希算法计算文件摘要，用于文件完整性校验和去重识别。
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 *
 * 支持的算法:
 * - MD5（快速，适合去重）
 * - SHA-1（兼容 Git 风格校验）
 * - SHA-256（安全校验，推荐）
 * - SHA-512
 * - CRC32（快速循环冗余，适合传输校验）
 *
 * 核心特性:
 * - 文件完整哈希计算
 * - 字节数组 / 字符串哈希
 * - 大文件分块哈希（带进度回调）
 * - 文件完整性校验 (verify)
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object FileHashUtils {

    private const val BUFFER_SIZE = 8192  // 8KB

    /** 哈希算法枚举 */
    enum class Algorithm(val jceName: String) {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA512("SHA-512");

        companion object {
            @JvmStatic
            fun fromString(name: String): Algorithm? {
                val normalized = name.uppercase().replace("-", "").replace("_", "")
                return entries.find { it.name.uppercase() == normalized }
            }
        }
    }

    // ==================== 文件哈希 ====================

    /**
     * 计算文件的哈希值
     *
     * @param path      文件路径
     * @param algorithm 哈希算法，默认 SHA-256
     * @return 十六进制小写哈希字符串，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val md5 = FileHashUtils.hashFile("/sdcard/photo.jpg", Algorithm.MD5)
     * val sha256 = FileHashUtils.hashFile("/sdcard/photo.jpg", Algorithm.SHA256)
     * println("MD5: $md5")
     * println("SHA256: $sha256")
     * ```
     */
    private fun hashFileImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, algorithm: Algorithm = Algorithm.SHA256): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileHashUtils.hashFile 失败：源文件不存在或不是普通文件")
        return try {
            val digest = MessageDigest.getInstance(algorithm.jceName)
            FileInputStream(File(path)).use { fis ->
                DigestInputStream(fis, digest).use { dis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (dis.read(buffer) != -1) { /* 数据被自动送入 digest */ }
                }
            }
            digest.digest().toHexString()
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算文件哈希
     *
     * @param path      文件路径
     * @param algorithm 哈希算法
     * @param onResult  回调 (hash: String?, error: Throwable?)
     * @return Unit
     *
     * 使用示例:
     * ```kotlin
     * viewModelScope.launch {
     *     val hash = FileCoroutines.withIo { FileHashUtils.hashFile(path, Algorithm.MD5) }
     *     hashState.value = hash
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    suspend fun hashFileSuspend(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val hash = hashFile(path, algorithm, onFailure = { failure = it })
                safeCallback {
                    onResult(hash, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    /**
     * 计算文件哈希（带进度回调，适合大文件）
     *
     * @param path       文件路径
     * @param algorithm  哈希算法
     * @param onProgress 进度回调 (bytesProcessed, totalBytes)
     * @return 哈希字符串，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val sha256 = FileHashUtils.hashFileWithProgress("/sdcard/large.iso", Algorithm.SHA256,
     *     onProgress = { processed, total ->
     *         updateProgressBar((processed * 100 / total).toInt())
     *     })
     * ```
     */
    private fun hashFileWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileHashUtils.hashFileWithProgress 失败：源文件不存在或不是普通文件")

        return try {
            val file = File(path)
            val totalSize = file.length()
            val digest = MessageDigest.getInstance(algorithm.jceName)

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var processed = 0L
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                    processed += bytesRead
                    safeCallback(failureReporter) { onProgress?.invoke(processed, totalSize) }
                }
            }
            digest.digest().toHexString()
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算文件哈希（带进度）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun hashFileWithProgressSuspend(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val hash = hashFileWithProgress(path, algorithm, onProgress, onFailure = { failure = it })
                safeCallback {
                    onResult(hash, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 计算文件 MD5 值
     */
    private fun md5Impl(failureReporter: FileFailureCallbacks.Reporter, path: String): String? = hashFileImpl(failureReporter, path, Algorithm.MD5)

    /**
     * 异步计算 MD5
     */
    @JvmStatic
    suspend fun md5Suspend(path: String, onResult: (String?, Throwable?) -> Unit): Unit =
        hashFileSuspend(path, Algorithm.MD5, onResult)

    /**
     * 计算文件 SHA-1 值
     */
    private fun sha1Impl(failureReporter: FileFailureCallbacks.Reporter, path: String): String? = hashFileImpl(failureReporter, path, Algorithm.SHA1)

    /**
     * 异步计算 SHA-1
     */
    @JvmStatic
    suspend fun sha1Suspend(path: String, onResult: (String?, Throwable?) -> Unit): Unit =
        hashFileSuspend(path, Algorithm.SHA1, onResult)

    /**
     * 计算文件 SHA-256 值
     */
    private fun sha256Impl(failureReporter: FileFailureCallbacks.Reporter, path: String): String? = hashFileImpl(failureReporter, path, Algorithm.SHA256)

    /**
     * 异步计算 SHA-256
     */
    @JvmStatic
    suspend fun sha256Suspend(path: String, onResult: (String?, Throwable?) -> Unit): Unit =
        hashFileSuspend(path, Algorithm.SHA256, onResult)

    /**
     * 计算文件 SHA-512 值
     */
    private fun sha512Impl(failureReporter: FileFailureCallbacks.Reporter, path: String): String? = hashFileImpl(failureReporter, path, Algorithm.SHA512)

    /**
     * 异步计算 SHA-512
     */
    @JvmStatic
    suspend fun sha512Suspend(path: String, onResult: (String?, Throwable?) -> Unit): Unit =
        hashFileSuspend(path, Algorithm.SHA512, onResult)

    // ==================== CRC32 ====================

    /**
     * 计算文件 CRC32 校验值
     *
     * CRC32 速度比 MD5 快，适用于快速变化检测和传输校验。
     *
     * @param path 文件路径
     * @return CRC32 十六进制字符串，失败返回 null
     */
    private fun crc32Impl(failureReporter: FileFailureCallbacks.Reporter, path: String): String? {
        if (!FileUtils.isFile(path)) return failureReporter.fail(null, "FileHashUtils.crc32 失败：源文件不存在或不是普通文件")
        return try {
            val crc = CRC32()
            FileInputStream(File(path)).use { fis ->
                CheckedInputStream(fis, crc).use { cis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (cis.read(buffer) != -1) { /* 自动更新 CRC */ }
                }
            }
            java.lang.Long.toHexString(crc.value).padStart(8, '0')
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算 CRC32
     */
    @JvmStatic
    suspend fun crc32Suspend(
        path: String,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = crc32(path, onFailure = { failure = it })
                safeCallback {
                    onResult(result, failure)
                }
            } catch (e: Exception) {
                FileFailureCallbacks.rethrowCancellation(e)
                safeCallback { onResult(null, e) }
            }
        }
    }

    // ==================== 字节/字符串哈希 ====================

    /**
     * 计算字节数组的哈希值
     *
     * @param data      字节数据
     * @param algorithm 哈希算法
     * @return 十六进制哈希字符串
     */
    @JvmStatic
    @JvmOverloads
    fun hashBytes(data: ByteArray, algorithm: Algorithm = Algorithm.SHA256): String {
        val digest = MessageDigest.getInstance(algorithm.jceName)
        return digest.digest(data).toHexString()
    }

    /**
     * 计算字符串的哈希值
     *
     * @param text      字符串
     * @param algorithm 哈希算法
     * @return 十六进制哈希字符串
     */
    @JvmStatic
    @JvmOverloads
    fun hashString(text: String, algorithm: Algorithm = Algorithm.SHA256): String {
        return hashBytes(text.toByteArray(Charsets.UTF_8), algorithm)
    }

    /**
     * 计算字节数组的 CRC32 值
     */
    @JvmStatic
    fun crc32(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return java.lang.Long.toHexString(crc.value).padStart(8, '0')
    }

    // ==================== 文件校验 ====================

    /**
     * 校验文件完整性（对比哈希值）
     *
     * @param path           文件路径
     * @param expectedHash   期望的哈希值（十六进制）
     * @param algorithm      哈希算法
     * @param ignoreCase     是否忽略大小写，默认 true
     * @return true 表示文件哈希与期望值一致
     *
     * 使用示例:
     * ```kotlin
     * val valid = FileHashUtils.verify("/sdcard/download.apk",
     *     "a1b2c3d4e5f6...", Algorithm.SHA256)
     * if (valid) installApk() else showError("文件已损坏")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun verify(
        path: String,
        expectedHash: String,
        algorithm: Algorithm = Algorithm.SHA256,
        ignoreCase: Boolean = true
    ): Boolean {
        val actual = hashFile(path, algorithm) ?: return false
        return actual.equals(expectedHash, ignoreCase)
    }

    /**
     * 异步校验文件完整性
     *
     * @param path          文件路径
     * @param expectedHash  期望哈希值
     * @param algorithm     算法
     * @param ignoreCase    是否忽略大小写
     * @param onResult      回调 (valid: Boolean, actualHash: String?)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun verifySuspend(
        path: String,
        expectedHash: String,
        algorithm: Algorithm = Algorithm.SHA256,
        ignoreCase: Boolean = true,
        onResult: (Boolean, String?) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            val actual = hashFile(path, algorithm, onFailure)
            val valid = actual != null && actual.equals(expectedHash, ignoreCase)
            safeCallback { onResult(valid, actual) }
        }
    }

    /**
     * 比较两个文件的内容是否相同（通过哈希对比）
     *
     * @param path1     文件1路径
     * @param path2     文件2路径
     * @param algorithm 哈希算法
     * @return true 表示内容相同
     */
    @JvmStatic
    @JvmOverloads
    fun compareFiles(
        path1: String,
        path2: String,
        algorithm: Algorithm = Algorithm.SHA256
    ): Boolean {
        val hash1 = hashFile(path1, algorithm) ?: return false
        val hash2 = hashFile(path2, algorithm) ?: return false
        return hash1.equals(hash2, ignoreCase = true)
    }

    /**
     * 异步比较两个文件
     */
    @JvmStatic
    @JvmOverloads
    suspend fun compareFilesSuspend(
        path1: String,
        path2: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            val first = hashFile(path1, algorithm, onFailure)
            val second = if (first != null) hashFile(path2, algorithm, onFailure) else null
            safeCallback { onResult(first != null && second != null && first.equals(second, ignoreCase = true)) }
        }
    }

    // ==================== 内部方法 ====================

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** hashFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun hashFile(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.hashFile", "$path", onFailure, { it == null }, null) { failureReporter ->
            hashFileImpl(failureReporter, path, algorithm)
        }

    /** hashFileWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun hashFileWithProgress(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.hashFileWithProgress", "$path", onFailure, { it == null }, null) { failureReporter ->
            hashFileWithProgressImpl(failureReporter, path, algorithm, onProgress)
        }

    /** md5 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun md5(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.md5", "$path", onFailure, { it == null }, null) { failureReporter ->
            md5Impl(failureReporter, path)
        }

    /** sha1 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun sha1(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.sha1", "$path", onFailure, { it == null }, null) { failureReporter ->
            sha1Impl(failureReporter, path)
        }

    /** sha256 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun sha256(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.sha256", "$path", onFailure, { it == null }, null) { failureReporter ->
            sha256Impl(failureReporter, path)
        }

    /** sha512 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun sha512(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.sha512", "$path", onFailure, { it == null }, null) { failureReporter ->
            sha512Impl(failureReporter, path)
        }

    /** crc32 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun crc32(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileHashUtils.crc32", "$path", onFailure, { it == null }, null) { failureReporter ->
            crc32Impl(failureReporter, path)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("verifySuspendLegacyCallback")
    suspend fun verifySuspend(
        path: String,
        expectedHash: String,
        algorithm: Algorithm = Algorithm.SHA256,
        ignoreCase: Boolean = true,
        onResult: (Boolean, String?) -> Unit
    ) = verifySuspend(path = path, expectedHash = expectedHash, algorithm = algorithm, ignoreCase = ignoreCase, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("compareFilesSuspendLegacyCallback")
    suspend fun compareFilesSuspend(
        path1: String,
        path2: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onResult: (Boolean) -> Unit
    ) = compareFilesSuspend(path1 = path1, path2 = path2, algorithm = algorithm, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("hashFileWithProgressLegacyCallback")
    fun hashFileWithProgress(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) = hashFileWithProgress(path = path, algorithm = algorithm, onProgress = onProgress, onFailure = null)
}
