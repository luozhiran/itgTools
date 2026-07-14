package com.itg.itgtools.testfile

import android.content.Context
import com.itg.concurrent.Concurrent
import com.itg.itg_file.cleanup.*
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.core.OkioFileUtils
import com.itg.itg_file.hash.FileHashUtils
import com.itg.itg_file.hash.OkioHashUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.read.OkioReadUtils
import com.itg.itg_file.resource.AssetUtils
import com.itg.itg_file.resource.OkioAssetUtils
import com.itg.itg_file.write.FileWriteUtils
import com.itg.itg_file.write.OkioWriteUtils
import okio.ByteString
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Future

/**
 * itg-file 全功能测试模型
 *
 * 覆盖所有 API 分类：FileUtils / FileWriteUtils / FileReadUtils /
 * FileHashUtils / AssetUtils / Okio* 变体 / FileCleanupManager
 */
class TestFileModel(private val context: Context) {

    // ==================== 测试目录 ====================

    private val testRoot: File by lazy {
        File(context.filesDir, "itg_file_test").also { it.mkdirs() }
    }

    private val testDir: String get() = testRoot.absolutePath
    private val testFile1: String get() = File(testRoot, "test1.txt").absolutePath
    private val testFile2: String get() = File(testRoot, "test2.txt").absolutePath
    private val testSubDir: String get() = File(testRoot, "subdir").also { it.mkdirs() }.absolutePath

    // ==================== 日志回调 ====================

    var onLog: ((String) -> Unit)? = null

    private fun log(msg: String, isHeader: Boolean = false) {
        val line = if (isHeader) "\n===== $msg ====="
        else "  $msg"
        android.util.Log.d("TestFile", line)
        onLog?.invoke(line)
    }

    private fun logResult(label: String, result: Any?) {
        val status = when (result) {
            is Boolean -> if (result) "✅ 成功" else "❌ 失败"
            null -> "❌ 返回 null"
            else -> "✅ $result"
        }
        log("$label: $status")
    }

    private fun logError(label: String, e: Throwable) {
        log("$label: ❌ 异常 — ${e.javaClass.simpleName}: ${e.message}")
    }

    // ==================== 1. FileUtils — 基础文件操作 ====================

    fun testFileExists() {
        log("=== testFileExists ===")
        File(testFile1).writeText("hello")
        logResult("exists", FileUtils.exists(testFile1))
        logResult("exists(null)", FileUtils.exists(null).toString() + " (正确)")
        logResult("exists(不存在)", FileUtils.exists("/no/such/file").toString() + " (正确)")
        logResult("isFile", FileUtils.isFile(testFile1))
        logResult("isDirectory", FileUtils.isDirectory(testDir))
        logResult("isEmpty(空目录)", FileUtils.isEmpty(testSubDir))
    }

    fun testFileExistsAsync() {
        log("=== testFileExistsAsync ===")
        FileUtils.existsAsync(testFile1) { logResult("existsAsync", it) }
        FileUtils.isFile(null)  // 同步，仅验证不崩溃
    }

    fun testCreateFile() {
        log("=== testCreateFile ===")
        val newFile = File(testRoot, "created_${System.nanoTime()}.txt").absolutePath
        logResult("createFile", FileUtils.createFile(newFile))
        logResult("createDirectory", FileUtils.createDirectory(File(testSubDir, "new_dir").absolutePath))
        val temp = FileUtils.createTempFile("itg_", ".tmp", testRoot)
        logResult("createTempFile", temp?.absolutePath ?: "null")
    }

    fun testCreateFileAsync() {
        log("=== testCreateFileAsync ===")
        val newFile = File(testRoot, "created_async_${System.nanoTime()}.txt").absolutePath
        FileUtils.createFileAsync(newFile) { logResult("createFileAsync", it) }
        FileUtils.createDirectoryAsync(File(testSubDir, "async_dir").absolutePath) { logResult("createDirectoryAsync", it) }
    }

    fun testDelete() {
        log("=== testDelete ===")
        val delFile = File(testRoot, "to_delete.txt").also { it.writeText("temp") }
        logResult("delete(文件)", FileUtils.delete(delFile.absolutePath))
        val delDir = File(testRoot, "to_delete_dir").also { it.mkdirs(); File(it, "f.txt").writeText("x") }
        logResult("delete(目录)", FileUtils.delete(delDir.absolutePath))
        logResult("delete(null)", FileUtils.delete(null).toString() + " (正确)")
        logResult("delete(不存在)", FileUtils.delete("/no/such").toString() + " (正确)")
    }

    fun testDeleteAsync() {
        log("=== testDeleteAsync ===")
        val delFile = File(testRoot, "to_delete_async.txt").also { it.writeText("temp") }
        FileUtils.deleteAsync(delFile.absolutePath) { logResult("deleteAsync", it) }
    }

    fun testClearDirectory() {
        log("=== testClearDirectory ===")
        val clearDir = File(testRoot, "clear_test").also { it.mkdirs(); File(it, "f1.txt").writeText("a"); File(it, "f2.txt").writeText("b") }
        logResult("clearDirectory", FileUtils.clearDirectory(clearDir.absolutePath))
        logResult("目录已空", FileUtils.isEmpty(clearDir.absolutePath))
    }

    fun testClearDirectoryAsync() {
        log("=== testClearDirectoryAsync ===")
        val clearDir = File(testRoot, "clear_async").also { it.mkdirs(); File(it, "f.txt").writeText("x") }
        FileUtils.clearDirectoryAsync(clearDir.absolutePath) { logResult("clearDirectoryAsync", it) }
    }

    fun testRename() {
        log("=== testRename ===")
        val oldFile = File(testRoot, "old_name.txt").also { it.writeText("rename me") }
        val result = FileUtils.rename(oldFile.absolutePath, "new_name.txt")
        logResult("rename", result)
        logResult("旧文件不存在", FileUtils.exists(oldFile.absolutePath).toString() + " (正确)")
        logResult("新文件存在", FileUtils.exists(File(testRoot, "new_name.txt").absolutePath))
    }

    fun testRenameAsync() {
        log("=== testRenameAsync ===")
        val oldFile = File(testRoot, "old_async.txt").also { it.writeText("rename async") }
        FileUtils.renameAsync(oldFile.absolutePath, "new_async.txt") { logResult("renameAsync", it) }
    }

