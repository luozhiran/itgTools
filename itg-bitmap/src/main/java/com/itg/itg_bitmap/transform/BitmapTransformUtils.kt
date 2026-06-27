package com.itg.itg_bitmap.transform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * 位图变换工具类
 *
 * 提供 Bitmap 的缩放、旋转、翻转、裁剪、倾斜等几何变换操作。
 * 所有变换方法返回新的 Bitmap，不会修改原图。
 *
 * 支持的变换:
 * - 缩放 (等比/非等比)
 * - 旋转 (任意角度)
 * - 翻转 (水平/垂直)
 * - 裁剪 (指定区域/居中裁剪)
 * - 倾斜 (X/Y 轴)
 *
 * @author ITG Team
 * @since 1.0.0
 */
object BitmapTransformUtils {

    // ==================== 缩放 ====================

    /**
     * 将 Bitmap 缩放至指定尺寸
     *
     * @param bitmap  源 Bitmap
     * @param newWidth  目标宽度 (px)
     * @param newHeight 目标高度 (px)
     * @param filter   是否使用双线性过滤（抗锯齿），默认 true
     * @return 缩放后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val scaled = BitmapTransformUtils.scale(bitmap, 400, 300)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun scale(
        bitmap: Bitmap,
        newWidth: Int,
        newHeight: Int,
        filter: Boolean = true
    ): Bitmap? {
        if (newWidth <= 0 || newHeight <= 0) return null
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
     * @param factor 缩放比例 (> 0)，1.0 为原始大小，0.5 缩小一半，2.0 放大一倍
     * @param filter 是否使用双线性过滤，默认 true
     * @return 缩放后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val halfSize = BitmapTransformUtils.scaleByFactor(bitmap, 0.5f)
     * val doubleSize = BitmapTransformUtils.scaleByFactor(bitmap, 2.0f)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun scaleByFactor(
        bitmap: Bitmap,
        factor: Float,
        filter: Boolean = true
    ): Bitmap? {
        if (factor <= 0) return null
        val newWidth = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return scale(bitmap, newWidth, newHeight, filter)
    }

    /**
     * 缩放 Bitmap 到指定最大宽度（高度等比缩放）
     *
     * @param bitmap   源 Bitmap
     * @param maxWidth 最大宽度 (px)
     * @param filter   是否使用双线性过滤，默认 true
     * @return 缩放后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun scaleToWidth(
        bitmap: Bitmap,
        maxWidth: Int,
        filter: Boolean = true
    ): Bitmap? {
        if (maxWidth <= 0) return null
        val ratio = maxWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return scale(bitmap, maxWidth, newHeight, filter)
    }

    /**
     * 缩放 Bitmap 到指定最大高度（宽度等比缩放）
     *
     * @param bitmap    源 Bitmap
     * @param maxHeight 最大高度 (px)
     * @param filter    是否使用双线性过滤，默认 true
     * @return 缩放后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun scaleToHeight(
        bitmap: Bitmap,
        maxHeight: Int,
        filter: Boolean = true
    ): Bitmap? {
        if (maxHeight <= 0) return null
        val ratio = maxHeight.toFloat() / bitmap.height
        val newWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        return scale(bitmap, newWidth, maxHeight, filter)
    }

    /**
     * 缩放 Bitmap 以适配指定边界（等比缩放，不超出边界）
     *
     * @param bitmap       源 Bitmap
     * @param boundWidth   边界宽度 (px)
     * @param boundHeight  边界高度 (px)
     * @param filter       是否使用双线性过滤，默认 true
     * @return 缩放后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 缩放以适配 800x600 的容器，保持宽高比
     * val fitted = BitmapTransformUtils.scaleToFit(bitmap, 800, 600)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun scaleToFit(
        bitmap: Bitmap,
        boundWidth: Int,
        boundHeight: Int,
        filter: Boolean = true
    ): Bitmap? {
        if (boundWidth <= 0 || boundHeight <= 0) return null
        val ratio = minOf(
            boundWidth.toFloat() / bitmap.width,
            boundHeight.toFloat() / bitmap.height
        )
        return scaleByFactor(bitmap, ratio, filter)
    }

    // ==================== 旋转 ====================

    /**
     * 旋转 Bitmap
     *
     * @param bitmap  源 Bitmap
     * @param degrees 旋转角度（顺时针为正）
     * @param pivotX  旋转中心 X，默认图片中心
     * @param pivotY  旋转中心 Y，默认图片中心
     * @param filter  是否使用双线性过滤，默认 true
     * @return 旋转后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val rotated90 = BitmapTransformUtils.rotate(bitmap, 90f)
     * val rotated45 = BitmapTransformUtils.rotate(bitmap, 45f)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun rotate(
        bitmap: Bitmap,
        degrees: Float,
        pivotX: Float = bitmap.width / 2f,
        pivotY: Float = bitmap.height / 2f,
        filter: Boolean = true
    ): Bitmap? {
        val matrix = Matrix().apply {
            postRotate(degrees, pivotX, pivotY)
        }
        return applyMatrix(bitmap, matrix, filter)
    }

    // ==================== 翻转 ====================

    /**
     * 水平翻转（镜像）
     *
     * @param bitmap 源 Bitmap
     * @return 水平翻转后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val mirrored = BitmapTransformUtils.flipHorizontal(bitmap)
     * ```
     */
    @JvmStatic
    fun flipHorizontal(bitmap: Bitmap): Bitmap? {
        val matrix = Matrix().apply {
            preScale(-1f, 1f)
        }
        return applyMatrix(bitmap, matrix)
    }

