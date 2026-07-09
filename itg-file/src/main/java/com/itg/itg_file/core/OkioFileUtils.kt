package com.itg.itg_file.core
import com.itg.itg_thread_pools.executor.TaskExecutor
import android.os.StatFs
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Okio 文件基础操作工具类
 *
 * 基于 Okio 的 [okio.BufferedSource] / [okio.BufferedSink] 实现高效的文件操作，相比传统 [java.io.File]：
 * - 更高效的缓冲 I/O（Okio Buffer 零拷贝）
 * - 内置超时控制
 * - 原子性操作支持
 *
 * 所有同步方法直接阻塞执行；异步方法通过 [TaskExecutor] 在 I/O 线程池执行。
 *
 * 核心特性:
 * - 快速复制（Okio Buffer 优化，零拷贝路径）
 * - 快速移动（同文件系统 rename + 跨文件系统 copy/delete）
 * - 文件元数据查询（大小、修改时间等）
 * - 目录递归遍历
 * - 磁盘空间查询
 * - 超时控制的 I/O 操作
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
object OkioFileUtils {

    private const val DEFAULT_BUFFER_SIZE = 8192L  // 8KB

    // ==================== 存在性与元数据 ====================

    /**
     * 检查路径是否存在（Okio 实现）
     *
     * 通过 java.io.File 检查，统一接口。
     */
    @JvmStatic
    fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查路径是否存在（异步）
     */
    @JvmStatic
    fun existsAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { safeCallback { onResult(exists(path)) } }
    }

    /**
     * 获取文件大小（字节）
     */
    @JvmStatic
    fun getSize(path: String): Long {
        return try {
            val file = File(path)
            if (file.isFile) file.length() else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 获取文件最后修改时间戳（毫秒）
     */
    @JvmStatic
    fun getLastModifiedMillis(path: String): Long {
        return try {
            File(path).lastModified()
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 检查路径是否为常规文件
     */
    @JvmStatic
    fun isRegularFile(path: String): Boolean {
        return try {
            File(path).isFile
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查路径是否为目录
     */
    @JvmStatic
    fun isDirectory(path: String): Boolean {
        return try {
            File(path).isDirectory
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 快速复制 ====================

    /**
     * 使用 Okio 快速复制文件
     *
     * Okio 的 Buffer 实现比 java.io 标准流更高效，
     * 支持 segment 级别的零拷贝传输。
     *
     * @param srcPath   源文件路径
     * @param destPath  目标文件路径
     * @param overwrite 是否覆盖已存在的文件
     * @return true 表示复制成功
     *
     * 使用示例:
     * ```kotlin
     * // Okio 复制通常比 java.io 快 2-3x
     * OkioFileUtils.copy("/sdcard/large.zip", "/sdcard/backup/large.zip")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun copy(srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!exists(srcPath)) return false

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return false

            if (destFile.exists() && !overwrite) return false

            writeToFileAtomically(destFile, overwrite) { tempFile ->
                srcFile.source().buffer().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步复制
     */
    @JvmStatic
    @JvmOverloads
    fun copyAsync(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { safeCallback { onResult(copy(srcPath, destPath, overwrite)) } }
    }

    // ==================== 移动 ====================

    /**
     * 使用 Okio 移动文件
     *
     * 先尝试 rename（同一文件系统），失败则走 copy+delete。
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

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return true

            if (destFile.exists() && !overwrite) return false
            ensureParentDirectory(destFile)

            // 先尝试 rename（同一文件系统下极快）
            if (srcFile.renameTo(destFile)) return true

            // 跨文件系统则走 copy+delete
            val success = copy(srcPath, destPath, overwrite)
            if (success) {
                srcFile.deleteRecursively()
            }
            success
        } catch (e: Exception) {
            // rename 失败，回退到 copy+delete
            try {
                if (copy(srcPath, destPath, overwrite)) {
                    delete(srcPath)
                    true
                } else false
            } catch (e2: Exception) {
                e2.printStackTrace()
                false
            }
        }
    }

    /**
     * 异步移动
     */
    @JvmStatic
    fun moveAsync(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io { safeCallback { onResult(move(srcPath, destPath, overwrite)) } }
    }

    // ==================== 删除 ====================

    /**
     * 删除文件或递归删除目录
     *
     * @param path 文件/目录路径
     * @return true 表示删除成功
     */
    @JvmStatic
    fun delete(path: String): Boolean {
        if (!exists(path)) return false
        return try {
            File(path).deleteRecursively()
        } catch (e: Exception) {
            // 回退到 FileUtils 递归删除
            FileUtils.delete(path)
        }
    }

    /**
     * 异步删除
     */
    @JvmStatic
    fun deleteAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { safeCallback { onResult(delete(path)) } }
    }

    /**
     * 创建目录（递归）
     *
     * @param path 目录路径
     * @return true 表示创建成功或已存在
     */
    @JvmStatic
    fun createDirectory(path: String): Boolean {
        return try {
            val dir = File(path)
            dir.exists() || dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步创建目录
     */
    @JvmStatic
    fun createDirectoryAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { safeCallback { onResult(createDirectory(path)) } }
    }

    // ==================== 目录遍历 ====================

    /**
     * 递归列出目录下所有文件
     *
     * 使用 kotlin File.walkTopDown() 遍历。
     *
     * @param path 目录路径
     * @return 文件列表
     */
    @JvmStatic
    fun listRecursively(path: String): List<File> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        return try {
            dir.walkTopDown().filter { it.isFile }.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 递归列出（异步）
     */
    @JvmStatic
    fun listRecursivelyAsync(path: String, onResult: (List<String>) -> Unit): Future<*> {
        return TaskExecutor.io {
            val files = listRecursively(path).map { it.absolutePath }
            safeCallback { onResult(files) }
        }
    }

    /**
     * 列出目录直接子项
     */
    @JvmStatic
    fun list(path: String): List<File> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        return try {
            dir.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 磁盘空间 ====================

    /**
     * 获取指定路径所在分区的可用空间
     *
     * 通过 Android [StatFs] 查询。
     *
     * @param path 路径（用于确定所在分区）
     * @return 可用字节数，失败返回 -1
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
     * 获取指定路径所在分区的总空间
     *
     * @param path 路径（用于确定所在分区）
     * @return 总字节数，失败返回 -1
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

    // ==================== 超时控制 ====================

    /**
     * 文件操作超时包装
     *
     * 适用于需要设置超时的 I/O 操作场景。
     *
     * @param timeoutMs 超时毫秒数
     * @param block     文件操作
     * @return 操作结果，超时返回 null
     */
    @JvmStatic
    fun <T> withTimeout(timeoutMs: Long, block: () -> T): T? {
        return try {
            val future = TaskExecutor.io<T> { block() }
            TaskExecutor.await(future, timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            android.util.Log.w("OkioFileUtils", "Operation timed out after ${timeoutMs}ms")
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步超时操作
     */
    @JvmStatic
    fun <T> withTimeoutAsync(
        timeoutMs: Long,
        block: () -> T,
        onResult: (T?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            val result = withTimeout(timeoutMs, block)
            safeCallback { onResult(result) }
        }
    }

    private fun writeToFileAtomically(
        destFile: File,
        overwrite: Boolean,
        writer: (File) -> Unit
    ): Boolean {
        var tempFile: File? = null
        return try {
            val canonicalDest = destFile.canonicalFile
            if (canonicalDest.exists()) {
                if (!overwrite || canonicalDest.isDirectory) return false
            }
            val parent = ensureParentDirectory(canonicalDest) ?: return false
            tempFile = File.createTempFile(".${canonicalDest.name}.", ".tmp", parent)
            writer(tempFile)
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            if (canonicalDest.exists() && !overwrite) return false
            replaceFile(tempFile, canonicalDest)
            tempFile = null
            true
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

    private fun replaceFile(tempFile: File, destFile: File) {
        if (tempFile.renameTo(destFile)) return
        if (destFile.exists() && !destFile.delete()) {
            throw IOException("Failed to delete destination: ${destFile.absolutePath}")
        }
        if (!tempFile.renameTo(destFile)) {
            throw IOException("Failed to move temp file to destination: ${destFile.absolutePath}")
        }
    }

    private fun ensureParentDirectory(file: File): File? {
        val parent = file.parentFile ?: return null
        if (parent.exists()) return if (parent.isDirectory) parent else null
        return if (parent.mkdirs()) parent else null
    }

    private inline fun safeCallback(callback: () -> Unit) {
        try {
            callback()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
