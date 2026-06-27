package com.itg.itg_file.hash

import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.Future
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/**
 * 文件哈希/校验工具类
 *
 * 提供多种哈希算法计算文件摘要，用于文件完整性校验和去重识别。
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
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
    @JvmStatic
    @JvmOverloads
    fun hashFile(path: String, algorithm: Algorithm = Algorithm.SHA256): String? {
        if (!FileUtils.isFile(path)) return null
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
     * @return [Future]
     *
     * 使用示例:
     * ```kotlin
     * FileHashUtils.hashFileAsync("/sdcard/photo.jpg", Algorithm.MD5) { hash, error ->
     *     if (hash != null) {
     *         TaskExecutor.main { textView.text = "MD5: $hash" }
     *     }
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun hashFileAsync(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val hash = hashFile(path, algorithm)
                onResult(hash, if (hash == null) IOException("Hash failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
    @JvmStatic
    @JvmOverloads
    fun hashFileWithProgress(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): String? {
        if (!FileUtils.isFile(path)) return null

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
                    onProgress?.invoke(processed, totalSize)
                }
            }
            digest.digest().toHexString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算文件哈希（带进度）
     */
    @JvmStatic
    @JvmOverloads
    fun hashFileWithProgressAsync(
        path: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val hash = hashFileWithProgress(path, algorithm, onProgress)
                onResult(hash, if (hash == null) IOException("Hash failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
            }
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 计算文件 MD5 值
     */
    @JvmStatic
    fun md5(path: String): String? = hashFile(path, Algorithm.MD5)

    /**
     * 异步计算 MD5
     */
    @JvmStatic
    fun md5Async(path: String, onResult: (String?, Throwable?) -> Unit): Future<*> =
        hashFileAsync(path, Algorithm.MD5, onResult)

    /**
     * 计算文件 SHA-1 值
     */
    @JvmStatic
    fun sha1(path: String): String? = hashFile(path, Algorithm.SHA1)

    /**
     * 异步计算 SHA-1
     */
    @JvmStatic
    fun sha1Async(path: String, onResult: (String?, Throwable?) -> Unit): Future<*> =
        hashFileAsync(path, Algorithm.SHA1, onResult)

    /**
     * 计算文件 SHA-256 值
     */
    @JvmStatic
    fun sha256(path: String): String? = hashFile(path, Algorithm.SHA256)

    /**
     * 异步计算 SHA-256
     */
    @JvmStatic
    fun sha256Async(path: String, onResult: (String?, Throwable?) -> Unit): Future<*> =
        hashFileAsync(path, Algorithm.SHA256, onResult)

    /**
     * 计算文件 SHA-512 值
     */
    @JvmStatic
    fun sha512(path: String): String? = hashFile(path, Algorithm.SHA512)

    /**
     * 异步计算 SHA-512
     */
    @JvmStatic
    fun sha512Async(path: String, onResult: (String?, Throwable?) -> Unit): Future<*> =
        hashFileAsync(path, Algorithm.SHA512, onResult)

    // ==================== CRC32 ====================

    /**
     * 计算文件 CRC32 校验值
     *
     * CRC32 速度比 MD5 快，适用于快速变化检测和传输校验。
     *
     * @param path 文件路径
     * @return CRC32 十六进制字符串，失败返回 null
     */
    @JvmStatic
    fun crc32(path: String): String? {
        if (!FileUtils.isFile(path)) return null
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
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步计算 CRC32
     */
    @JvmStatic
    fun crc32Async(
        path: String,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            try {
                val result = crc32(path)
                onResult(result, if (result == null) IOException("CRC32 failed: $path") else null)
            } catch (e: Exception) {
                onResult(null, e)
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
     * @return [Future]
     */
    @JvmStatic
    @JvmOverloads
    fun verifyAsync(
        path: String,
        expectedHash: String,
        algorithm: Algorithm = Algorithm.SHA256,
        ignoreCase: Boolean = true,
        onResult: (Boolean, String?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            val actual = hashFile(path, algorithm)
            val valid = actual != null && actual.equals(expectedHash, ignoreCase)
            onResult(valid, actual)
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
    fun compareFilesAsync(
        path1: String,
        path2: String,
        algorithm: Algorithm = Algorithm.SHA256,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(compareFiles(path1, path2, algorithm)) }
    }

    // ==================== 内部方法 ====================

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
