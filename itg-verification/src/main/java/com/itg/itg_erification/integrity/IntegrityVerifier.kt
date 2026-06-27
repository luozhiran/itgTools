package com.itg.itg_erification.integrity

import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.io.IOException
import java.util.concurrent.Future

/**
 * 文件完整性校验器
 *
 * 提供多维度文件完整性检查：存在性、非空、尺寸范围、
 * 文件头 Magic Number、文件类型、可读性、权限等。
 *
 * 适用于下载后验证、文件传输后确认、文件系统健康检查等场景。
 *
 * @author ITG Team
 * @since 1.0.0
 */
object IntegrityVerifier {

    /**
     * 完整性校验结果
     *
     * @property isValid 是否通过
     * @property failedChecks 未通过的检查项名称列表
     * @property details 各检查项的详细结果
     */
    data class IntegrityResult(
        val isValid: Boolean,
        val failedChecks: List<String> = emptyList(),
        val details: Map<String, Any> = emptyMap()
    ) {
        override fun toString(): String {
            return if (isValid) {
                "PASS (${details.size} checks)"
            } else {
                "FAIL: ${failedChecks.joinToString(", ")}"
            }
        }

        companion object {
            fun pass(details: Map<String, Any> = emptyMap()) = IntegrityResult(true, emptyList(), details)
            fun fail(checks: List<String>, details: Map<String, Any> = emptyMap()) = IntegrityResult(false, checks, details)
        }
    }

    // ==================== 基础检查 ====================

    /**
     * 检查文件是否存在
     */
    @JvmStatic
    fun verifyExists(path: String): IntegrityResult {
        val exists = FileUtils.exists(path)
        return if (exists) IntegrityResult.pass(mapOf("exists" to true))
        else IntegrityResult.fail(listOf("exists"), mapOf("exists" to false))
    }

    /**
     * 检查文件非空（存在且 size > 0）
     */
    @JvmStatic
    fun verifyNotEmpty(path: String): IntegrityResult {
        val checks = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()

        details["exists"] = FileUtils.exists(path)
        if (!FileUtils.exists(path)) checks.add("exists")

        val size = FileUtils.getSize(path)
        details["size"] = size
        if (size <= 0) checks.add("notEmpty")

        return if (checks.isEmpty()) IntegrityResult.pass(details)
        else IntegrityResult.fail(checks, details)
    }

    /**
     * 检查文件尺寸 ≥ 最小值
     *
     * @param path     文件路径
     * @param minBytes 最小字节数
     */
    @JvmStatic
    fun verifyMinSize(path: String, minBytes: Long): IntegrityResult {
        val size = FileUtils.getSize(path)
        val details = mapOf("size" to size, "minBytes" to minBytes, "exists" to FileUtils.exists(path))

        if (!FileUtils.exists(path)) return IntegrityResult.fail(listOf("exists"), details)
        if (size < minBytes) return IntegrityResult.fail(listOf("minSize"), details)
        return IntegrityResult.pass(details)
    }

    /**
     * 检查文件尺寸 ≤ 最大值
     */
    @JvmStatic
    fun verifyMaxSize(path: String, maxBytes: Long): IntegrityResult {
        val size = FileUtils.getSize(path)
        val details = mapOf("size" to size, "maxBytes" to maxBytes, "exists" to FileUtils.exists(path))

        if (!FileUtils.exists(path)) return IntegrityResult.fail(listOf("exists"), details)
        if (size > maxBytes) return IntegrityResult.fail(listOf("maxSize"), details)
        return IntegrityResult.pass(details)
    }

