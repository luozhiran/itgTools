package com.itg.itg_erification.compare

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.hash.FileHashUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Future

/**
 * 文件比对器
 *
 * 提供文件/目录之间的比对能力，适用于验证文件一致性、
 * 检测文件变化、去重等场景。
 *
 * 核心功能:
 * - 逐字节比对两个文件
 * - 按哈希比对两个文件（快速，适合大文件）
 * - 目录比对（找出新增/删除/修改的文件）
 * - 重复文件查找（哈希去重）
 * - 同步 + 异步双模式
 *
 * @author ITG Team
 * @since 1.0.0
 */
object FileComparator {

    /**
     * 比对结果
     */
    data class CompareResult(
        val areEqual: Boolean,
        val reason: String = "",
        val details: Map<String, Any> = emptyMap()
    ) {
        companion object {
            fun equal(details: Map<String, Any> = emptyMap()) = CompareResult(true, "Files are identical", details)
            fun different(reason: String, details: Map<String, Any> = emptyMap()) = CompareResult(false, reason, details)
        }
    }

    /**
     * 目录比对结果
     */
    data class DirectoryDiff(
        val added: List<String> = emptyList(),     // 仅在 dir2 中存在的文件
        val removed: List<String> = emptyList(),    // 仅在 dir1 中存在的文件
        val modified: List<String> = emptyList(),   // 两个目录都有但内容不同
        val unchanged: List<String> = emptyList()   // 内容相同的文件
    ) {
        val hasDifference: Boolean get() = added.isNotEmpty() || removed.isNotEmpty() || modified.isNotEmpty()
        val totalChanged: Int get() = added.size + removed.size + modified.size
    }

    /**
     * 重复文件组
     */
    data class DuplicateGroup(
        val hash: String,
        val files: List<String>,
        val wastedBytes: Long  // 重复文件额外占用的空间
    )

    // ==================== 逐字节比对 ====================

    /**
     * 逐字节比对两个文件（100% 精确，适合中小文件）
     *
     * @param path1 文件1路径
     * @param path2 文件2路径
     * @return [CompareResult]
     *
     * 使用示例:
     * ```kotlin
     * val result = FileComparator.compareBytes("/sdcard/a.txt", "/sdcard/b.txt")
     * if (result.areEqual) println("完全一致")
     * ```
     */
    @JvmStatic
    fun compareBytes(path1: String, path2: String): CompareResult {
        if (!FileUtils.isFile(path1)) return CompareResult.different("File not found: $path1")
        if (!FileUtils.isFile(path2)) return CompareResult.different("File not found: $path2")

        val file1 = File(path1)
        val file2 = File(path2)

        // 先比大小，快速排除
        if (file1.length() != file2.length()) {
            return CompareResult.different(
                "Size mismatch: ${file1.length()} vs ${file2.length()}",
                mapOf("size1" to file1.length(), "size2" to file2.length())
            )
        }

        return try {
            FileInputStream(file1).use { fis1 ->
                FileInputStream(file2).use { fis2 ->
                    val buf1 = ByteArray(8192)
                    val buf2 = ByteArray(8192)
                    var offset = 0L
                    var bytesRead1: Int
                    var bytesRead2: Int

                    while (fis1.read(buf1).also { bytesRead1 = it } != -1) {
                        bytesRead2 = fis2.read(buf2)
                        if (bytesRead1 != bytesRead2 || !buf1.contentEquals(buf2)) {
                            return CompareResult.different(
                                "Content differs at byte $offset",
                                mapOf("diffOffset" to offset, "size" to file1.length())
                            )
                        }
                        offset += bytesRead1
                    }
                }
            }
            CompareResult.equal(mapOf("size" to file1.length(), "comparedBytes" to file1.length()))
        } catch (e: IOException) {
            CompareResult.different("Error: ${e.message}")
        }
    }

    /**
     * 异步逐字节比对
     */
    @JvmStatic
    fun compareBytesAsync(
        path1: String,
        path2: String,
        onResult: (CompareResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(compareBytes(path1, path2)) }
    }

    // ==================== 哈希比对 ====================

