package com.itg.itg_erification.archive

import android.content.Context
import android.content.pm.PackageManager
import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.Future
import java.util.zip.CheckedInputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * 压缩包/归档文件校验器
 *
 * 提供 ZIP / GZIP / APK / JAR 等归档文件的完整性验证。
 * 适用于下载的压缩包校验、APK 安装前验证、归档文件内容检测等场景。
 *
 * 核心功能:
 * - ZIP 文件完整性（Central Directory + 逐条目 CRC）
 * - GZIP 文件完整性
 * - APK 签名验证
 * - 压缩包内容列表与检查
 * - 解压前预检（防止 Zip Bomb）
 *
 * @author ITG Team
 * @since 1.0.0
 */
object ArchiveVerifier {

    /** 最大压缩比阈值（防 Zip Bomb），默认 100 */
    private const val DEFAULT_MAX_COMPRESSION_RATIO = 100

    /** 最大条目数阈值 */
    private const val DEFAULT_MAX_ENTRIES = 100_000

    // ==================== 结果类型 ====================

    data class ArchiveResult(
        val isValid: Boolean,
        val reason: String = "",
        val entries: Int = 0,
        val details: Map<String, Any> = emptyMap()
    ) {
        override fun toString(): String = if (isValid) "PASS ($entries entries)"
        else "FAIL: $reason"

        companion object {
            fun pass(entries: Int = 0, details: Map<String, Any> = emptyMap()) =
                ArchiveResult(true, "", entries, details)
            fun fail(reason: String, details: Map<String, Any> = emptyMap()) =
                ArchiveResult(false, reason, 0, details)
        }
    }

    data class ApkSignatureResult(
        val isValid: Boolean,
        val signatures: List<String> = emptyList(),  // 签名证书 SHA-256 指纹
        val packageName: String = "",
        val versionName: String = "",
        val versionCode: Long = 0L,
        val reason: String = ""
    )

    // ==================== ZIP 校验 ====================

    /**
     * 验证 ZIP 文件完整性
     *
     * 检查: Central Directory 是否存在 → 逐条目 CRC 校验 → 条目完整性。
     *
     * @param path ZIP 文件路径
     * @return [ArchiveResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = ArchiveVerifier.verifyZip("/sdcard/download.zip")
     * if (result.isValid) {
     *     println("ZIP valid: ${result.entries} entries")
     * }
     * ```
     */
    @JvmStatic
    fun verifyZip(path: String): ArchiveResult {
        if (!FileUtils.isFile(path)) return ArchiveResult.fail("File not found")

        return try {
            var entryCount = 0
            var failedEntries = 0
            val failedEntryNames = mutableListOf<String>()

            ZipFile(path).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++

                    // 逐条目 CRC 校验
                    try {
                        val checkedInput = CheckedInputStream(zip.getInputStream(entry), CRC32())
                        checkedInput.use { input ->
                            val buffer = ByteArray(8192)
                            while (input.read(buffer) != -1) { /* 读取以触发 CRC 计算 */ }
                        }
                        if (entry.crc < 0L || checkedInput.checksum.value != entry.crc) {
                            throw java.util.zip.ZipException(
                                "CRC mismatch: expected=${entry.crc}, actual=${checkedInput.checksum.value}"
                            )
                        }
                        // ZipFile 内部会自动校验 CRC，读取完整则 CRC 通过
                    } catch (e: Exception) {
                        failedEntries++
                        failedEntryNames.add(entry.name)
                    }
                }
            }

