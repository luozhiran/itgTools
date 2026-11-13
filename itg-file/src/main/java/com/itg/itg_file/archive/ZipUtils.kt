package com.itg.itg_file.archive

import com.itg.itg_file.core.FileCoroutines
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** 将 ZIP 解压到新目录；校验通过前，解压结果只保存在临时目录中。 */
object ZipUtils {
    private const val DEFAULT_MAX_ENTRIES = 10_000
    private const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L // 1GB
    private const val BUFFER_SIZE = 8192

    /**
     * 在 IO 调度器中解压 ZIP，调用方协程等待结果时不会阻塞原线程。
     * [onFailure] 在 IO 线程执行；需要更新界面时，请在调用返回后切回主线程。
     * 取消调用方协程会中断正在执行的解压并清理临时目录，取消仍遵循协程的传播语义。
     */
    @JvmStatic
    @JvmOverloads
    suspend fun extractSuspend(
        zipPath: String,
        destinationDir: String,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean = FileCoroutines.withIo {
        extract(zipPath, destinationDir, maxEntries, maxUncompressedBytes, onFailure)
    }

    /**
     * 将 [zipPath] 解压到 [destinationDir]，目标目录必须尚不存在。
     *
     * 所有条目先写入目标目录旁的临时目录；只有全部文件条目的实际大小和 CRC 校验通过后，
     * 才将临时目录移动到目标位置。读取、写入或校验失败时返回 false，不发布已解压的部分文件；
     * 进程仍在运行时会尝试清理临时目录。调用时已存在目标目录会返回 false；
     * 与其他进程同时创建目标目录的竞争不由此方法保证。
     *
     * 注意：这里只能校验 ZIP 中已列出的条目，无法判断压缩包是否遗漏业务必需文件；
     * 因此合法的空 ZIP 也会返回 true。进程被强制结束时可能留下临时目录，
     * 移动成功也不代表目录元数据已持久化到磁盘。失败时返回 false，并通过 [onFailure]
     * 回调失败原因；线程中断也按失败处理，不向调用方抛出中断异常。
     */
    @JvmStatic
    @JvmOverloads
    fun extract(
        zipPath: String,
        destinationDir: String,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean {
        if (zipPath.isBlank() || destinationDir.isBlank() || maxEntries <= 0 || maxUncompressedBytes <= 0L) {
            notifyFailure(onFailure, IllegalArgumentException("Invalid ZIP extraction arguments"))
            return false
        }

        var staging: File? = null
        var failure: Exception? = null
        return try {
            val archive = File(zipPath).canonicalFile
            val destination = File(destinationDir).canonicalFile
            if (!archive.isFile) {
                failure = FileNotFoundException("ZIP archive is not a file: $zipPath")
                return false
            }
            if (destination.exists()) {
                failure = IOException("Destination already exists: $destinationDir")
                return false
            }

            val parent = destination.parentFile ?: throw IOException("Destination has no parent: $destinationDir")
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create parent directory: $parent")
            val temporary = File(parent, ".${destination.name.take(32)}.${UUID.randomUUID()}.tmp")
            if (!temporary.mkdir()) throw IOException("Cannot create temporary directory: $temporary")
            staging = temporary

            // ZipFile 依据中央目录枚举条目；读到一半或校验失败时不会移动临时目录。
            ZipFile(archive).use { zip ->
                extractEntries(zip, temporary, maxEntries, maxUncompressedBytes)
            }

            checkInterrupted()
            // 与目标目录同级，避免跨文件系统移动。renameTo 不保证所有平台的原子性；
            // 目标目录的存在性检查也无法防止其他进程在检查与移动之间创建同名目录。
            if (destination.exists() || !temporary.renameTo(destination)) {
                failure = IOException("Cannot publish ZIP extraction to: $destinationDir")
                return false
            }
            staging = null
            true
        } catch (e: InterruptedIOException) {
            // 保留线程中断状态，并在清理临时目录后通过回调报告失败。
            Thread.currentThread().interrupt()
            failure = e
            false
        } catch (e: IOException) {
            failure = e
            false
        } catch (e: SecurityException) {
            failure = e
            false
        } finally {
            // 正常异常退出时清理半成品；进程被杀死时 finally 无法执行。
            staging?.deleteRecursively()
            failure?.let { notifyFailure(onFailure, it) }
        }
    }

    private fun extractEntries(zip: ZipFile, staging: File, maxEntries: Int, maxBytes: Long) {
        val root = staging.canonicalPath + File.separator
        val seen = HashSet<String>()
        val entries = zip.entries()
        val buffer = ByteArray(BUFFER_SIZE)
        var entryCount = 0
        var totalBytes = 0L

        while (entries.hasMoreElements()) {
            checkInterrupted()
            if (++entryCount > maxEntries) throw IOException("ZIP has too many entries")

            val entry = entries.nextElement()
            val name = validateName(entry)
            // 对大小写和 Unicode 组合形式统一后去重，避免在大小写不敏感的文件系统上合并条目。
            val nameKey = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT)
            if (!seen.add(nameKey)) throw IOException("Duplicate ZIP entry: $name")

            val output = File(staging, name).canonicalFile
            if (!output.path.startsWith(root)) throw IOException("ZIP entry escapes destination: $name")

            if (entry.isDirectory) {
                if (!output.isDirectory && !output.mkdirs()) throw IOException("Cannot create directory: $name")
                continue
            }

            // totalBytes 始终位于 [0, maxBytes]，所以 maxBytes - totalBytes 不会溢出。
            // 必须有可校验的大小与 CRC；同时限制总解压量，防止超额写入。
            if (output.exists() || entry.size < 0L || entry.crc < 0L || entry.size > maxBytes - totalBytes) {
                throw IOException("Invalid or oversized ZIP entry: $name")
            }
            val outputParent = output.parentFile ?: throw IOException("Missing parent directory: $name")
            if (!outputParent.isDirectory && !outputParent.mkdirs()) {
                throw IOException("Cannot create parent directory: $name")
            }

            val crc = CRC32()
            var entryBytes = 0L
            zip.getInputStream(entry).use { input ->
                // 即使 ZIP 的外部属性标记为符号链接，也只把条目内容写为普通文件。
                FileOutputStream(output).use { fileOutput ->
                    while (true) {
                        checkInterrupted()
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (count == 0) continue
                        if (count.toLong() > maxBytes - totalBytes || count.toLong() > entry.size - entryBytes) {
                            throw IOException("ZIP entry exceeds declared size or limit: $name")
                        }
                        fileOutput.write(buffer, 0, count)
                        crc.update(buffer, 0, count)
                        entryBytes += count
                        totalBytes += count
                    }
                    // 确保文件数据落盘；每个文件同步一次会增加耗时，目录元数据未单独同步。
                    fileOutput.fd.sync()
                }
            }
            // 提前结束的条目会因字节数不足而失败；CRC 用于发现常见的数据损坏。
            if (entryBytes != entry.size || crc.value != entry.crc) {
                throw IOException("ZIP entry failed size or CRC check: $name")
            }
        }
    }

    private fun validateName(entry: ZipEntry): String {
        val name = if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
        if (name.isEmpty() || name.startsWith('/') || '\\' in name || '\u0000' in name ||
            name.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            throw IOException("Unsafe ZIP entry name: ${entry.name}")
        }
        return name
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("ZIP extraction interrupted")
    }

    private fun notifyFailure(onFailure: ((Exception) -> Unit)?, failure: Exception) {
        try {
            onFailure?.invoke(failure)
        } catch (callbackError: Exception) {
            // 用户回调抛错时仍保持 extract 的失败返回值。
            callbackError.printStackTrace()
        }
    }
}
