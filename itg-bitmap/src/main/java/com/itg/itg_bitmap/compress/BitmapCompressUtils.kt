package com.itg.itg_bitmap.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.itg.itg_bitmap.core.BitmapUtils
import java.io.ByteArrayOutputStream

/**
 * 位图压缩工具类
 *
 * 提供多维度的 Bitmap 压缩功能，包括质量压缩、尺寸压缩、采样压缩等。
 * 可根据实际需求灵活组合不同压缩策略。
 *
 * 支持的压缩方式:
 * - 质量压缩 (保持尺寸，降低质量)
 * - 尺寸压缩 (保持质量，缩小尺寸)
 * - 采样压缩 (解码时降低分辨率)
 * - 目标大小压缩 (迭代找到最优压缩比)
 *
 * 压缩策略建议:
 * - 缩略图场景: 采样压缩 + 尺寸压缩
 * - 网络上传: 质量压缩 + 尺寸压缩
 * - 本地缓存: 尺寸压缩为主
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object BitmapCompressUtils {

    private const val DEFAULT_QUALITY = 80
    private const val MAX_ITERATIONS = 10

    // ==================== 质量压缩 ====================

    /**
     * 按质量压缩 Bitmap（不改变尺寸）
     *
     * 注意: 质量压缩只对 JPEG/WebP 等有损格式有效，PNG 为无损格式，质量参数不影响大小。
     *
     * @param bitmap  源 Bitmap
     * @param quality 压缩质量 0-100，值越小文件越小但质量越低
     * @param format  压缩格式，默认 JPEG
     * @return 压缩后的字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val compressed = BitmapCompressUtils.compressByQuality(bitmap, 60)
     * // 写入文件:
     * FileOutputStream("/path/to/output.jpg").use { it.write(compressed) }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compressByQuality(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ByteArray? {
        if (!BitmapUtils.isValid(bitmap)) return null
        val q = quality.coerceIn(0, 100)
        return try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(format, q, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 按质量压缩并返回压缩后的文件大小（字节）
     *
     * @param bitmap  源 Bitmap
     * @param quality 压缩质量 0-100
     * @param format  压缩格式
     * @return 压缩后的文件大小（字节），失败返回 -1
     */
    @JvmStatic
    @JvmOverloads
    fun estimateCompressedSize(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): Long {
        return compressByQuality(bitmap, quality, format)?.size?.toLong() ?: -1L
    }

    // ==================== 尺寸压缩 ====================

    /**
     * 按尺寸压缩 Bitmap（等比缩放，不损失质量）
     *
     * 适用于需要减小内存占用的场景。
     *
     * @param bitmap    源 Bitmap
     * @param maxWidth  最大宽度 (px)
     * @param maxHeight 最大高度 (px)
     * @param filter    是否抗锯齿，默认 true
     * @return 缩放后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 将大图缩放至不超过 1280x720
     * val resized = BitmapCompressUtils.compressBySize(bitmap, 1280, 720)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compressBySize(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int,
        filter: Boolean = true
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null
        val width = bitmap.width
        val height = bitmap.height

        // 如果原始尺寸已经小于目标尺寸，直接复制返回
        if (width <= maxWidth && height <= maxHeight) {
            return BitmapUtils.copy(bitmap)
        }

        val ratio = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, filter)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 按比例缩放 Bitmap（等比缩放）
     *
     * @param bitmap 源 Bitmap
     * @param scale  缩放比例 0.0-1.0
     * @param filter 是否抗锯齿
     * @return 缩放后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun compressByScale(
        bitmap: Bitmap,
        scale: Float,
        filter: Boolean = true
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null
        val s = scale.coerceIn(0.01f, 1f)
        val newWidth = (bitmap.width * s).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * s).toInt().coerceAtLeast(1)
        return compressBySize(bitmap, newWidth, newHeight, filter)
    }

    // ==================== 采样压缩 ====================

    /**
     * 通过采样压缩图片文件（直接解码为低分辨率，内存效率最高）
     *
     * 适用于加载超大图片的场景，从文件解码阶段就降低分辨率。
     *
     * @param imagePath 图片文件路径
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @param config    Bitmap 配置，默认 ARGB_8888
     * @return 压缩后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 直接以低分辨率解码，避免加载全尺寸图片
     * val bitmap = BitmapCompressUtils.compressBySampleSize("/sdcard/big_photo.jpg", 800, 600)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compressBySampleSize(
        imagePath: String,
        maxWidth: Int,
        maxHeight: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        if (imagePath.isBlank()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        val rawWidth = options.outWidth
        val rawHeight = options.outHeight

        options.apply {
            inSampleSize = calculateSampleSize(rawWidth, rawHeight, maxWidth, maxHeight)
            inJustDecodeBounds = false
            inPreferredConfig = config
        }

        return try {
            BitmapFactory.decodeFile(imagePath, options)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 目标大小压缩（迭代） ====================

    /**
     * 压缩至目标文件大小（通过迭代调整质量参数）
     *
     * 适用于有文件大小限制的场景（如头像上传限制 100KB）。
     * 仅 JPEG/WebP 格式通过质量迭代，PNG 通过尺寸迭代。
     *
     * @param bitmap      源 Bitmap
     * @param targetBytes 目标文件大小（字节）
     * @param format      压缩格式，默认 JPEG
     * @return 最接近目标大小的字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 压缩到 100KB 以内
     * val bytes = BitmapCompressUtils.compressToTargetSize(bitmap, 100 * 1024)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compressToTargetSize(
        bitmap: Bitmap,
        targetBytes: Long,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ByteArray? {
        if (!BitmapUtils.isValid(bitmap)) return null
        if (targetBytes <= 0) return null

        return try {
            // 先以默认质量压缩
            var quality = DEFAULT_QUALITY
            var result = compressByQuality(bitmap, quality, format) ?: return null

            if (result.size.toLong() <= targetBytes) {
                return result
            }

            // 二分查找最优质量
            var low = 0
            var high = quality
            var bestResult = result

            repeat(MAX_ITERATIONS) {
                val mid = (low + high) / 2
                result = compressByQuality(bitmap, mid, format) ?: return@repeat

                if (result.size.toLong() <= targetBytes) {
                    bestResult = result
                    low = mid + 1  // 尝试更高一点的质量
                } else {
                    high = mid - 1  // 需要更低的质量
                }
            }

            // 如果质量压缩达不到目标，考虑缩小尺寸
            if (bestResult.size.toLong() > targetBytes && format != Bitmap.CompressFormat.PNG) {
                val scale = sqrt(targetBytes.toFloat() / bestResult.size.toFloat())
                    .coerceIn(0.1f, 0.9f)
                val scaled = compressByScale(bitmap, scale) ?: return bestResult
                val scaledResult = compressByQuality(scaled, quality, format) ?: return bestResult
                return if (scaledResult.size.toLong() <= targetBytes) {
                    scaledResult
                } else {
                    compressToTargetSize(scaled, targetBytes, format) ?: bestResult
                }
            }

            bestResult
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 压缩至目标文件大小，并返回压缩详情
     *
     * @param bitmap      源 Bitmap
     * @param targetKB    目标文件大小（KB）
     * @param format      压缩格式
     * @return 包含压缩后数据和统计信息的 Map，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun compressToTargetSizeWithInfo(
        bitmap: Bitmap,
        targetKB: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): Map<String, Any>? {
        val originalSize = BitmapUtils.getSizeKB(bitmap)
        val result = compressToTargetSize(bitmap, targetKB * 1024L, format) ?: return null

        return mapOf(
            "data" to result,
            "originalSizeKB" to originalSize,
            "compressedSizeKB" to (result.size / 1024f),
            "compressionRatio" to (result.size.toFloat() / (bitmap.byteCount.toFloat())),
            "targetKB" to targetKB
        )
    }

    // ==================== 组合压缩 ====================

    /**
     * 组合压缩：先缩小尺寸再降低质量
     *
     * 这是最实用的压缩方法，兼顾内存和文件大小。
     *
     * @param bitmap    源 Bitmap
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @param quality   压缩质量 0-100
     * @param format    压缩格式，默认 JPEG
     * @return 压缩后的字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 常用组合：缩放到 1280x720 + JPEG 80% 质量
     * val bytes = BitmapCompressUtils.compress(
     *     bitmap,
     *     maxWidth = 1280,
     *     maxHeight = 720,
     *     quality = 80
     * )
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compress(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int = DEFAULT_QUALITY,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ByteArray? {
        if (!BitmapUtils.isValid(bitmap)) return null

        // 先缩小尺寸
        val resized = compressBySize(bitmap, maxWidth, maxHeight) ?: return null

        // 再质量压缩
        return compressByQuality(resized, quality, format)
    }

    /**
     * 智能压缩 — 自动选择最佳压缩策略
     *
     * 规则:
     * - 图片 <= 目标尺寸: 仅质量压缩
     * - 图片 > 目标尺寸: 先缩小尺寸再质量压缩
     * - 超大图片 (>2倍目标): 先采样再质量压缩
     *
     * @param bitmap     源 Bitmap
     * @param maxWidth   最大宽度
     * @param maxHeight  最大高度
     * @param maxSizeKB  最大文件大小（KB），0 表示不限制
     * @param format     输出格式
     * @return 压缩后的字节数组，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun smartCompress(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int,
        maxSizeKB: Long = 0,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ByteArray? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val width = bitmap.width
        val height = bitmap.height

        // 智能选择压缩策略
        val resized = when {
            width <= maxWidth && height <= maxHeight -> BitmapUtils.copy(bitmap)
            width > maxWidth * 2 || height > maxHeight * 2 -> {
                // 超大图片: 先进行较大的缩放
                val scale = minOf(maxWidth * 2f / width, maxHeight * 2f / height)
                compressByScale(bitmap, scale)?.let {
                    compressBySize(it, maxWidth, maxHeight)
                }
            }
            else -> compressBySize(bitmap, maxWidth, maxHeight)
        } ?: return null

        // 质量压缩
        val quality = DEFAULT_QUALITY
        val result = compressByQuality(resized, quality, format)

        // 如果指定了文件大小限制，继续迭代
        return if (maxSizeKB > 0 && result != null && result.size > maxSizeKB * 1024) {
            compressToTargetSize(resized, maxSizeKB * 1024, format) ?: result
        } else {
            result
        }
    }

    // ==================== 采样率计算 ====================

    /**
     * 计算最佳采样率 (2的幂次方)
     *
     * @param rawWidth   原始宽度
     * @param rawHeight  原始高度
     * @param maxWidth   最大宽度
     * @param maxHeight  最大高度
     * @return 采样率
     */
    @JvmStatic
    fun calculateSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawWidth <= 0 || rawHeight <= 0) return inSampleSize
        if (rawWidth <= maxWidth && rawHeight <= maxHeight) return inSampleSize

        val halfWidth = rawWidth / 2
        val halfHeight = rawHeight / 2

        while ((halfWidth / inSampleSize) >= maxWidth &&
            (halfHeight / inSampleSize) >= maxHeight
        ) {
            inSampleSize *= 2
        }

        return inSampleSize
    }
}