            if (failedEntries > 0) {
                ArchiveResult.fail(
                    "$failedEntries/$entryCount entries corrupted",
                    mapOf("entryCount" to entryCount, "failedEntries" to failedEntries,
                        "failedNames" to failedEntryNames)
                )
            } else {
                ArchiveResult.pass(entryCount, mapOf("entryCount" to entryCount))
            }
        } catch (e: Exception) {
            ArchiveResult.fail("Invalid ZIP: ${e.message}", mapOf("error" to e.message.toString()))
        }
    }

    /**
     * 异步验证 ZIP
     */
    @JvmStatic
    fun verifyZipAsync(path: String, onResult: (ArchiveResult) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(verifyZip(path)) }
    }

    /**
     * 验证 ZIP 中指定条目的 CRC
     *
     * @param zipPath   ZIP 文件路径
     * @param entryName 条目名称
     * @param expectedCrc 期望的 CRC32 值
     * @return [ArchiveResult]
     */
    @JvmStatic
    fun verifyZipEntry(path: String, entryName: String, expectedCrc: Long): ArchiveResult {
        if (!FileUtils.isFile(path)) return ArchiveResult.fail("File not found")

        return try {
            ZipFile(path).use { zip ->
                val entry = zip.getEntry(entryName)
                    ?: return ArchiveResult.fail("Entry not found: $entryName")

                val checkedInput = CheckedInputStream(zip.getInputStream(entry), CRC32())
                checkedInput.use { input ->
                    val buffer = ByteArray(8192)
                    while (input.read(buffer) != -1) { /* Read the complete entry. */ }
                }
                val actualCrc = checkedInput.checksum.value
                if (actualCrc != entry.crc) {
                    ArchiveResult.fail(
                        "Entry data is corrupted: '$entryName'",
                        mapOf("declaredCrc" to entry.crc, "actualCrc" to actualCrc)
                    )
                } else if (actualCrc == expectedCrc) {
                    ArchiveResult.pass(1, mapOf("entryName" to entryName, "crc" to actualCrc))
                } else {
                    ArchiveResult.fail(
                        "CRC mismatch for '$entryName': expected=$expectedCrc, actual=$actualCrc",
                        mapOf("expectedCrc" to expectedCrc, "actualCrc" to actualCrc)
                    )
                }
            }
        } catch (e: Exception) {
            ArchiveResult.fail("Error: ${e.message}")
        }
    }

    // ==================== 解压前安全检查 ====================

    /**
     * ZIP 解压前预检（防 Zip Bomb / 路径穿越）
     *
     * @param path               ZIP 文件路径
     * @param maxCompressionRatio 最大压缩比（解压后/压缩前），超阈值视为 Zip Bomb
     * @param maxEntries          最大条目数
     * @param checkZipSlip        是否检查路径穿越攻击
     * @return [ArchiveResult]
     *
     * 使用示例:
     * ```kotlin
     * // 解压前安全检查
     * val result = ArchiveVerifier.verifyZipSafety("/sdcard/download.zip")
     * if (result.isValid) {
     *     unzipToDirectory()  // 安全，可以解压
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun verifyZipSafety(
        path: String,
        maxCompressionRatio: Int = DEFAULT_MAX_COMPRESSION_RATIO,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        checkZipSlip: Boolean = true
    ): ArchiveResult {
        if (!FileUtils.isFile(path)) return ArchiveResult.fail("File not found")
        if (maxCompressionRatio <= 0) {
            return ArchiveResult.fail("maxCompressionRatio must be greater than 0")
        }
        if (maxEntries <= 0) {
            return ArchiveResult.fail("maxEntries must be greater than 0")
        }

        return try {
            var entryCount = 0
            var totalCompressed = 0L
            var totalUncompressed = 0L

            ZipFile(path).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++

                    if (entryCount > maxEntries) {
                        return ArchiveResult.fail(
                            "Too many entries: $entryCount > $maxEntries (possible Zip Bomb)",
                            mapOf("entryCount" to entryCount, "maxEntries" to maxEntries)
                        )
                    }

                    // Zip Slip 检测
                    if (checkZipSlip && isZipSlipPath(entry.name)) {
                        return ArchiveResult.fail(
                            "Zip Slip detected: '${entry.name}'",
                            mapOf("dangerousEntry" to entry.name)
                        )
                    }

                    val compressedSize = entry.compressedSize
                    val uncompressedSize = entry.size
                    if (compressedSize < 0L || uncompressedSize < 0L) {
                        return ArchiveResult.fail(
                            "Unknown entry size: '${entry.name}'",
                            mapOf("entryName" to entry.name)
                        )
                    }
                    if (isCompressionRatioExceeded(
                            uncompressedSize,
                            compressedSize,
                            maxCompressionRatio
                        )) {
                        return ArchiveResult.fail(
                            "Compression ratio too high for '${entry.name}' (possible Zip Bomb)",
                            mapOf(
                                "entryName" to entry.name,
                                "ratio" to compressionRatio(uncompressedSize, compressedSize),
                                "maxRatio" to maxCompressionRatio
                            )
                        )
                    }
                    if (Long.MAX_VALUE - totalCompressed < compressedSize ||
                        Long.MAX_VALUE - totalUncompressed < uncompressedSize
                    ) {
                        return ArchiveResult.fail("Archive size overflow (possible Zip Bomb)")
                    }
                    totalCompressed += compressedSize
                    totalUncompressed += uncompressedSize
                }
            }

            // 压缩比检查
            if (isCompressionRatioExceeded(
                    totalUncompressed,
                    totalCompressed,
                    maxCompressionRatio
                )) {
                val ratio = compressionRatio(totalUncompressed, totalCompressed)
                return ArchiveResult.fail(
                    "Compression ratio too high: ${ratio}x > ${maxCompressionRatio}x (possible Zip Bomb)",
                    mapOf("ratio" to ratio, "maxRatio" to maxCompressionRatio,
                        "compressed" to totalCompressed, "uncompressed" to totalUncompressed)
                )
            }

            ArchiveResult.pass(entryCount, mapOf(
                "entryCount" to entryCount,
                "compressedSize" to totalCompressed,
                "uncompressedSize" to totalUncompressed
            ))
        } catch (e: Exception) {
            ArchiveResult.fail("Invalid ZIP: ${e.message}")
        }
    }

    /**
     * 异步安全预检
     */
    @JvmStatic
    fun verifyZipSafetyAsync(
        path: String,
        maxCompressionRatio: Int = DEFAULT_MAX_COMPRESSION_RATIO,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        checkZipSlip: Boolean = true,
        onResult: (ArchiveResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(verifyZipSafety(path, maxCompressionRatio, maxEntries, checkZipSlip))
        }
    }

    // ==================== GZIP 校验 ====================

    /**
     * 验证 GZIP 文件完整性
     *
     * @param path GZIP 文件路径
     * @return [ArchiveResult]
     */
    @JvmStatic
    fun verifyGzip(path: String): ArchiveResult {
        if (!FileUtils.isFile(path)) return ArchiveResult.fail("File not found")

        return try {
            // GZIP 文件末尾有 CRC32 和 ISIZE，解压时自动校验
            java.util.zip.GZIPInputStream(FileInputStream(path)).use { gz ->
                val buffer = ByteArray(8192)
                while (gz.read(buffer) != -1) { /* 全量读取以触发 CRC 校验 */ }
            }
            ArchiveResult.pass(1, mapOf("format" to "GZIP"))
        } catch (e: java.util.zip.ZipException) {
            ArchiveResult.fail("GZIP corrupted: ${e.message}")
        } catch (e: Exception) {
            ArchiveResult.fail("Error: ${e.message}")
        }
    }

    /**
     * 异步验证 GZIP
     */
    @JvmStatic
    fun verifyGzipAsync(path: String, onResult: (ArchiveResult) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(verifyGzip(path)) }
    }

    // ==================== APK 签名验证 ====================

    /**
     * 验证 APK 签名
     *
     * @param context  Android Context
     * @param apkPath  APK 文件路径
     * @return [ApkSignatureResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = ArchiveVerifier.verifyApkSignature(context, "/sdcard/app.apk")
     * if (result.isValid) {
     *     println("签名有效: ${result.signatures}")
     * } else {
     *     println("签名无效: ${result.reason}")
     * }
     * ```
     */
    @JvmStatic
    fun verifyApkSignature(context: Context, apkPath: String): ApkSignatureResult {
        if (!FileUtils.isFile(apkPath)) {
            return ApkSignatureResult(false, reason = "File not found")
        }

        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            val archiveInfo = pm.getPackageArchiveInfo(apkPath, flags)
                ?: return ApkSignatureResult(false, reason = "Invalid APK: cannot parse package info")

            val signatures = mutableListOf<String>()

            // Android P+ 使用 signingInfo
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                archiveInfo.signingInfo?.let { signingInfo ->
                    // 历史签名
                    signingInfo.signingCertificateHistory?.forEach { cert ->
                        signatures.add(computeCertFingerprint(cert.toByteArray()))
                    }
                    // 当前签名（可能重复，去重在外部处理）
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners?.forEach { cert ->
                            signatures.add(computeCertFingerprint(cert.toByteArray()))
                        }
                    }
                }
            }

            // 兼容旧版
            if (signatures.isEmpty() && archiveInfo.signatures != null) {
                archiveInfo.signatures?.forEach { sig ->
                    signatures.add(computeCertFingerprint(sig.toByteArray()))
                }
            }

            if (signatures.isEmpty()) {
                ApkSignatureResult(false, reason = "No signature found")
            } else {
                ApkSignatureResult(
                    isValid = true,
                    signatures = signatures.distinct(),
                    packageName = archiveInfo.packageName ?: "",
                    versionName = archiveInfo.versionName ?: "",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        archiveInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        archiveInfo.versionCode.toLong()
                    }
                )
            }
        } catch (e: Exception) {
            ApkSignatureResult(false, reason = "Error: ${e.message}")
        }
    }

    /**
     * 异步验证 APK 签名
     */
    @JvmStatic
    fun verifyApkSignatureAsync(
        context: Context,
        apkPath: String,
        onResult: (ApkSignatureResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyApkSignature(context, apkPath)) }
    }

    // ==================== 压缩包内容列表 ====================

    /**
     * 列出 ZIP 文件中的所有条目
     *
     * @param path ZIP 文件路径
     * @return 条目信息列表 (name, size, compressedSize, crc)
     */
    @JvmStatic
    fun listZipEntries(path: String): List<Map<String, Any>> {
        if (!FileUtils.isFile(path)) return emptyList()
        return try {
            val entries = mutableListOf<Map<String, Any>>()
            ZipFile(path).use { zip ->
                val it = zip.entries()
                while (it.hasMoreElements()) {
                    val entry = it.nextElement()
                    entries.add(mapOf(
                        "name" to entry.name,
                        "size" to entry.size,
                        "compressedSize" to entry.compressedSize,
                        "crc" to entry.crc,
                        "isDirectory" to entry.isDirectory,
                        // ZipEntry.getLastModifiedTime() is unavailable below Android 26.
                        "lastModifiedTime" to entry.time
                    ))
                }
            }
            entries
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 异步列出条目
     */
    @JvmStatic
    fun listZipEntriesAsync(path: String, onResult: (List<Map<String, Any>>) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(listZipEntries(path)) }
    }

    // ==================== 内部方法 ====================

    /**
     * 检测路径穿越攻击
     *
     * 如果条目名称包含 ".." 或以 "/" 开头，
     * 解压时可能覆盖系统文件。
     */
    private fun isZipSlipPath(name: String): Boolean {
        // 路径穿越特征
        if (name.isEmpty() || name.indexOf('\u0000') >= 0) return true
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/')) return true
        // Windows 盘符
        if (normalized.length >= 2 && normalized[0].isLetter() && normalized[1] == ':') {
            return true
        }
        return normalized.split('/').any { it == ".." }
    }

    private fun isCompressionRatioExceeded(
        uncompressedSize: Long,
        compressedSize: Long,
        maxCompressionRatio: Int
    ): Boolean {
        if (uncompressedSize <= 0L) return false
        if (compressedSize == 0L) return true
        val quotient = uncompressedSize / compressedSize
        return quotient > maxCompressionRatio.toLong() ||
            (quotient == maxCompressionRatio.toLong() &&
                uncompressedSize % compressedSize != 0L)
    }

    private fun compressionRatio(uncompressedSize: Long, compressedSize: Long): Double {
        return if (compressedSize == 0L) Double.POSITIVE_INFINITY
        else uncompressedSize.toDouble() / compressedSize.toDouble()
    }

    private fun computeCertFingerprint(certBytes: ByteArray): String {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(java.io.ByteArrayInputStream(certBytes)) as X509Certificate
        val digest = MessageDigest.getInstance("SHA-256")
        val fingerprint = digest.digest(cert.encoded)
        return fingerprint.joinToString(":") { "%02X".format(it) }
    }
}