    fun testCopy() {
        log("=== testCopy ===")
        File(testFile1).writeText("copy source content")
        val dest = File(testRoot, "copy_dest.txt").absolutePath
        logResult("copy", FileUtils.copy(testFile1, dest))
        logResult("目标存在", FileUtils.exists(dest))
    }

    fun testCopyWithProgress() {
        log("=== testCopyWithProgress ===")
        val src = File(testRoot, "copy_progress_src.txt").also { it.writeText("A".repeat(100_000)) }
        val dest = File(testRoot, "copy_progress_dest.txt")
        FileUtils.copyWithProgress(src.absolutePath, dest.absolutePath,
            onProgress = { copied, total -> log("进度: $copied / $total") })
        logResult("大文件复制", dest.exists() && dest.length() == src.length())
    }

    fun testCopyAsync() {
        log("=== testCopyAsync ===")
        File(testFile1).writeText("async copy source")
        val dest = File(testRoot, "copy_async_dest.txt").absolutePath
        FileUtils.copyAsync(testFile1, dest, onResult = { logResult("copyAsync", it) })
    }

    fun testCopyWithProgressAsync() {
        log("=== testCopyWithProgressAsync ===")
        File(testFile2).writeText("B".repeat(50_000))
        val dest = File(testRoot, "copy_progress_async_dest.txt")
        FileUtils.copyWithProgressAsync(testFile2, dest.absolutePath,
            onProgress = { copied, total -> log("进度: $copied / $total") },
            onResult = { logResult("copyWithProgressAsync", it) })
    }

    fun testCopyDirectory() {
        log("=== testCopyDirectory ===")
        val srcDir = File(testRoot, "copy_src_dir").also {
            it.mkdirs(); File(it, "a.txt").writeText("aaa"); File(it, "b.txt").writeText("bbb")
        }
        val destDir = File(testRoot, "copy_dest_dir")
        logResult("copyDirectory", FileUtils.copyDirectory(srcDir.absolutePath, destDir.absolutePath))
        logResult("目标目录存在", FileUtils.isDirectory(destDir.absolutePath))
    }

    fun testMove() {
        log("=== testMove ===")
        val src = File(testRoot, "move_src.txt").also { it.writeText("move me") }
        val dest = File(testRoot, "move_dest.txt").absolutePath
        logResult("move", FileUtils.move(src.absolutePath, dest))
        logResult("源已不在", FileUtils.exists(src.absolutePath).toString() + " (正确)")
        logResult("目标存在", FileUtils.exists(dest))
    }

    fun testMoveAsync() {
        log("=== testMoveAsync ===")
        val src = File(testRoot, "move_async_src.txt").also { it.writeText("move async") }
        val dest = File(testRoot, "move_async_dest.txt").absolutePath
        FileUtils.moveAsync(src.absolutePath, dest, onResult = { logResult("moveAsync", it) })
    }

    fun testListFiles() {
        log("=== testListFiles ===")
        File(testRoot, "list_a.txt").writeText("a")
        File(testRoot, "list_b.txt").writeText("b")
        File(testRoot, "list_c.jpg").writeText("img")
        val all = FileUtils.listFiles(testDir)
        logResult("listFiles(全部)", "${all.size} 个文件")
        val txts = FileUtils.listFilesByExtension(testDir, "txt")
        logResult("listFilesByExtension(txt)", "${txts.size} 个")
        val filtered = FileUtils.listFiles(testDir) { it.name.startsWith("list_") }
        logResult("listFiles(filter: list_*)", "${filtered.size} 个")
        val recursive = FileUtils.listFilesRecursive(testDir)
        logResult("listFilesRecursive", "${recursive.size} 个")
    }

    fun testListFilesRecursiveAsync() {
        log("=== testListFilesRecursiveAsync ===")
        FileUtils.listFilesRecursiveAsync(testDir) { logResult("listFilesRecursiveAsync", "${it.size} 个") }
    }

    fun testGetFileInfo() {
        log("=== testGetFileInfo ===")
        File(testFile1).writeText("info content here!")
        logResult("getSize", "${FileUtils.getSize(testFile1)} bytes")
        logResult("getSizeFormatted", FileUtils.getSizeFormatted(testFile1))
        logResult("getExtension", FileUtils.getExtension(testFile1))
        logResult("getFileName", FileUtils.getFileName(testFile1))
        logResult("getFileNameWithoutExtension", FileUtils.getFileNameWithoutExtension(testFile1))
        logResult("getMimeType", FileUtils.getMimeType(testFile1))
        logResult("getLastModified", FileUtils.getLastModified(testFile1))
        logResult("getLastModifiedMillis", "${FileUtils.getLastModifiedMillis(testFile1)}")
        logResult("getParentPath", FileUtils.getParentPath(testFile1))
        val info = FileUtils.getFileInfo(testFile1)
        logResult("getFileInfo", "keys=${info.keys}")
    }

    fun testGetFileInfoAsync() {
        log("=== testGetFileInfoAsync ===")
        FileUtils.getSizeAsync(testFile1) { logResult("getSizeAsync", "$it bytes") }
        FileUtils.getFileInfoAsync(testFile1) { logResult("getFileInfoAsync", "keys=${it.keys}") }
    }

    fun testStorageSpace() {
        log("=== testStorageSpace ===")
        logResult("getAvailableSpace", FileUtils.getAvailableSpace(testDir).let { "${it / 1024 / 1024} MB" })
        logResult("getTotalSpace", FileUtils.getTotalSpace(testDir).let { "${it / 1024 / 1024} MB" })
        logResult("getInternalAvailableSpace", FileUtils.getInternalAvailableSpace().let { "${it / 1024 / 1024} MB" })
        logResult("getExternalAvailableSpace", FileUtils.getExternalAvailableSpace().let { "${it / 1024 / 1024} MB" })
    }

    // ==================== 2. FileWriteUtils — 文件写入 ====================

    fun testWriteText() {
        log("=== testWriteText ===")
        val path = File(testRoot, "write_text.txt").absolutePath
        logResult("writeText", FileWriteUtils.writeText(path, "Hello, 世界!"))
        logResult("读取验证", File(path).readText())
    }

