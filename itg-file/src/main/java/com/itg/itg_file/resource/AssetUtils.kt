package com.itg.itg_file.resource
import com.itg.itg_file.core.FileFailureCallbacks

import android.content.Context
import android.content.res.Resources
import android.system.ErrnoException
import android.system.Os
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.FileCoroutines
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

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
            FileFailureCallbacks.rethrowCancellation(e)
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
     * @return Unit
     */
    @JvmStatic
    suspend fun listAssetsSuspend(
        context: Context,
        path: String,
        onResult: (List<String>) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
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
        listAssetsRecursiveInternal(context = context, path = path, result = result, currentDepth = 0, maxDepth = maxDepth)
        return result
    }

    /**
     * 异步递归列出 assets
     */
    @JvmStatic
    @JvmOverloads
    suspend fun listAssetsRecursiveSuspend(
        context: Context,
        path: String = "",
        maxDepth: Int = 10,
        onResult: (List<String>) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
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
            FileFailureCallbacks.rethrowCancellation(e)
            false
        }
    }

    /**
     * 异步检查 assets 文件是否存在
     */
    @JvmStatic
    suspend fun assetExistsSuspend(
        context: Context,
        assetPath: String,
        onResult: (Boolean) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
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
            FileFailureCallbacks.rethrowCancellation(e)
            // 打不开，检查是否可 list
            try {
                val list = context.assets.list(assetPath)
                list != null && list.isNotEmpty()
            } catch (e2: Exception) {
                FileFailureCallbacks.rethrowCancellation(e2)
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
    private fun getAssetSizeImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): Long {
        if (!assetExists(context, assetPath)) {
            return failureReporter.fail(-1L, "读取 assets 大小失败：资源不存在：$assetPath")
        }
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
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 异步获取 assets 文件大小
     */
    @JvmStatic
    @JvmOverloads
    suspend fun getAssetSizeSuspend(
        context: Context,
        assetPath: String,
        onResult: (Long) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(getAssetSize(appContext, assetPath, onFailure = onFailure)) }
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
                formatAssetSize(bytes = size)
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
    private fun readAssetTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): String? {
        val bytes = readAssetBytesImpl(failureReporter, context, assetPath, maxBytes) ?: return failureReporter.fail(null, "AssetUtils.readAssetText 失败：大小或深度超出限制")
        return String(bytes, charset)
    }

    /**
     * 异步读取 assets 为字符串
     *
     * @param context   Android Context
     * @param assetPath assets 文件路径
     * @param charset   编码
     * @param onResult  回调 (content: String?, error: Throwable?)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readAssetTextSuspend(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetText(appContext, assetPath, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
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
    private fun readAssetBytesImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): ByteArray? {
        if (!assetExists(context, assetPath)) return failureReporter.fail(null, "AssetUtils.readAssetBytes 失败：assets 文件不存在：$assetPath")
        return try {
            context.assets.open(assetPath).use { stream ->
                if (maxBytes <= 0) {
                    stream.readBytes()
                } else {
                    stream.readBytesWithLimit(failureReporter, maxBytes)
                }
            }
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 assets 为字节数组
     */
    @JvmStatic
    suspend fun readAssetBytesSuspend(
        context: Context,
        assetPath: String,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetBytes(appContext, assetPath, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
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
    private fun readAssetLinesImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): List<String>? {
        if (!assetExists(context, assetPath)) return failureReporter.fail(null, "AssetUtils.readAssetLines 失败：assets 文件不存在：$assetPath")
        return try {
            context.assets.open(assetPath).use { stream ->
                if (maxBytes <= 0) {
                    stream.bufferedReader(charset).readLines()
                } else {
                    String(stream.readBytesWithLimit(failureReporter, maxBytes), charset).lineSequence().toList()
                }
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步按行读取 assets
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readAssetLinesSuspend(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (List<String>?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readAssetLines(appContext, assetPath, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
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
    private fun readAssetLinesStreamingImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean
    ): Int {
        if (!assetExists(context, assetPath)) return failureReporter.fail(-1, "AssetUtils.readAssetLinesStreaming 失败：assets 文件不存在：$assetPath")
        return try {
            var count = 0
            context.assets.open(assetPath).bufferedReader(charset).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val currentIndex = count
                    count++
                    if (!safeLineCallback(failureReporter, line, currentIndex, onEachLine)) break
                    line = reader.readLine()
                }
            }
            count
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
            -1
        }
    }

    /**
     * 异步流式逐行读取 assets
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readAssetLinesStreamingSuspend(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean,
        onComplete: (totalLines: Int, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val count = readAssetLinesStreaming(appContext, assetPath, charset, onEachLine, onFailure = { failure = it })
                safeCallback { onComplete(count, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
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
    private fun copyAssetToFileImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (!assetExists(context, assetPath)) return failureReporter.fail(false, "AssetUtils.copyAssetToFile 失败：assets 文件不存在：$assetPath")
        if (destPath.isBlank()) return failureReporter.fail(false, "AssetUtils.copyAssetToFile 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { output ->
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
    suspend fun copyAssetToFileSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyAssetToFile(appContext, assetPath, destPath, overwrite, onFailure = onFailure)) }
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
    private fun copyAssetToFileWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!assetExists(context, assetPath)) return failureReporter.fail(false, "AssetUtils.copyAssetToFileWithProgress 失败：assets 文件不存在：$assetPath")
        if (destPath.isBlank()) return failureReporter.fail(false, "AssetUtils.copyAssetToFileWithProgress 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { output ->
            context.assets.open(assetPath).use { input ->
                val totalBytes = getAssetSizeFast(failureReporter, context, assetPath)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesCopied = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    safeCallback(failureReporter) { onProgress?.invoke(bytesCopied, totalBytes) }
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
    suspend fun copyAssetToFileWithProgressSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyAssetToFileWithProgress(appContext, assetPath, destPath, overwrite, onProgress, onFailure = onFailure)) }
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
    private fun copyAssetDirToFileImpl(failureReporter: FileFailureCallbacks.Reporter,
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
            failureReporter.rethrow(e)
            e.printStackTrace()
            return failureReporter.fail(-1, "AssetUtils.copyAssetDirToFile 失败：assets 目录为空")
        }

        var successCount = 0
        val total = files.size
        for ((index, file) in files.withIndex()) {
            val relativePath = if (normalizedPath.isEmpty()) file else file.removePrefix("$normalizedPath/")
            val destFile = safeChildFile(failureReporter, destDir, relativePath) ?: return failureReporter.fail(-1, "AssetUtils.copyAssetDirToFile 失败：资源相对路径不安全")
            if (copyAssetToFileImpl(failureReporter, context, file, destFile.absolutePath, overwrite)) {
                successCount++
            }
            safeCallback(failureReporter) { onProgress?.invoke(index + 1, total) }
        }
        return successCount
    }

    /**
     * 异步批量复制 assets 目录
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyAssetDirToFileSuspend(
        context: Context,
        assetDirPath: String,
        destDirPath: String,
        overwrite: Boolean = true,
        onProgress: ((current: Int, estimatedTotal: Int) -> Unit)? = null,
        onResult: (Int) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyAssetDirToFile(appContext, assetDirPath, destDirPath, overwrite, onProgress, onFailure = onFailure)) }
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
    private fun readRawTextImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        charset: Charset = StandardCharsets.UTF_8
    ): String? {
        val bytes = readRawBytesImpl(failureReporter, context, resId) ?: return failureReporter.fail(null, "AssetUtils.readRawText 失败：读取 raw 字节失败")
        return try {
            String(bytes, charset)
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 raw 资源为字符串
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readRawTextSuspend(
        context: Context,
        resId: Int,
        charset: Charset = StandardCharsets.UTF_8,
        onResult: (String?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readRawText(appContext, resId, charset, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
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
    private fun readRawBytesImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES
    ): ByteArray? {
        return try {
            context.resources.openRawResource(resId).use { stream ->
                if (maxBytes <= 0) {
                    stream.readBytes()
                } else {
                    stream.readBytesWithLimit(failureReporter, maxBytes)
                }
            }
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步读取 raw 为字节数组
     */
    @JvmStatic
    @JvmOverloads
    suspend fun readRawBytesSuspend(
        context: Context,
        resId: Int,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onResult: (ByteArray?, Throwable?) -> Unit
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            try {
                var failure: Exception? = null
                val result = readRawBytes(appContext, resId, maxBytes, onFailure = { failure = it })
                safeCallback { onResult(result, failure) }
            } catch (t: Throwable) {
                FileFailureCallbacks.rethrowCancellation(t)
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
    private fun getRawSizeImpl(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int): Long {
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
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1L
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 异步获取 raw 资源大小
     */
    @JvmStatic
    @JvmOverloads
    suspend fun getRawSizeSuspend(
        context: Context,
        resId: Int,
        onResult: (Long) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(getRawSize(appContext, resId, onFailure = onFailure)) }
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
    private fun copyRawToFileImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true
    ): Boolean {
        if (destPath.isBlank()) return failureReporter.fail(false, "AssetUtils.copyRawToFile 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { output ->
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
    suspend fun copyRawToFileSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyRawToFile(appContext, resId, destPath, overwrite, onFailure = onFailure)) }
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
    private fun copyRawToFileWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (destPath.isBlank()) return failureReporter.fail(false, "AssetUtils.copyRawToFileWithProgress 失败：输入路径或参数为空")

        return writeToFileAtomically(failureReporter, destPath, overwrite) { output ->
            context.resources.openRawResource(resId).use { input ->
                val totalBytes = getRawSizeFast(failureReporter, context, resId)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesCopied = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    safeCallback(failureReporter) { onProgress?.invoke(bytesCopied, totalBytes) }
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
    suspend fun copyRawToFileWithProgressSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        val appContext = context.applicationContext ?: context
        return FileCoroutines.withIo {
            safeCallback { onResult(copyRawToFileWithProgress(appContext, resId, destPath, overwrite, onProgress, onFailure = onFailure)) }
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

    private fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        destPath: String,
        overwrite: Boolean,
        writer: (OutputStream) -> Unit
    ): Boolean {
        if (destPath.isBlank()) return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：输入路径或参数为空")
        var tempFile: File? = null
        return try {
            val destFile = File(destPath).canonicalFile
            if (destFile.exists()) {
                if (!overwrite) return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
                if (destFile.isDirectory) return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：目标路径是目录")
            }

            val parent = destFile.parentFile ?: return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：无法获取目标文件的父目录")
            if (parent.exists()) {
                if (!parent.isDirectory) return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：父路径不是目录：${parent.path}")
            } else if (!parent.mkdirs()) {
                return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：无法创建父目录：${parent.path}")
            }

            tempFile = File.createTempFile(".${destFile.name}.", ".tmp", parent)
            FileOutputStream(tempFile).use { output ->
                writer(output)
                output.fd.sync()
            }

            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "AssetUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
            Os.rename(tempFile.absolutePath, destFile.absolutePath)
            tempFile = null
            true
        } catch (e: Resources.NotFoundException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: ErrnoException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } finally {
            tempFile?.delete()
        }
    }

    private fun safeChildFile(failureReporter: FileFailureCallbacks.Reporter, parent: File, relativePath: String): File? {
        return try {
            val child = File(parent, relativePath).canonicalFile
            val parentPath = parent.canonicalPath.trimEnd(File.separatorChar) + File.separator
            if (child.path == parent.canonicalPath || child.path.startsWith(parentPath)) child else null
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    private fun getAssetSizeFast(failureReporter: FileFailureCallbacks.Reporter, context: Context, assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { descriptor -> descriptor.length }
        } catch (e: IOException) {
            // 压缩的 assets 无法使用 openFd；未知大小时仍可流式复制。
            UNKNOWN_SIZE
        }
    }

    private fun getRawSizeFast(failureReporter: FileFailureCallbacks.Reporter, context: Context, resId: Int): Long {
        return try {
            context.resources.openRawResourceFd(resId).use { descriptor -> descriptor.length }
        } catch (e: Resources.NotFoundException) {
            UNKNOWN_SIZE
        } catch (e: IOException) {
            // 压缩资源没有文件描述符；复制时以实际流读取结果为准。
            UNKNOWN_SIZE
        }
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
        }
    }

    private fun safeLineCallback(failureReporter: FileFailureCallbacks.Reporter,
        line: String,
        index: Int,
        callback: (line: String, index: Int) -> Boolean
    ): Boolean {
        return try {
            failureReporter.invokeUserCallback { callback(line, index) }
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
            false
        }
    }

    private fun InputStream.readBytesWithLimit(failureReporter: FileFailureCallbacks.Reporter, maxBytes: Int): ByteArray {
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
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** getAssetSize 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun getAssetSize(
        context: Context,
        assetPath: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("AssetUtils.getAssetSize", "$assetPath", onFailure, { it < 0L }, -1L) { failureReporter ->
            getAssetSizeImpl(failureReporter, context, assetPath)
        }

    /** readAssetText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetText(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("AssetUtils.readAssetText", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetTextImpl(failureReporter, context, assetPath, charset, maxBytes)
        }

    /** readAssetBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetBytes(
        context: Context,
        assetPath: String,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("AssetUtils.readAssetBytes", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetBytesImpl(failureReporter, context, assetPath, maxBytes)
        }

    /** readAssetLines 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetLines(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): List<String>? =
        FileFailureCallbacks.run("AssetUtils.readAssetLines", "$assetPath", onFailure, { it == null }, null) { failureReporter ->
            readAssetLinesImpl(failureReporter, context, assetPath, charset, maxBytes)
        }

    /** readAssetLinesStreaming 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readAssetLinesStreaming(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean,
        onFailure: ((Exception) -> Unit)? = null
    ): Int =
        FileFailureCallbacks.run("AssetUtils.readAssetLinesStreaming", "$assetPath", onFailure, { it < 0 }, -1) { failureReporter ->
            readAssetLinesStreamingImpl(failureReporter, context, assetPath, charset, onEachLine)
        }

    /** copyAssetToFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFile(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("AssetUtils.copyAssetToFile", "$assetPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyAssetToFileImpl(failureReporter, context, assetPath, destPath, overwrite)
        }

    /** copyAssetToFileWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAssetToFileWithProgress(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("AssetUtils.copyAssetToFileWithProgress", "$assetPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyAssetToFileWithProgressImpl(failureReporter, context, assetPath, destPath, overwrite, onProgress)
        }

    /** copyAssetDirToFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyAssetDirToFile(
        context: Context,
        assetDirPath: String,
        destDirPath: String,
        overwrite: Boolean = true,
        onProgress: ((current: Int, estimatedTotal: Int) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Int {
        var expectedCount = 0
        return FileFailureCallbacks.run(
            "AssetUtils.copyAssetDirToFile", "$assetDirPath → $destDirPath", onFailure,
            { it < 0 || it < expectedCount }, -1
        ) { failureReporter ->
            copyAssetDirToFileImpl(failureReporter, context, assetDirPath, destDirPath, overwrite) { current, total ->
                expectedCount = total
                onProgress?.invoke(current, total)
            }
        }
    }

    /** readRawText 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readRawText(
        context: Context,
        resId: Int,
        charset: Charset = StandardCharsets.UTF_8,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("AssetUtils.readRawText", "$resId", onFailure, { it == null }, null) { failureReporter ->
            readRawTextImpl(failureReporter, context, resId, charset)
        }

    /** readRawBytes 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun readRawBytes(
        context: Context,
        resId: Int,
        maxBytes: Int = DEFAULT_MAX_IN_MEMORY_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): ByteArray? =
        FileFailureCallbacks.run("AssetUtils.readRawBytes", "$resId", onFailure, { it == null }, null) { failureReporter ->
            readRawBytesImpl(failureReporter, context, resId, maxBytes)
        }

    /** getRawSize 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun getRawSize(
        context: Context,
        resId: Int,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("AssetUtils.getRawSize", "$resId", onFailure, { it < 0L }, -1L) { failureReporter ->
            getRawSizeImpl(failureReporter, context, resId)
        }

    /** copyRawToFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFile(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("AssetUtils.copyRawToFile", "$resId → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyRawToFileImpl(failureReporter, context, resId, destPath, overwrite)
        }

    /** copyRawToFileWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyRawToFileWithProgress(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("AssetUtils.copyRawToFileWithProgress", "$resId → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyRawToFileWithProgressImpl(failureReporter, context, resId, destPath, overwrite, onProgress)
        }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("getAssetSizeSuspendLegacyCallback")
    suspend fun getAssetSizeSuspend(
        context: Context,
        assetPath: String,
        onResult: (Long) -> Unit
    ) = getAssetSizeSuspend(context = context, assetPath = assetPath, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetToFileSuspendLegacyCallback")
    suspend fun copyAssetToFileSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = copyAssetToFileSuspend(context = context, assetPath = assetPath, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetToFileWithProgressSuspendLegacyCallback")
    suspend fun copyAssetToFileWithProgressSuspend(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = copyAssetToFileWithProgressSuspend(context = context, assetPath = assetPath, destPath = destPath, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetDirToFileSuspendLegacyCallback")
    suspend fun copyAssetDirToFileSuspend(
        context: Context,
        assetDirPath: String,
        destDirPath: String,
        overwrite: Boolean = true,
        onProgress: ((current: Int, estimatedTotal: Int) -> Unit)? = null,
        onResult: (Int) -> Unit
    ) = copyAssetDirToFileSuspend(context = context, assetDirPath = assetDirPath, destDirPath = destDirPath, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("getRawSizeSuspendLegacyCallback")
    suspend fun getRawSizeSuspend(
        context: Context,
        resId: Int,
        onResult: (Long) -> Unit
    ) = getRawSizeSuspend(context = context, resId = resId, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyRawToFileSuspendLegacyCallback")
    suspend fun copyRawToFileSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = copyRawToFileSuspend(context = context, resId = resId, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyRawToFileWithProgressSuspendLegacyCallback")
    suspend fun copyRawToFileWithProgressSuspend(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = copyRawToFileWithProgressSuspend(context = context, resId = resId, destPath = destPath, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("readAssetLinesStreamingLegacyCallback")
    fun readAssetLinesStreaming(
        context: Context,
        assetPath: String,
        charset: Charset = StandardCharsets.UTF_8,
        onEachLine: (line: String, index: Int) -> Boolean
    ) = readAssetLinesStreaming(context = context, assetPath = assetPath, charset = charset, onEachLine = onEachLine, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetToFileWithProgressLegacyCallback")
    fun copyAssetToFileWithProgress(
        context: Context,
        assetPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) = copyAssetToFileWithProgress(context = context, assetPath = assetPath, destPath = destPath, overwrite = overwrite, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyAssetDirToFileLegacyCallback")
    fun copyAssetDirToFile(
        context: Context,
        assetDirPath: String,
        destDirPath: String,
        overwrite: Boolean = true,
        onProgress: ((current: Int, estimatedTotal: Int) -> Unit)? = null
    ) = copyAssetDirToFile(context = context, assetDirPath = assetDirPath, destDirPath = destDirPath, overwrite = overwrite, onProgress = onProgress, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyRawToFileWithProgressLegacyCallback")
    fun copyRawToFileWithProgress(
        context: Context,
        resId: Int,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) = copyRawToFileWithProgress(context = context, resId = resId, destPath = destPath, overwrite = overwrite, onProgress = onProgress, onFailure = null)
}
