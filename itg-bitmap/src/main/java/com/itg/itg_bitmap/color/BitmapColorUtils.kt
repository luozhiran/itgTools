package com.itg.itg_bitmap.color

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 位图颜色处理工具类
 *
 * 提供 Bitmap 的颜色调整、滤镜和转换功能。
 * 所有方法通过 [ColorMatrix] 实现高效的像素级颜色处理。
 *
 * 支持的颜色操作:
 * - 灰度化
 * - 反色
 * - 亮度/对比度调整
 * - 色相/饱和度调整
 * - 复古/黑白/冷/暖色调
 * - 色彩覆盖 (Tint)
 * - 通道提取 (R/G/B)
 * - 透明度调整
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object BitmapColorUtils {

    // ==================== 灰度/黑白 ====================

    /**
     * 将 Bitmap 转换为灰度图
     *
     * 使用标准的 ITU-R BT.601 亮度权重公式:
     * Gray = 0.299*R + 0.587*G + 0.114*B
     *
     * @param bitmap 源 Bitmap
     * @return 灰度 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val grayBitmap = BitmapColorUtils.grayscale(colorBitmap)
     * ```
     */
    @JvmStatic
    fun grayscale(bitmap: Bitmap): Bitmap? {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * 将 Bitmap 转换为黑白二值图（黑白化，无灰度过渡）
     *
     * @param bitmap     源 Bitmap
     * @param threshold  阈值 0-255，低于此值为黑色，高于等于为白色，默认 128
     * @return 黑白 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun blackWhite(bitmap: Bitmap, threshold: Int = 128): Bitmap? {
        val t = threshold.toFloat() / 255f
        // 先转为灰度，再通过对比度矩阵实现二值化效果
        val grayMatrix = ColorMatrix(
            floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                10f, 0f, 0f, 0f, -10f * t,
                10f, 0f, 0f, 0f, -10f * t,
                10f, 0f, 0f, 0f, -10f * t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayMatrix.postConcat(contrastMatrix)
        return applyColorMatrix(bitmap, grayMatrix)
    }

    // ==================== 反色 ====================

    /**
     * 反色处理 — 每个颜色通道取反
     *
     * 公式: R' = 255 - R, G' = 255 - G, B' = 255 - B
     *
     * @param bitmap 源 Bitmap
     * @return 反色 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val inverted = BitmapColorUtils.invert(bitmap)
     * ```
     */
    @JvmStatic
    fun invert(bitmap: Bitmap): Bitmap? {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 亮度 ====================

    /**
     * 调整 Bitmap 的亮度
     *
     * @param bitmap 源 Bitmap
     * @param value  亮度值，范围 [-255, 255]
     *               正值为变亮，负值为变暗，0 为不变
     * @return 调整后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val brighter = BitmapColorUtils.adjustBrightness(bitmap, 50f)   // 变亮
     * val darker = BitmapColorUtils.adjustBrightness(bitmap, -50f)     // 变暗
     * ```
     */
    @JvmStatic
    fun adjustBrightness(bitmap: Bitmap, value: Float): Bitmap? {
        val clamped = value.coerceIn(-255f, 255f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, clamped,
                0f, 1f, 0f, 0f, clamped,
                0f, 0f, 1f, 0f, clamped,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 对比度 ====================

    /**
     * 调整 Bitmap 的对比度
     *
     * @param bitmap 源 Bitmap
     * @param value  对比度值，范围 [-1.0, 2.0]
     *               1.0 为原始对比度，>1 增强对比度，<1 降低对比度
     * @return 调整后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val highContrast = BitmapColorUtils.adjustContrast(bitmap, 1.5f)
     * val lowContrast = BitmapColorUtils.adjustContrast(bitmap, 0.5f)
     * ```
     */
    @JvmStatic
    fun adjustContrast(bitmap: Bitmap, value: Float): Bitmap? {
        val clamped = value.coerceIn(-1f, 3f)
        val translate = (-(clamped - 1f) * 128f + 0.5f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                clamped, 0f, 0f, 0f, translate,
                0f, clamped, 0f, 0f, translate,
                0f, 0f, clamped, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 饱和度 ====================

    /**
     * 调整 Bitmap 的饱和度
     *
     * @param bitmap 源 Bitmap
     * @param value  饱和度值
     *               1.0 为原始饱和度，0.0 为完全去色（灰度），>1 增强饱和度
     * @return 调整后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val vibrant = BitmapColorUtils.adjustSaturation(bitmap, 2.0f)  // 更鲜艳
     * val muted = BitmapColorUtils.adjustSaturation(bitmap, 0.3f)     // 接近灰度
     * ```
     */
    @JvmStatic
    fun adjustSaturation(bitmap: Bitmap, value: Float): Bitmap? {
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(value.coerceAtLeast(0f))
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 色相 ====================

    /**
     * 旋转 Bitmap 的色相
     *
     * @param bitmap 源 Bitmap
     * @param value  色相旋转值，范围 [0, 360]
     *               0 = 红色，120 = 绿色，240 = 蓝色
     * @return 调整后的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun adjustHue(bitmap: Bitmap, value: Float): Bitmap? {
        val radians = value * PI.toFloat() / 180f
        val cosine = cos(radians)
        val sine = sin(radians)
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        val colorMatrix = ColorMatrix(floatArrayOf(
            lumR + cosine * (1 - lumR) + sine * -lumR,
            lumG + cosine * -lumG + sine * -lumG,
            lumB + cosine * -lumB + sine * (1 - lumB), 0f, 0f,
            lumR + cosine * -lumR + sine * 0.143f,
            lumG + cosine * (1 - lumG) + sine * 0.140f,
            lumB + cosine * -lumB + sine * -0.283f, 0f, 0f,
            lumR + cosine * -lumR + sine * -(1 - lumR),
            lumG + cosine * -lumG + sine * lumG,
            lumB + cosine * (1 - lumB) + sine * lumB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 色调滤镜 ====================

    /**
     * 复古色调（Sepia / 老照片效果）
     *
     * @param bitmap 源 Bitmap
     * @return 复古色调 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val sepia = BitmapColorUtils.sepia(bitmap)
     * ```
     */
    @JvmStatic
    fun sepia(bitmap: Bitmap): Bitmap? {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * 冷色调滤镜（蓝/青色偏）
     *
     * @param bitmap  源 Bitmap
     * @param strength 强度 0.0-1.0，默认 0.5
     * @return 冷色调 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun coolTone(bitmap: Bitmap, strength: Float = 0.5f): Bitmap? {
        val s = strength.coerceIn(0f, 1f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, -30f * s,
                0f, 1f, 0f, 0f, -10f * s,
                0f, 0f, 1f, 0f, 50f * s,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * 暖色调滤镜（黄/橙色偏）
     *
     * @param bitmap   源 Bitmap
     * @param strength 强度 0.0-1.0，默认 0.5
     * @return 暖色调 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun warmTone(bitmap: Bitmap, strength: Float = 0.5f): Bitmap? {
        val s = strength.coerceIn(0f, 1f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 50f * s,
                0f, 1f, 0f, 0f, 20f * s,
                0f, 0f, 1f, 0f, -30f * s,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 色彩覆盖 ====================

    /**
     * 使用指定颜色对 Bitmap 进行染色（Tint）
     *
     * @param bitmap 源 Bitmap
     * @param color  染色颜色（ARGB 值，如 Color.RED）
     * @param alpha  染色强度 0.0-1.0，默认 0.5
     * @return 染色后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val redTinted = BitmapColorUtils.tint(bitmap, Color.RED, 0.3f)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun tint(bitmap: Bitmap, color: Int, alpha: Float = 0.5f): Bitmap? {
        val a = alpha.coerceIn(0f, 1f)
        val r = (Color.red(color) * a).toInt()
        val g = (Color.green(color) * a).toInt()
        val b = (Color.blue(color) * a).toInt()
        val scale = 1f - a

        val colorMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, r.toFloat(),
                0f, scale, 0f, 0f, g.toFloat(),
                0f, 0f, scale, 0f, b.toFloat(),
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 通道操作 ====================

    /**
     * 提取红色通道
     *
     * @param bitmap 源 Bitmap
     * @return 仅包含红色通道的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun extractRed(bitmap: Bitmap): Bitmap? {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * 提取绿色通道
     *
     * @param bitmap 源 Bitmap
     * @return 仅包含绿色通道的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun extractGreen(bitmap: Bitmap): Bitmap? {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * 提取蓝色通道
     *
     * @param bitmap 源 Bitmap
     * @return 仅包含蓝色通道的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun extractBlue(bitmap: Bitmap): Bitmap? {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 透明度 ====================

    /**
     * 调整 Bitmap 的透明度
     *
     * @param bitmap 源 Bitmap
     * @param alpha  透明度 0.0-1.0，0 为完全透明，1 为完全不透明
     * @return 调整后的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun adjustAlpha(bitmap: Bitmap, alpha: Float): Bitmap? {
        val a = alpha.coerceIn(0f, 1f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, a, 0f
            )
        )
        return applyColorMatrix(bitmap, colorMatrix)
    }

    // ==================== 通用 ColorMatrix 支持 ====================

    /**
     * 使用自定义 [ColorMatrix] 处理 Bitmap
     *
     * 这是最通用的颜色处理方法，适用于需要自定义颜色矩阵的场景。
     *
     * @param bitmap      源 Bitmap
     * @param colorMatrix 颜色变换矩阵 (4x5)
     * @return 处理后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val matrix = ColorMatrix().apply {
     *     setSaturation(0f)  // 自定义颜色矩阵组合
     *     setScale(1.2f, 1f, 1f, 1f)  // 加强红色
     * }
     * val result = BitmapColorUtils.applyColorMatrix(bitmap, matrix)
     * ```
     */
    @JvmStatic
    fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap? {
        return try {
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
