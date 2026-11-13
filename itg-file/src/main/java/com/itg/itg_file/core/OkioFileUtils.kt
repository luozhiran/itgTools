package com.itg.itg_file.core
import android.system.ErrnoException
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import android.os.StatFs
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream

/**
 * Okio 文件基础操作工具类
 *
 * 基于 Okio 的 [okio.BufferedSource] / [okio.BufferedSink] 实现高效的文件操作，相比传统 [java.io.File]：
 * - 更高效的缓冲 I/O（Okio Buffer 零拷贝）
 * - 内置超时控制
 * - 原子性操作支持
 *
 * 同步方法会阻塞调用线程；在协程中使用 FileCoroutines.withIo 将操作切换到 IO 调度器。
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
            FileFailureCallbacks.rethrowCancellation(e)
            false
        }
    }

    /**
     * 检查路径是否存在（异步）
     */
    @JvmStatic
    suspend fun existsSuspend(path: String, onResult: (Boolean) -> Unit): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(exists(path)) } }
    }

    /**
     * 获取文件大小（字节）
     */
    private fun getSizeImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Long {
        return try {
            val file = File(path)
            if (file.isFile) file.length()
            else failureReporter.fail(-1L, "读取大小失败：文件不存在或不是普通文件：$path")
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            -1L
        }
    }

    /**
     * 获取文件最后修改时间戳（毫秒）
     */
    private fun getLastModifiedMillisImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Long {
        return try {
            if (!File(path).exists()) {
                return failureReporter.fail(-1L, "读取修改时间失败：文件不存在：$path")
            }
            File(path).lastModified()
        } catch (e: Exception) {
            failureReporter.rethrow(e)
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
            FileFailureCallbacks.rethrowCancellation(e)
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
            FileFailureCallbacks.rethrowCancellation(e)
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
    private fun copyImpl(failureReporter: FileFailureCallbacks.Reporter, srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!exists(srcPath)) return failureReporter.fail(false, "OkioFileUtils.copy 失败：源文件不存在")

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return failureReporter.fail(false, "OkioFileUtils.copy 失败：源与目标指向同一文件")

            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "OkioFileUtils.copy 失败：目标已存在且禁止覆盖")

            writeToFileAtomically(failureReporter, destFile, overwrite) { tempFile ->
                srcFile.source().buffer().use { source ->
                    tempFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        } catch (e: IOException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步复制
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
    private fun moveImpl(failureReporter: FileFailureCallbacks.Reporter, srcPath: String, destPath: String, overwrite: Boolean = true): Boolean {
        if (!exists(srcPath)) return failureReporter.fail(false, "移动失败：源文件不存在：$srcPath")

        return try {
            val srcFile = File(srcPath)
            val destFile = File(destPath)
            if (srcFile.canonicalFile == destFile.canonicalFile) return true

            if (destFile.exists() && !overwrite) return failureReporter.fail(false, "移动失败：目标已存在且禁止覆盖：$destPath")
            ensureParentDirectory(failureReporter, destFile)
                ?: return failureReporter.fail(false, "移动失败：无法创建目标父目录：${destFile.parent}")

            // 先尝试 rename（同一文件系统下极快）
            if (srcFile.renameTo(destFile)) return true

            // 跨文件系统则走 copy+delete
            if (!copyImpl(failureReporter, srcPath, destPath, overwrite)) {
                return failureReporter.fail(false, "移动失败：跨文件系统复制未完成：$srcPath → $destPath")
            }
            if (!srcFile.deleteRecursively()) {
                return failureReporter.fail(false, "移动未完成：目标已复制但源文件未删除：$srcPath")
            }
            true
        } catch (e: Exception) {
            // rename 失败，回退到 copy+delete
            failureReporter.rethrow(e)
            try {
                if (!copyImpl(failureReporter, srcPath, destPath, overwrite)) {
                    failureReporter.fail(false, "移动失败：回退复制未完成：$srcPath → $destPath")
                } else if (!deleteImpl(failureReporter, srcPath)) {
                    failureReporter.fail(false, "移动未完成：目标已复制但源文件未删除：$srcPath")
                } else true
            } catch (e2: Exception) {
                failureReporter.rethrow(e2)
                e2.printStackTrace()
                false
            }
        }
    }

    /**
     * 异步移动
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

    // ==================== 删除 ====================

    /**
     * 删除文件或递归删除目录
     *
     * @param path 文件/目录路径
     * @return true 表示删除成功
     */
    private fun deleteImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Boolean {
        if (!exists(path)) return failureReporter.fail(false, "删除失败：目标不存在：$path")
        return try {
            if (File(path).deleteRecursively()) true
            else failureReporter.fail(false, "删除失败：目标或子项无法删除：$path")
        } catch (e: Exception) {
            // 回退到 FileUtils 递归删除
            FileUtils.delete(path)
        }
    }

    /**
     * 异步删除
     */
    @JvmStatic
    @JvmOverloads
    suspend fun deleteSuspend(
        path: String,
        onResult: (Boolean) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ): Unit {
        return FileCoroutines.withIo { safeCallback { onResult(delete(path, onFailure = onFailure)) } }
    }

    /**
     * 创建目录（递归）
     *
     * @param path 目录路径
     * @return true 表示创建成功或已存在
     */
    private fun createDirectoryImpl(failureReporter: FileFailureCallbacks.Reporter, path: String): Boolean {
        return try {
            val dir = File(path)
            when {
                dir.isDirectory -> true
                dir.exists() -> failureReporter.fail(false, "创建目录失败：目标已有普通文件：$path")
                dir.mkdirs() -> true
                else -> failureReporter.fail(false, "创建目录失败：无法创建目标目录：$path")
            }
        } catch (e: Exception) {
            failureReporter.rethrow(e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 异步创建目录
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
            FileFailureCallbacks.rethrowCancellation(e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 递归列出（异步）
     */
    @JvmStatic
    suspend fun listRecursivelySuspend(path: String, onResult: (List<String>) -> Unit): Unit {
        return FileCoroutines.withIo {
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
            FileFailureCallbacks.rethrowCancellation(e)
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
     * 获取指定路径所在分区的总空间
     *
     * @param path 路径（用于确定所在分区）
     * @return 总字节数，失败返回 -1
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
    suspend fun <T> withTimeout(timeoutMs: Long, block: () -> T): T? {
        return try {
            if (timeoutMs <= 0) FileCoroutines.withIo(block)
            else withTimeoutOrNull(timeoutMs) { FileCoroutines.withIo(block) }
        } catch (e: CancellationException) {
            FileFailureCallbacks.rethrowCancellation(e)
            throw e
        } catch (e: Exception) {
            FileFailureCallbacks.rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步超时操作
     */
    @JvmStatic
    suspend fun <T> withTimeoutSuspend(
        timeoutMs: Long,
        block: () -> T,
        onResult: (T?) -> Unit
    ): Unit {
        val result = withTimeout(timeoutMs, block)
        safeCallback { onResult(result) }
    }

    private fun writeToFileAtomically(failureReporter: FileFailureCallbacks.Reporter,
        destFile: File,
        overwrite: Boolean,
        writer: (File) -> Unit
    ): Boolean {
        var tempFile: File? = null
        return try {
            val canonicalDest = destFile.canonicalFile
            if (canonicalDest.exists()) {
                if (!overwrite) return failureReporter.fail(false, "OkioFileUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
                if (canonicalDest.isDirectory) return failureReporter.fail(false, "OkioFileUtils.writeToFileAtomically 失败：目标路径是目录")
            }
            val parent = ensureParentDirectory(failureReporter, canonicalDest) ?: return failureReporter.fail(false, "OkioFileUtils.writeToFileAtomically 失败：无法创建目标父目录")
            tempFile = File.createTempFile(".${canonicalDest.name}.", ".tmp", parent)
            writer(tempFile)
            FileOutputStream(tempFile, true).use { it.fd.sync() }
            if (canonicalDest.exists() && !overwrite) return failureReporter.fail(false, "OkioFileUtils.writeToFileAtomically 失败：目标已存在且禁止覆盖")
            Os.rename(tempFile.absolutePath, canonicalDest.absolutePath)
            tempFile = null
            true
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

    private fun ensureParentDirectory(failureReporter: FileFailureCallbacks.Reporter, file: File): File? {
        val parent = file.parentFile ?: return failureReporter.fail(null, "OkioFileUtils.ensureParentDirectory 失败：无法获取目标文件的父目录")
        if (parent.exists()) return if (parent.isDirectory) parent else null
        return if (parent.mkdirs()) parent else null
    }

    private fun safeCallback(failureReporter: FileFailureCallbacks.Reporter = FileFailureCallbacks.Reporter.NONE, callback: () -> Unit) {
        try {
            failureReporter.invokeUserCallback(callback)
        } catch (t: Throwable) {
            failureReporter.rethrow(t)
            t.printStackTrace()
        }
    }
    // ==================== 公开操作入口：onFailure 为可选末位参数 ====================

    /** 读取文件大小；失败时通过 onFailure 通知调用方。 */
    @JvmStatic
    @JvmOverloads
    fun getSize(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("OkioFileUtils.getSize", path, onFailure, { it < 0L }, -1L) { failureReporter ->
            getSizeImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getLastModifiedMillis(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("OkioFileUtils.getLastModifiedMillis", path, onFailure,
            { it < 0L || (it == 0L && !File(path).exists()) }, -1L) { failureReporter ->
            getLastModifiedMillisImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getAvailableSpace(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("OkioFileUtils.getAvailableSpace", path, onFailure, { it < 0L }, -1L) { failureReporter ->
            getAvailableSpaceImpl(failureReporter, path)
        }

    @JvmStatic
    @JvmOverloads
    fun getTotalSpace(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Long =
        FileFailureCallbacks.run("OkioFileUtils.getTotalSpace", path, onFailure, { it < 0L }, -1L) { failureReporter ->
            getTotalSpaceImpl(failureReporter, path)
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
        FileFailureCallbacks.run("OkioFileUtils.copy", "$srcPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            copyImpl(failureReporter, srcPath, destPath, overwrite)
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
        FileFailureCallbacks.run("OkioFileUtils.move", "$srcPath → $destPath", onFailure, { !it }, false) { failureReporter ->
            moveImpl(failureReporter, srcPath, destPath, overwrite)
        }

    /** delete 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun delete(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioFileUtils.delete", "$path", onFailure, { !it }, false) { failureReporter ->
            deleteImpl(failureReporter, path)
        }

    /** createDirectory 失败时通过 onFailure 通知调用方；回调在当前线程执行。 */
    @JvmStatic
    @JvmOverloads
    fun createDirectory(
        path: String,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean =
        FileFailureCallbacks.run("OkioFileUtils.createDirectory", "$path", onFailure, { !it }, false) { failureReporter ->
            createDirectoryImpl(failureReporter, path)
        }

    // 兼容原有尾随 lambda 调用

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
    @JvmName("moveSuspendLegacyCallback")
    suspend fun moveSuspend(
        srcPath: String,
        destPath: String,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ) = moveSuspend(srcPath = srcPath, destPath = destPath, overwrite = overwrite, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("deleteSuspendLegacyCallback")
    suspend fun deleteSuspend(
        path: String,
        onResult: (Boolean) -> Unit
    ) = deleteSuspend(path = path, onResult = onResult, onFailure = null)

    /** 保留原有 Kotlin 尾随 lambda 调用方式。 */
    @JvmStatic
    @JvmName("createDirectorySuspendLegacyCallback")
    suspend fun createDirectorySuspend(
        path: String,
        onResult: (Boolean) -> Unit
    ) = createDirectorySuspend(path = path, onResult = onResult, onFailure = null)
}
