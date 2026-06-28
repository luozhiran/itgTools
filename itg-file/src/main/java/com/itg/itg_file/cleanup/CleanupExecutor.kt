package com.itg.itg_file.cleanup

import java.io.File
import java.io.IOException

/**
 * 同步执行单条文件清理规则。
 *
 * 大文件或大目录应通过 [FileCleanupManager] 在 I/O 线程执行。
 */
object CleanupExecutor {

    @JvmStatic
    fun execute(rule: CleanupRule): CleanupResult {
        val target = runCatching { validateTarget(rule.path) }.getOrElse { error ->
            return failure(rule, false, error.message ?: "清理路径无效", error)
        }
        val existedBefore = target.exists()
        if (!existedBefore) {
            return CleanupResult(
                key = rule.key,
                path = rule.path,
                action = rule.action,
                success = true,
                deletedEntries = 0,
                existedBefore = false,
                existedAfter = false,
                message = "目标不存在，无需清理"
            )
        }

        return try {
            ensureWritable(target, rule.action)
            val deletedEntries = when (rule.action) {
                CleanupAction.CLEAR_DIRECTORY -> clearDirectory(target)
                CleanupAction.DELETE_TARGET ->
                    deleteRecursivelyCount(target)
                        ?: throw IOException("目标删除失败")
            }
            val existedAfter = target.exists()
            val success = when (rule.action) {
                CleanupAction.CLEAR_DIRECTORY ->
                    target.isDirectory && target.listFiles().isNullOrEmpty()
                CleanupAction.DELETE_TARGET -> !existedAfter
            }
            CleanupResult(
                key = rule.key,
                path = rule.path,
                action = rule.action,
                success = success,
                deletedEntries = deletedEntries,
                existedBefore = true,
                existedAfter = existedAfter,
                message = if (success) {
                    "清理完成，共删除 $deletedEntries 项"
                } else {
                    "清理未完成，目标仍有内容"
                }
            )
        } catch (error: Throwable) {
            failure(rule, existedBefore, error.message ?: "清理失败", error)
        }
    }

    private fun validateTarget(path: String): File {
        require(path.isNotBlank()) { "清理路径不能为空" }
        val target = File(path).canonicalFile
        require(target.parentFile != null) { "禁止清理文件系统根目录" }
        return target
    }

    private fun clearDirectory(directory: File): Int {
        require(directory.isDirectory) {
            "清空目录操作的目标必须是目录"
        }
        var deleted = 0
        directory.listFiles()?.forEach { child ->
            deleted += deleteRecursivelyCount(child)
                ?: throw IOException("部分内容删除失败")
        }
        return deleted
    }

    private fun ensureWritable(target: File, action: CleanupAction) {
        if (!target.canWrite()) {
            throw SecurityException("没有目标的写入权限")
        }
        if (action == CleanupAction.DELETE_TARGET && target.parentFile?.canWrite() == false) {
            throw SecurityException("没有目标父目录的写入权限")
        }
    }

    private fun deleteRecursivelyCount(file: File): Int? {
        ensureWritable(file, CleanupAction.DELETE_TARGET)
        var deleted = 1
        if (file.isDirectory && !isSymbolicLink(file)) {
            file.listFiles()?.forEach { child ->
                deleted += deleteRecursivelyCount(child) ?: return null
            }
        }
        if (!file.delete()) {
            return null
        }
        return deleted
    }

    private fun isSymbolicLink(file: File): Boolean {
        return try {
            file.absoluteFile != file.canonicalFile
        } catch (_: IOException) {
            true
        }
    }

    private fun failure(
        rule: CleanupRule,
        existedBefore: Boolean,
        message: String,
        error: Throwable
    ): CleanupResult {
        return CleanupResult(
            key = rule.key,
            path = rule.path,
            action = rule.action,
            success = false,
            deletedEntries = 0,
            existedBefore = existedBefore,
            existedAfter = runCatching { File(rule.path).exists() }.getOrDefault(false),
            message = message,
            error = error
        )
    }
}