    /**
     * 垂直翻转
     *
     * @param bitmap 源 Bitmap
     * @return 垂直翻转后的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun flipVertical(bitmap: Bitmap): Bitmap? {
        val matrix = Matrix().apply {
            preScale(1f, -1f)
        }
        return applyMatrix(bitmap, matrix)
    }

    // ==================== 裁剪 ====================

    /**
     * 裁剪 Bitmap 指定区域
     *
     * @param bitmap 源 Bitmap
     * @param x      裁剪起始 X 坐标
     * @param y      裁剪起始 Y 坐标
     * @param width  裁剪宽度
     * @param height 裁剪高度
     * @return 裁剪后的 Bitmap，参数越界时自动修正并返回修正后的结果，完全无效时返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 从 (100, 50) 开始裁剪 300x200 的区域
     * val cropped = BitmapTransformUtils.crop(bitmap, 100, 50, 300, 200)
     * ```
     */
    @JvmStatic
    fun crop(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val safeX = x.coerceIn(0, bitmap.width - 1)
        val safeY = y.coerceIn(0, bitmap.height - 1)
        val safeWidth = width.coerceAtMost(bitmap.width - safeX)
        val safeHeight = height.coerceAtMost(bitmap.height - safeY)

        if (safeWidth <= 0 || safeHeight <= 0) return null

        return try {
            Bitmap.createBitmap(bitmap, safeX, safeY, safeWidth, safeHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 居中裁剪 Bitmap 为指定尺寸
     *
     * 适用于将任意尺寸图片裁剪为固定宽高比（如头像裁剪）。
     *
     * @param bitmap       源 Bitmap
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @return 居中裁剪后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 居中裁剪为 200x200 的正方形
     * val centered = BitmapTransformUtils.centerCrop(bitmap, 200, 200)
     * ```
     */
    @JvmStatic
    fun centerCrop(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null

        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        val srcRatio = srcWidth.toFloat() / srcHeight

        val cropWidth: Int
        val cropHeight: Int
        var startX = 0
        var startY = 0

        if (srcRatio > targetRatio) {
            // 源图更宽，裁剪宽度
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetRatio).toInt()
            startX = (srcWidth - cropWidth) / 2
        } else {
            // 源图更高，裁剪高度
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetRatio).toInt()
            startY = (srcHeight - cropHeight) / 2
        }

        val cropped = crop(bitmap, startX, startY, cropWidth, cropHeight) ?: return null
        return scale(cropped, targetWidth, targetHeight)
    }

    /**
     * 填充裁剪 — 源图完全覆盖目标区域，超出部分被裁掉
     *
     * 类似于 ImageView 的 centerCrop scaleType。
     *
     * @param bitmap       源 Bitmap
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @return 填充裁剪后的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun fillCrop(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null

        val scaleFactor = maxOf(
            targetWidth.toFloat() / bitmap.width,
            targetHeight.toFloat() / bitmap.height
        )
        val scaledWidth = (bitmap.width * scaleFactor).toInt()
        val scaledHeight = (bitmap.height * scaleFactor).toInt()
        val scaled = scale(bitmap, scaledWidth, scaledHeight) ?: return null

        return centerCrop(scaled, targetWidth, targetHeight)
    }

    // ==================== 倾斜 ====================

    /**
     * 对 Bitmap 进行 X 轴倾斜变换
     *
     * @param bitmap 源 Bitmap
     * @param skewX  X 轴倾斜角度（度），正值为向右倾斜
     * @param filter 是否使用双线性过滤，默认 true
     * @return 倾斜后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun skewX(bitmap: Bitmap, skewX: Float, filter: Boolean = true): Bitmap? {
        val matrix = Matrix().apply {
            postSkew(skewX.tan(), 0f)
        }
        return applyMatrix(bitmap, matrix, filter)
    }

    /**
     * 对 Bitmap 进行 Y 轴倾斜变换
     *
     * @param bitmap 源 Bitmap
     * @param skewY  Y 轴倾斜角度（度），正值为向下倾斜
     * @param filter 是否使用双线性过滤，默认 true
     * @return 倾斜后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun skewY(bitmap: Bitmap, skewY: Float, filter: Boolean = true): Bitmap? {
        val matrix = Matrix().apply {
            postSkew(0f, skewY.tan())
        }
        return applyMatrix(bitmap, matrix, filter)
    }

    // ==================== 通用 Matrix 变换 ====================

    /**
     * 使用自定义 [Matrix] 对 Bitmap 进行变换
     *
     * 这是最通用的变换方法，适用于需要组合多种变换的场景。
     *
     * @param bitmap 源 Bitmap
     * @param matrix 变换矩阵
     * @param filter 是否使用双线性过滤，默认 true
     * @return 变换后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val matrix = Matrix().apply {
     *     postScale(0.5f, 0.5f)
     *     postRotate(45f)
     * }
     * val result = BitmapTransformUtils.applyMatrix(bitmap, matrix)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun applyMatrix(
        bitmap: Bitmap,
        matrix: Matrix,
        filter: Boolean = true
    ): Bitmap? {
        return try {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                matrix, filter
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
