package com.itg.itg_file.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Future
import kotlin.math.absoluteValue

/**
 * 文件基础操作工具类
 *
 * 提供文件/目录的创建、删除、重命名、复制、移动、信息获取等核心操作。
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
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
     * @return [Future] 可用于取消
     */
    @JvmStatic
    fun existsAsync(path: String?, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(exists(path)) }
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
    @JvmStatic
    fun createFile(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (file.exists()) return true
            file.parentFile?.mkdirs()
            file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建文件（异步）
     *
     * @param path     文件路径
     * @param onResult 回调 (success: Boolean)
     * @return [Future]
     */
    @JvmStatic
    fun createFileAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(createFile(path)) }
    }

    /**
     * 创建目录（含父目录自动创建）
     *
     * @param path 目录路径
     * @return true 表示创建成功或目录已存在
     */
    @JvmStatic
    fun createDirectory(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) return true
            dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建目录（异步）
     */
    @JvmStatic
    fun createDirectoryAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(createDirectory(path)) }
    }

    /**
     * 创建临时文件
     *
     * @param prefix 文件名前缀，默认 "itg_"
     * @param suffix 文件名后缀，默认 ".tmp"
     * @param directory 存放目录，默认系统临时目录
     * @return 临时文件，失败返回 null
     */
    @JvmStatic
    @JvmOverloads
    fun createTempFile(
        prefix: String = "itg_",
        suffix: String = ".tmp",
        directory: File? = null
    ): File? {
        return try {
            File.createTempFile(prefix, suffix, directory)
        } catch (e: IOException) {
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
    @JvmStatic
    fun delete(path: String?): Boolean {
        if (!exists(path)) return false
        return deleteRecursive(File(path!!))
    }

    /**
     * 删除文件（异步）
     *
     * @param path     路径
     * @param onResult 回调 (success: Boolean)
     * @return [Future]
     */
    @JvmStatic
    fun deleteAsync(path: String?, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(delete(path)) }
    }

    /**
     * 清空目录内容（保留目录自身）
     *
     * @param path 目录路径
     * @return true 表示清空成功
     */
    @JvmStatic
    fun clearDirectory(path: String): Boolean {
        if (!isDirectory(path)) return false
        val dir = File(path)
        var success = true
        dir.listFiles()?.forEach { child ->
            if (!deleteRecursive(child)) success = false
        }
        return success
    }

    /**
     * 清空目录（异步）
     */
    @JvmStatic
    fun clearDirectoryAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(clearDirectory(path)) }
    }

    // ==================== 重命名 ====================

    /**
     * 重命名文件或目录
     *
     * @param path    原路径
     * @param newName 新名称（不含路径）
     * @return 新文件路径，失败返回 null
     */
    @JvmStatic
    fun rename(path: String, newName: String): String? {
        if (!exists(path)) return null
        val file = File(path)
        val newFile = File(file.parent, newName)
        return if (file.renameTo(newFile)) newFile.absolutePath else null
    }

    /**
     * 重命名文件（异步）
     */
    @JvmStatic
    fun renameAsync(path: String, newName: String, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(rename(path, newName)) }
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
    @JvmStatic
    @JvmOverloads
    fun copy(srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!isFile(srcPath)) return false

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return false
            if (destFile.exists() && !overwrite) return false
            destFile.parentFile?.mkdirs()

            FileInputStream(srcFile).use { input ->
                FileOutputStream(destFile).use { output ->
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
                                if (transferred <= 0L) return false
                                position += transferred
                            }
                        }
                    }
                }
            }
            true
        } catch (e: IOException) {
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
    @JvmStatic
    @JvmOverloads
    fun copyWithProgress(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null
    ): Boolean {
        if (!isFile(srcPath)) return false

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return false
            if (destFile.exists() && !overwrite) return false
            destFile.parentFile?.mkdirs()
            val totalSize = srcFile.length()

            FileInputStream(srcFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var bytesCopied = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        onProgress?.invoke(bytesCopied, totalSize)
                    }
                    output.flush()
                }
            }
            true
        } catch (e: IOException) {
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
     * @return [Future]
     */
    @JvmStatic
    fun copyAsync(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(copy(srcPath, destPath, overwrite)) }
    }

    /**
     * 复制文件（异步，带进度）
     */
    @JvmStatic
    fun copyWithProgressAsync(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(copyWithProgress(srcPath, destPath, overwrite, onProgress))
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
    @JvmStatic
    @JvmOverloads
    fun copyDirectory(srcDir: String, destDir: String, overwrite: Boolean = true): Boolean {
        return copyDirectoryInternal(srcDir, destDir, overwrite, mutableSetOf())
    }

    private fun copyDirectoryInternal(
        srcDir: String,
        destDir: String,
        overwrite: Boolean,
        visitedDirectories: MutableSet<String>
    ): Boolean {
        if (!isDirectory(srcDir)) return false
        return try {
            val src = File(srcDir)
            val dest = File(destDir)
            val srcCanonical = src.canonicalFile
            val destCanonical = dest.canonicalFile
            if (!visitedDirectories.add(srcCanonical.path)) return false
            if (srcCanonical == destCanonical ||
                destCanonical.path.startsWith(
                    srcCanonical.path.trimEnd(File.separatorChar) + File.separator
                )
            ) return false
            if (!dest.exists() && !dest.mkdirs()) return false

            var success = true
            src.listFiles()?.forEach { child ->
                val childDest = File(dest, child.name)
                val copied = if (child.isDirectory) {
                    copyDirectoryInternal(
                        child.absolutePath,
                        childDest.absolutePath,
                        overwrite,
                        visitedDirectories
                    )
                } else {
                    copy(child.absolutePath, childDest.absolutePath, overwrite)
                }
                if (!copied) success = false
            }
            success
        } catch (e: IOException) {
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
    @JvmStatic
    @JvmOverloads
    fun move(srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!exists(srcPath)) return false

        try {
            val src = File(srcPath)
            val dest = File(destPath)
            if (src.canonicalFile == dest.canonicalFile) return true

            if (dest.exists() && !overwrite) return false
            dest.parentFile?.mkdirs()

            // 先尝试 rename（同一文件系统下极快）
            if (src.renameTo(dest)) return true

            // 跨文件系统则走 copy+delete
            val success = if (src.isDirectory) {
                copyDirectory(srcPath, destPath, overwrite)
            } else {
                copy(srcPath, destPath, overwrite)
            }

            if (success) {
                deleteRecursive(src)
            }
            return success
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 移动文件（异步）
     */
    @JvmStatic
    fun moveAsync(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(move(srcPath, destPath, overwrite)) }
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
        val result = mutableListOf<File>()
        val dir = File(path)
        if (!dir.isDirectory) return result
        dir.walkTopDown().forEach { file ->
            if (file.isFile) result.add(file)
        }
        return result
    }

    /**
     * 递归获取目录下所有文件（异步）
     */
    @JvmStatic
    fun listFilesRecursiveAsync(path: String, onResult: (List<File>) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(listFilesRecursive(path)) }
    }

    // ==================== 文件信息 ====================

    /**
     * 获取文件/目录大小（字节）
     *
     * @param path 路径
     * @return 字节数，目录返回所有子文件的总和；失败返回 -1
     */
    @JvmStatic
    fun getSize(path: String?): Long {
        if (!exists(path)) return -1L
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
     * @return [Future]
     */
    @JvmStatic
    fun getSizeAsync(path: String?, onResult: (Long) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getSize(path)) }
    }

    /**
     * 获取格式化的文件大小字符串
     *
     * @param path 路径
     * @return 如 "1.50 MB"，失败返回 "Unknown"
     */
    @JvmStatic
    fun getSizeFormatted(path: String?): String {
        val size = getSize(path)
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
    @JvmStatic
    @JvmOverloads
    fun getLastModified(path: String, format: String = "yyyy-MM-dd HH:mm:ss"): String {
        if (!exists(path)) return ""
        return try {
            val date = Date(File(path).lastModified())
            SimpleDateFormat(format, Locale.getDefault()).format(date)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 获取文件最后修改时间戳（毫秒）
     */
    @JvmStatic
    fun getLastModifiedMillis(path: String): Long {
        return if (exists(path)) File(path).lastModified() else -1L
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
            "sizeFormatted" to formatFileSize(if (file.isFile) file.length() else -1L),
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
    fun getFileInfoAsync(path: String, onResult: (Map<String, Any>) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getFileInfo(path)) }
    }

    // ==================== 存储空间 ====================

    /**
     * 获取指定路径所在分区的可用空间（字节）
     *
     * @param path 路径（用于确定分区）
     * @return 可用空间字节数，失败返回 -1
     */
    @JvmStatic
    fun getAvailableSpace(path: String): Long {
        return try {
            val stat = StatFs(path)
            stat.availableBytes
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 获取指定路径所在分区的总空间（字节）
     */
    @JvmStatic
    fun getTotalSpace(path: String): Long {
        return try {
            val stat = StatFs(path)
            stat.totalBytes
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 获取内置存储的可用空间
     */
    @JvmStatic
    fun getInternalAvailableSpace(): Long {
        return getAvailableSpace(Environment.getDataDirectory().absolutePath)
    }

    /**
     * 获取 SD 卡可用空间（无 SD 卡返回 -1）
     */
    @JvmStatic
    fun getExternalAvailableSpace(): Long {
        return try {
            val external = Environment.getExternalStorageDirectory()
            getAvailableSpace(external.absolutePath)
        } catch (e: Exception) {
            -1L
        }
    }

    // ==================== 内部方法 ====================

    private fun deleteRecursive(file: File): Boolean {
        try {
            if (file.absoluteFile != file.canonicalFile) return file.delete()
        } catch (e: IOException) {
            return false
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursive(child)) return false
            }
        }
        return file.delete()
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
}