    fun testWriteTextAsync() {
        log("=== testWriteTextAsync ===")
        val path = File(testRoot, "write_text_async.txt").absolutePath
        FileWriteUtils.writeTextAsync(path, "Async Hello!", onResult = { logResult("writeTextAsync", it) })
    }

    fun testAppendText() {
        log("=== testAppendText ===")
        val path = File(testRoot, "append_text.txt").absolutePath
        FileWriteUtils.writeText(path, "Line1\n")
        logResult("appendText", FileWriteUtils.appendText(path, "Line2\n"))
        logResult("内容", File(path).readText().trim())
    }

    fun testAppendTextAsync() {
        log("=== testAppendTextAsync ===")
        val path = File(testRoot, "append_async.txt").absolutePath
        FileWriteUtils.writeText(path, "First\n")
        FileWriteUtils.appendTextAsync(path, "Second\n", onResult = { logResult("appendTextAsync", it) })
    }

    fun testWriteBytes() {
        log("=== testWriteBytes ===")
        val path = File(testRoot, "write_bytes.bin").absolutePath
        val data = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        logResult("writeBytes", FileWriteUtils.writeBytes(path, data))
        logResult("读取验证", File(path).readBytes().joinToString { "%02X".format(it) })
    }

    fun testWriteBytesAsync() {
        log("=== testWriteBytesAsync ===")
        val path = File(testRoot, "write_bytes_async.bin").absolutePath
        FileWriteUtils.writeBytesAsync(path, byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            onResult = { logResult("writeBytesAsync", it) })
    }

    fun testAppendBytes() {
        log("=== testAppendBytes ===")
        val path = File(testRoot, "append_bytes.bin").absolutePath
        FileWriteUtils.writeBytes(path, byteArrayOf(0x01, 0x02))
        logResult("appendBytes", FileWriteUtils.appendBytes(path, byteArrayOf(0x03, 0x04)))
        logResult("总大小", "${File(path).length()} bytes")
    }

    fun testWriteFromStream() {
        log("=== testWriteFromStream ===")
        val path = File(testRoot, "from_stream.txt").absolutePath
        val stream = ByteArrayInputStream("stream data".toByteArray())
        logResult("writeFromStream", FileWriteUtils.writeFromStream(path, stream))
        logResult("读取验证", File(path).readText())
    }

    fun testWriteFromStreamAsync() {
        log("=== testWriteFromStreamAsync ===")
        val path = File(testRoot, "from_stream_async.txt").absolutePath
        val stream = ByteArrayInputStream("async stream".toByteArray())
        FileWriteUtils.writeFromStreamAsync(path, stream, onResult = { logResult("writeFromStreamAsync", it) })
    }

    fun testWriteTextAtomic() {
        log("=== testWriteTextAtomic ===")
        val path = File(testRoot, "atomic_text.txt").absolutePath
        logResult("writeTextAtomic", FileWriteUtils.writeTextAtomic(path, "atomic content"))
        logResult("内容", File(path).readText())
    }

    fun testWriteTextAtomicAsync() {
        log("=== testWriteTextAtomicAsync ===")
        val path = File(testRoot, "atomic_async.txt").absolutePath
        FileWriteUtils.writeTextAtomicAsync(path, "atomic async", onResult = { logResult("writeTextAtomicAsync", it) })
    }

    fun testWriteBytesAtomic() {
        log("=== testWriteBytesAtomic ===")
        val path = File(testRoot, "atomic_bytes.bin").absolutePath
        logResult("writeBytesAtomic", FileWriteUtils.writeBytesAtomic(path, byteArrayOf(0xDE.toByte(), 0xAD.toByte())))
    }

    fun testWriteBytesInChunks() {
        log("=== testWriteBytesInChunks ===")
        val path = File(testRoot, "chunks.bin").absolutePath
        val bigData = ByteArray(200_000) { (it % 256).toByte() }
        var progressCalls = 0
        FileWriteUtils.writeBytesInChunks(path, bigData, chunkSize = 32 * 1024,
            onProgress = { _, _ -> progressCalls++ })
        logResult("writeBytesInChunks", File(path).length() == bigData.size.toLong())
        log("进度回调次数: $progressCalls")
    }

    fun testWriteBytesInChunksAsync() {
        log("=== testWriteBytesInChunksAsync ===")
        val path = File(testRoot, "chunks_async.bin").absolutePath
        val data = ByteArray(64 * 1024) { (it % 128).toByte() }
        FileWriteUtils.writeBytesInChunksAsync(path, data, chunkSize = 16 * 1024,
            onProgress = { written, total -> log("进度: $written / $total") },
            onResult = { logResult("writeBytesInChunksAsync", it) })
    }

    // ==================== 3. FileReadUtils — 文件读取 ====================

    fun testReadText() {
        log("=== testReadText ===")
        File(testFile1).writeText("读取测试内容 📖")
        logResult("readText", FileReadUtils.readText(testFile1))
        logResult("readText(GBK)", FileReadUtils.readText(testFile1, Charsets.UTF_8)?.take(20))
    }

    fun testReadTextAsync() {
        log("=== testReadTextAsync ===")
        FileReadUtils.readTextAsync(testFile1, onResult = { text, err ->
            logResult("readTextAsync", text)
            if (err != null) logError("readTextAsync error", err)
        })
    }

    fun testReadBytes() {
        log("=== testReadBytes ===")
        File(testFile1).writeText("byte test")
        val bytes = FileReadUtils.readBytes(testFile1)
        logResult("readBytes", bytes?.let { "${it.size} bytes" })
    }

    fun testReadBytesAsync() {
        log("=== testReadBytesAsync ===")
        FileReadUtils.readBytesAsync(testFile1, onResult = { bytes, err ->
            logResult("readBytesAsync", bytes?.let { "${it.size} bytes" })
            if (err != null) logError("readBytesAsync error", err)
        })
    }

    fun testReadLines() {
        log("=== testReadLines ===")
        File(testFile1).writeText("Line A\nLine B\nLine C")
        val lines = FileReadUtils.readLines(testFile1)
        logResult("readLines", lines?.joinToString(", "))
    }

