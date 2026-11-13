package com.itg.itg_file.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.system.Os
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * 文件基础操作工具类
 *
 * 提供文件/目录的创建、删除、重命名、复制、移动、信息获取等核心操作。
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
 * 操作方法的 onFailure 位于参数末尾；不需要失败详情时可省略或传入 null。
 *
 * 核心特性:
 * - 文件是否存在 / 是否为空 / 大小 / 类型判断
 * - 文件/目录创建、删除、重命名
 * - 文件复制、移动（含进度回调）
 * - 文件信息获取（大小、MIME类型、修改时间、权限等）
 * - 目录列表、过滤、遍历
 * - 存储空间查询
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object FileUtils {

    private const val COPY_BUFFER_SIZE = 8192  // 8KB 缓冲

    // ==================== 存在性判断 ====================

    /**
     * 检查文件或目录是否存在
     *
     * @param path 文件/目录路径
     * @return true 表示存在
     */
    @JvmStatic
    fun exists(path: String?): Boolean {
        return !path.isNullOrBlank() && File(path).exists()
    }

    /**
     * 异步检查文件是否存在
     *
     * @param path   文件路径
     * @param onResult 回调 (exists: Boolean)
     * @return Unit 可用于取消
     */
    @JvmStatic
    suspend fun existsSuspend(path: String?, onResult: (Boolean) -> Unit): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(exists(path)) } }
    }

    /**
     * 检查路径是否为文件（非目录）
     */
    @JvmStatic
    fun isFile(path: String?): Boolean {
        return exists(path) && File(path!!).isFile
    }

    /**
     * 检查路径是否为目录
     */
    @JvmStatic
    fun isDirectory(path: String?): Boolean {
        return exists(path) && File(path!!).isDirectory
    }

    /**
     * 检查文件是否为空（0 字节或目录中无文件）
     */
    @JvmStatic
    fun isEmpty(path: String?): Boolean {
        if (!exists(path)) return true
        val file = File(path!!)
        return if (file.isFile) file.length() == 0L
        else file.list().isNullOrEmpty()
    }

    // ==================== 创建 ====================

    /**
     * 创建文件（含父目录自动创建）
     *
     * @param path 文件路径
     * @return true 表示创建成功或文件已存在
     *
     * 使用示例:
     * ```kotlin
     * FileUtils.createFile("/sdcard/MyApp/data/config.json")
     * ```
     */
    private fun createFileImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "创建文件失败：路径为空")
        return try {
            val file = File(path)
            if (file.exists()) return true
            if (!ensureParentDirectory(failureReporter, file)) {
                return failureReporter.fail(false, "创建文件失败：无法创建父目录：${file.parent}")
            }
            if (file.createNewFile()) true
            else failureReporter.fail(false, "创建文件失败：无法创建目标文件：$path")
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建文件（异步）
     *
     * @param path     文件路径
     * @param onResult 回调 (success: Boolean)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun createFileSuspend(
        path: String,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(createFile(path, onFailure = onFailure)) } }
    }

    /**
     * 创建目录（含父目录自动创建）
     *
     * @param path 目录路径
     * @return true 表示创建成功或目录已存在
     */
    private fun createDirectoryImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Boolean {
        if (path.isBlank()) return failureReporter.fail(false, "FileUtils.createDirectory 失败：输入路径或参数为空")
        return try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) return true
            if (dir.mkdirs()) true
            else failureReporter.fail(false, "创建目录失败：目标已被文件占用或父目录不可写：$path")
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建目录（异步）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun createDirectorySuspend(
        path: String,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(createDirectory(path, onFailure = onFailure)) } }
    }

    /**
     * 创建临时文件
     *
     * @param prefix 文件名前缀，默认 "itg_"
     * @param suffix 文件名后缀，默认 ".tmp"
     * @param directory 存放目录，默认系统临时目录
     * @return 临时文件，失败返回 null
     */
    private fun createTempFileImpl(failureReporter: FileFailureCallbacks.Reporter,
        prefix: String = "itg_",
        suffix: String = ".tmp",
        directory: File? = null
    ): File? {
        return try {
            File.createTempFile(prefix, suffix, directory)
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            null
        }
    }

    // ==================== 删除 ====================

    /**
     * 删除文件或目录（目录将递归删除）
     *
     * @param path 文件/目录路径
     * @return true 表示删除成功
     *
     * 使用示例:
     * ```kotlin
     * FileUtils.delete("/sdcard/MyApp/cache/")
     * ```
     */
    private fun deleteImpl(failureReporter: FileFailureCallbacks.Reporter, path: String?): Boolean {
        if (!exists(path)) return failureReporter.fail(false, "FileUtils.delete 失败：目标路径不存在")
        if (deleteRecursive(failureReporter, File(path!!))) return true
        return failureReporter.fail(false, "删除失败：无法删除目标或其子项：$path")
    }

    /**
     * 删除文件（异步）
     *
     * @param path     路径
     * @param onResult 回调 (success: Boolean)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun deleteSuspend(
        path: String?,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(delete(path, onFailure = onFailure)) } }
    }

    /**
     * 清空目录内容（保留目录自身）
     *
     * @param path 目录路径
     * @return true 表示清空成功
     */
    private fun clearDirectoryImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Boolean {
        if (!isDirectory(path)) return failureReporter.fail(false, "FileUtils.clearDirectory 失败：目标不是目录或目录不存在")
        val dir = File(path)
        var success = true
        val children = dir.listFiles()
            ?: return failureReporter.fail(false, "清空目录失败：无法列出目录内容：$path")
        children.forEach { child ->
            if (!deleteRecursive(failureReporter, child)) success = false
        }
        if (success) return true
        return failureReporter.fail(false, "清空目录失败：部分子项无法删除：$path")
    }

    /**
     * 清空目录（异步）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun clearDirectorySuspend(
        path: String,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(clearDirectory(path, onFailure = onFailure)) } }
    }

    // ==================== 重命名 ====================

    /**
     * 重命名文件或目录
     *
     * @param path    原路径
     * @param newName 新名称（不含路径）
     * @return 新文件路径，失败返回 null
     */
    private fun renameImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, newName: String): String? {
        if (!exists(path)) return failureReporter.fail(null, "重命名失败：源文件不存在：$path")
        val file = File(path)
        val newFile = File(file.parent, newName)
        return if (file.renameTo(newFile)) newFile.absolutePath
        else failureReporter.fail(null, "重命名失败：无法移动到目标名称：${newFile.path}")
    }

    /**
     * 重命名文件（异步）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun renameSuspend(
        path: String,
        newName: String,
        onResult: (String?) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(rename(path, newName, onFailure = onFailure)) } }
    }

    // ==================== 复制 ====================

    /**
     * 复制文件
     *
     * @param srcPath  源文件路径
     * @param destPath 目标文件路径
     * @param overwrite 是否覆盖已存在的目标文件
     * @return true 表示复制成功
     */
    private fun copyImpl(failureReporter: FileFailureCallbacks.Reporter, srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!isFile(srcPath)) return failureReporter.fail(false, "复制失败：源文件不存在或不是普通文件：$srcPath")

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath).absoluteFile
            if (srcFile.canonicalFile == destFile.canonicalFile) {
                return failureReporter.fail(false, "复制失败：源文件与目标文件相同：$srcPath")
            }
            if (destFile.exists() && !overwrite) {
                return failureReporter.fail(false, "复制失败：目标文件已存在且禁止覆盖：$destPath")
            }

            writeToFileAtomically(failureReporter, destFile, overwrite) { tempFile ->
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.channel.use { srcChannel ->
                            output.channel.use { destChannel ->
                                var position = 0L
                                val size = srcChannel.size()
                                while (position < size) {
                                    val transferred = srcChannel.transferTo(
                                        position,
                                        minOf(COPY_BUFFER_SIZE.toLong(), size - position),
                                        destChannel
                                    )
                                    if (transferred <= 0L) {
                                        throw IOException("File channel transfer failed")
                                    }
                                    position += transferred
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 复制文件（带进度回调）
     *
     * @param srcPath      源文件路径
     * @param destPath     目标文件路径
     * @param overwrite    是否覆盖
     * @param onProgress   进度回调 (bytesCopied, totalBytes)，每个 buffer 触发一次
     * @return true 表示复制成功
     *
     * 使用示例:
     * ```kotlin
     * FileUtils.copyWithProgress("/sdcard/large.zip", "/sdcard/backup.zip",
     *     onProgress = { copied, total ->
     *         val percent = (copied * 100 / total)
     *         updateProgressBar(percent)
     *     })
     * ```
     */
    private fun copyWithProgressImpl(failureReporter: FileFailureCallbacks.Reporter,
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null
    ): Boolean {
        if (!isFile(srcPath)) return failureReporter.fail(false, "FileUtils.copyWithProgress 失败：源文件不存在或不是普通文件")

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath).absoluteFile
            if (srcFile.canonicalFile == destFile.canonicalFile) return failureReporter.fail(false, "FileUtils.copyWithProgress 失败：源与目标指向同一文件")
            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "FileUtils.copyWithProgress 失败：目标已存在且禁止覆盖")
            val totalSize = srcFile.length()

            writeToFileAtomically(failureReporter, destFile, overwrite) { tempFile ->
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        var bytesCopied = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            safeCallback(failureReporter) { onProgress?.invoke(bytesCopied, totalSize) }
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 复制文件（异步）
     *
     * @param srcPath   源路径
     * @param destPath  目标路径
     * @param overwrite 是否覆盖
     * @param onResult  回调 (success: Boolean)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copySuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(copy(srcPath, destPath, overwrite, onFailure = onFailure)) } }
    }

    /**
     * 复制文件（异步，带进度）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun copyWithProgressSuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo {
            safeCallback { onResult(copyWithProgress(srcPath, destPath, overwrite, onProgress, onFailure = onFailure)) }
        }
    }

    /**
     * 复制目录（递归）
     *
     * @param srcDir   源目录路径
     * @param destDir  目标目录路径
     * @param overwrite 是否覆盖
     * @return true 表示复制成功
     */
    private fun copyDirectoryImpl(failureReporter: FileFailureCallbacks.Reporter, srcDir: String, destDir: String, overwrite: Boolean = true): Boolean {
        if (copyDirectoryInternal(failureReporter, srcDir, destDir, overwrite, mutableSetOf())) return true
        return failureReporter.fail(false, "复制目录失败：请检查源目录和目标目录：$srcDir → $destDir")
    }

    private fun copyDirectoryInternal(failureReporter: FileFailureCallbacks.Reporter,
        srcDir: String,
        destDir: String,
        overwrite: Boolean,
        visitedDirectories: MutableSet<String>
    ): Boolean {
        if (!isDirectory(srcDir)) return failureReporter.fail(false, "复制目录失败：源目录不存在：$srcDir")
        return try {
            val src = File(srcDir)
            val dest = File(destDir)
            val srcCanonical = src.canonicalFile
            val destCanonical = dest.canonicalFile
            if (!visitedDirectories.add(srcCanonical.path)) {
                return failureReporter.fail(false, "复制目录失败：检测到重复目录或循环：$srcDir")
            }
            if (srcCanonical == destCanonical ||
                destCanonical.path.startsWith(
                    srcCanonical.path.trimEnd(File.separatorChar) + File.separator
                )
            ) return failureReporter.fail(false, "复制目录失败：目标位于源目录内：$destDir")
            if (!dest.exists() && !dest.mkdirs()) {
                return failureReporter.fail(false, "复制目录失败：无法创建目标目录：$destDir")
            }

            var success = true
            val children = src.listFiles() ?: throw IOException("无法列出源目录内容：$srcDir")
            children.forEach { child ->
                val childDest = File(dest, child.name)
                val copied = if (child.isDirectory) {
                    copyDirectoryInternal(failureReporter,
                        child.absolutePath,
                        childDest.absolutePath,
                        overwrite,
                        visitedDirectories
                    )
                } else {
                    copyImpl(failureReporter, child.absolutePath, childDest.absolutePath, overwrite)
                }
                if (!copied) success = false
            }
            success
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    // ==================== 移动 ====================

    /**
     * 移动文件或目录
     *
     * 优先尝试 rename（同一分区效率高），失败后走 copy+delete。
     *
     * @param srcPath   源路径
     * @param destPath  目标路径
     * @param overwrite 是否覆盖
     * @return true 表示移动成功
     */
    private fun moveImpl(failureReporter: FileFailureCallbacks.Reporter, srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!exists(srcPath)) return failureReporter.fail(false, "移动失败：源文件不存在：$srcPath")

        try {
            val src = File(srcPath)
            val dest = File(destPath).absoluteFile
            if (src.canonicalFile == dest.canonicalFile) return true

            if (dest.exists() && !overwrite) return failureReporter.fail(false, "移动失败：目标已存在且禁止覆盖：$destPath")
            if (!ensureParentDirectory(failureReporter, dest)) return failureReporter.fail(false, "移动失败：无法创建父目录：${dest.parent}")

            // 先尝试 rename（同一文件系统下极快）
            if (src.renameTo(dest)) return true

            // 跨文件系统则走 copy+delete
            val success = if (src.isDirectory) {
                copyDirectoryImpl(failureReporter, srcPath, destPath, overwrite)
            } else {
                copyImpl(failureReporter, srcPath, destPath, overwrite)
            }

            if (!success) return failureReporter.fail(false, "移动失败：跨文件系统复制未完成：$srcPath → $destPath")
            if (!deleteRecursive(failureReporter, src)) return failureReporter.fail(false, "移动未完成：目标已复制但源文件未删除：$srcPath")
            return true
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            return false
        }
    }

    /**
     * 移动文件（异步）
     */
    @JvmStatic
    @JvmOverloads
    suspend fun moveSuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(move(srcPath, destPath, overwrite, onFailure = onFailure)) } }
    }

    // ==================== 文件列表 ====================

    /**
     * 列出目录中所有文件和子目录
     *
     * @param path 目录路径
     * @return 文件列表，失败或为空返回 emptyList
     */
    @JvmStatic
    fun listFiles(path: String): List<File> {
        if (!isDirectory(path)) return emptyList()
        return File(path).listFiles()?.toList() ?: emptyList()
    }

    /**
     * 列出目录中符合过滤条件的文件
     *
     * @param path   目录路径
     * @param filter 文件过滤器
     * @return 匹配的文件列表
     *
     * 使用示例:
     * ```kotlin
     * // 列出所有 .jpg 文件
     * val jpgs = FileUtils.listFiles("/sdcard/DCIM") { file ->
     *     file.extension.equals("jpg", ignoreCase = true)
     * }
     * ```
     */
    @JvmStatic
    fun listFiles(path: String, filter: (File) -> Boolean): List<File> {
        return listFiles(path).filter(filter)
    }

    /**
     * 列出目录中指定扩展名的所有文件
     *
     * @param path      目录路径
     * @param extension 扩展名（不含点），如 "jpg"
     * @return 匹配的文件列表
     */
    @JvmStatic
    fun listFilesByExtension(path: String, extension: String): List<File> {
        return listFiles(path) { file ->
            file.isFile && file.extension.equals(extension, ignoreCase = true)
        }
    }

    /**
     * 递归获取目录下所有文件
     *
     * @param path 目录路径
     * @return 所有文件列表（含子目录中的文件）
     */
    @JvmStatic
    fun listFilesRecursive(path: String): List<File> {
        return try {
            val result = mutableListOf<File>()
            val dir = File(path)
            if (!dir.isDirectory) return result
            dir.walkTopDown().forEach { file ->
                if (file.isFile) result.add(file)
            }
            result
        } catch (e: Exception) {
            FileFailureCallbacks.rethrowCancellation(e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 递归获取目录下所有文件（异步）
     */
    @JvmStatic
    suspend fun listFilesRecursiveSuspend(path: String, onResult: (List<File>) -> Unit): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(listFilesRecursive(path)) } }
    }

    // ==================== 文件信息 ====================

    /**
     * 获取文件/目录大小（字节）
     *
     * @param path 路径
     * @return 字节数，目录返回所有子文件的总和；失败返回 -1
     */
    private fun getSizeImpl(failureReporter: FileFailureCallbacks.Reporter, path: String?): Long {
        if (!exists(path)) return failureReporter.fail(-1L, "读取大小失败：文件不存在：$path")
        val file = File(path!!)
        return if (file.isFile) {
            file.length()
        } else {
            var total = 0L
            file.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length()
            }
            total
        }
    }

    /**
     * 获取文件大小（异步）
     *
     * @param path     路径
     * @param onResult 回调 (sizeBytes: Long, -1 表示失败)
     * @return Unit
     */
    @JvmStatic
    @JvmOverloads
    suspend fun getSizeSuspend(
        path: String?,
        onResult: (Long) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(getSize(path, onFailure = onFailure)) } }
    }

    /**
     * 获取格式化的文件大小字符串
     *
     * @param path 路径
     * @return 如 "1.50 MB"，失败返回 "Unknown"
     */
    private fun getSizeFormattedImpl(failureReporter: FileFailureCallbacks.Reporter, path: String?): String {
        val size = getSizeImpl(failureReporter, path)
        if (size < 0) return "Unknown"
        return formatFileSize(size)
    }

    /**
     * 获取文件扩展名（不含点，小写）
     *
     * @param path 文件路径
     * @return 扩展名，如 "jpg"
     */
    @JvmStatic
    fun getExtension(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return File(path).extension.lowercase(Locale.ROOT)
    }

    /**
     * 获取文件名（含扩展名）
     *
     * @param path 文件路径
     * @return 文件名，如 "photo.jpg"
     */
    @JvmStatic
    fun getFileName(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return File(path).name
    }

    /**
     * 获取文件名（不含扩展名）
     *
     * @param path 文件路径
     * @return 不含扩展名的文件名，如 "photo"
     */
    @JvmStatic
    fun getFileNameWithoutExtension(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return File(path).nameWithoutExtension
    }

    /**
     * 获取文件的 MIME 类型
     *
     * @param path 文件路径
     * @return MIME 类型，如 "image/jpeg"，未知返回 "application/octet-stream"
     */
    @JvmStatic
    fun getMimeType(path: String?): String {
        val extension = getExtension(path)
        if (extension.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    /**
     * 获取文件的 MIME 类型（从 URI）
     *
     * @param context Android Context
     * @param uri     文件 URI
     * @return MIME 类型字符串
     */
    @JvmStatic
    fun getMimeType(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (e: Exception) {
            FileFailureCallbacks.rethrowCancellation(e)
            "application/octet-stream"
        }
    }

    /**
     * 获取文件最后修改时间
     *
     * @param path   文件路径
     * @param format 时间格式，默认 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化的时间字符串，失败返回 ""
     */
    private fun getLastModifiedImpl(failureReporter: FileFailureCallbacks.Reporter, path: String, format: String = "yyyy-MM-dd HH:mm:ss"): String {
        if (!exists(path)) return failureReporter.fail("", "读取修改时间失败：文件不存在：$path")
        return try {
            val date = Date(File(path).lastModified())
            SimpleDateFormat(format, Locale.getDefault()).format(date)
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            ""
        }
    }

    /**
     * 获取文件最后修改时间戳（毫秒）
     */
    private fun getLastModifiedMillisImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Long {
        if (!exists(path)) return failureReporter.fail(-1L, "读取修改时间失败：文件不存在：$path")
        return File(path).lastModified()
    }

    /**
     * 获取文件父目录路径
     */
    @JvmStatic
    fun getParentPath(path: String): String {
        return File(path).parent ?: ""
    }

    /**
     * 获取文件详细信息
     *
     * @param path 文件路径
     * @return 包含各项属性的 Map
     */
    @JvmStatic
    fun getFileInfo(path: String): Map<String, Any> {
        if (!exists(path)) return mapOf("exists" to false)
        val file = File(path)
        return mapOf(
            "exists" to true,
            "name" to file.name,
            "nameWithoutExtension" to file.nameWithoutExtension,
            "extension" to file.extension,
            "path" to file.absolutePath,
            "parent" to (file.parent ?: ""),
            "isFile" to file.isFile,
            "isDirectory" to file.isDirectory,
            "isHidden" to file.isHidden,
            "size" to (if (file.isFile) file.length() else -1L),
            "sizeFormatted" to formatFileSize(bytes = if (file.isFile) file.length() else -1L),
            "lastModified" to getLastModified(path),
            "canRead" to file.canRead(),
            "canWrite" to file.canWrite(),
            "canExecute" to file.canExecute(),
            "mimeType" to (if (file.isFile) getMimeType(path) else "directory")
        )
    }

    /**
     * 获取文件信息（异步）
     */
    @JvmStatic
    suspend fun getFileInfoSuspend(path: String, onResult: (Map<String, Any>) -> Unit): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(getFileInfo(path)) } }
    }

    // ==================== 存储空间 ====================

    /**
     * 获取指定路径所在分区的可用空间（字节）
     *
     * @param path 路径（用于确定分区）
     * @return 可用空间字节数，失败返回 -1
     */
    private fun getAvailableSpaceImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Long {
        return try {
            val stat = StatFs(path)
            stat.availableBytes
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            -1L
        }
    }

    /**
     * 获取指定路径所在分区的总空间（字节）
     */
    private fun getTotalSpaceImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Long {
        return try {
            val stat = StatFs(path)
            stat.totalBytes
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            -1L
        }
    }

    /**
     * 获取内置存储的可用空间
     */
    private fun getInternalAvailableSpaceImpl(failureReporter: FileFailureCallbacks.Reporter): Long {
        return getAvailableSpaceImpl(failureReporter, Environment.getDataDirectory().absolutePath)
    }

    /**
     * 获取 SD 卡可用空间（无 SD 卡返回 -1）
     */
    private fun getExternalAvailableSpaceImpl(failureReporter: FileFailureCallbacks.Reporter): Long {
        return try {
            val external = Environment.getExternalStorageDirectory()
            getAvailableSpaceImpl(failureReporter, external.absolutePath)
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            -1L
        }
    }

    // ==================== 内部方法 ====================

    private fun deleteRecursive(failureReporter: FileFailureCallbacks.Reporter, file: File): Boolean {
        try {
            if (file.absoluteFile != file.canonicalFile) {
                if (file.delete()) return true
                return failureReporter.fail(false, "删除失败：无法删除符号链接：${file.path}")
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            return false
        }
        if (file.isDirectory) {
            val children = file.listFiles()
                ?: return failureReporter.fail(false, "删除失败：无法列出目录内容：${file.path}")
            children.forEach { child ->
                if (!deleteRecursive(failureReporter, child)) {
                    return failureReporter.fail(false, "删除失败：子项无法删除：${child.path}")
                }
            }
        }
        if (file.delete()) return true
        return failureReporter.fail(false, "删除失败：目标无法删除：${file.path}")
    }

    private inline fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        destination: File,
        overwrite: Boolean,
        writer: (File) -> Unit
    ): Boolean {
        if (destination.exists()) {
            if (!overwrite) return failureReporter.fail(false, "写入失败：目标已存在且禁止覆盖：${destination.path}")
            if (destination.isDirectory) return failureReporter.fail(false, "写入失败：目标路径是目录：${destination.path}")
        }
        if (!ensureParentDirectory(failureReporter, destination)) {
            return failureReporter.fail(false, "写入失败：无法创建父目录：${destination.parent}")
        }

        var tempFile: File? = null
        return try {
            val parent = destination.absoluteFile.parentFile
                ?: return failureReporter.fail(false, "写入失败：目标没有父目录：${destination.path}")
            val prefix = (destination.name.ifBlank { "file" }.take(32) + "_").padEnd(3, '_')
            tempFile = File.createTempFile(prefix, ".tmp", parent)
            writer(tempFile)
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            Os.rename(tempFile.absolutePath, destination.absolutePath)
            true
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } finally {
            tempFile?.takeIf { it.exists() }?.delete()
        }
    }

    private fun ensureParentDirectory(failureReporter: FileFailureCallbacks.Reporter, file: File): Boolean {
        return try {
            val parent = file.absoluteFile.parentFile
                ?: return failureReporter.fail(false, "无法确定父目录：${file.path}")
            if (parent.isDirectory || parent.mkdirs() || parent.isDirectory) true
            else failureReporter.fail(false, "无法创建父目录：${parent.path}")
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (e: Throwable) {
            failureReporter.rethrow(e)
            e.printStackTrace()
        }
    }

    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** 读取文件大小；失败时通过 onFailure 通知调用方。 */
    @JvmStatic
    @JvmOverloads
    fun getSize(
        path: String?,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("FileUtils.getSize", "$path", onFailure, { it < 0L }, -1L) { failureReporter ->
            getSizeImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getLastModifiedMillis(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("FileUtils.getLastModifiedMillis", path, onFailure, { it < 0L }, -1L) { failureReporter ->
            getLastModifiedMillisImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getLastModified(
        path: String,
        format: String = "yyyy-MM-dd HH:mm:ss",
        onFailure: ((Exception) -> Unit)? = null
    ): String =
        FileFailureCallbacks.run("FileUtils.getLastModified", path, onFailure, { it.isEmpty() && format.isNotEmpty() }, "") { failureReporter ->
            getLastModifiedImpl(failureReporter, path, format)
        }

    @JvmStatic
    @JvmOverloads
    fun getSizeFormatted(
        path: String?,
        onFailure: ((Exception) -> Unit)? = null
    ): String =
        FileFailureCallbacks.run("FileUtils.getSizeFormatted", "$path", onFailure, { it == "Unknown" }, "Unknown") { failureReporter ->
            getSizeFormattedImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getAvailableSpace(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("FileUtils.getAvailableSpace", path, onFailure, { it < 0L }, -1L) { failureReporter ->
            getAvailableSpaceImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getTotalSpace(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("FileUtils.getTotalSpace", path, onFailure, { it < 0L }, -1L) { failureReporter ->
            getTotalSpaceImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getInternalAvailableSpace(onFailure: ((Exception) -> Unit)? = null): Long =
        FileFailureCallbacks.run("FileUtils.getInternalAvailableSpace", "internal storage", onFailure, { it < 0L }, -1L) { failureReporter ->
            getInternalAvailableSpaceImpl(failureReporter)
        }

    @JvmStatic
    @JvmOverloads
    fun getExternalAvailableSpace(onFailure: ((Exception) -> Unit)? = null): Long =
        FileFailureCallbacks.run("FileUtils.getExternalAvailableSpace", "external storage", onFailure, { it < 0L }, -1L) { failureReporter ->
            getExternalAvailableSpaceImpl(failureReporter)
        }

    /** createFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun createFile(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.createFile", "$path", onFailure, { !it }, false) { failureReporter ->
            createFileImpl(failureReporter, path)
        }

    /** createDirectory 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun createDirectory(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.createDirectory", "$path", onFailure, { !it }, false) { failureReporter ->
            createDirectoryImpl(failureReporter, path)
        }

    /** createTempFile 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun createTempFile(
        prefix: String = "itg_",
        suffix: String = ".tmp",
        directory: File? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): File? =
        FileFailureCallbacks.run("FileUtils.createTempFile", "$prefix", onFailure, { it == null }, null) { failureReporter ->
            createTempFileImpl(failureReporter, prefix, suffix, directory)
        }

    /** delete 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun delete(
        path: String?,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.delete", "$path", onFailure, { !it }, false) { failureReporter ->
            deleteImpl(failureReporter, path)
        }

    /** clearDirectory 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun clearDirectory(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.clearDirectory", "$path", onFailure, { !it }, false) { failureReporter ->
            clearDirectoryImpl(failureReporter, path)
        }

    /** rename 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun rename(
        path: String,
        newName: String,
        onFailure: ((Exception) -> Unit)? = null
    ): String? =
        FileFailureCallbacks.run("FileUtils.rename", "$path", onFailure, { it == null }, null) { failureReporter ->
            renameImpl(failureReporter, path, newName)
        }

    /** copy 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copy(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.copy", "$srcPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyImpl(failureReporter, srcPath, destPath, overwrite)
        }

    /** copyWithProgress 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyWithProgress(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.copyWithProgress", "$srcPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyWithProgressImpl(failureReporter, srcPath, destPath, overwrite, onProgress)
        }

    /** copyDirectory 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun copyDirectory(
        srcDir: String,
        destDir: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.copyDirectory", "$destDir", onFailure, { !it }, false) { failureReporter ->
            copyDirectoryImpl(failureReporter, srcDir, destDir, overwrite)
        }

    /** move 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun move(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("FileUtils.move", "$srcPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            moveImpl(failureReporter, srcPath, destPath, overwrite)
        }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        val absBytes = bytes.absoluteValue
        return when {
            absBytes < 1024L -> "$bytes B"
            absBytes < 1024L * 1024L -> "%.2f KB".format(absBytes / 1024.0)
            absBytes < 1024L * 1024L * 1024L -> "%.2f MB".format(absBytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(absBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // 兼容原有尾随 lambda 调用

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("createFileSuspendLegacyCallback")
    suspend fun createFileSuspend(
        path: String,
        onResult: (Boolean) -> Unit
    ) = createFileSuspend(path = path, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("createDirectorySuspendLegacyCallback")
    suspend fun createDirectorySuspend(
        path: String,
        onResult: (Boolean) -> Unit
    ) = createDirectorySuspend(path = path, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("deleteSuspendLegacyCallback")
    suspend fun deleteSuspend(
        path: String?,
        onResult: (Boolean) -> Unit
    ) = deleteSuspend(path = path, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("clearDirectorySuspendLegacyCallback")
    suspend fun clearDirectorySuspend(
        path: String,
        onResult: (Boolean) -> Unit
    ) = clearDirectorySuspend(path = path, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("renameSuspendLegacyCallback")
    suspend fun renameSuspend(
        path: String,
        newName: String,
        onResult: (String?) -> Unit
    ) = renameSuspend(path = path, newName = newName, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copySuspendLegacyCallback")
    suspend fun copySuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = copySuspend(srcPath = srcPath, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyWithProgressSuspendLegacyCallback")
    suspend fun copyWithProgressSuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) = copyWithProgressSuspend(srcPath = srcPath, destPath = destPath, overwrite = overwrite, onProgress = onProgress, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("moveSuspendLegacyCallback")
    suspend fun moveSuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = moveSuspend(srcPath = srcPath, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("getSizeSuspendLegacyCallback")
    suspend fun getSizeSuspend(
        path: String?,
        onResult: (Long) -> Unit
    ) = getSizeSuspend(path = path, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("copyWithProgressLegacyCallback")
    fun copyWithProgress(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null
    ) = copyWithProgress(srcPath = srcPath, destPath = destPath, overwrite = overwrite, onProgress = onProgress, onFailure = null)
}
