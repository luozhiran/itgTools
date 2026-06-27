package com.itg.itg_bitmap.decode

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URL

/**
 * 图片解码工具类
 *
 * 提供高效的 Bitmap 解码方法，支持采样率、尺寸限制等优化策略，
 * 有效避免 OOM (OutOfMemoryError)。
 *
 * 核心功能:
 * - 从资源/文件/流/字节数组解码 Bitmap
 * - 按需采样 (inSampleSize)
 * - 尺寸边界限制解码
 * - 仅获取图片信息而不加载完整图片
 *
 * @author ITG Team
 * @since 1.0.0
 */
object BitmapDecodeUtils {

    private const val UNCONSTRAINED = -1

    // ==================== 从资源解码 ====================

    /**
     * 从 drawable 资源解码 Bitmap
     *
     * @param res   Resources 实例
     * @param resId 资源 ID (如 R.drawable.image)
     * @param opts  解码选项，可选
     * @return 解码后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bitmap = BitmapDecodeUtils.decodeFromResource(resources, R.drawable.photo)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun decodeFromResource(
        res: Resources,
        resId: Int,
        opts: BitmapFactory.Options? = null
    ): Bitmap? {
        return try {
            BitmapFactory.decodeResource(res, resId, opts)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 drawable 资源解码 Bitmap，并限制最大尺寸
     *
     * @param res       Resources 实例
     * @param resId     资源 ID
     * @param maxWidth  最大宽度 (px)，超出则自动采样
     * @param maxHeight 最大高度 (px)，超出则自动采样
     * @return 解码后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bitmap = BitmapDecodeUtils.decodeFromResource(resources, R.drawable.photo, 800, 600)
     * ```
     */
    @JvmStatic
    fun decodeFromResource(
        res: Resources,
        resId: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(res, resId, options)

        val optionsOut = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight,
                maxWidth, maxHeight
            )
        }
        return decodeFromResource(res, resId, optionsOut)
    }

    // ==================== 从文件解码 ====================

    /**
     * 从文件路径解码 Bitmap
     *
     * @param path  文件路径
     * @param opts  解码选项，可选
     * @return 解码后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val bitmap = BitmapDecodeUtils.decodeFromFile("/sdcard/photo.jpg")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun decodeFromFile(
        path: String,
        opts: BitmapFactory.Options? = null
    ): Bitmap? {
        if (path.isBlank()) return null
        return try {
            BitmapFactory.decodeFile(path, opts)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从文件解码 Bitmap，自动计算采样率以适配目标尺寸
     *
     * 这是推荐的解码方法，能够有效避免加载超大图片时的 OOM 问题。
     *
     * @param path      文件路径
     * @param reqWidth  目标最大宽度 (px)
     * @param reqHeight 目标最大高度 (px)
     * @param config    位图配置，默认 [Bitmap.Config.ARGB_8888]
     * @return 按需采样后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * // 加载为不超过 800x600 的缩略图
     * val bitmap = BitmapDecodeUtils.decodeSampledBitmap("/sdcard/photo.jpg", 800, 600)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun decodeSampledBitmap(
        path: String,
        reqWidth: Int,
        reqHeight: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        if (path.isBlank()) return null

        // 第一步: 仅解码尺寸信息
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        // 第二步: 计算采样率并解码
        options.apply {
            inSampleSize = calculateInSampleSize(originalWidth, originalHeight, reqWidth, reqHeight)
            inJustDecodeBounds = false
            inPreferredConfig = config
        }
        return decodeFromFile(path, options)
    }

    // ==================== 从字节数组解码 ====================

    /**
     * 从字节数组解码 Bitmap
     *
     * @param data  图片字节数据
     * @param opts  解码选项，可选
     * @return 解码后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun decodeFromByteArray(
        data: ByteArray,
        opts: BitmapFactory.Options? = null
    ): Bitmap? {
        if (data.isEmpty()) return null
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从字节数组解码 Bitmap，限制最大尺寸
     *
     * @param data      图片字节数据
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @return 采样后的 Bitmap，失败返回 null
     */
    @JvmStatic
    fun decodeFromByteArray(
        data: ByteArray,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        val optionsOut = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight,
                maxWidth, maxHeight
            )
        }
        return decodeFromByteArray(data, optionsOut)
    }

    // ==================== 从流解码 ====================

    /**
     * 从 InputStream 解码 Bitmap
     *
     * 注意: 调用方负责关闭 InputStream。
     *
     * @param inputStream 输入流
     * @param opts        解码选项，可选
     * @return 解码后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun decodeFromStream(
        inputStream: InputStream,
        opts: BitmapFactory.Options? = null
    ): Bitmap? {
        return try {
            BitmapFactory.decodeStream(inputStream, null, opts)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 从 URI 解码 ====================

    /**
     * 从 Content URI 解码 Bitmap（自动处理）
     *
     * @param context Android Context
     * @param uri     图片 URI
     * @param opts    解码选项，可选
     * @return 解码后的 Bitmap，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun decodeFromUri(
        context: Context,
        uri: Uri,
        opts: BitmapFactory.Options? = null
    ): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, opts)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从网络 URL 解码 Bitmap（同步操作，需在子线程调用）
     *
     * 注意: 此为同步方法，会阻塞当前线程，必须在子线程中调用。
     *
     * @param url 图片 URL
     * @return 解码后的 Bitmap，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * thread {
     *     val bitmap = BitmapDecodeUtils.decodeFromUrl("https://example.com/photo.jpg")
     *     runOnUiThread { imageView.setImageBitmap(bitmap) }
     * }
     * ```
     */
    @JvmStatic
    fun decodeFromUrl(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 信息获取 ====================

    /**
     * 获取图片文件的尺寸信息（不加载完整图片，内存开销极小）
     *
     * @param path 文件路径
     * @return Pair(width, height)，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val (width, height) = BitmapDecodeUtils.getImageDimensions("/sdcard/photo.jpg") ?: return
     * println("图片尺寸: ${width}x${height}")
     * ```
     */
    @JvmStatic
    fun getImageDimensions(path: String): Pair<Int, Int>? {
        if (path.isBlank()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取图片文件的 MIME 类型（不加载完整图片）
     *
     * @param path 文件路径
     * @return MIME 类型字符串，如 "image/jpeg"，失败返回 null
     */
    @JvmStatic
    fun getImageMimeType(path: String): String? {
        if (path.isBlank()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            options.outMimeType
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取图片的详细信息（不加载完整图片）
     *
     * @param path 文件路径
     * @return 包含宽度、高度、MIME 类型、文件大小的 Map，失败返回 null
     */
    @JvmStatic
    fun getImageInfo(path: String): Map<String, String>? {
        if (path.isBlank()) return null
        return try {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            val info = mutableMapOf<String, String>()
            if (options.outWidth > 0) {
                info["width"] = options.outWidth.toString()
                info["height"] = options.outHeight.toString()
            }
            options.outMimeType?.let { info["mimeType"] = it }
            info["fileSize"] = "${file.length()} bytes"
            info

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 采样率计算 ====================

    /**
     * 计算合适的 inSampleSize（采样率）
     *
     * 采样率必须为 2 的幂次方（1, 2, 4, 8, ...），
     * 这样解码器可以对相邻像素进行平均，获得更平滑的结果。
     *
     * @param rawWidth   原始宽度
     * @param rawHeight  原始高度
     * @param reqWidth   目标最大宽度，传入 -1 表示无限制
     * @param reqHeight  目标最大高度，传入 -1 表示无限制
     * @return 计算出的采样率 (>= 1)
     */
    @JvmStatic
    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawWidth <= 0 || rawHeight <= 0) return inSampleSize

        val effectiveReqWidth = if (reqWidth == UNCONSTRAINED) rawWidth else reqWidth
        val effectiveReqHeight = if (reqHeight == UNCONSTRAINED) rawHeight else reqHeight

        if (rawWidth > effectiveReqWidth || rawHeight > effectiveReqHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while ((halfWidth / inSampleSize) >= effectiveReqWidth &&
                (halfHeight / inSampleSize) >= effectiveReqHeight
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