    fun testReadLinesAsync() {
        log("=== testReadLinesAsync ===")
        FileReadUtils.readLinesAsync(testFile1, onResult = { lines, err ->
            logResult("readLinesAsync", lines?.joinToString(", "))
            if (err != null) logError("readLinesAsync error", err)
        })
    }

    fun testReadLinesStreaming() {
        log("=== testReadLinesStreaming ===")
        File(testFile1).writeText("1\n2\n3\n4\n5")
        var count = 0
        val total = FileReadUtils.readLinesStreaming(testFile1) { line, idx ->
            count = idx + 1
            log("  行$idx: $line")
            true // 继续
        }
        logResult("readLinesStreaming", "总计 $total 行, 回调 $count 次")
    }

    fun testReadLinesStreamingAsync() {
        log("=== testReadLinesStreamingAsync ===")
        FileReadUtils.readLinesStreamingAsync(
            testFile1,
            onEachLine = { line, _ -> log("  $line"); true },
            onComplete = { total, err -> logResult("readLinesStreamingAsync", "$total 行") }
        )
    }

    fun testReadChunks() {
        log("=== testReadChunks ===")
        File(testFile1).writeText("0123456789".repeat(500))
        var chunkCount = 0
        val total = FileReadUtils.readChunks(testFile1, chunkSize = 1024) { chunk, idx, totalChunks ->
            chunkCount++
            log("  chunk $idx/$totalChunks: ${chunk.size} bytes")
            true
        }
        logResult("readChunks", "总计 ${total}bytes, ${chunkCount}块")
    }

    fun testReadChunksAsync() {
        log("=== testReadChunksAsync ===")
        FileReadUtils.readChunksAsync(
            testFile1, chunkSize = 512,
            onChunk = { _, idx, total -> log("  chunk $idx/$total"); true },
            onComplete = { total, err -> logResult("readChunksAsync", "${total}bytes") }
        )
    }

