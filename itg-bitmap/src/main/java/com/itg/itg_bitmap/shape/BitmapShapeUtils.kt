package com.itg.itg_bitmap.shape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Region
import com.itg.itg_bitmap.core.BitmapUtils
import com.itg.itg_bitmap.transform.BitmapTransformUtils

/**
 * 位图形状处理工具类
 *
 * 提供将 Bitmap 裁剪为各种形状的功能，包括圆形、圆角、椭圆、自定义路径等。
 * 所有方法返回裁剪/绘制为新形状的 Bitmap。
 *
 * 支持的形状:
 * - 圆形 (Circle)
 * - 圆角矩形 (Round Rect)
 * - 椭圆 (Oval)
 * - 自定义路径 (Custom Path)
 * - 带边框的形状
 * - 叠加形状的图片合成
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object BitmapShapeUtils {

    // ==================== 圆形 ====================

    /**
     * 将 Bitmap 裁剪为圆形
     *
     * 以图片中心为圆心，取短边的一半为半径。
     * 适用于制作圆形头像等场景。
     *
     * @param bitmap  源 Bitmap
     * @param recycleSource 是否回收源 Bitmap，默认 false
     * @return 圆形裁剪后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * imageView.setImageBitmap(BitmapShapeUtils.toCircle(bitmap))
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun toCircle(
        bitmap: Bitmap,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2f
        val y = (bitmap.height - size) / 2f

        return try {
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 绘制圆形遮罩
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, -x, -y, paint)

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 Bitmap 裁剪为圆形（带边框）
     *
     * @param bitmap        源 Bitmap
     * @param borderWidth   边框宽度 (px)
     * @param borderColor   边框颜色
     * @param recycleSource 是否回收源 Bitmap
     * @return 带边框的圆形 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val avatar = BitmapShapeUtils.toCircleWithBorder(bitmap, 4, Color.WHITE)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun toCircleWithBorder(
        bitmap: Bitmap,
        borderWidth: Int,
        borderColor: Int = Color.WHITE,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val size = minOf(bitmap.width, bitmap.height)
        val totalSize = size + borderWidth * 2
        val x = (bitmap.width - size) / 2f
        val y = (bitmap.height - size) / 2f

        return try {
            val output = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val radius = totalSize / 2f

            // 绘制边框
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(radius, radius, radius, borderPaint)

            // 绘制图片
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            canvas.drawCircle(radius, radius, radius - borderWidth, imagePaint)

            imagePaint.xfermode = null
            canvas.drawBitmap(
                bitmap,
                borderWidth - x,
                borderWidth - y,
                imagePaint
            )

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 圆角矩形 ====================

    /**
     * 将 Bitmap 裁剪为圆角矩形
     *
     * @param bitmap        源 Bitmap
     * @param cornerRadius  圆角半径 (px)
     * @param recycleSource 是否回收源 Bitmap
     * @return 圆角裁剪后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val rounded = BitmapShapeUtils.roundCorners(bitmap, 20f)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun roundCorners(
        bitmap: Bitmap,
        cornerRadius: Float,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null
        if (cornerRadius <= 0) return BitmapUtils.copy(bitmap)

        val width = bitmap.width
        val height = bitmap.height
        val radius = cornerRadius.coerceAtMost(minOf(width, height) / 2f)

        return try {
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

            // 绘制圆角矩形遮罩
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 Bitmap 裁剪为圆角矩形（四个角分别设置）
     *
     * @param bitmap         源 Bitmap
     * @param topLeftRadius  左上角圆角半径
     * @param topRightRadius 右上角圆角半径
     * @param bottomLeftRadius  左下角圆角半径
     * @param bottomRightRadius 右下角圆角半径
     * @param recycleSource  是否回收源 Bitmap
     * @return 圆角裁剪后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun roundCornersIndividual(
        bitmap: Bitmap,
        topLeftRadius: Float = 0f,
        topRightRadius: Float = 0f,
        bottomLeftRadius: Float = 0f,
        bottomRightRadius: Float = 0f,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val width = bitmap.width
        val height = bitmap.height

        return try {
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val path = Path()
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val radii = floatArrayOf(
                topLeftRadius, topLeftRadius,
                topRightRadius, topRightRadius,
                bottomRightRadius, bottomRightRadius,
                bottomLeftRadius, bottomLeftRadius
            )
            path.addRoundRect(rect, radii, Path.Direction.CW)
            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, 0f, 0f, null)

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 Bitmap 裁剪为圆角矩形（带边框）
     *
     * @param bitmap        源 Bitmap
     * @param cornerRadius  圆角半径
     * @param borderWidth   边框宽度
     * @param borderColor   边框颜色
     * @param recycleSource 是否回收源 Bitmap
     * @return 带边框的圆角 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun roundCornersWithBorder(
        bitmap: Bitmap,
        cornerRadius: Float,
        borderWidth: Int,
        borderColor: Int = Color.WHITE,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val totalWidth = bitmap.width + borderWidth * 2
        val totalHeight = bitmap.height + borderWidth * 2

        return try {
            val output = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            // 绘制边框
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.FILL
            }
            val borderRect = RectF(0f, 0f, totalWidth.toFloat(), totalHeight.toFloat())
            canvas.drawRoundRect(borderRect, cornerRadius + borderWidth, cornerRadius + borderWidth, borderPaint)

            // 绘制图片区域
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            val imageRect = RectF(
                borderWidth.toFloat(),
                borderWidth.toFloat(),
                (totalWidth - borderWidth).toFloat(),
                (totalHeight - borderWidth).toFloat()
            )
            canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, imagePaint)

            imagePaint.xfermode = null
            canvas.drawBitmap(bitmap, borderWidth.toFloat(), borderWidth.toFloat(), imagePaint)

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 椭圆 ====================

    /**
     * 将 Bitmap 裁剪为椭圆
     *
     * @param bitmap        源 Bitmap
     * @param recycleSource 是否回收源 Bitmap
     * @return 椭圆裁剪后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val oval = BitmapShapeUtils.toOval(bitmap)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun toOval(
        bitmap: Bitmap,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null

        return try {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

            // 使用 Path 绘制椭圆，兼容性更好
            val path = Path().apply {
                addOval(rect, Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, 0f, 0f, null)

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 自定义路径 ====================

    /**
     * 将 Bitmap 裁剪为自定义路径形状
     *
     * @param bitmap        源 Bitmap
     * @param path          裁剪路径（Path 对象的坐标相对于 Bitmap 尺寸）
     * @param recycleSource 是否回收源 Bitmap
     * @return 按路径裁剪后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val starPath = Path().apply {
     *     // ... 绘制五角星路径
     * }
     * val starBitmap = BitmapShapeUtils.toCustomPath(bitmap, starPath)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun toCustomPath(
        bitmap: Bitmap,
        path: Path,
        recycleSource: Boolean = false
    ): Bitmap? {
        if (!BitmapUtils.isValid(bitmap)) return null

        return try {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            if (recycleSource && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 形状叠加 ====================

    /**
     * 在指定 Bitmap 上叠加另一个 Bitmap
     *
     * @param background 背景 Bitmap
     * @param foreground 前景 Bitmap（覆盖在背景上）
     * @param x          前景的 X 偏移
     * @param y          前景的 Y 偏移
     * @return 叠加后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 将水印叠加到背景图右下角
     * val x = background.width - watermark.width - 16
     * val y = background.height - watermark.height - 16
     * val watermarked = BitmapShapeUtils.overlay(background, watermark, x, y)
     * ```
     */
    @JvmStatic
    fun overlay(
        background: Bitmap,
        foreground: Bitmap,
        x: Int = 0,
        y: Int = 0
    ): Bitmap? {
        if (!BitmapUtils.isValid(background) || !BitmapUtils.isValid(foreground)) return null

        return try {
            val result = BitmapUtils.copy(background) ?: return null
            val canvas = Canvas(result)
            canvas.drawBitmap(foreground, x.toFloat(), y.toFloat(), null)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
