package com.itg.itg_bitmap.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

/**
 * 位图基础操作工具类
 *
 * 提供 Bitmap 的创建、复制、转换、信息获取等基础操作。
 * 所有方法均为静态方法，可直接调用。
 *
 * @author ITG Team
 * @since 1.0.0
 */
object BitmapUtils {

    private const val DEFAULT_COMPRESS_QUALITY = 100

    // ==================== 创建 Bitmap ====================

    /**
     * 创建一个指定宽高的空白 Bitmap
     *
     * @param width  目标宽度 (px)
     * @param height 目标高度 (px)
     * @param config 位图配置，默认为 [Bitmap.Config.ARGB_8888]
     * @return 创建的空白 Bitmap，若参数无效则返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bitmap = BitmapUtils.createBitmap(1080, 1920)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun createBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从字节数组创建 Bitmap
     *
     * @param data    图片字节数据
     * @param options 解码选项，可选
     * @return 解码后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun createBitmapFromByteArray(
        data: ByteArray,
        options: BitmapFactory.Options? = null
    ): Bitmap? {
        if (data.isEmpty()) return null
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 复制 Bitmap ====================

    /**
     * 复制一个 Bitmap
     *
     * @param source     源 Bitmap
     * @param config     目标位图配置，默认为源 Bitmap 的配置
     * @param isMutable  是否可变，默认为 true
     * @return 复制的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val copy = BitmapUtils.copy(originalBitmap)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copy(
        source: Bitmap,
        config: Bitmap.Config? = null,
        isMutable: Boolean = true
    ): Bitmap? {
        if (!isValid(source)) return null
        val targetConfig = config ?: source.config ?: Bitmap.Config.ARGB_8888
        return try {
            source.copy(targetConfig, isMutable)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 转换 ====================

    /**
     * 将 Bitmap 转换为字节数组
     *
     * @param bitmap  源 Bitmap
     * @param format  压缩格式，默认 JPEG ([Bitmap.CompressFormat.JPEG])
     * @param quality 压缩质量 0-100，默认 100
     * @return 字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bytes = BitmapUtils.toByteArray(bitmap, Bitmap.CompressFormat.PNG, 100)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun toByteArray(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = DEFAULT_COMPRESS_QUALITY
    ): ByteArray? {
        if (!isValid(bitmap)) return null
        val qualityClamped = quality.coerceIn(0, 100)
        return try {
            ByteArrayOutputStream().use { stream ->
                if (!bitmap.compress(format, qualityClamped, stream)) return null
                stream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 信息获取 ====================

    /**
     * 检查 Bitmap 是否有效（非空且未回收）
     *
     * @param bitmap 待检查的 Bitmap
     * @return true 表示有效，false 表示无效
     */
    @JvmStatic
    fun isValid(bitmap: Bitmap?): Boolean {
        return bitmap != null && !bitmap.isRecycled
    }

    /**
     * 获取 Bitmap 占用的内存大小（字节）
     *
     * @param bitmap 源 Bitmap
     * @return 内存大小（字节），无效时返回 -1
     */
    @JvmStatic
    fun getByteCount(bitmap: Bitmap?): Long {
        return if (isValid(bitmap)) {
            bitmap!!.byteCount.toLong()
        } else {
            -1L
        }
    }

    /**
     * 获取 Bitmap 占用的内存大小（KB）
     *
     * @param bitmap 源 Bitmap
     * @return 内存大小（KB），无效时返回 -1f
     */
    @JvmStatic
    fun getSizeKB(bitmap: Bitmap?): Float {
        val bytes = getByteCount(bitmap)
        return if (bytes >= 0) bytes / 1024f else -1f
    }

    /**
     * 获取 Bitmap 占用的内存大小（MB）
     *
     * @param bitmap 源 Bitmap
     * @return 内存大小（MB），无效时返回 -1f
     */
    @JvmStatic
    fun getSizeMB(bitmap: Bitmap?): Float {
        val kb = getSizeKB(bitmap)
        return if (kb >= 0) kb / 1024f else -1f
    }

