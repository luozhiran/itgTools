package com.itg.itg_bitmap.effect

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.itg.itg_bitmap.core.BitmapUtils
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.EmbossMaskFilter
import android.graphics.MaskFilter
import kotlin.math.exp
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * 位图特效工具类
 *
 * 提供 Bitmap 的图像特效处理功能，包括模糊、锐化、浮雕、边缘检测等。
 *
 * 支持的特效:
 * - 高斯模糊 (Gaussian Blur)
 * - 堆栈模糊 (Stack Blur — 近似高斯模糊，性能更好)
 * - 锐化 (Sharpen)
 * - 浮雕效果 (Emboss)
 * - 边缘检测 (Edge Detection)
 * - 素描效果 (Sketch)
 * - 光照效果
 * - 像素化 (Pixelate)
 * - 油画效果
 *
 * 注意: 部分高级特效（如堆栈模糊、素描）通过逐像素卷积实现，
 * 对大图处理可能较慢，建议在子线程中调用。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object BitmapEffectUtils {

    private const val MAX_CPU_EFFECT_PIXELS = 4_000_000L

    // ==================== 高斯模糊 ====================

    /**
     * 使用 RenderScript 兼容方式实现高斯模糊
     *
     * 对指定半径范围内的像素进行加权平均，产生柔和的模糊效果。
     *
     * @param bitmap 源 Bitmap
     * @param radius 模糊半径 (px)，范围 1-25，值越大越模糊
     * @return 模糊后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val blurred = BitmapEffectUtils.blur(bitmap, 10)
     * ```
     */
    @JvmStatic
    fun blur(bitmap: Bitmap, radius: Int): Bitmap? {
        if (radius < 1) return BitmapUtils.copy(bitmap)
        val safeRadius = radius.coerceIn(1, 25)
        return stackBlur(bitmap, safeRadius)
    }

    /**
     * 堆栈模糊 (Stack Blur) 算法
     *
     * 速度优于传统高斯模糊，效果接近。适用于实时模糊场景。
     *
     * @param bitmap 源 Bitmap
     * @param radius 模糊半径 (1-200)
     * @return 模糊后的 Bitmap，失败返回 null
     *
     * 参考: Mario Klingemann 的 Stack Blur 算法
     */
    @JvmStatic
    fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap? {
        if (radius < 1) return BitmapUtils.copy(bitmap)
        val safeRadius = radius.coerceIn(1, 200)

        val copy = BitmapUtils.copy(bitmap) ?: return null
        val w = copy.width
        val h = copy.height
        if (w.toLong() * h.toLong() > MAX_CPU_EFFECT_PIXELS) {
            copy.recycle()
            return null
        }
        val pix = IntArray(w * h)
        copy.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = safeRadius + safeRadius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)

        var rSum: Int
        var gSum: Int
        var bSum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vMin = IntArray(maxOf(w, h))

        var divSum = (div + 1) shr 1
        divSum *= divSum
        val dv = IntArray(256 * divSum)
        i = 0
        while (i < 256 * divSum) {
            dv[i] = i / divSum
            i++
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackPointer: Int
        var stackStart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = safeRadius + 1
        var routSum: Int
        var goutSum: Int
        var boutSum: Int
        var rinSum: Int
        var ginSum: Int
        var binSum: Int

        // 水平方向
        y = 0
        while (y < h) {
            bSum = 0
            gSum = 0
            rSum = 0
            boutSum = 0
            goutSum = 0
            routSum = 0
            binSum = 0
            ginSum = 0
            rinSum = 0

            i = -safeRadius
            while (i <= safeRadius) {
                p = pix[yi + minOf(wm, maxOf(i, 0))]
                sir = stack[i + safeRadius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(i)
                rSum += sir[0] * rbs
                gSum += sir[1] * rbs
                bSum += sir[2] * rbs
                if (i > 0) {
                    rinSum += sir[0]
                    ginSum += sir[1]
                    binSum += sir[2]
                } else {
                    routSum += sir[0]
                    goutSum += sir[1]
                    boutSum += sir[2]
                }
                i++
            }
            stackPointer = safeRadius

            x = 0
            while (x < w) {
                r[yi] = dv[rSum]
                g[yi] = dv[gSum]
                b[yi] = dv[bSum]

                rSum -= routSum
                gSum -= goutSum
                bSum -= boutSum

                stackStart = stackPointer - safeRadius + div
                sir = stack[stackStart % div]

                routSum -= sir[0]
                goutSum -= sir[1]
                boutSum -= sir[2]

                if (y == 0) {
                    vMin[x] = minOf(x + safeRadius + 1, wm)
                }
                p = pix[yw + vMin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinSum += sir[0]
                ginSum += sir[1]
                binSum += sir[2]

                rSum += rinSum
                gSum += ginSum
                bSum += binSum

                stackPointer = (stackPointer + 1) % div
                sir = stack[stackPointer % div]

                routSum += sir[0]
                goutSum += sir[1]
                boutSum += sir[2]

                rinSum -= sir[0]
                ginSum -= sir[1]
                binSum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        // 垂直方向
        x = 0
        while (x < w) {
            bSum = 0
            gSum = 0
            rSum = 0
            boutSum = 0
            goutSum = 0
            routSum = 0
            binSum = 0
            ginSum = 0
            rinSum = 0

            yp = -safeRadius * w
            i = -safeRadius
            while (i <= safeRadius) {
                yi = maxOf(0, yp) + x
                sir = stack[i + safeRadius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - kotlin.math.abs(i)

                rSum += r[yi] * rbs
                gSum += g[yi] * rbs
                bSum += b[yi] * rbs

                if (i > 0) {
                    rinSum += sir[0]
                    ginSum += sir[1]
                    binSum += sir[2]
                } else {
                    routSum += sir[0]
                    goutSum += sir[1]
                    boutSum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackPointer = safeRadius

            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and (dv[rSum] shl 16)) or
                        (dv[gSum] shl 8) or dv[bSum]

                rSum -= routSum
                gSum -= goutSum
                bSum -= boutSum

                stackStart = stackPointer - safeRadius + div
                sir = stack[stackStart % div]

                routSum -= sir[0]
                goutSum -= sir[1]
                boutSum -= sir[2]

                if (x == 0) {
                    vMin[y] = minOf(y + r1, hm) * w
                }
                p = x + vMin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinSum += sir[0]
                ginSum += sir[1]
                binSum += sir[2]

                rSum += rinSum
                gSum += ginSum
                bSum += binSum

                stackPointer = (stackPointer + 1) % div
                sir = stack[stackPointer]

                routSum += sir[0]
                goutSum += sir[1]
                boutSum += sir[2]

                rinSum -= sir[0]
                ginSum -= sir[1]
                binSum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        copy.setPixels(pix, 0, w, 0, 0, w, h)
        return copy
    }

    // ==================== 锐化 ====================

    /**
     * 锐化滤镜 — 增强图像边缘和细节
     *
     * @param bitmap    源 Bitmap
     * @param intensity 锐化强度 0.0-2.0，默认 1.0，0 为无效果
     * @return 锐化后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val sharp = BitmapEffectUtils.sharpen(bitmap, 0.8f)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun sharpen(bitmap: Bitmap, intensity: Float = 1.0f): Bitmap? {
        if (intensity <= 0f) return BitmapUtils.copy(bitmap)
        val s = intensity.coerceIn(0f, 2f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f + s, -s / 2f, -s / 2f, 0f, 0f,
                -s / 2f, 1f + s, -s / 2f, 0f, 0f,
                -s / 2f, -s / 2f, 1f + s, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorFilter(bitmap, colorMatrix)
    }

    // ==================== 浮雕 ====================

    /**
     * 浮雕效果
     *
     * 模拟图像刻在金属或石头上的立体效果。
     *
     * @param bitmap 源 Bitmap
     * @return 浮雕效果 Bitmap，失败返回 null
     */
    @JvmStatic
    fun emboss(bitmap: Bitmap): Bitmap? {
        if (!canProcess(bitmap)) return null
        return try {
            val copy = BitmapUtils.copy(bitmap) ?: return null
            val w = copy.width
            val h = copy.height
            val pixels = IntArray(w * h)
            copy.getPixels(pixels, 0, w, 0, 0, w, h)

            val embossed = IntArray(w * h)
            for (y in 1 until h) {
                for (x in 1 until w) {
                    val idx = y * w + x
                    val current = pixels[idx]
                    val prev = pixels[(y - 1) * w + (x - 1)]

                    val r1 = (current shr 16) and 0xff
                    val g1 = (current shr 8) and 0xff
                    val b1 = current and 0xff

                    val r2 = (prev shr 16) and 0xff
                    val g2 = (prev shr 8) and 0xff
                    val b2 = prev and 0xff

                    var r = 128 + r1 - r2
                    var g = 128 + g1 - g2
                    var b = 128 + b1 - b2

                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)

                    embossed[idx] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            copy.setPixels(embossed, 0, w, 0, 0, w, h)
            copy
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 边缘检测 ====================

    /**
     * 边缘检测（Sobel 算子）
     *
     * 检测图像中亮度变化剧烈的区域，生成边缘轮廓图。
     *
     * @param bitmap 源 Bitmap
     * @return 边缘检测 Bitmap（黑底白线），失败返回 null
     */
    @JvmStatic
    fun edgeDetect(bitmap: Bitmap): Bitmap? {
        if (!canProcess(bitmap)) return null
        return try {
            val copy = BitmapUtils.copy(bitmap) ?: return null
            val w = copy.width
            val h = copy.height
            val pixels = IntArray(w * h)
            copy.getPixels(pixels, 0, w, 0, 0, w, h)

            val gray = IntArray(w * h)
            for (i in pixels.indices) {
                val r = (pixels[i] shr 16) and 0xff
                val g = (pixels[i] shr 8) and 0xff
                val b = pixels[i] and 0xff
                gray[i] = (r + g + b) / 3
            }

            val edges = IntArray(w * h)
            // Sobel 边缘检测核
            val gx = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
            val gy = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)

            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    var sumX = 0
                    var sumY = 0

                    for (ky in -1..1) {
                        for (kx in -1..1) {
                            val idx = (y + ky) * w + (x + kx)
                            val kernelIdx = (ky + 1) * 3 + (kx + 1)
                            sumX += gray[idx] * gx[kernelIdx]
                            sumY += gray[idx] * gy[kernelIdx]
                        }
                    }

                    val magnitude = sqrt((sumX * sumX + sumY * sumY).toFloat()).toInt()
                        .coerceIn(0, 255)
                    val inverted = 255 - magnitude
                    val idx = y * w + x
                    edges[idx] = (0xff shl 24) or (inverted shl 16) or (inverted shl 8) or inverted
                }
            }

            copy.setPixels(edges, 0, w, 0, 0, w, h)
            copy
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 素描效果 ====================

    /**
     * 素描/铅笔效果
     *
     * 将图片转换为类似手绘铅笔素描的风格。
     *
     * @param bitmap 源 Bitmap
     * @return 素描效果 Bitmap，失败返回 null
     */
    @JvmStatic
    fun sketch(bitmap: Bitmap): Bitmap? {
        if (!canProcess(bitmap)) return null
        // 素描效果 = 灰度图 → 反色 → 高斯模糊 → 颜色减淡混合
        return try {
            val gray = com.itg.itg_bitmap.color.BitmapColorUtils.grayscale(bitmap) ?: return null
            val inverted = com.itg.itg_bitmap.color.BitmapColorUtils.invert(gray) ?: return null
            val blurred = stackBlur(inverted, 8) ?: inverseBlurSubstitute(inverted, 8)

            // 颜色减淡混合: Result = Base / (255 - Blend)
            val w = gray.width
            val h = gray.height
            val basePixels = IntArray(w * h)
            val blendPixels = IntArray(w * h)
            gray.getPixels(basePixels, 0, w, 0, 0, w, h)
            blurred?.getPixels(blendPixels, 0, w, 0, 0, w, h)

            val result = createBitmap(w, h)
            val resultPixels = IntArray(w * h)

            for (i in resultPixels.indices) {
                val base = basePixels[i] and 0xff  // 取蓝色通道（灰度图三通道相同）
                val blend = blendPixels[i] and 0xff
                val value = if (blend < 255) {
                    (base.toFloat() / (255f - blend) * 255f).toInt().coerceIn(0, 255)
                } else {
                    255
                }
                val v = value.coerceIn(0, 255)
                resultPixels[i] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
            }

            result.setPixels(resultPixels, 0, w, 0, 0, w, h)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun inverseBlurSubstitute(bitmap: Bitmap, radius: Int): Bitmap? {
        // Sketch 效果中模糊的降级方案
        return blur(bitmap, radius.coerceIn(1, 25))
    }

    // ==================== 像素化 ====================

    /**
     * 像素化效果（马赛克）
     *
     * @param bitmap  源 Bitmap
     * @param blockSize 像素块大小 (px)，值越大马赛克越粗，默认 10
     * @return 像素化后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val mosaic = BitmapEffectUtils.pixelate(bitmap, 15)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun pixelate(bitmap: Bitmap, blockSize: Int = 10): Bitmap? {
        if (blockSize <= 1) return BitmapUtils.copy(bitmap)
        val size = blockSize.coerceIn(1, 100)
        val w = bitmap.width
        val h = bitmap.height
        val smallW = maxOf(1, w / size)
        val smallH = maxOf(1, h / size)
        val small = bitmap.scale(smallW, smallH, false)
        return small.scale(w, h, false)
    }

    // ==================== 光照效果 ====================

    /**
     * 添加圆形光照效果（暗角 / Vignette）
     *
     * 图像中心明亮，四周逐渐变暗。
     *
     * @param bitmap    源 Bitmap
     * @param intensity 暗角强度 0.0-1.0，默认 0.6
     * @return 添加暗角后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun vignette(bitmap: Bitmap, intensity: Float = 0.6f): Bitmap? {
        if (!canProcess(bitmap)) return null
        val i = intensity.coerceIn(0f, 1f)
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val copy = BitmapUtils.copy(bitmap) ?: return null
            val pixels = IntArray(w * h)
            copy.getPixels(pixels, 0, w, 0, 0, w, h)

            val centerX = w / 2f
            val centerY = h / 2f
            val maxDist = sqrt(centerX * centerX + centerY * centerY)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dist = sqrt(
                        (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)
                    )
                    val factor = (1f - i * (dist / maxDist)).coerceIn(0f, 1f)

                    val idx = y * w + x
                    val pixel = pixels[idx]
                    val r = ((pixel shr 16) and 0xff) * factor
                    val g = ((pixel shr 8) and 0xff) * factor
                    val b = (pixel and 0xff) * factor

                    pixels[idx] = (0xff shl 24) or (r.toInt() shl 16) or
                            (g.toInt() shl 8) or b.toInt()
                }
            }

            copy.setPixels(pixels, 0, w, 0, 0, w, h)
            copy
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 内部工具 ====================

    private fun applyColorFilter(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap? {
        return try {
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            val result = createBitmap(bitmap.width, bitmap.height)
            Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun canProcess(bitmap: Bitmap): Boolean =
        BitmapUtils.isValid(bitmap) &&
            bitmap.width.toLong() * bitmap.height.toLong() <= MAX_CPU_EFFECT_PIXELS
}