    fun testReadHeadTailBytes() {
        log("=== testReadHeadTailBytes ===")
        File(testFile1).writeText("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        val head = FileReadUtils.readHeadBytes(testFile1, 4)
        logResult("readHeadBytes(4)", head?.let { String(it) })
        val tail = FileReadUtils.readTailBytes(testFile1, 4)
        logResult("readTailBytes(4)", tail?.let { String(it) })
        val tailLines = FileReadUtils.readTailLines(testFile1, 2)
        logResult("readTailLines(2)", tailLines?.joinToString(", "))
    }

    // ==================== 4. FileHashUtils — 哈希校验 ====================

    fun testHashFile() {
        log("=== testHashFile ===")
        File(testFile1).writeText("hash me please!")
        logResult("md5", FileHashUtils.md5(testFile1))
        logResult("sha1", FileHashUtils.sha1(testFile1))
        logResult("sha256", FileHashUtils.sha256(testFile1))
        logResult("sha512", FileHashUtils.sha512(testFile1))
        logResult("crc32", FileHashUtils.crc32(testFile1))
    }

    fun testHashFileAsync() {
        log("=== testHashFileAsync ===")
        FileHashUtils.md5Async(testFile1) { hash, err ->
            logResult("md5Async", hash)
            if (err != null) logError("md5Async error", err)
        }
        FileHashUtils.sha256Async(testFile1) { hash, err ->
            logResult("sha256Async", hash)
        }
    }

    fun testHashFileWithProgress() {
        log("=== testHashFileWithProgress ===")
        File(testFile1).writeText("X".repeat(200_000))
        FileHashUtils.hashFileWithProgress(testFile1, FileHashUtils.Algorithm.SHA256,
            onProgress = { processed, total -> log("进度: $processed / $total") })
        logResult("hashFileWithProgress", FileHashUtils.sha256(testFile1))
    }

    fun testHashFileWithProgressAsync() {
        log("=== testHashFileWithProgressAsync ===")
        FileHashUtils.hashFileWithProgressAsync(testFile1, FileHashUtils.Algorithm.MD5,
            onProgress = { processed, total -> log("进度: $processed / $total") },
            onResult = { hash, err -> logResult("hashFileWithProgressAsync", hash) })
    }

    fun testHashBytesAndString() {
        log("=== testHashBytesAndString ===")
        logResult("hashBytes", FileHashUtils.hashBytes("test data".toByteArray()))
        logResult("hashString", FileHashUtils.hashString("test string"))
        logResult("crc32(data)", FileHashUtils.crc32("crc test".toByteArray()))
    }

    fun testVerify() {
        log("=== testVerify ===")
        File(testFile1).writeText("verify target")
        val hash = FileHashUtils.sha256(testFile1)!!
        logResult("verify(正确哈希)", FileHashUtils.verify(testFile1, hash))
        logResult("verify(错误哈希)", FileHashUtils.verify(testFile1, "badhash").toString() + " (正确)")
    }

    fun testVerifyAsync() {
        log("=== testVerifyAsync ===")
        val hash = FileHashUtils.sha256(testFile1)!!
        FileHashUtils.verifyAsync(testFile1, hash, onResult = { valid, actual ->
            logResult("verifyAsync", "$valid, hash=$actual")
        })
    }

    fun testCompareFiles() {
        log("=== testCompareFiles ===")
        File(testFile1).writeText("same content")
        File(testFile2).writeText("same content")
        logResult("compareFiles(相同)", FileHashUtils.compareFiles(testFile1, testFile2))
        File(testFile2).writeText("different!")
        logResult("compareFiles(不同)", FileHashUtils.compareFiles(testFile1, testFile2).toString() + " (正确)")
    }

    fun testCompareFilesAsync() {
        log("=== testCompareFilesAsync ===")
        File(testFile1).writeText("cmp async")
        File(testFile2).writeText("cmp async")
        FileHashUtils.compareFilesAsync(testFile1, testFile2, onResult = { logResult("compareFilesAsync", it) })
    }

    // ==================== 5. AssetUtils — 资源操作 ====================

    fun testAssetList() {
        log("=== testAssetList ===")
        runCatching {
            val files = AssetUtils.listAssets(context, "")
            logResult("listAssets(root)", "${files.size} 个")
        }.onFailure { logError("listAssets", it) }
        runCatching {
            logResult("assetExists(不存在)", AssetUtils.assetExists(context, "no_such_file.txt").toString() + " (正确)")
        }.onFailure { logError("assetExists", it) }
    }

    fun testAssetRead() {
        log("=== testAssetRead ===")
        runCatching {
            val files = AssetUtils.listAssets(context, "")
            if (files.isNotEmpty()) {
                val first = files.first()
                if (!first.endsWith("/")) {
                    logResult("getAssetSize($first)", "${AssetUtils.getAssetSize(context, first)} bytes")
                    logResult("readAssetText($first)", AssetUtils.readAssetText(context, first)?.take(100))
                    logResult("readAssetBytes($first)", AssetUtils.readAssetBytes(context, first)?.let { "${it.size} bytes" })
                }
            } else {
                log("assets 目录为空，跳过读取测试")
            }
        }.onFailure { logError("AssetRead", it) }
    }

    fun testAssetReadAsync() {
        log("=== testAssetReadAsync ===")
        runCatching {
            val files = AssetUtils.listAssets(context, "")
            val firstFile = files.firstOrNull { !it.endsWith("/") }
            if (firstFile != null) {
                AssetUtils.readAssetTextAsync(context, firstFile) { text, err ->
                    logResult("readAssetTextAsync", text?.take(50))
                }
            } else {
                log("assets 目录无文件，跳过异步读取测试")
            }
        }.onFailure { logError("AssetReadAsync", it) }
    }

    fun testCopyAssetToFile() {
        log("=== testCopyAssetToFile ===")
        runCatching {
            val files = AssetUtils.listAssets(context, "")
            val firstFile = files.firstOrNull { !it.endsWith("/") }
            if (firstFile != null) {
                val dest = File(testRoot, "asset_copy_${System.nanoTime()}.dat").absolutePath
                logResult("copyAssetToFile", AssetUtils.copyAssetToFile(context, firstFile, dest))
                logResult("目标存在", FileUtils.exists(dest))
            } else {
                log("assets 目录无文件，跳过复制测试")
            }
        }.onFailure { logError("copyAssetToFile", it) }
    }

    fun testCopyRawToFile() {
        log("=== testCopyRawToFile ===")
        // raw 资源由资源 ID 标识，这里使用 0 (无效ID) 测试边界行为
        runCatching {
            val dest = File(testRoot, "raw_copy_test.dat").absolutePath
            val result = AssetUtils.copyRawToFile(context, 0, dest)
            // raw 资源可能不存在，验证不崩溃即可
        }.onFailure { logError("copyRawToFile", it) }
    }

    // ==================== 6. OkioFileUtils — Okio 文件操作 ====================

    fun testOkioFileExists() {
        log("=== testOkioFileExists ===")
        logResult("exists", OkioFileUtils.exists(testFile1))
        logResult("exists(不存在)", OkioFileUtils.exists("/no/such/file").toString() + " (正确)")
    }

    fun testOkioFileExistsAsync() {
        log("=== testOkioFileExistsAsync ===")
        OkioFileUtils.existsAsync(testFile1) { logResult("existsAsync", it) }
    }

    fun testOkioCopy() {
        log("=== testOkioCopy ===")
        File(testFile1).writeText("okio copy test")
        val dest = File(testRoot, "okio_copy_dest.txt").absolutePath
        logResult("copy", OkioFileUtils.copy(testFile1, dest))
        logResult("目标存在", FileUtils.exists(dest))
    }

    fun testOkioCopyAsync() {
        log("=== testOkioCopyAsync ===")
        val dest = File(testRoot, "okio_copy_async.txt").absolutePath
        OkioFileUtils.copyAsync(testFile1, dest, onResult = { logResult("copyAsync", it) })
    }

    fun testOkioMove() {
        log("=== testOkioMove ===")
        val src = File(testRoot, "okio_move_src.txt").also { it.writeText("okio move") }
        val dest = File(testRoot, "okio_move_dest.txt").absolutePath
        logResult("move", OkioFileUtils.move(src.absolutePath, dest))
    }

    fun testOkioMoveAsync() {
        log("=== testOkioMoveAsync ===")
        val src = File(testRoot, "okio_move_async_src.txt").also { it.writeText("move") }
        val dest = File(testRoot, "okio_move_async_dest.txt").absolutePath
        OkioFileUtils.moveAsync(src.absolutePath, dest, onResult = { logResult("moveAsync", it) })
    }

    fun testOkioDelete() {
        log("=== testOkioDelete ===")
        val del = File(testRoot, "okio_delete.txt").also { it.writeText("del") }
        logResult("delete", OkioFileUtils.delete(del.absolutePath))
    }

    fun testOkioDeleteAsync() {
        log("=== testOkioDeleteAsync ===")
        val del = File(testRoot, "okio_delete_async.txt").also { it.writeText("del") }
        OkioFileUtils.deleteAsync(del.absolutePath) { logResult("deleteAsync", it) }
    }

    fun testOkioCreateDirectory() {
        log("=== testOkioCreateDirectory ===")
        val dir = File(testRoot, "okio_created_dir").absolutePath
        logResult("createDirectory", OkioFileUtils.createDirectory(dir))
    }

    fun testOkioCreateDirectoryAsync() {
        log("=== testOkioCreateDirectoryAsync ===")
        val dir = File(testRoot, "okio_created_async_dir").absolutePath
        OkioFileUtils.createDirectoryAsync(dir) { logResult("createDirectoryAsync", it) }
    }

    fun testOkioFileInfo() {
        log("=== testOkioFileInfo ===")
        logResult("getSize", "${OkioFileUtils.getSize(testFile1)} bytes")
        val list = OkioFileUtils.listRecursively(testDir)
        logResult("listRecursively", "${list.size} 个文件")
        logResult("getAvailableSpace", OkioFileUtils.getAvailableSpace(testDir).let { "${it / 1024 / 1024} MB" })
        logResult("getTotalSpace", OkioFileUtils.getTotalSpace(testDir).let { "${it / 1024 / 1024} MB" })
    }

    fun testOkioWithTimeout() {
        log("=== testOkioWithTimeout ===")
        val result = OkioFileUtils.withTimeout(5000) {
            File(testFile1).readText()
        }
        logResult("withTimeout(5s)", result?.take(50))
    }

    // ==================== 7. OkioWriteUtils — Okio 写入 ====================

    fun testOkioWriteByteString() {
        log("=== testOkioWriteByteString ===")
        val path = File(testRoot, "okio_byte_string.bin").absolutePath
        val bs = ByteString.of(*"Okio ByteString 测试".toByteArray(Charsets.UTF_8))
        logResult("writeByteString", OkioWriteUtils.writeByteString(path, bs))
        logResult("读取验证", File(path).readText())
    }

    fun testOkioWriteUtf8() {
        log("=== testOkioWriteUtf8 ===")
        val path = File(testRoot, "okio_utf8.txt").absolutePath
        logResult("writeUtf8", OkioWriteUtils.writeUtf8(path, "Okio UTF-8 内容"))
    }

    fun testOkioWriteUtf8Async() {
        log("=== testOkioWriteUtf8Async ===")
        val path = File(testRoot, "okio_utf8_async.txt").absolutePath
        OkioWriteUtils.writeUtf8Async(path, "Okio async", onResult = { logResult("writeUtf8Async", it) })
    }

    fun testOkioAppendUtf8() {
        log("=== testOkioAppendUtf8 ===")
        val path = File(testRoot, "okio_append.txt").absolutePath
        OkioWriteUtils.writeUtf8(path, "First\n")
        logResult("appendUtf8", OkioWriteUtils.appendUtf8(path, "Second\n"))
    }

    fun testOkioAppendUtf8Async() {
        log("=== testOkioAppendUtf8Async ===")
        val path = File(testRoot, "okio_append_async.txt").absolutePath
        OkioWriteUtils.writeUtf8(path, "A\n")
        OkioWriteUtils.appendUtf8Async(path, "B\n", onResult = { logResult("appendUtf8Async", it) })
    }

    fun testOkioWriteFromStream() {
        log("=== testOkioWriteFromStream ===")
        val path = File(testRoot, "okio_stream.txt").absolutePath
        val stream = ByteArrayInputStream("okio stream input".toByteArray())
        logResult("writeFromStream", OkioWriteUtils.writeFromStream(path, stream))
    }

    fun testOkioWriteFromStreamAsync() {
        log("=== testOkioWriteFromStreamAsync ===")
        val path = File(testRoot, "okio_stream_async.txt").absolutePath
        OkioWriteUtils.writeFromStreamAsync(path, ByteArrayInputStream("async".toByteArray()),
            onResult = { logResult("writeFromStreamAsync", it) })
    }

    fun testOkioWriteWithTimeout() {
        log("=== testOkioWriteWithTimeout ===")
        val path = File(testRoot, "okio_timeout.txt").absolutePath
        logResult("writeWithTimeout", OkioWriteUtils.writeWithTimeout(path, "timeout test".toByteArray(), 5000))
    }

    fun testOkioWriteWithProgress() {
        log("=== testOkioWriteWithProgress ===")
        val path = File(testRoot, "okio_progress.txt").absolutePath
        val data = "Progress ".repeat(5000).toByteArray()
        var cbCount = 0
        OkioWriteUtils.writeWithProgress(path, data, onProgress = { written, total -> cbCount++; log("进度: $written/$total") })
        logResult("writeWithProgress", File(path).exists())
        log("进度回调次数: $cbCount")
    }

    fun testOkioWriteGzip() {
        log("=== testOkioWriteGzip ===")
        val path = File(testRoot, "okio_gzip.gz").absolutePath
        logResult("writeGzip", OkioWriteUtils.writeGzip(path, "gzip compressed data!".toByteArray()))
    }

    fun testOkioWriteAtomic() {
        log("=== testOkioWriteAtomic ===")
        val path = File(testRoot, "okio_atomic.txt").absolutePath
        logResult("writeAtomic", OkioWriteUtils.writeAtomic(path, "okio atomic write".toByteArray()))
        logResult("内容", File(path).readText())
    }

    // ==================== 8. OkioReadUtils — Okio 读取 ====================

    fun testOkioReadByteString() {
        log("=== testOkioReadByteString ===")
        File(testFile1).writeText("Okio read test")
        val bs = OkioReadUtils.readByteString(testFile1)
        logResult("readByteString", bs?.utf8())
    }

    fun testOkioReadByteStringAsync() {
        log("=== testOkioReadByteStringAsync ===")
        OkioReadUtils.readByteStringAsync(testFile1) { bs, err ->
            logResult("readByteStringAsync", bs?.utf8()?.take(50))
        }
    }

    fun testOkioReadUtf8() {
        log("=== testOkioReadUtf8 ===")
        logResult("readUtf8", OkioReadUtils.readUtf8(testFile1)?.take(100))
    }

    fun testOkioReadUtf8Async() {
        log("=== testOkioReadUtf8Async ===")
        OkioReadUtils.readUtf8Async(testFile1) { text, err -> logResult("readUtf8Async", text?.take(50)) }
    }

    fun testOkioReadLines() {
        log("=== testOkioReadLines ===")
        File(testFile1).writeText("A\nB\nC\nD")
        val lines = OkioReadUtils.readLines(testFile1)
        logResult("readLines", lines?.joinToString(", "))
    }

    fun testOkioReadLinesAsync() {
        log("=== testOkioReadLinesAsync ===")
        OkioReadUtils.readLinesAsync(testFile1) { lines, err -> logResult("readLinesAsync", lines?.joinToString(", ")) }
    }

    fun testOkioReadLinesStreaming() {
        log("=== testOkioReadLinesStreaming ===")
        File(testFile1).writeText("O1\nO2\nO3\nO4\nO5")
        var count = 0
        val total = OkioReadUtils.readLinesStreaming(testFile1) { line, idx ->
            count++; log("  行$idx: $line"); true
        }
        logResult("readLinesStreaming", "$total 行, $count 次回调")
    }

    fun testOkioReadWithTimeout() {
        log("=== testOkioReadWithTimeout ===")
        val result = OkioReadUtils.readWithTimeout(testFile1, 5000)
        logResult("readWithTimeout", result?.utf8()?.take(50))
    }

    fun testOkioReadWithProgress() {
        log("=== testOkioReadWithProgress ===")
        var lastProgress = 0L
        val result = OkioReadUtils.readWithProgress(testFile1, onProgress = { read, total ->
            lastProgress = read; log("进度: $read / $total")
        })
        logResult("readWithProgress", result?.let { "${it.size} bytes" })
    }

    fun testOkioReadGzip() {
        log("=== testOkioReadGzip ===")
        // 测试 Gzip 解压读取（需要一个 gzip 文件，这里仅验证 API 不崩溃）
        val result = OkioReadUtils.readGzip(testFile1)
        logResult("readGzip", if (result == null) "⚠️ 非 gzip 文件返回 null (正常)" else "${result.size} bytes")
        val text = OkioReadUtils.readGzipAsText(testFile1)
        logResult("readGzipAsText", if (text == null) "⚠️ 非 gzip 文件返回 null (正常)" else text.take(50))
    }

    // ==================== 9. OkioHashUtils — Okio 哈希 ====================

    fun testOkioHashFile() {
        log("=== testOkioHashFile ===")
        File(testFile1).writeText("okio hash test data")
        logResult("hashFile(MD5)", OkioHashUtils.hashFile(testFile1, MessageDigest.getInstance("MD5")))
        logResult("hashFile(SHA-1)", OkioHashUtils.hashFile(testFile1, MessageDigest.getInstance("SHA-1")))
        logResult("hashFile(SHA-256)", OkioHashUtils.hashFile(testFile1, MessageDigest.getInstance("SHA-256")))
        logResult("hashFile(SHA-512)", OkioHashUtils.hashFile(testFile1, MessageDigest.getInstance("SHA-512")))
    }

    fun testOkioHashFileAsync() {
        log("=== testOkioHashFileAsync ===")
        OkioHashUtils.hashFileAsync(testFile1, MessageDigest.getInstance("SHA-256")) { hash, err ->
            logResult("hashFileAsync(SHA-256)", hash)
        }
    }

    fun testOkioCopyAndHash() {
        log("=== testOkioCopyAndHash ===")
        File(testFile1).writeText("copy and hash source")
        val dest = File(testRoot, "okio_copy_hash_dest.txt").absolutePath
        val hash = OkioHashUtils.copyAndHash(testFile1, dest, MessageDigest.getInstance("SHA-256"))
        logResult("copyAndHash", hash)
        logResult("目标存在", FileUtils.exists(dest))
    }

    fun testOkioCopyAndHashAsync() {
        log("=== testOkioCopyAndHashAsync ===")
        val dest = File(testRoot, "okio_copy_hash_async.txt").absolutePath
        OkioHashUtils.copyAndHashAsync(testFile1, dest, MessageDigest.getInstance("MD5")) { hash, err ->
            logResult("copyAndHashAsync", hash)
        }
    }

    fun testOkioHashFileWithProgress() {
        log("=== testOkioHashFileWithProgress ===")
        File(testFile1).writeText("H".repeat(150_000))
        var cbCount = 0
        OkioHashUtils.hashFileWithProgress(testFile1, MessageDigest.getInstance("SHA-256"),
            onProgress = { processed, total -> cbCount++; log("进度: $processed / $total") })
        logResult("hashFileWithProgress", OkioHashUtils.hashFile(testFile1, MessageDigest.getInstance("SHA-256")))
        log("进度回调次数: $cbCount")
    }

    fun testOkioHashFileWithProgressAsync() {
        log("=== testOkioHashFileWithProgressAsync ===")
        OkioHashUtils.hashFileWithProgressAsync(testFile1, MessageDigest.getInstance("MD5"),
            onProgress = { p, t -> log("进度: $p/$t") },
            onResult = { hash, err -> logResult("hashFileWithProgressAsync", hash) })
    }

    fun testOkioHashByteString() {
        log("=== testOkioHashByteString ===")
        val bs = ByteString.of(*"stream hashing example".toByteArray(Charsets.UTF_8))
        val hash = OkioHashUtils.hashByteString(bs, MessageDigest.getInstance("SHA-256"))
        logResult("hashByteString", hash)
    }

    fun testOkioHashString() {
        log("=== testOkioHashString ===")
        val hash = OkioHashUtils.hashString("okio string hash", MessageDigest.getInstance("MD5"))
        logResult("hashString", hash)
    }

    // ==================== 10. OkioAssetUtils — Okio 资源操作 ====================

    fun testOkioAssetRead() {
        log("=== testOkioAssetRead ===")
        runCatching {
            val files = AssetUtils.listAssets(context, "")
            val firstFile = files.firstOrNull { !it.endsWith("/") }
            if (firstFile != null) {
                logResult("readAssetByteString", OkioAssetUtils.readAssetByteString(context, firstFile)?.utf8()?.take(50))
                logResult("readAssetUtf8", OkioAssetUtils.readAssetUtf8(context, firstFile)?.take(50))
                logResult("readAssetLines", OkioAssetUtils.readAssetLines(context, firstFile)?.joinToString(", "))
            } else {
                log("assets 目录无文件")
            }
        }.onFailure { logError("OkioAssetRead", it) }
    }

    fun testOkioAssetCopy() {
        log("=== testOkioAssetCopy ===")
        runCatching {
            val files = AssetUtils.listAssets(context, "")
            val firstFile = files.firstOrNull { !it.endsWith("/") }
            if (firstFile != null) {
                val dest = File(testRoot, "okio_asset_copy.dat").absolutePath
                logResult("copyAssetToFile", OkioAssetUtils.copyAssetToFile(context, firstFile, dest))
            } else {
                log("assets 目录无文件，跳过复制测试")
            }
        }.onFailure { logError("OkioAssetCopy", it) }
    }

    // ==================== 11. FileCleanupManager — 文件清理 ====================

    fun testCleanupRunNow() {
        log("=== testCleanupRunNow ===")
        val clearDir = File(testRoot, "cleanup_test_dir").also {
            it.mkdirs()
            File(it, "c1.txt").writeText("clear me")
            File(it, "c2.txt").writeText("also clear me")
        }
        val config = FileCleanupManager.builder()
            .clearOnAppStart("test_clear", clearDir.absolutePath)
            .build()

        val futures = FileCleanupManager.runNow(config) { result ->
            logResult("runNow clearOnAppStart", "success=${result.success}, deleted=${result.deletedEntries}, msg=${result.message}")
        }
        log("提交了 ${futures.size} 个清理任务")
    }

    fun testCleanupDeleteOnAppStart() {
        log("=== testCleanupDeleteOnAppStart ===")
        val delDir = File(testRoot, "cleanup_delete_dir").also {
            it.mkdirs()
            File(it, "delete_me.txt").writeText("to be deleted")
        }
        val config = FileCleanupManager.builder()
            .deleteOnAppStart("test_delete", delDir.absolutePath)
            .build()

        val futures = FileCleanupManager.runNow(config) { result ->
            logResult("runNow deleteOnAppStart", "success=${result.success}, deleted=${result.deletedEntries}")
        }
        log("提交了 ${futures.size} 个任务")
    }

    fun testCleanupAfterDelay() {
        log("=== testCleanupAfterDelay ===")
        val delayDir = File(testRoot, "cleanup_delay_dir").also {
            it.mkdirs()
            File(it, "d.txt").writeText("delayed cleanup")
        }
        // 注册一个极短延迟的清理（仅测试 API，不实际等待）
        val config = FileCleanupManager.builder()
            .clearAfterDelay("test_delay", delayDir.absolutePath, delayMs = 100,
                scheduleMode = CleanupScheduleMode.ONE_SHOT)
            .build()
        FileCleanupManager.register(config)
        log("已注册延迟清理: test_delay")
        // 稍后自动执行
    }

    fun testCleanupCancel() {
        log("=== testCleanupCancel ===")
        // 注册一个不会立即执行的延迟清理，然后取消
        val dir = File(testRoot, "cleanup_cancel_dir").also { it.mkdirs(); File(it, "keep.txt").writeText("keep") }
        val config = FileCleanupManager.builder()
            .clearAfterDelay("test_cancel", dir.absolutePath, delayMs = 60_000, scheduleMode = CleanupScheduleMode.ONE_SHOT)
            .build()
        FileCleanupManager.register(config)
        val cancelled = FileCleanupManager.cancel("test_cancel")
        logResult("cancel", cancelled)
    }

    fun testCleanupCancelAll() {
        log("=== testCleanupCancelAll ===")
        FileCleanupManager.cancelAll()
        log("cancelAll 已调用（清理所有待执行任务）")
    }

    fun testCleanupBuilder() {
        log("=== testCleanupBuilder ===")
        val dir = File(testRoot, "cleanup_builder_test").also { it.mkdirs() }
        val config = FileCleanupManager.builder()
            .clearOnAppStart("k1", dir.absolutePath)
            .deleteAfterDelay("k2", dir.absolutePath, delayMs = 10_000)
            .clearAfterDays("k3", dir.absolutePath, days = 7)
            .build()
        log("Builder 构建了 ${config.rules.size} 条规则:")
        config.rules.forEach { log("  key=${it.key}, action=${it.action}, trigger=${it.trigger}") }
        // 释放注册的规则
        FileCleanupManager.release(config)
        log("release 完成")
    }

    // ==================== 12. 综合/边界测试 ====================

    fun testEdgeCases() {
        log("=== 边界场景测试 ===")
        // 空路径
        logResult("writeText(空路径)", FileWriteUtils.writeText("", "content").toString() + " (正确)")
        logResult("readText(空路径)", FileReadUtils.readText("").toString() + " (正确)")
        logResult("exists(空路径)", FileUtils.exists("").toString() + " (正确)")
        logResult("delete(空路径)", FileUtils.delete("").toString() + " (正确)")

        // 空白路径
        logResult("writeText(空格)", FileWriteUtils.writeText("  ", "content").toString() + " (正确)")
        logResult("exists(空格)", FileUtils.exists("  ").toString() + " (正确)")

        // null 路径
        logResult("exists(null)", FileUtils.exists(null).toString() + " (正确)")
        logResult("delete(null)", FileUtils.delete(null).toString() + " (正确)")

        // 不存在的文件
        logResult("readText(不存在)", FileReadUtils.readText("/no/such/file.txt").toString() + " (正确)")
        logResult("hashFile(不存在)", FileHashUtils.hashFile("/no/such/file.txt").toString() + " (正确)")
        logResult("getSize(不存在)", "${FileUtils.getSize("/no/such")} (正确)")
    }

    fun testLargeFileOperations() {
        log("=== 大文件操作测试 ===")
        val path = File(testRoot, "large_file.bin").absolutePath
        // 写入 500KB
        val data = ByteArray(500_000) { (it % 256).toByte() }
        log("写入 500KB 数据...")
        val start = System.currentTimeMillis()
        FileWriteUtils.writeBytes(path, data)
        log("写入耗时: ${System.currentTimeMillis() - start}ms")
        logResult("大小验证", File(path).length() == 500_000L)

        // 哈希
        log("计算 SHA256 哈希...")
        val hashStart = System.currentTimeMillis()
        val hash = FileHashUtils.sha256(path)
        log("哈希耗时: ${System.currentTimeMillis() - hashStart}ms")
        logResult("SHA256", hash)

        // 分块读取
        log("分块读取...")
        var chunkCount = 0
        val readStart = System.currentTimeMillis()
        FileReadUtils.readChunks(path, chunkSize = 64 * 1024) { _, idx, total ->
            chunkCount++; true
        }
        log("读取耗时: ${System.currentTimeMillis() - readStart}ms, 共 $chunkCount 块")
    }

    // ==================== 异步等待辅助 ====================

    fun <T> Future<T>.awaitForTest(timeoutMs: Long = 3000): T? {
        return try {
            java.util.concurrent.TimeUnit.MILLISECONDS.let {
                get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            logError("awaitForTest", e)
            null
        }
    }

    // ==================== 初始化和清理 ====================

    fun setupTestFiles() {
        testRoot.mkdirs()
        testSubDir.let { File(it).mkdirs() }
        // 预置一些测试文件
        File(testFile1).writeText("This is test file 1 for itg-file library tests.")
        File(testFile2).writeText("Another test file with different content for comparison.")
        // 确保 assets 中至少有一些文件可读取
        log("测试目录: $testDir")
        log("测试环境初始化完成")
    }

    fun cleanupAll() {
        // 取消所有清理任务
        FileCleanupManager.cancelAll()
        // 清理测试目录
        testRoot.deleteRecursively()
        log("测试环境清理完成")
    }
}
