package com.itg.itgtools.testfile

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itg.itgtools.R
import com.itg.itgtools.route.RoutePath.ITG_FILE_TEST_ACTIVITY
import com.therouter.router.Route

/**
 * itg-file 全功能测试 Activity
 *
 * 按模块分类组织所有测试场景，每个分类包含同步和异步测试。
 * 所有 API 覆盖：FileUtils / FileWriteUtils / FileReadUtils /
 * FileHashUtils / AssetUtils / Okio* 变体 / FileCleanupManager
 */
@Route(path = ITG_FILE_TEST_ACTIVITY)
class TestFileActivity : AppCompatActivity() {

    private lateinit var model: TestFileModel
    private lateinit var container: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_file)

        container = findViewById(R.id.testContainer)
        logView = findViewById(R.id.logTextView)
        logScroll = findViewById(R.id.logScrollView)
        logView.movementMethod = ScrollingMovementMethod()

        model = TestFileModel(applicationContext)
        model.onLog = { text ->
            runOnUiThread {
                logView.append("\n$text")
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
        model.setupTestFiles()

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            logView.text = ""
            log("日志已清空")
        }

        buildAllSections()
    }

    override fun onDestroy() {
        model.cleanupAll()
        super.onDestroy()
    }

    private fun log(msg: String) {
        model.onLog?.invoke(msg)
    }

    // ==================== UI 构建 ====================

    private fun buildAllSections() {
        buildSection("1. FileUtils — 基础文件操作") {
            button("exists / isFile / isDirectory (同步)") { model.testFileExists() }
            button("existsAsync (异步)") { model.testFileExistsAsync() }
            button("createFile / createDirectory / createTempFile (同步)") { model.testCreateFile() }
            button("createFileAsync (异步)") { model.testCreateFileAsync() }
            button("delete (同步)") { model.testDelete() }
            button("deleteAsync (异步)") { model.testDeleteAsync() }
            button("clearDirectory (同步)") { model.testClearDirectory() }
            button("clearDirectoryAsync (异步)") { model.testClearDirectoryAsync() }
            button("rename (同步)") { model.testRename() }
            button("renameAsync (异步)") { model.testRenameAsync() }
            button("copy (同步)") { model.testCopy() }
            button("copyWithProgress (同步+进度)") { model.testCopyWithProgress() }
            button("copyAsync (异步)") { model.testCopyAsync() }
            button("copyWithProgressAsync (异步+进度)") { model.testCopyWithProgressAsync() }
            button("copyDirectory (同步)") { model.testCopyDirectory() }
            button("move (同步)") { model.testMove() }
            button("moveAsync (异步)") { model.testMoveAsync() }
            button("listFiles / listFilesRecursive (同步)") { model.testListFiles() }
            button("listFilesRecursiveAsync (异步)") { model.testListFilesRecursiveAsync() }
            button("getFileInfo: 大小/扩展名/MIME/时间 (同步)") { model.testGetFileInfo() }
            button("getFileInfoAsync (异步)") { model.testGetFileInfoAsync() }
            button("getAvailableSpace / getTotalSpace (同步)") { model.testStorageSpace() }
        }

        buildSection("2. FileWriteUtils — 文件写入") {
            button("writeText / appendText (同步)") { model.testWriteText() }
            button("writeTextAsync (异步)") { model.testWriteTextAsync() }
            button("appendText (同步)") { model.testAppendText() }
            button("appendTextAsync (异步)") { model.testAppendTextAsync() }
            button("writeBytes / appendBytes (同步)") { model.testWriteBytes() }
            button("writeBytesAsync (异步)") { model.testWriteBytesAsync() }
            button("appendBytes (同步)") { model.testAppendBytes() }
            button("writeFromStream (同步)") { model.testWriteFromStream() }
            button("writeFromStreamAsync (异步)") { model.testWriteFromStreamAsync() }
            button("writeTextAtomic (同步)") { model.testWriteTextAtomic() }
            button("writeTextAtomicAsync (异步)") { model.testWriteTextAtomicAsync() }
            button("writeBytesAtomic (同步)") { model.testWriteBytesAtomic() }
            button("writeBytesInChunks(200KB) (同步+分块)") { model.testWriteBytesInChunks() }
            button("writeBytesInChunksAsync (异步+分块)") { model.testWriteBytesInChunksAsync() }
        }

        buildSection("3. FileReadUtils — 文件读取") {
            button("readText (同步)") { model.testReadText() }
            button("readTextAsync (异步)") { model.testReadTextAsync() }
            button("readBytes (同步)") { model.testReadBytes() }
            button("readBytesAsync (异步)") { model.testReadBytesAsync() }
            button("readLines (同步)") { model.testReadLines() }
            button("readLinesAsync (异步)") { model.testReadLinesAsync() }
            button("readLinesStreaming (同步+逐行)") { model.testReadLinesStreaming() }
            button("readLinesStreamingAsync (异步+逐行)") { model.testReadLinesStreamingAsync() }
            button("readChunks (同步+分块)") { model.testReadChunks() }
            button("readChunksAsync (异步+分块)") { model.testReadChunksAsync() }
            button("readHeadBytes / readTailBytes / readTailLines (同步)") { model.testReadHeadTailBytes() }
        }

        buildSection("4. FileHashUtils — 哈希校验") {
            button("md5 / sha1 / sha256 / sha512 / crc32 (同步)") { model.testHashFile() }
            button("md5Async / sha256Async (异步)") { model.testHashFileAsync() }
            button("hashFileWithProgress (同步+进度)") { model.testHashFileWithProgress() }
            button("hashFileWithProgressAsync (异步+进度)") { model.testHashFileWithProgressAsync() }
            button("hashBytes / hashString / crc32 (同步)") { model.testHashBytesAndString() }
            button("verify — 文件完整性校验 (同步)") { model.testVerify() }
            button("verifyAsync (异步)") { model.testVerifyAsync() }
            button("compareFiles (同步)") { model.testCompareFiles() }
            button("compareFilesAsync (异步)") { model.testCompareFilesAsync() }
        }

        buildSection("5. AssetUtils — 资源操作") {
            button("listAssets / assetExists (同步)") { model.testAssetList() }
            button("readAssetText / readAssetBytes (同步)") { model.testAssetRead() }
            button("readAssetTextAsync (异步)") { model.testAssetReadAsync() }
            button("copyAssetToFile (同步)") { model.testCopyAssetToFile() }
            button("copyRawToFile (同步)") { model.testCopyRawToFile() }
        }

        buildSection("6. OkioFileUtils — Okio 文件操作") {
            button("exists (同步)") { model.testOkioFileExists() }
            button("existsAsync (异步)") { model.testOkioFileExistsAsync() }
            button("copy (同步)") { model.testOkioCopy() }
            button("copyAsync (异步)") { model.testOkioCopyAsync() }
            button("move (同步)") { model.testOkioMove() }
            button("moveAsync (异步)") { model.testOkioMoveAsync() }
            button("delete (同步)") { model.testOkioDelete() }
            button("deleteAsync (异步)") { model.testOkioDeleteAsync() }
            button("createDirectory (同步)") { model.testOkioCreateDirectory() }
            button("createDirectoryAsync (异步)") { model.testOkioCreateDirectoryAsync() }
            button("getSize / listRecursive / 磁盘空间 (同步)") { model.testOkioFileInfo() }
            button("withTimeout (同步+超时)") { model.testOkioWithTimeout() }
        }

        buildSection("7. OkioWriteUtils — Okio 写入") {
            button("writeByteString (同步)") { model.testOkioWriteByteString() }
            button("writeUtf8 (同步)") { model.testOkioWriteUtf8() }
            button("writeUtf8Async (异步)") { model.testOkioWriteUtf8Async() }
            button("appendUtf8 (同步)") { model.testOkioAppendUtf8() }
            button("appendUtf8Async (异步)") { model.testOkioAppendUtf8Async() }
            button("writeFromStream (同步)") { model.testOkioWriteFromStream() }
            button("writeFromStreamAsync (异步)") { model.testOkioWriteFromStreamAsync() }
            button("writeWithTimeout (同步+超时)") { model.testOkioWriteWithTimeout() }
            button("writeWithProgress (同步+进度)") { model.testOkioWriteWithProgress() }
            button("writeGzip (同步)") { model.testOkioWriteGzip() }
            button("writeAtomic (同步)") { model.testOkioWriteAtomic() }
        }

        buildSection("8. OkioReadUtils — Okio 读取") {
            button("readByteString / readUtf8 (同步)") { model.testOkioReadByteString(); model.testOkioReadUtf8() }
            button("readByteStringAsync / readUtf8Async (异步)") { model.testOkioReadByteStringAsync(); model.testOkioReadUtf8Async() }
            button("readLines (同步)") { model.testOkioReadLines() }
            button("readLinesAsync (异步)") { model.testOkioReadLinesAsync() }
            button("readLinesStreaming (同步+流式)") { model.testOkioReadLinesStreaming() }
            button("readWithTimeout (同步+超时)") { model.testOkioReadWithTimeout() }
            button("readWithProgress (同步+进度)") { model.testOkioReadWithProgress() }
            button("readGzip (同步)") { model.testOkioReadGzip() }
        }

        buildSection("9. OkioHashUtils — Okio 哈希") {
            button("hashFile: MD5/SHA-1/SHA-256/SHA-512 (同步)") { model.testOkioHashFile() }
            button("hashFileAsync (异步)") { model.testOkioHashFileAsync() }
            button("copyAndHash (同步)") { model.testOkioCopyAndHash() }
            button("copyAndHashAsync (异步)") { model.testOkioCopyAndHashAsync() }
            button("hashFileWithProgress (同步+进度)") { model.testOkioHashFileWithProgress() }
            button("hashFileWithProgressAsync (异步+进度)") { model.testOkioHashFileWithProgressAsync() }
            button("hashByteString / hashString (同步)") { model.testOkioHashByteString(); model.testOkioHashString() }
        }

        buildSection("10. OkioAssetUtils — Okio 资源操作") {
            button("readAssetByteString / readAssetUtf8 / readAssetLines (同步)") { model.testOkioAssetRead() }
            button("copyAssetToFile (同步)") { model.testOkioAssetCopy() }
        }

        buildSection("11. FileCleanupManager — 文件清理") {
            button("runNow: clearOnAppStart (同步)") { model.testCleanupRunNow() }
            button("runNow: deleteOnAppStart (同步)") { model.testCleanupDeleteOnAppStart() }
            button("register: AfterDelay (注册延迟清理)") { model.testCleanupAfterDelay() }
            button("cancel 取消单条规则") { model.testCleanupCancel() }
            button("cancelAll 取消所有规则") { model.testCleanupCancelAll() }
            button("Builder 链式构建") { model.testCleanupBuilder() }
        }

        buildSection("12. 边界场景 + 大文件综合测试") {
            button("边界测试: 空路径 / null / 不存在文件") { model.testEdgeCases() }
            button("大文件(500KB): 写入 / 哈希 / 分块读取") { model.testLargeFileOperations() }
        }
    }

    // ==================== UI 构建辅助 ====================

    private fun buildSection(title: String, builder: LinearLayout.() -> Unit) {
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#1565C0"))
            textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        }
        container.addView(titleView)

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), dp(4))
        }
        container.addView(buttonContainer)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        container.addView(divider)

        // Execute builder on buttonContainer
        buttonContainer.builder()
    }

    private fun LinearLayout.button(label: String, action: () -> Unit) {
        val btn = Button(this@TestFileActivity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            setPadding(dp(12), dp(6), dp(12), dp(6))

            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = dp(2)
            this.layoutParams = layoutParams
        }

        btn.setOnClickListener {
            log("\n▶ $label")
            val start = System.currentTimeMillis()
            action()
            val cost = System.currentTimeMillis() - start
            log("⏱ 耗时: ${cost}ms")
        }
        addView(btn)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
