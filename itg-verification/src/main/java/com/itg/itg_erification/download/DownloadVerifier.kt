package com.itg.itg_erification.download

import com.itg.itg_erification.hash.HashVerifier
import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.util.concurrent.Future

/**
 * 下载文件验证器
 *
 * 针对下载场景的专项验证，覆盖下载完成性、断点续传兼容性、
 * 校验文件配对验证等常见需求。
 *
 * 核心功能:
 * - 下载大小验证（对比 HTTP Content-Length）
 * - 校验文件配对验证（.md5 / .sha1 / .sha256 文件）
 * - 断点续传完整性验证
 * - 分片下载合并验证
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
object DownloadVerifier {

    /**
     * 下载验证综合结果
     */
    data class DownloadResult(
        val isComplete: Boolean,
        val isValid: Boolean,
        val expectedSize: Long = -1L,
        val actualSize: Long = -1L,
        val hashResult: HashVerifier.HashResult? = null,
        val messages: List<String> = emptyList()
    ) {
        override fun toString(): String = buildString {
            append(if (isValid) "PASS" else "FAIL")
            if (expectedSize > 0) append(" | size=${actualSize}/${expectedSize}")
            if (hashResult != null) append(" | hash=${if (hashResult.isValid) "OK" else "MISMATCH"}")
            if (messages.isNotEmpty()) append(" | ${messages.joinToString("; ")}")
        }

        companion object {
            fun fail(vararg msg: String) = DownloadResult(false, false, messages = msg.toList())
        }
    }

    // ==================== 下载大小验证 ====================

    /**
     * 验证下载文件尺寸是否与服务器声明一致
     *
     * 典型场景: HTTP 下载时，响应头包含 `Content-Length`，
     * 下载完成后对比文件实际大小。
     *
     * @param filePath     下载的文件路径
     * @param expectedSize 期望大小（来自 Content-Length 头），-1 表示跳过尺寸检查
     * @return [DownloadResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = DownloadVerifier.verifySize("/sdcard/download.apk",
     *     expectedSize = 25 * 1024 * 1024)  // 25MB
     * if (result.isComplete) installApk()
     * ```
     */
    @JvmStatic
    fun verifySize(filePath: String, expectedSize: Long): DownloadResult {
        if (!FileUtils.exists(filePath)) {
            return DownloadResult.fail("File not found: $filePath")
        }

        val actualSize = FileUtils.getSize(filePath)
        val sizeMatch = expectedSize <= 0 || actualSize == expectedSize

        return DownloadResult(
            isComplete = sizeMatch,
            isValid = sizeMatch,
            expectedSize = expectedSize,
            actualSize = actualSize,
            messages = if (sizeMatch) emptyList()
            else listOf("Size mismatch: expected=$expectedSize, actual=$actualSize")
        )
    }

    /**
     * 异步验证大小
     */
    @JvmStatic
    fun verifySizeAsync(
        filePath: String,
        expectedSize: Long,
        onResult: (DownloadResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifySize(filePath, expectedSize)) }
    }

    /**
     * 验证文件至少达到了最小尺寸（下载未完成检测）
     *
     * 适用于检测下载是否被截断。
     *
     * @param filePath 文件路径
     * @param minBytes 最小期望字节数
     * @return [DownloadResult]
     */
    @JvmStatic
    fun verifyMinSize(filePath: String, minBytes: Long): DownloadResult {
        if (!FileUtils.exists(filePath)) {
            return DownloadResult.fail("File not found")
        }

        val actualSize = FileUtils.getSize(filePath)
        val complete = actualSize >= minBytes

        return DownloadResult(
            isComplete = complete,
            isValid = complete,
            expectedSize = minBytes,
            actualSize = actualSize,
            messages = if (complete) listOf("Size OK: $actualSize >= $minBytes")
            else listOf("Incomplete: $actualSize < $minBytes")
        )
    }

    // ==================== 校验文件配对验证 ====================

    /**
     * 使用 .md5 文件验证下载完整性
     *
     * 常见于下载页面提供 `file.zip` + `file.zip.md5`。
     *
     * @param filePath    下载的文件路径
     * @param md5FilePath .md5 校验文件路径，如果为 null 则自动查找同名 .md5 文件
     * @return [DownloadResult]
     */
    @JvmStatic
    @JvmOverloads
    fun verifyWithMd5File(filePath: String, md5FilePath: String? = null): DownloadResult {
        if (!FileUtils.isFile(filePath)) return DownloadResult.fail("File not found")

        val checksumPath = md5FilePath ?: "$filePath.md5"
        val result = HashVerifier.verifyFromMd5File(filePath, checksumPath)

        return DownloadResult(
            isComplete = FileUtils.exists(filePath),
            isValid = result.isValid,
            actualSize = FileUtils.getSize(filePath),
            hashResult = result,
            messages = if (result.isValid) listOf("Hash verified") else listOf("Hash mismatch")
        )
    }

    /**
     * 使用 .sha1 / .sha256 文件验证
     */
    @JvmStatic
    @JvmOverloads
    fun verifyWithShaFile(
        filePath: String,
        algorithm: String = "SHA-256",
        checksumFilePath: String? = null
    ): DownloadResult {
        if (!FileUtils.isFile(filePath)) return DownloadResult.fail("File not found")

        val ext = algorithm.lowercase().replace("-", "")
        val checksumPath = checksumFilePath ?: "$filePath.$ext"
        val result = HashVerifier.verifyFromChecksumFile(filePath, checksumPath, algorithm)

        return DownloadResult(
            isComplete = FileUtils.exists(filePath),
            isValid = result.isValid,
            actualSize = FileUtils.getSize(filePath),
            hashResult = result,
            messages = if (result.isValid) listOf("$algorithm verified") else listOf("$algorithm mismatch")
        )
    }

    /**
     * 异步校验文件验证
     */
    @JvmStatic
    fun verifyWithMd5FileAsync(
        filePath: String,
        md5FilePath: String? = null,
        onResult: (DownloadResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyWithMd5File(filePath, md5FilePath)) }
    }

    // ==================== 断点续传验证 ====================

    /**
     * 验证断点续传是否可行
     *
     * 检查已下载部分文件的尺寸是否小于目标总尺寸，
     * 如果文件已完整（尺寸匹配），返回 isComplete=true。
     *
     * @param existingFilePath 已下载的不完整文件
     * @param expectedTotal    期望的总文件大小
     * @return [DownloadResult]
     */
    @JvmStatic
    fun verifyResumePossible(existingFilePath: String, expectedTotal: Long): DownloadResult {
        if (!FileUtils.exists(existingFilePath)) {
            // 文件不存在 = 需要从头下载
            return DownloadResult(
                isComplete = false,
                isValid = true,  // 从头下载是可行的
                expectedSize = expectedTotal,
                actualSize = 0,
                messages = listOf("File not found, need full download")
            )
        }

        val actualSize = FileUtils.getSize(existingFilePath)

        return when {
            actualSize == expectedTotal -> DownloadResult(
                isComplete = true,
                isValid = true,
                expectedSize = expectedTotal,
                actualSize = actualSize,
                messages = listOf("Already complete")
            )
            actualSize < expectedTotal -> DownloadResult(
                isComplete = false,
                isValid = true,  // 可以续传
                expectedSize = expectedTotal,
                actualSize = actualSize,
                messages = listOf("Can resume from byte $actualSize")
            )
            else -> DownloadResult(
                isComplete = false,
                isValid = false,  // 文件比期望的还大，异常
                expectedSize = expectedTotal,
                actualSize = actualSize,
                messages = listOf("File larger than expected: $actualSize > $expectedTotal")
            )
        }
    }

    // ==================== 综合下载验证 ====================

    /**
     * 一站式下载验证
     *
     * 同时检查: 存在性 → 尺寸 → 哈希（如果提供校验文件）
     *
     * @param filePath     下载的文件路径
     * @param expectedSize 期望大小，-1 跳过
     * @param expectedHash 期望哈希值，null 跳过
     * @param hashAlgo     哈希算法，默认 SHA-256
     * @return [DownloadResult]
     *
     * 使用示例:
     * ```kotlin
     * // 完整下载验证: 大小 + SHA-256
     * val result = DownloadVerifier.verifyDownload(
     *     "/sdcard/app.apk",
     *     expectedSize = 25 * 1024 * 1024,
     *     expectedHash = "e3b0c44298fc1c..."
     * )
     * println(result)  // PASS | size=26214400/26214400 | hash=OK
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun verifyDownload(
        filePath: String,
        expectedSize: Long = -1L,
        expectedHash: String? = null,
        hashAlgo: String = "SHA-256"
    ): DownloadResult {
        val messages = mutableListOf<String>()
        var valid = true
        var complete = true

        // 1. 存在性
        if (!FileUtils.exists(filePath)) {
            return DownloadResult.fail("File not found")
        }

        val actualSize = FileUtils.getSize(filePath)

        // 2. 尺寸
        if (expectedSize > 0 && actualSize != expectedSize) {
            valid = false
            complete = actualSize < expectedSize
            messages.add("Size mismatch: expected=$expectedSize, actual=$actualSize")
        }

        // 3. 哈希
        var hashResult: HashVerifier.HashResult? = null
        if (expectedHash != null) {
            hashResult = HashVerifier.verifyHash(filePath, expectedHash, hashAlgo)
            if (!hashResult.isValid) {
                valid = false
                messages.add("Hash mismatch")
            }
        }

        return DownloadResult(
            isComplete = complete,
            isValid = valid,
            expectedSize = expectedSize,
            actualSize = actualSize,
            hashResult = hashResult,
            messages = messages.ifEmpty { listOf("All checks passed") }
        )
    }

    /**
     * 异步综合验证
     */
    @JvmStatic
    fun verifyDownloadAsync(
        filePath: String,
        expectedSize: Long = -1L,
        expectedHash: String? = null,
        hashAlgo: String = "SHA-256",
        onResult: (DownloadResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyDownload(filePath, expectedSize, expectedHash, hashAlgo)) }
    }

    // ==================== 分片下载验证 ====================

    /**
     * 验证分片文件数量和命名连贯性
     *
     * 适用于 `file.part0`, `file.part1`, ... 分片下载场景。
     *
     * @param directory  分片文件所在目录
     * @param prefix     文件名前缀，如 "download"
     * @param expectedCount 期望的分片数量
     * @return 缺失的分片索引列表，空列表表示完整
     */
    @JvmStatic
    fun verifyParts(
        directory: String,
        prefix: String,
        expectedCount: Int
    ): List<Int> {
        val missing = mutableListOf<Int>()
        for (i in 0 until expectedCount) {
            val partFile = File(directory, "$prefix.part$i")
            if (!partFile.exists() || partFile.length() == 0L) {
                missing.add(i)
            }
        }
        return missing
    }

    /**
     * 异步验证分片
     */
    @JvmStatic
    fun verifyPartsAsync(
        directory: String,
        prefix: String,
        expectedCount: Int,
        onResult: (List<Int>) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyParts(directory, prefix, expectedCount)) }
    }
}