    /**
     * 按哈希比对两个文件（快速，适合大文件）
     *
     * 先比大小，相同则计算 SHA-256 比对。
     *
     * @param path1     文件1路径
     * @param path2     文件2路径
     * @param algorithm 哈希算法，默认 SHA-256
     * @return [CompareResult]
     *
     * 使用示例:
     * ```kotlin
     * // 快速比对两个大文件
     * val result = FileComparator.compareByHash(
     *     "/sdcard/large1.iso", "/sdcard/large2.iso")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compareByHash(
        path1: String,
        path2: String,
        algorithm: String = "SHA-256"
    ): CompareResult {
        if (!FileUtils.isFile(path1)) return CompareResult.different("File not found: $path1")
        if (!FileUtils.isFile(path2)) return CompareResult.different("File not found: $path2")

        // 先比大小
        val size1 = FileUtils.getSize(path1)
        val size2 = FileUtils.getSize(path2)
        if (size1 != size2) {
            return CompareResult.different(
                "Size mismatch: $size1 vs $size2",
                mapOf("size1" to size1, "size2" to size2)
            )
        }

        // 按哈希比对
        val algo = FileHashUtils.Algorithm.fromString(algorithm)
            ?: return CompareResult.different("Unknown algorithm: $algorithm")

        val hash1 = FileHashUtils.hashFile(path1, algo) ?: return CompareResult.different("Hash failed: $path1")
        val hash2 = FileHashUtils.hashFile(path2, algo) ?: return CompareResult.different("Hash failed: $path2")

        return if (hash1.equals(hash2, ignoreCase = true)) {
            CompareResult.equal(mapOf("size" to size1, "algorithm" to algorithm, "hash" to hash1))
        } else {
            CompareResult.different(
                "Hash mismatch",
                mapOf("size" to size1, "algorithm" to algorithm, "hash1" to hash1, "hash2" to hash2)
            )
        }
    }

    /**
     * 异步哈希比对
     */
    @JvmStatic
    fun compareByHashAsync(
        path1: String,
        path2: String,
        algorithm: String = "SHA-256",
        onResult: (CompareResult) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(compareByHash(path1, path2, algorithm)) }
    }

    // ==================== 目录比对 ====================

    /**
     * 比对两个目录的差异
     *
     * @param dir1      基准目录
     * @param dir2      对比目录
     * @param compareContent 是否对比文件内容（哈希比对），默认 true
     * @return [DirectoryDiff]
     *
     * 使用示例:
     * ```kotlin
     * // 比对备份与当前版本
     * val diff = FileComparator.compareDirectories(
     *     "/sdcard/backup/v1/", "/sdcard/current/v2/")
     * println("新增: ${diff.added.size}, 删除: ${diff.removed.size}, 修改: ${diff.modified.size}")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun compareDirectories(
        dir1: String,
        dir2: String,
        compareContent: Boolean = true
    ): DirectoryDiff {
        val files1 = getRelativeFileMap(dir1) ?: return DirectoryDiff()
        val files2 = getRelativeFileMap(dir2) ?: return DirectoryDiff()

        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val modified = mutableListOf<String>()
        val unchanged = mutableListOf<String>()

        // dir2 有但 dir1 没有 = 新增
        files2.keys.filter { it !in files1 }.forEach { added.add(it) }

        // dir1 有但 dir2 没有 = 删除
        files1.keys.filter { it !in files2 }.forEach { removed.add(it) }

        // 两个目录都有 = 检查是否修改
        files1.keys.intersect(files2.keys).forEach { relPath ->
            val f1 = files1[relPath]!!
            val f2 = files2[relPath]!!

            if (compareContent && f1.length() != f2.length()) {
                modified.add(relPath)
            } else if (compareContent) {
                // 大小相同，进一步按哈希对比
                val result = compareByHash(f1.absolutePath, f2.absolutePath)
                if (result.areEqual) {
                    unchanged.add(relPath)
                } else {
                    modified.add(relPath)
                }
            } else {
                // 只比大小
                if (f1.length() == f2.length()) unchanged.add(relPath)
                else modified.add(relPath)
            }
        }

        return DirectoryDiff(added.sorted(), removed.sorted(), modified.sorted(), unchanged.sorted())
    }

    /**
     * 异步目录比对
     */
    @JvmStatic
    fun compareDirectoriesAsync(
        dir1: String,
        dir2: String,
        compareContent: Boolean = true,
        onResult: (DirectoryDiff) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(compareDirectories(dir1, dir2, compareContent)) }
    }

    // ==================== 重复文件查找 ====================

    /**
     * 查找目录中的重复文件
     *
     * 通过 大小→MD5 两级过滤，高效定位重复文件。
     *
     * @param directory 目标目录
     * @return 重复文件组列表
     *
     * 使用示例:
     * ```kotlin
     * val duplicates = FileComparator.findDuplicates("/sdcard/Downloads/")
     * duplicates.forEach { group ->
     *     val wasted = group.wastedBytes / (1024 * 1024)
     *     println("${group.files.size} duplicates (MD5: ${group.hash.take(8)}...), waste ${wasted}MB")
     *     group.files.drop(1).forEach { File(it).delete() }  // 保留第一个，删除其余
     * }
     * ```
     */
    @JvmStatic
    fun findDuplicates(directory: String): List<DuplicateGroup> {
        if (!FileUtils.isDirectory(directory)) return emptyList()

        // 第一级: 按大小分组（跳过 0 字节文件和大小不同的文件）
        val bySize = mutableMapOf<Long, MutableList<String>>()
        File(directory).walkTopDown().forEach { file ->
            if (file.isFile && file.length() > 0) {
                bySize.getOrPut(file.length()) { mutableListOf() }.add(file.absolutePath)
            }
        }

        // 只保留大小相同的组（≥2个文件）
        val candidateGroups = bySize.filter { it.value.size >= 2 }

        // 第二级: 按 MD5 分组
        val duplicates = mutableListOf<DuplicateGroup>()
        candidateGroups.forEach { (size, paths) ->
            val byHash = mutableMapOf<String, MutableList<String>>()
            paths.forEach { path ->
                val md5 = FileHashUtils.md5(path) ?: return@forEach
                byHash.getOrPut(md5) { mutableListOf() }.add(path)
            }
            byHash.filter { it.value.size >= 2 }.forEach { (hash, files) ->
                // 浪费的空间 = (重复份数 - 1) × 文件大小
                val wasted = (files.size - 1) * size
                duplicates.add(DuplicateGroup(hash, files.sorted(), wasted))
            }
        }

        return duplicates.sortedByDescending { it.wastedBytes }
    }

    /**
     * 异步查找重复文件
     */
    @JvmStatic
    fun findDuplicatesAsync(directory: String, onResult: (List<DuplicateGroup>) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(findDuplicates(directory)) }
    }

    /**
     * 查找指定文件列表中哪些是相同的（两两比对）
     *
     * @param paths 文件路径列表
     * @return 重复组 (referencePath → duplicatePaths)
     */
    @JvmStatic
    fun findDuplicatesInList(paths: List<String>): Map<String, List<String>> {
        if (paths.size < 2) return emptyMap()

        // 按 大小+哈希 分组
        val groups = mutableMapOf<String, MutableList<String>>() // key = "size:hash"
        paths.forEach { path ->
            val size = FileUtils.getSize(path)
            if (size <= 0) return@forEach
            val md5 = FileHashUtils.md5(path) ?: return@forEach
            groups.getOrPut("$size:$md5") { mutableListOf() }.add(path)
        }

        // 只保留有重复的组
        val result = LinkedHashMap<String, List<String>>()
        groups.filter { it.value.size >= 2 }.forEach { (_, files) ->
            result[files.first()] = files.drop(1)
        }
        return result
    }

    // ==================== 内部方法 ====================

    /**
     * 获取目录中所有文件的相对路径 → File 映射
     */
    private fun getRelativeFileMap(dir: String): Map<String, File>? {
        val baseDir = File(dir)
        if (!baseDir.isDirectory) return null

        val map = LinkedHashMap<String, File>()
        baseDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relPath = file.relativeTo(baseDir).path
                map[relPath] = file
            }
        }
        return map
    }
}