    /**
     * 获取 Bitmap 的宽高比 (宽/高)
     *
     * @param bitmap 源 Bitmap
     * @return 宽高比，无效时返回 -1f
     */
    @JvmStatic
    fun getAspectRatio(bitmap: Bitmap?): Float {
        return if (isValid(bitmap) && bitmap!!.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else {
            -1f
        }
    }

    /**
     * 判断 Bitmap 是否为正方形
     *
     * @param bitmap 源 Bitmap
     * @return true 表示正方形
     */
    @JvmStatic
    fun isSquare(bitmap: Bitmap?): Boolean {
        return isValid(bitmap) && bitmap!!.width == bitmap.height
    }

    /**
     * 判断 Bitmap 是否为横屏方向 (宽 > 高)
     *
     * @param bitmap 源 Bitmap
     * @return true 表示横向
     */
    @JvmStatic
    fun isLandscape(bitmap: Bitmap?): Boolean {
        return isValid(bitmap) && bitmap!!.width > bitmap.height
    }

    /**
     * 判断 Bitmap 是否为竖屏方向 (高 > 宽)
     *
     * @param bitmap 源 Bitmap
     * @return true 表示纵向
     */
    @JvmStatic
    fun isPortrait(bitmap: Bitmap?): Boolean {
        return isValid(bitmap) && bitmap!!.height > bitmap.width
    }

    /**
     * 获取 Bitmap 的简要信息描述
     *
     * @param bitmap 源 Bitmap
     * @return 信息字符串，如 "Bitmap(1080x1920, ARGB_8888, 8.00MB)"
     */
    @JvmStatic
    fun getInfo(bitmap: Bitmap?): String {
        if (!isValid(bitmap)) return "Bitmap(invalid)"
        val b = bitmap!!
        return "Bitmap(${b.width}x${b.height}, ${b.config ?: "unknown"}, %.2fMB)".format(getSizeMB(b))
    }

    // ==================== 像素操作 ====================

    /**
     * 获取指定坐标的像素颜色值
     *
     * @param bitmap 源 Bitmap
     * @param x      X 坐标
     * @param y      Y 坐标
     * @return ARGB 颜色值，失败返回 0
     */
    @JvmStatic
    fun getPixel(bitmap: Bitmap?, x: Int, y: Int): Int {
        if (!isValid(bitmap)) return 0
        if (x < 0 || x >= bitmap!!.width || y < 0 || y >= bitmap.height) return 0
        return bitmap.getPixel(x, y)
    }

    /**
     * 设置指定坐标的像素颜色值（需要 Bitmap 为 mutable）
     *
     * @param bitmap 源 Bitmap（必须为 mutable）
     * @param x      X 坐标
     * @param y      Y 坐标
     * @param color  ARGB 颜色值
     * @return true 表示设置成功
     */
    @JvmStatic
    fun setPixel(bitmap: Bitmap?, x: Int, y: Int, color: Int): Boolean {
        if (!isValid(bitmap) || !bitmap!!.isMutable) return false
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return false
        bitmap.setPixel(x, y, color)
        return true
    }

    /**
     * 批量获取像素数组
     *
     * @param bitmap 源 Bitmap
     * @return 像素数组 (IntArray)，失败返回空数组
     */
    @JvmStatic
    fun getPixels(bitmap: Bitmap?): IntArray {
        if (!isValid(bitmap)) return IntArray(0)
        val b = bitmap!!
        val pixels = IntArray(b.width * b.height)
        b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
        return pixels
    }

    /**
     * 批量设置像素数组（需要 Bitmap 为 mutable）
     *
     * @param bitmap 目标 Bitmap（必须为 mutable）
     * @param pixels 像素数组
     * @return true 表示设置成功
     */
    @JvmStatic
    fun setPixels(bitmap: Bitmap?, pixels: IntArray): Boolean {
        if (!isValid(bitmap) || !bitmap!!.isMutable) return false
        if (pixels.isEmpty()) return false
        val b = bitmap
        b.setPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
        return true
    }
}
