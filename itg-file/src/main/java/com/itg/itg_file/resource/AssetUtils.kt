package com.itg.itg_file.resource

import android.content.Context
import android.content.res.Resources
import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.Future

/**
 * Android Assets / Raw 资源读写工具类
 *
 * 提供对 Android 应用内置 assets 和 res/raw 资源的读取、列举、复制到外部存储等操作。
 *
 * **重要说明:**
 * - `assets/` 和 `res/raw/` 是 APK 内置的只读资源，运行时无法直接修改。
 * - "写入" 操作指将内置资源复制到文件系统，以便后续修改或外部使用。
 * - 所有方法需要 [Context] 参数，用于获取 [android.content.res.AssetManager] 和 [Resources]。
 *
 * 核心特性:
 * - Assets 文件列举（含递归）
 * - Assets 存在性 / 大小查询
 * - Assets 读取为 String / ByteArray / 按行读取
 * - Assets 复制到文件系统（写入能力）
 * - Raw 资源读取为 String / ByteArray
 * - Raw 资源复制到文件系统（写入能力）
 * - 大文件流式复制（带进度回调）
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object AssetUtils {

    private const val DEFAULT_BUFFER_SIZE = 8192  // 8KB
    private const val DEFAULT_MAX_IN_MEMORY_BYTES = 10 * 1024 * 1024
    private const val UNKNOWN_SIZE = -1L

    // ==================== Assets 列举 ====================

    /**
     * 列出 assets 目录下的所有文件和子目录
     *
     * 注意: Android AssetManager.list() 对深层目录可能返回空数组，
     * 即使该目录确实存在子文件（已知限制）。
     *
     * @param context Android Context
     * @param path    assets 中的路径，传 "" 表示根目录
     * @return 文件名列表（不含路径），失败或为空返回 emptyList
     *
     * 使用示例:
     * ```kotlin
     * // 列出 assets 根目录
     * val files = AssetUtils.listAssets(context, "")
     * // 列出 assets/data 子目录
     * val dataFiles = AssetUtils.listAssets(context, "data")
     * ```
     */
    @JvmStatic
    fun listAssets(context: Context, path: String): List<String> {
        return try {
            context.assets.list(path)?.toList() ?: emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 异步列出 assets 目录
     *
     * @param context  Android Context
     * @param path     assets 路径
     * @param onResult 回调 (fileNames: List<String>)
     * @return [Future]
     */
    @JvmStatic
    fun listAssetsAsync(
        context: Context,
        path: String,
        onResult: (List<String>) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(listAssets(appContext, path)) }
        }
    }

    /**
     * 递归列出 assets 目录下所有文件
     *
     * 遍历 assets 目录树，返回所有文件的完整路径。
     * 目录会被跳过，仅返回文件。
     *
     * @param context     Android Context
     * @param path        起始路径，传 "" 表示根目录
     * @param maxDepth    最大递归深度（防止意外深层遍历），默认 10
     * @return 文件路径列表（相对于 assets 根目录）
     *
     * 使用示例:
     * ```kotlin
     * val allFiles = AssetUtils.listAssetsRecursive(context, "")
     * allFiles.forEach { println("Asset: $it") }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun listAssetsRecursive(
        context: Context,
        path: String = "",
        maxDepth: Int = 10
    ): List<String> {
        val result = mutableListOf<String>()
        listAssetsRecursiveInternal(context, path, result, 0, maxDepth)
        return result
    }

    /**
     * 异步递归列出 assets
     */
    @JvmStatic
    @JvmOverloads
    fun listAssetsRecursiveAsync(
        context: Context,
        path: String = "",
        maxDepth: Int = 10,
        onResult: (List<String>) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(listAssetsRecursive(appContext, path, maxDepth)) }
        }
    }

    // ==================== Assets 存在性与信息 ====================

    /**
     * 检查 assets 文件是否存在
     *
     * 通过尝试打开文件来判断，如果打开成功则存在。
     *
     * @param context   Android Context
     * @param assetPath assets 中的文件路径
     * @return true 表示文件存在
     *
     * 使用示例:
     * ```kotlin
     * if (AssetUtils.assetExists(context, "config.json")) {
     *     loadConfig()
     * }
     * ```
     */
    @JvmStatic
    fun assetExists(context: Context, assetPath: String): Boolean {
        if (assetPath.isBlank()) return false
        return try {
            context.assets.open(assetPath).use { /* 能打开说明存在 */ }
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * 异步检查 assets 文件是否存在
     */
    @JvmStatic
    fun assetExistsAsync(
        context: Context,
        assetPath: String,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(assetExists(appContext, assetPath)) }
        }
    }

    /**
     * 检查 assets 路径是否为目录
     *
     * 通过尝试以文件方式打开来判断：打开失败且 list 非空则为目录。
     *
     * @param context   Android Context
     * @param assetPath assets 中的路径
     * @return true 表示路径为目录
     */
    @JvmStatic
    fun isAssetDirectory(context: Context, assetPath: String): Boolean {
        if (assetPath.isBlank()) return true  // 根目录
        return try {
            // 先尝试以文件方式打开
            context.assets.open(assetPath).use { /* 能打开说明是文件 */ }
            false
        } catch (e: IOException) {
            // 打不开，检查是否可 list
            try {
                val list = context.assets.list(assetPath)
                list != null && list.isNotEmpty()
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * 获取 assets 文件大小（字节）
     *
     * 通过打开 InputStream 并读取全部字节来获取大小。
     * 注意: 对于大文件（>10MB）建议使用 [copyAssetToFile] 代替。
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @return 字节数，失败返回 -1
     *
     * 使用示例:
     * ```kotlin
     * val size = AssetUtils.getAssetSize(context, "images/logo.png")
     * println("Logo size: ${FileUtils.getSizeFormatted(null)}") // 格式化显示
     * ```
     */
    @JvmStatic
    fun getAssetSize(context: Context, assetPath: String): Long {
        if (!assetExists(context, assetPath)) return -1L
        return try {
            context.assets.open(assetPath).use { stream ->
                var total = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    total += bytesRead
                }
                total
            }
        } catch (e: IOException) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 异步获取 assets 文件大小
     */
    @JvmStatic
    fun getAssetSizeAsync(
        context: Context,
        assetPath: String,
        onResult: (Long) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(getAssetSize(appContext, assetPath)) }
        }
    }

    /**
     * 获取 assets 文件详细信息
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @return 包含各项属性的 Map
     */
    @JvmStatic
    fun getAssetInfo(context: Context, assetPath: String): Map<String, Any> {
        if (!assetExists(context, assetPath)) return mapOf("exists" to false)
        val size = getAssetSize(context, assetPath)
        val isDir = isAssetDirectory(context, assetPath)
        return mapOf(
            "exists" to true,
            "name" to File(assetPath).name,
            "path" to assetPath,
            "isDirectory" to isDir,
            "isFile" to !isDir,
            "size" to size,
            "sizeFormatted" to FileUtils.getSizeFormatted(null).let {
                // 使用 FileUtils 的格式化逻辑
                formatAssetSize(size)
            }
        )
    }

    // ==================== Assets 读取 ====================

    /**
     * 读取 assets 文件为字符串
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param charset   字符编码，默认 UTF-8
     * @return 文件内容字符串，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val json = AssetUtils.readAssetText(context, "data/config.json")
     * val config = Gson().fromJson(json, Config::class.java)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetText(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): String? {
        val bytes = readAssetBytes(context, assetPath, maxBytes) ?: return null
        return String(bytes, charset)
    }

    /**
     * 异步读取 assets 为字符串
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param charset   编码
     * @param onResult  回调 (content: String?, error: Throwable?)
     * @return [Future]
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetTextAsync(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            try {
                val result = readAssetText(appContext, assetPath, charset)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 读取 assets 文件为字节数组
     *
     * 注意: 大文件（>10MB）请使用 [copyAssetToFile] 避免 OOM。
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @return 字节数组，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetBytes(
        context: Context,
        assetPath: String,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): ByteArray? {
        if (!assetExists(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                if (maxBytes <= 0) {
                    stream.readBytes()
                } else {
                    stream.readBytesWithLimit(maxBytes)
                }
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 assets 为字节数组
     */
    @JvmStatic
    fun readAssetBytesAsync(
        context: Context,
        assetPath: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            try {
                val result = readAssetBytes(appContext, assetPath)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 按行读取 assets 文件
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param charset   字符编码，默认 UTF-8
     * @return 每行内容的列表，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val lines = AssetUtils.readAssetLines(context, "data/words.txt")
     * lines?.forEach { word -> dictionary.add(word.trim()) }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetLines(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): List<String>? {
        if (!assetExists(context, assetPath)) return null
        return try {
            context.assets.open(assetPath).use { stream ->
                if (maxBytes <= 0) {
                    stream.bufferedReader(charset).readLines()
                } else {
                    String(stream.readBytesWithLimit(maxBytes), charset).lineSequence().toList()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取 assets
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetLinesAsync(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            try {
                val result = readAssetLines(appContext, assetPath, charset)
                safeCallback { onResult(result, if (result == null) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 流式逐行读取 assets 文件（大文件友好）
     *
     * @param context    Android Context
     * @param assetPath  assets 文件路径
     * @param charset    字符编码
     * @param onEachLine 每行处理回调，返回 false 可提前终止读取
     * @return 成功读取的行数，失败返回 -1
     *
     * 使用示例:
     * ```kotlin
     * AssetUtils.readAssetLinesStreaming(context, "data/large.csv",
     *     onEachLine = { line, index ->
     *         processCsvLine(line)
     *         true // 继续读取
     *     })
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetLinesStreaming(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!assetExists(context, assetPath)) return -1
        return try {
            var count = 0
            context.assets.open(assetPath).bufferedReader(charset).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val currentIndex = count
                    count++
                    if (!safeLineCallback(line, currentIndex, onEachLine)) break
                    line = reader.readLine()
                }
            }
            count
        } catch (e: IOException) {
            e.printStackTrace()
            -1
        } catch (t: Throwable) {
            t.printStackTrace()
            -1
        }
    }

    /**
     * 异步流式逐行读取 assets
     */
    @JvmStatic
    @JvmOverloads
    fun readAssetLinesStreamingAsync(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean,
        onComplete: (totalLines: Int, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            try {
                val count = readAssetLinesStreaming(appContext, assetPath, charset, onEachLine)
                safeCallback { onComplete(count, if (count < 0) IOException("Read asset failed: $assetPath") else null) }
            } catch (t: Throwable) {
                safeCallback { onComplete(-1, t) }
            }
        }
    }

    // ==================== Assets 复制到文件系统（写入能力） ====================

    /**
     * 将 assets 文件复制到文件系统
     *
     * 这是 assets "写入" 的核心方法。将只读的 assets 资源复制到可读写的文件路径。
     *
     * @param context    Android Context
     * @param assetPath  assets 中的源文件路径
     * @param destPath   目标文件系统路径
     * @param overwrite  是否覆盖已存在的目标文件，默认 true
     * @return true 表示复制成功
     *
     * 使用示例:
     * ```kotlin
     * // 将 assets 中的数据库模板复制到外部存储
     * AssetUtils.copyAssetToFile(context, "templates/app.db", "/sdcard/MyApp/app.db")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFile(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (!assetExists(context, assetPath)) return false
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { output ->
            context.assets.open(assetPath).use { input ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 异步复制 assets 到文件系统
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileAsync(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(copyAssetToFile(appContext, assetPath, destPath, overwrite)) }
        }
    }

    /**
     * 将 assets 文件复制到文件系统（带进度回调）
     *
     * 适用于大文件复制，可实时获取进度。
     *
     * @param context     Android Context
     * @param assetPath   assets 源路径
     * @param destPath    目标路径
     * @param overwrite   是否覆盖
     * @param onProgress  进度回调 (bytesCopied, totalBytes)
     * @return true 表示复制成功
     *
     * 使用示例:
     * ```kotlin
     * AssetUtils.copyAssetToFileWithProgress(context, "bundled_data.bin",
     *     "/sdcard/data.bin",
     *     onProgress = { copied, total ->
     *         updateProgressBar((copied * 100 / total).toInt())
     *     })
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileWithProgress(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!assetExists(context, assetPath)) return false
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { output ->
            context.assets.open(assetPath).use { input ->
                val totalBytes = getAssetSizeFast(context, assetPath)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesCopied = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    onProgress?.invoke(bytesCopied, totalBytes)
                }
                output.flush()
            }
        }
    }

    /**
     * 异步带进度复制 assets 到文件系统
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileWithProgressAsync(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(copyAssetToFileWithProgress(appContext, assetPath, destPath, overwrite, onProgress)) }
        }
    }

    /**
     * 批量复制 assets 目录到文件系统
     *
     * 递归复制整个 assets 子目录到目标路径。会自动创建子目录结构。
     *
     * @param context      Android Context
     * @param assetDirPath assets 中的目录路径
     * @param destDirPath  目标文件系统目录路径
     * @param overwrite    是否覆盖已存在文件
     * @param onProgress   进度回调 (currentFileIndex, estimatedTotal)
     * @return 成功复制的文件数量，失败返回 -1
     *
     * 使用示例:
     * ```kotlin
     * // 将 assets/templates 整个目录复制出去
     * val count = AssetUtils.copyAssetDirToFile(context,
     *     "templates", "/sdcard/MyApp/templates")
     * println("Copied $count files")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetDirToFile(
        context: Context,
        assetDirPath: String,
        destDirPath: String,
        overwrite: Boolean = true,
        onProgress: ((current: Int, estimatedTotal: Int) -> Unit)? = null
    ): Int {
        val normalizedPath = assetDirPath.trimEnd('/')
        val files = listAssetsRecursive(context, normalizedPath)
        if (files.isEmpty()) return 0
        val destDir = try {
            File(destDirPath).canonicalFile
        } catch (e: IOException) {
            e.printStackTrace()
            return -1
        }

        var successCount = 0
        val total = files.size
        for ((index, file) in files.withIndex()) {
            val relativePath = if (normalizedPath.isEmpty()) file else file.removePrefix("$normalizedPath/")
            val destFile = safeChildFile(destDir, relativePath) ?: return -1
            if (copyAssetToFile(context, file, destFile.absolutePath, overwrite)) {
                successCount++
            }
            onProgress?.invoke(index + 1, total)
        }
        return successCount
    }

    /**
     * 异步批量复制 assets 目录
     */
    @JvmStatic
    @JvmOverloads
    fun copyAssetDirToFileAsync(
        context: Context,
        assetDirPath: String,
        destDirPath: String,
        overwrite: Boolean = true,
        onProgress: ((current: Int, estimatedTotal: Int) -> Unit)? = null,
        onResult: (Int) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(copyAssetDirToFile(appContext, assetDirPath, destDirPath, overwrite, onProgress)) }
        }
    }

    // ==================== Raw 资源读取 ====================

    /**
     * 读取 res/raw 资源为字符串
     *
     * @param context Android Context
     * @param resId   R.raw.xxx 资源 ID
     * @param charset 字符编码，默认 UTF-8
     * @return 资源内容字符串，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val text = AssetUtils.readRawText(context, R.raw.license)
     * text?.let { showLicenseDialog(it) }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readRawText(
        context: Context,
        resId: Int,
        charset: Charset = StandardCharsets.UTF_8
    ): String? {
        val bytes = readRawBytes(context, resId) ?: return null
        return try {
            String(bytes, charset)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 raw 资源为字符串
     */
    @JvmStatic
    @JvmOverloads
    fun readRawTextAsync(
        context: Context,
        resId: Int,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            try {
                val result = readRawText(appContext, resId, charset)
                safeCallback { onResult(result, if (result == null) IOException("Read raw failed: $resId") else null) }
            } catch (t: Throwable) {
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 读取 res/raw 资源为字节数组
     *
     * @param context     Android Context
     * @param resId       R.raw.xxx 资源 ID
     * @param maxBytes    最大读取字节数（0 表示不限制），防止超大资源 OOM
     * @return 字节数组，失败返回 null
     *
     * 使用示例:
     * ```kotlin
     * val rawBytes = AssetUtils.readRawBytes(context, R.raw.sound_effect)
     * soundPool.load(rawBytes)
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun readRawBytes(
        context: Context,
        resId: Int,
        maxBytes: Int = 0
    ): ByteArray? {
        return try {
            context.resources.openRawResource(resId).use { stream ->
                if (maxBytes <= 0) {
                    stream.readBytes()
                } else {
                    stream.readBytesWithLimit(maxBytes)
                }
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 raw 为字节数组
     */
    @JvmStatic
    @JvmOverloads
    fun readRawBytesAsync(
        context: Context,
        resId: Int,
        maxBytes: Int = 0,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            try {
                val result = readRawBytes(appContext, resId, maxBytes)
                safeCallback { onResult(result, if (result == null) IOException("Read raw failed: $resId") else null) }
            } catch (t: Throwable) {
                safeCallback { onResult(null, t) }
            }
        }
    }

    /**
     * 获取 raw 资源的大小（字节）
     *
     * 通过读取资源流全部内容计算大小。
     *
     * @param context Android Context
     * @param resId   R.raw.xxx 资源 ID
     * @return 字节数，失败返回 -1
     */
    @JvmStatic
    fun getRawSize(context: Context, resId: Int): Long {
        return try {
            context.resources.openRawResource(resId).use { stream ->
                var total = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    total += bytesRead
                }
                total
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            -1L
        } catch (e: IOException) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 异步获取 raw 资源大小
     */
    @JvmStatic
    fun getRawSizeAsync(
        context: Context,
        resId: Int,
        onResult: (Long) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(getRawSize(appContext, resId)) }
        }
    }

    // ==================== Raw 资源复制到文件系统（写入能力） ====================

    /**
     * 将 res/raw 资源复制到文件系统
     *
     * 这是 raw 资源 "写入" 的核心方法。
     *
     * @param context    Android Context
     * @param resId      R.raw.xxx 资源 ID
     * @param destPath   目标文件系统路径
     * @param overwrite  是否覆盖已存在的目标文件，默认 true
     * @return true 表示复制成功
     *
     * 使用示例:
     * ```kotlin
     * // 将内置的默认头像保存到外部存储
     * AssetUtils.copyRawToFile(context, R.raw.default_avatar,
     *     "/sdcard/MyApp/default_avatar.png")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFile(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { output ->
            context.resources.openRawResource(resId).use { input ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 异步复制 raw 到文件系统
     */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileAsync(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(copyRawToFile(appContext, resId, destPath, overwrite)) }
        }
    }

    /**
     * 将 res/raw 资源复制到文件系统（带进度回调）
     *
     * @param context     Android Context
     * @param resId       R.raw.xxx 资源 ID
     * @param destPath    目标路径
     * @param overwrite   是否覆盖
     * @param onProgress  进度回调 (bytesCopied, totalBytes)
     * @return true 表示复制成功
     */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileWithProgress(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (destPath.isBlank()) return false

        return writeToFileAtomically(destPath, overwrite) { output ->
            context.resources.openRawResource(resId).use { input ->
                val totalBytes = getRawSizeFast(context, resId)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesCopied = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    onProgress?.invoke(bytesCopied, totalBytes)
                }
                output.flush()
            }
        }
    }

    /**
     * 异步带进度复制 raw 到文件系统
     */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileWithProgressAsync(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        val appContext = context.applicationContext ?: context
        return TaskExecutor.io {
            safeCallback { onResult(copyRawToFileWithProgress(appContext, resId, destPath, overwrite, onProgress)) }
        }
    }

    // ==================== 通用资源读取（自动判断 Assets / Raw） ====================

    /**
     * 根据文件扩展名自动推断 MIME 类型
     *
     * @param fileName 文件名
     * @return MIME 类型，如 "image/png"
     */
    @JvmStatic
    fun guessMimeType(fileName: String): String {
        return FileUtils.getMimeType(fileName)
    }

    // ==================== 内部方法 ====================

    private fun listAssetsRecursiveInternal(
        context: Context,
        path: String,
        result: MutableList<String>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth > maxDepth) return

        val entries = listAssets(context, path)
        for (entry in entries) {
            val fullPath = if (path.isEmpty()) entry else "$path/$entry"
            if (isAssetDirectory(context, fullPath)) {
                listAssetsRecursiveInternal(context, fullPath, result, currentDepth + 1, maxDepth)
            } else {
                result.add(fullPath)
            }
        }
    }

    private fun writeToFileAtomically(
        destPath: String,
        overwrite: Boolean,
        writer: (OutputStream) -> Unit
    ): Boolean {
        if (destPath.isBlank()) return false
        var tempFile: File? = null
        return try {
            val destFile = File(destPath).canonicalFile
            if (destFile.exists()) {
                if (!overwrite || destFile.isDirectory) return false
            }

            val parent = destFile.parentFile ?: return false
            if (parent.exists()) {
                if (!parent.isDirectory) return false
            } else if (!parent.mkdirs()) {
                return false
            }

            tempFile = File.createTempFile(".${destFile.name}.", ".tmp", parent)
            FileOutputStream(tempFile).use { output ->
                writer(output)
                output.fd.sync()
            }

            if (destFile.exists() && !overwrite) return false
            if (!tempFile.renameTo(destFile)) {
                if (destFile.exists() && (!destFile.delete() || !tempFile.renameTo(destFile))) {
                    throw IOException("Failed to replace destination: ${destFile.absolutePath}")
                } else if (!destFile.exists() && !tempFile.renameTo(destFile)) {
                    throw IOException("Failed to move temp file to destination: ${destFile.absolutePath}")
                }
            }
            tempFile = null
            true
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        } finally {
            tempFile?.delete()
        }
    }

    private fun safeChildFile(parent: File, relativePath: String): File? {
        return try {
            val child = File(parent, relativePath).canonicalFile
            val parentPath = parent.canonicalPath.trimEnd(File.separatorChar) + File.separator
            if (child.path == parent.canonicalPath || child.path.startsWith(parentPath)) child else null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun getAssetSizeFast(context: Context, assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { descriptor -> descriptor.length }
        } catch (e: IOException) {
            UNKNOWN_SIZE
        }
    }

    private fun getRawSizeFast(context: Context, resId: Int): Long {
        return try {
            context.resources.openRawResourceFd(resId).use { descriptor -> descriptor.length }
        } catch (e: Resources.NotFoundException) {
            UNKNOWN_SIZE
        } catch (e: IOException) {
            UNKNOWN_SIZE
        }
    }

    private inline fun safeCallback(callback: () -> Unit) {
        try {
            callback()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private inline fun safeLineCallback(
        line: String,
        index: Int,
        callback: (line: String, index: Int) -> Boolean
    ): Boolean {
        return try {
            callback(line, index)
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    private fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "maxBytes must be non-negative" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0
        while (true) {
            if (totalRead == maxBytes) {
                if (read() != -1) throw IOException("Input exceeds maxBytes=$maxBytes")
                break
            }
            val bytesRead = read(buffer, 0, minOf(buffer.size, maxBytes - totalRead))
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
        }
        return output.toByteArray()
    }

    private fun formatAssetSize(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        val absBytes = kotlin.math.abs(bytes)
        return when {
            absBytes < 1024L -> "$bytes B"
            absBytes < 1024L * 1024L -> "%.2f KB".format(absBytes / 1024.0)
            absBytes < 1024L * 1024L * 1024L -> "%.2f MB".format(absBytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(absBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
