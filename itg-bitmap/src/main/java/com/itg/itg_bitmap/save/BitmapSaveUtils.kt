package com.itg.itg_bitmap.save

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.itg.itg_bitmap.core.BitmapUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 位图保存工具类
 *
 * 提供将 Bitmap 保存到文件系统、相册、缓存目录等功能。
 * 自动处理 Android 10+ 的分区存储 (Scoped Storage) 适配。
 *
 * 支持的保存方式:
 * - 保存到指定文件路径
 * - 保存到相册 (MediaStore)
 * - 保存到应用缓存目录
 * - 保存到应用私有目录
 * - 保存并获取 URI
 *
 * @author ITG Team
 * @since 1.0.0
 */
object BitmapSaveUtils {

    private const val DEFAULT_QUALITY = 100
    private const val DATE_FORMAT = "yyyyMMdd_HHmmss"
    private const val DEFAULT_PREFIX = "IMG"

    // ==================== 保存到文件 ====================

    /**
     * 保存 Bitmap 到指定文件路径
     *
     * @param bitmap   源 Bitmap
     * @param filePath 目标文件完整路径（含文件名和后缀）
     * @param format   压缩格式，默认 JPEG
     * @param quality  压缩质量 0-100，默认 100
     * @return true 表示保存成功
     *
     * 使用示例:
     * ```kotlin
     * val success = BitmapSaveUtils.saveToFile(
     *     bitmap,
     *     "/sdcard/Pictures/my_photo.jpg"
     * )
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun saveToFile(
        bitmap: Bitmap,
        filePath: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = DEFAULT_QUALITY
    ): Boolean {
        if (!BitmapUtils.isValid(bitmap) || filePath.isBlank()) return false
        val q = quality.coerceIn(0, 100)

        return try {
            val file = File(filePath)
            // 确保父目录存在
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { fos ->
                bitmap.compress(format, q, fos)
                fos.flush()
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 保存 Bitmap 到指定目录（自动生成文件名）
     *
     * @param bitmap    源 Bitmap
     * @param directory 目标目录
     * @param prefix    文件名前缀，默认 "IMG"
     * @param format    压缩格式，默认 JPEG
     * @param quality   压缩质量
     * @return 保存的文件路径，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val path = BitmapSaveUtils.saveToDirectory(
     *     bitmap,
     *     File("/sdcard/MyApp"),
     *     prefix = "Photo"
     * )
     * // 生成类似 "/sdcard/MyApp/Photo_20240101_120000.jpg"
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun saveToDirectory(
        bitmap: Bitmap,
        directory: File,
        prefix: String = DEFAULT_PREFIX,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = DEFAULT_QUALITY
    ): String? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val extension = when (format) {
            Bitmap.CompressFormat.JPEG -> ".jpg"
            Bitmap.CompressFormat.PNG -> ".png"
            Bitmap.CompressFormat.WEBP -> ".webp"
            else -> ".jpg"
        }
        val timestamp = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timestamp$extension"
        val file = File(directory, fileName)

        return if (saveToFile(bitmap, file.absolutePath, format, quality)) {
            file.absolutePath
        } else {
            null
        }
    }

    // ==================== 保存到相册 ====================

    /**
     * 保存 Bitmap 到相册 (MediaStore)
     *
     * 自动适配 Android 10+ 存储分区和传统存储模式。
     *
     * @param context    Android Context
     * @param bitmap     源 Bitmap
     * @param fileName   文件名（不含路径），可选，默认自动生成
     * @param format     压缩格式，默认 JPEG
     * @param quality    压缩质量
     * @return 保存后的 Content URI，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val uri = BitmapSaveUtils.saveToGallery(context, bitmap, "MyPhoto")
     * if (uri != null) {
     *     Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String? = null,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = DEFAULT_QUALITY
    ): Uri? {
        if (!BitmapUtils.isValid(bitmap)) return null
        val q = quality.coerceIn(0, 100)

        val timestamp = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        val displayName = fileName ?: "${DEFAULT_PREFIX}_$timestamp"
        val mimeType = when (format) {
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.WEBP -> "image/webp"
            else -> "image/jpeg"
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: 使用 MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return null

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    bitmap.compress(format, q, os)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                uri
            } else {
                // Android 9 及以下: 直接文件写入
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val extension = when (format) {
                    Bitmap.CompressFormat.JPEG -> ".jpg"
                    Bitmap.CompressFormat.PNG -> ".png"
                    Bitmap.CompressFormat.WEBP -> ".webp"
                    else -> ".jpg"
                }
                val file = File(picturesDir, "$displayName$extension")
                file.parentFile?.mkdirs()

                FileOutputStream(file).use { fos ->
                    bitmap.compress(format, q, fos)
                    fos.flush()
                }

                // 通知系统扫描
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                context.sendBroadcast(intent)

                Uri.fromFile(file)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 保存到缓存 ====================

    /**
     * 保存 Bitmap 到应用缓存目录
     *
     * 缓存文件可能在系统空间不足时被清理。
     *
     * @param context  Android Context
     * @param bitmap   源 Bitmap
     * @param name     缓存文件名
     * @param format   格式，默认 PNG（缓存保持高质量）
     * @return 保存的文件，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val file = BitmapSaveUtils.saveToCache(context, bitmap, "avatar_cache")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun saveToCache(
        context: Context,
        bitmap: Bitmap,
        name: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ): File? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val file = File(context.cacheDir, name)
        return if (saveToFile(bitmap, file.absolutePath, format, DEFAULT_QUALITY)) {
            file
        } else {
            null
        }
    }

    /**
     * 保存 Bitmap 到应用私有文件目录
     *
     * 存储在 internal storage 中，对外部应用不可见。
     *
     * @param context  Android Context
     * @param bitmap   源 Bitmap
     * @param name     文件名
     * @param format   格式
     * @return 保存的文件，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val file = BitmapSaveUtils.saveToPrivate(context, bitmap, "profile.jpg")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun saveToPrivate(
        context: Context,
        bitmap: Bitmap,
        name: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): File? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val file = File(context.filesDir, name)
        return if (saveToFile(bitmap, file.absolutePath, format, DEFAULT_QUALITY)) {
            file
        } else {
            null
        }
    }

    // ==================== 保存到临时文件 ====================

    /**
     * 将 Bitmap 保存到临时文件
     *
     * 适用于需要临时文件路径的场景（如分享、上传）。
     *
     * @param context  Android Context
     * @param bitmap   源 Bitmap
     * @param format   格式
     * @return 临时文件，失败返回 null。使用完毕后请自行删除。
     *
     * 使用示例:
     * ```kotlin
     * val tmpFile = BitmapSaveUtils.saveToTempFile(context, bitmap)
     * // 使用完毕后删除
     * tmpFile?.delete()
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun saveToTempFile(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): File? {
        if (!BitmapUtils.isValid(bitmap)) return null

        val extension = when (format) {
            Bitmap.CompressFormat.JPEG -> ".jpg"
            Bitmap.CompressFormat.PNG -> ".png"
            Bitmap.CompressFormat.WEBP -> ".webp"
            else -> ".jpg"
        }

        return try {
            val file = File.createTempFile(
                "itg_bitmap_",
                extension,
                context.cacheDir
            )
            if (saveToFile(bitmap, file.absolutePath, format, DEFAULT_QUALITY)) {
                file
            } else {
                file.delete()
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 根据文件扩展名推断 CompressFormat
     *
     * @param extension 文件扩展名，如 "jpg", "png", "webp"
     * @return 对应的 CompressFormat，默认 JPEG
     */
    @JvmStatic
    fun formatFromExtension(extension: String): Bitmap.CompressFormat {
        return when (extension.lowercase(Locale.ROOT)) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    /**
     * 根据文件路径推断 CompressFormat
     *
     * @param path 文件路径
     * @return 对应的 CompressFormat，默认 JPEG
     */
    @JvmStatic
    fun formatFromPath(path: String): Bitmap.CompressFormat {
        val extension = File(path).extension
        return formatFromExtension(extension)
    }
}
