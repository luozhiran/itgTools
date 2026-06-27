package com.itg.itg_file.core
import com.itg.itg_thread_pools.executor.TaskExecutor
import android.os.StatFs
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Okio 文件基础操作工具类
 *
 * 基于 Okio 的 [FileSystem] 实现高效的文件操作，相比传统 [java.io.File]：
 * - 更高效的缓冲 I/O（Okio Buffer 零拷贝）
 * - 内置超时控制
 * - 跨平台 Path 抽象
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

    private val fileSystem: FileSystem = FileSystem.SYSTEM
    private const val DEFAULT_BUFFER_SIZE = 8192L  // 8KB

    // ==================== Path 转换 ====================

    /**
     * 将文件路径字符串转为 Okio [Path]
     */
    @JvmStatic
    fun toPath(path: String): Path = path.toPath()

    /**
     * 将 Okio [Path] 转为文件路径字符串
     */
    @JvmStatic
    fun fromPath(path: Path): String = path.toString()

    // ==================== 存在性与元数据 ====================

    /**
     * 检查路径是否存在（Okio 实现）
     */
    @JvmStatic
    fun exists(path: String): Boolean {
        return try {
            fileSystem.exists(path.toPath())
        } catch (e: IOException) {
            false
        }
    }

    /**
     * 检查路径是否存在（异步）
     */
    @JvmStatic
    fun existsAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(exists(path)) }
    }

    /**
     * 获取文件大小（字节）
     */
    @JvmStatic
    fun getSize(path: String): Long {
        return try {
            fileSystem.metadata(path.toPath()).size ?: -1L
        } catch (e: IOException) {
            -1L
        }
    }

    /**
     * 获取文件最后修改时间戳（毫秒）
     */
    @JvmStatic
    fun getLastModifiedMillis(path: String): Long {
        return try {
            fileSystem.metadata(path.toPath()).lastModifiedAtMillis ?: -1L
        } catch (e: IOException) {
            -1L
        }
    }

    /**
     * 检查路径是否为常规文件
     */
    @JvmStatic
    fun isRegularFile(path: String): Boolean {
        return try {
            fileSystem.metadata(path.toPath()).isRegularFile
        } catch (e: IOException) {
            false
        }
    }

    /**
     * 检查路径是否为目录
     */
    @JvmStatic
    fun isDirectory(path: String): Boolean {
        return try {
            fileSystem.metadata(path.toPath()).isDirectory
        } catch (e: IOException) {
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
            if (File(srcPath).canonicalFile == File(destPath).canonicalFile) return false
            val source = srcPath.toPath()
            val target = destPath.toPath()
            val targetFile = File(destPath)

            if (FileUtils.exists(destPath) && !overwrite) return false
            targetFile.parentFile?.mkdirs()

            fileSystem.copy(source, target)
            true
        } catch (e: IOException) {
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
        return TaskExecutor.io { onResult(copy(srcPath, destPath, overwrite)) }
    }

    // ==================== 移动 ====================

    /**
     * 使用 Okio 移动文件
     *
     * 先尝试原子移动（同一文件系统），失败则走 copy+delete。
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
            if (File(srcPath).canonicalFile == File(destPath).canonicalFile) return true
            val source = srcPath.toPath()
            val target = destPath.toPath()
            val targetFile = File(destPath)

            if (FileUtils.exists(destPath) && !overwrite) return false
            targetFile.parentFile?.mkdirs()

            fileSystem.atomicMove(source, target)
            true
        } catch (e: IOException) {
            // 原子移动失败（跨文件系统），回退到 copy+delete
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
        return TaskExecutor.io { onResult(move(srcPath, destPath, overwrite)) }
    }

    // ==================== 删除 ====================

    /**
     * 使用 Okio 删除文件或递归删除目录
     *
     * @param path 文件/目录路径
     * @return true 表示删除成功
     */
    @JvmStatic
    fun delete(path: String): Boolean {
        if (!exists(path)) return false
        return try {
            fileSystem.deleteRecursively(path.toPath())
            true
        } catch (e: IOException) {
            // 回退到 java.io 递归删除
            FileUtils.delete(path)
        }
    }

    /**
     * 异步删除
     */
    @JvmStatic
    fun deleteAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(delete(path)) }
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
            fileSystem.createDirectories(path.toPath())
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步创建目录
     */
    @JvmStatic
    fun createDirectoryAsync(path: String, onResult: (Boolean) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(createDirectory(path)) }
    }

    // ==================== 目录遍历 ====================

    /**
     * 递归列出目录下所有文件
     *
     * 使用 Okio 的 [FileSystem.listRecursively]，比 java.io File.walk 更高效。
     *
     * @param path 目录路径
     * @return Okio Path 列表
     */
    @JvmStatic
    fun listRecursively(path: String): List<Path> {
        if (!isDirectory(path)) return emptyList()
        return try {
            fileSystem.listRecursively(path.toPath()).toList()
        } catch (e: IOException) {
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
            val paths = listRecursively(path).map { it.toString() }
            onResult(paths)
        }
    }

    /**
     * 列出目录直接子项
     */
    @JvmStatic
    fun list(path: String): List<Path> {
        if (!isDirectory(path)) return emptyList()
        return try {
            fileSystem.list(path.toPath()).toList()
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 磁盘空间 ====================

    /**
     * 获取指定路径所在分区的可用空间
     *
     * Okio [FileSystem.metadata] 不包含文件系统级别的空间信息，
     * 此处通过 Android [StatFs] 查询。
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
            onResult(result)
        }
    }
}
