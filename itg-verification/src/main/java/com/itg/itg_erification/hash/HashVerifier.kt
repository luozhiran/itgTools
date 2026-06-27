package com.itg.itg_erification.hash

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.hash.FileHashUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.Future
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/**
 * 哈希校验器
 *
 * 提供文件哈希校验能力，支持 MD5 / SHA-1 / SHA-256 / SHA-512 / CRC32，
 * 可用于下载文件完整性校验、文件去重、数据完整性验证等场景。
 *
 * 核心功能:
 * - 单文件哈希校验（对比预期值）
 * - 批量校验（多文件同时校验）
 * - 从校验文件读取预期值（.md5 / .sha1 / .sha256）
 * - CRC32 快速校验
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
object HashVerifier {

    /**
     * 校验结果
     *
     * @property isValid 是否通过校验
     * @property expected 期望的哈希值
     * @property actual 实际计算的哈希值
     * @property algorithm 使用的算法
     */
    data class HashResult(
        val isValid: Boolean,
        val expected: String,
        val actual: String?,
        val algorithm: String
    ) {
        override fun toString(): String {
            return if (isValid) {
                "PASS ($algorithm)"
            } else {
                "FAIL ($algorithm) expected=$expected, actual=${actual ?: "N/A"}"
            }
        }
    }

    // ==================== 单文件哈希校验 ====================

    /**
     * 校验文件 MD5
     *
     * @param path        文件路径
     * @param expectedMd5 期望的 MD5 值（十六进制）
     * @return [HashResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = HashVerifier.verifyMd5("/sdcard/download.apk", "a1b2c3d4...")
     * if (result.isValid) installApk() else deleteAndRetry()
     * ```
     */
    @JvmStatic
    fun verifyMd5(path: String, expectedMd5: String): HashResult {
        val actual = FileHashUtils.md5(path)
        return HashResult(
            isValid = actual != null && actual.equals(expectedMd5, ignoreCase = true),
            expected = expectedMd5,
            actual = actual,
            algorithm = "MD5"
        )
    }

    /**
     * 异步校验 MD5
     */
    @JvmStatic
    fun verifyMd5Async(
        path: String,
        expectedMd5: String,
        onResult: (HashResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyMd5(path, expectedMd5)) }
    }

    /**
     * 校验文件 SHA-1
     */
    @JvmStatic
    fun verifySha1(path: String, expectedSha1: String): HashResult {
        val actual = FileHashUtils.sha1(path)
        return HashResult(
            isValid = actual != null && actual.equals(expectedSha1, ignoreCase = true),
            expected = expectedSha1,
            actual = actual,
            algorithm = "SHA-1"
        )
    }

    /**
     * 异步校验 SHA-1
     */
    @JvmStatic
    fun verifySha1Async(
        path: String,
        expectedSha1: String,
        onResult: (HashResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifySha1(path, expectedSha1)) }
    }

    /**
     * 校验文件 SHA-256
     */
    @JvmStatic
    fun verifySha256(path: String, expectedSha256: String): HashResult {
        val actual = FileHashUtils.sha256(path)
        return HashResult(
            isValid = actual != null && actual.equals(expectedSha256, ignoreCase = true),
            expected = expectedSha256,
            actual = actual,
            algorithm = "SHA-256"
        )
    }

    /**
     * 异步校验 SHA-256
     */
    @JvmStatic
    fun verifySha256Async(
        path: String,
        expectedSha256: String,
        onResult: (HashResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifySha256(path, expectedSha256)) }
    }

    /**
     * 校验文件 SHA-512
     */
    @JvmStatic
    fun verifySha512(path: String, expectedSha512: String): HashResult {
        val actual = FileHashUtils.sha512(path)
        return HashResult(
            isValid = actual != null && actual.equals(expectedSha512, ignoreCase = true),
            expected = expectedSha512,
            actual = actual,
            algorithm = "SHA-512"
        )
    }

    /**
     * 通用哈希校验
     *
     * @param path          文件路径
     * @param expectedHash  期望值
     * @param algorithm     算法名称，如 "MD5", "SHA-256"
     * @return [HashResult]
     */
    @JvmStatic
    fun verifyHash(path: String, expectedHash: String, algorithm: String): HashResult {
        val algo = FileHashUtils.Algorithm.fromString(algorithm)
            ?: return HashResult(false, expectedHash, null, algorithm)

        val actual = FileHashUtils.hashFile(path, algo)
        return HashResult(
            isValid = actual != null && actual.equals(expectedHash, ignoreCase = true),
            expected = expectedHash,
            actual = actual,
            algorithm = algorithm.uppercase()
        )
    }

    /**
     * 异步通用哈希校验
     */
    @JvmStatic
    fun verifyHashAsync(
        path: String,
        expectedHash: String,
        algorithm: String,
        onResult: (HashResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyHash(path, expectedHash, algorithm)) }
    }

    // ==================== CRC32 校验 ====================

    /**
     * CRC32 快速校验
     *
     * CRC32 速度比 MD5 更快，适合快速变化检测（非安全校验）。
     *
     * @param path        文件路径
     * @param expectedCrc 期望的 CRC32 值（十六进制）
     * @return [HashResult]
     */
    @JvmStatic
    fun verifyCrc32(path: String, expectedCrc: String): HashResult {
        val actual = FileHashUtils.crc32(path)
        return HashResult(
            isValid = actual != null && actual.equals(expectedCrc, ignoreCase = true),
            expected = expectedCrc,
            actual = actual,
            algorithm = "CRC32"
        )
    }

    /**
     * 异步 CRC32 校验
     */
    @JvmStatic
    fun verifyCrc32Async(
        path: String,
        expectedCrc: String,
        onResult: (HashResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyCrc32(path, expectedCrc)) }
    }

    // ==================== 批量校验 ====================

    /**
     * 批量哈希校验
     *
     * @param files 文件路径到期望哈希值的映射，值格式为 "algorithm:hash" 或直接 "hash"（默认 SHA-256）
     * @return 每个文件的校验结果 Map
     *
     * 使用示例:
     * ```kotlin
     * val results = HashVerifier.batchVerify(mapOf(
     *     "/sdcard/file1.bin" to "SHA-256:e3b0c44...",
     *     "/sdcard/file2.bin" to "MD5:d41d8cd...",
     * ))
     * results.forEach { (path, result) -> println("$path: $result") }
     * ```
     */
    @JvmStatic
    fun batchVerify(files: Map<String, String>): Map<String, HashResult> {
        val results = LinkedHashMap<String, HashResult>()
        files.forEach { (path, hashSpec) ->
            val result = if (hashSpec.contains(":")) {
                val parts = hashSpec.split(":", limit = 2)
                verifyHash(path, parts[1], parts[0])
            } else {
                verifyHash(path, hashSpec, "SHA-256")
            }
            results[path] = result
        }
        return results
    }

    /**
     * 异步批量校验
     */
    @JvmStatic
    fun batchVerifyAsync(
        files: Map<String, String>,
        onResult: (Map<String, HashResult>) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(batchVerify(files)) }
    }

    /**
     * 批量校验（全部通过才返回 true）
     *
     * @param files 文件→期望哈希的映射
     * @return true 表示全部通过
     */
    @JvmStatic
    fun batchVerifyAll(files: Map<String, String>): Boolean {
        return batchVerify(files).all { it.value.isValid }
    }

    // ==================== 校验文件读取 ====================

    /**
     * 从 .md5 校验文件验证
     *
     * .md5 文件格式: `MD5_HASH  filename`（每行一个），
     * 典型场景：下载文件时附带 .md5 校验文件。
     *
     * @param filePath    要校验的文件路径
     * @param md5FilePath .md5 校验文件路径
     * @return [HashResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = HashVerifier.verifyFromMd5File(
     *     "/sdcard/download.iso",
     *     "/sdcard/download.iso.md5"
     * )
     * ```
     */
    @JvmStatic
    fun verifyFromMd5File(filePath: String, md5FilePath: String): HashResult {
        val expectedMd5 = parseChecksumFile(md5FilePath) ?: return HashResult(
            false, "N/A", null, "MD5"
        ).also { android.util.Log.w("HashVerifier", "Failed to parse: $md5FilePath") }

        return verifyMd5(filePath, expectedMd5)
    }

    /**
     * 从 .sha1 / .sha256 校验文件验证
     *
     * @param filePath     要校验的文件路径
     * @param checksumFile 校验文件路径
     * @param algorithm    算法，默认 SHA-256
     * @return [HashResult]
     */
    @JvmStatic
    @JvmOverloads
    fun verifyFromChecksumFile(
        filePath: String,
        checksumFile: String,
        algorithm: String = "SHA-256"
    ): HashResult {
        val expected = parseChecksumFile(checksumFile) ?: return HashResult(
            false, "N/A", null, algorithm
        )
        return verifyHash(filePath, expected, algorithm)
    }

    /**
     * 解析校验文件，提取哈希值
     *
     * 支持格式:
     * - `hash`（单行纯哈希）
     * - `hash  filename`（hash + 空格 + 文件名）
     * - `hash *filename`（hash + 空格 + 星号 + 文件名）
     *
     * @param checksumFile 校验文件路径
     * @return 提取的哈希值，失败返回 null
     */
    @JvmStatic
    fun parseChecksumFile(checksumFile: String): String? {
        if (!FileUtils.isFile(checksumFile)) return null
        return try {
            val line = File(checksumFile).readLines().firstOrNull { it.isNotBlank() } ?: return null
            // 格式: HASH  FILENAME 或 HASH *FILENAME 或纯 HASH
            val trimmed = line.trim()
            if (trimmed.contains(" ")) {
                trimmed.substringBefore(" ").trim()
            } else {
                trimmed
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