    /**
     * 检查文件尺寸在指定范围内
     *
     * @param path     文件路径
     * @param minBytes 最小字节数（含），-1 表示不限制
     * @param maxBytes 最大字节数（含），-1 表示不限制
     *
     * 使用示例:
     * ```kotlin
     * // 验证下载的图片应该在 1KB ~ 10MB 之间
     * val result = IntegrityVerifier.verifySizeRange(
     *     "/sdcard/photo.jpg", minBytes = 1024, maxBytes = 10 * 1024 * 1024)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun verifySizeRange(
        path: String,
        minBytes: Long = -1L,
        maxBytes: Long = -1L
    ): IntegrityResult {
        val checks = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()

        val exists = FileUtils.exists(path)
        details["exists"] = exists
        if (!exists) {
            checks.add("exists")
            return IntegrityResult.fail(checks, details)
        }

        val size = FileUtils.getSize(path)
        details["size"] = size
        details["minBytes"] = minBytes
        details["maxBytes"] = maxBytes

        if (minBytes >= 0 && size < minBytes) checks.add("minSize=$minBytes")
        if (maxBytes >= 0 && size > maxBytes) checks.add("maxSize=$maxBytes")

        return if (checks.isEmpty()) IntegrityResult.pass(details)
        else IntegrityResult.fail(checks, details)
    }

    // ==================== 文件头校验 ====================

    /**
     * 校验文件头 Magic Number
     *
     * 适用于验证文件类型是否真实（避免仅靠扩展名误判）。
     *
     * @param path       文件路径
     * @param magicBytes 期望的文件头字节
     * @param offset     在文件中的偏移，默认 0
     * @return [IntegrityResult]
     *
     * 使用示例:
     * ```kotlin
     * // 验证 PNG 文件头: 89 50 4E 47
     * val result = IntegrityVerifier.verifyFileHeader(
     *     "/sdcard/photo.png",
     *     byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun verifyFileHeader(
        path: String,
        magicBytes: ByteArray,
        offset: Int = 0
    ): IntegrityResult {
        if (!FileUtils.isFile(path)) {
            return IntegrityResult.fail(listOf("exists"), mapOf("exists" to false))
        }
        if (offset < 0 || magicBytes.isEmpty()) {
            return IntegrityResult.fail(listOf("invalidArguments"))
        }

        return try {
            val file = File(path)
            val headerSize = offset.toLong() + magicBytes.size.toLong()
            if (file.length() < headerSize) {
                return IntegrityResult.fail(
                    listOf("headerSize"),
                    mapOf("fileSize" to file.length(), "requiredHeaderSize" to headerSize)
                )
            }

            val actual = ByteArray(magicBytes.size)
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset.toLong())
                input.readFully(actual)
            }

            val match = actual.contentEquals(magicBytes)
            val details = mapOf(
                "expected" to magicBytes.joinToString(" ") { "%02X".format(it) },
                "actual" to actual.joinToString(" ") { "%02X".format(it) },
                "offset" to offset
            )

            if (match) IntegrityResult.pass(details)
            else IntegrityResult.fail(listOf("headerMagic"), details)
        } catch (e: IOException) {
            IntegrityResult.fail(listOf("readError"), mapOf("error" to e.message.toString()))
        }
    }

    /**
     * 异步文件头校验
     */
    @JvmStatic
    fun verifyFileHeaderAsync(
        path: String,
        magicBytes: ByteArray,
        offset: Int = 0,
        onResult: (IntegrityResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verifyFileHeader(path, magicBytes, offset)) }
    }

    // ==================== 文件类型常见 Magic Number ====================

    /** PNG: 89 50 4E 47 0D 0A 1A 0A */
    val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    /** JPEG: FF D8 FF */
    val JPEG_HEADER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    /** GIF: 47 49 46 38 */
    val GIF_HEADER = byteArrayOf(0x47, 0x49, 0x46, 0x38)
    /** PDF: 25 50 44 46 */
    val PDF_HEADER = byteArrayOf(0x25, 0x50, 0x44, 0x46)
    /** ZIP/APK/JAR/DOCX: 50 4B 03 04 */
    val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    /** GZIP: 1F 8B */
    val GZIP_HEADER = byteArrayOf(0x1F.toByte(), 0x8B.toByte())
    /** MP3 (ID3): 49 44 33 */
    val MP3_HEADER = byteArrayOf(0x49, 0x44, 0x33)
    /** MP4: 00 00 00 xx 66 74 79 70 */
    val MP4_HEADER_OFFSET_4 = byteArrayOf(0x66, 0x74, 0x79, 0x70)  // "ftyp" at offset 4
    /** ELF (Linux binary): 7F 45 4C 46 */
    val ELF_HEADER = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
    /** DEX (Android): 64 65 78 0A */
    val DEX_HEADER = byteArrayOf(0x64, 0x65, 0x78, 0x0A)

    // ==================== 高级完整性检查 ====================

    /**
     * 多重完整性检查（一站式）
     *
     * @param path      文件路径
     * @param checks   需要执行的检查项
     * @param minBytes 最小尺寸（0 表示不检查）
     * @param maxBytes 最大尺寸（0 表示不检查）
     * @return [IntegrityResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = IntegrityVerifier.verify("/sdcard/download.apk",
     *     checks = listOf("exists", "notEmpty", "minSize", "maxSize"),
     *     minBytes = 1024 * 1024,   // ≥ 1MB
     *     maxBytes = 100 * 1024 * 1024)  // ≤ 100MB
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun verify(
        path: String,
        checks: List<String> = listOf("exists", "notEmpty"),
        minBytes: Long = 0L,
        maxBytes: Long = 0L
    ): IntegrityResult {
        val failed = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()

        val exists = FileUtils.exists(path)
        details["exists"] = exists

        if ("exists" in checks && !exists) {
            failed.add("exists")
            // 如果不存在，后续检查无意义
            details["minBytes"] = minBytes
            details["maxBytes"] = maxBytes
            return IntegrityResult.fail(failed, details)
        }

        val size = FileUtils.getSize(path)
        details["size"] = size

        if ("notEmpty" in checks && size <= 0L) failed.add("notEmpty")
        if ("minSize" in checks && minBytes > 0L && size < minBytes) failed.add("minSize($minBytes)")
        if ("maxSize" in checks && maxBytes > 0L && size > maxBytes) failed.add("maxSize($maxBytes)")
        if ("isFile" in checks && !FileUtils.isFile(path)) failed.add("isFile")
        if ("isDirectory" in checks && !FileUtils.isDirectory(path)) failed.add("isDirectory")

        details["minBytes"] = minBytes
        details["maxBytes"] = maxBytes

        return if (failed.isEmpty()) IntegrityResult.pass(details)
        else IntegrityResult.fail(failed, details)
    }

    /**
     * 异步多重检查
     */
    @JvmStatic
    fun verifyAsync(
        path: String,
        checks: List<String> = listOf("exists", "notEmpty"),
        minBytes: Long = 0L,
        maxBytes: Long = 0L,
        onResult: (IntegrityResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(verify(path, checks, minBytes, maxBytes)) }
    }

    /**
     * 检查文件是否可读
     */
    @JvmStatic
    fun verifyReadable(path: String): IntegrityResult {
        if (!FileUtils.exists(path)) return IntegrityResult.fail(listOf("exists"))
        val readable = File(path).canRead()
        return if (readable) IntegrityResult.pass(mapOf("readable" to true))
        else IntegrityResult.fail(listOf("readable"), mapOf("readable" to false))
    }

    /**
     * 检查文件是否可写
     */
    @JvmStatic
    fun verifyWritable(path: String): IntegrityResult {
        if (!FileUtils.isFile(path)) return IntegrityResult.fail(listOf("isFile"), mapOf("isFile" to false))
        val writable = File(path).canWrite()
        return if (writable) IntegrityResult.pass(mapOf("writable" to true))
        else IntegrityResult.fail(listOf("writable"), mapOf("writable" to false))
    }

    /**
     * 检查文件是否可执行
     */
    @JvmStatic
    fun verifyExecutable(path: String): IntegrityResult {
        if (!FileUtils.isFile(path)) return IntegrityResult.fail(listOf("isFile"))
        val executable = File(path).canExecute()
        return if (executable) IntegrityResult.pass(mapOf("executable" to true))
        else IntegrityResult.fail(listOf("executable"), mapOf("executable" to false))
    }
}
