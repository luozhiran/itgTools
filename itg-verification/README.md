# ITG Verification — Android 文件验证工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](./LICENSE)

ITG Verification 是一个面向日常开发的文件验证工具库，覆盖下载完整性校验、文件哈希验证、压缩包验证、文件比对、重复查找等场景。异步操作基于 [itg-thread-pools](../itg-thread-pools/)，哈希计算基于 [itg-file](../itg-file/)。

---

## 目录

- [使用场景一览](#使用场景一览)
- [快速开始](#快速开始)
- [架构概览](#架构概览)
- [详细教程](#详细教程)
  - [1. 哈希校验 — HashVerifier](#1-哈希校验--hashverifier)
  - [2. 完整性校验 — IntegrityVerifier](#2-完整性校验--integrityverifier)
  - [3. 压缩包验证 — ArchiveVerifier](#3-压缩包验证--archiveverifier)
  - [4. 下载验证 — DownloadVerifier](#4-下载验证--downloadverifier)
  - [5. 文件比对 — FileComparator](#5-文件比对--filecomparator)
- [组合实战](#组合实战)
- [常见问题](#常见问题)
- [API 参考](#api-参考)
- [许可证](#许可证)

---

## 使用场景一览

> 点击链接直达对应教程。

| 你想验证什么？ | 使用工具 | 跳转 |
|---------------|---------|------|
| 🔐 **下载的 APK 哈希是否匹配** | HashVerifier | [→](#1-哈希校验--hashverifier) |
| 📄 **从 .md5 / .sha256 文件校验** | HashVerifier + DownloadVerifier | [→](#13-校验文件读取) |
| 📦 **ZIP 包是否损坏** | ArchiveVerifier | [→](#3-压缩包验证--archiveverifier) |
| 💣 **ZIP 解压前安全检查** (Zip Bomb / 路径穿越) | ArchiveVerifier | [→](#32-解压前安全检查) |
| 🔏 **APK 签名是否有效** | ArchiveVerifier | [→](#33-apk-签名验证) |
| 📏 **下载文件大小是否与 Content-Length 一致** | DownloadVerifier | [→](#4-下载验证--downloadverifier) |
| ⏸️ **断点续传是否可行** | DownloadVerifier | [→](#42-断点续传验证) |
| 🔍 **文件是否存在 / 非空 / 尺寸正确** | IntegrityVerifier | [→](#2-完整性校验--integrityverifier) |
| 🪄 **文件头 Magic Number 校验** | IntegrityVerifier | [→](#22-文件头校验) |
| ⚖️ **两个文件是否完全一致** | FileComparator | [→](#5-文件比对--filecomparator) |
| 📂 **两个目录的差异** (新增/删除/修改) | FileComparator | [→](#52-目录比对) |
| 🧹 **查找重复文件** (释放空间) | FileComparator | [→](#53-重复文件查找) |
| 📋 **批量校验多个文件的哈希** | HashVerifier | [→](#12-批量校验) |

---

## 快速开始

### 1. 添加依赖

```kotlin
dependencies {
    implementation(project(":itg-verification"))
    // 自动传递依赖 itg-thread-pools + itg-file
}
```

### 2. 基本使用

```kotlin
import com.itg.itg_erification.hash.HashVerifier
import com.itg.itg_erification.download.DownloadVerifier
import com.itg.itg_erification.archive.ArchiveVerifier
import com.itg.itg_thread_pools.executor.TaskExecutor

// 下载后验证
val result = DownloadVerifier.verifyDownload(
    "/sdcard/app.apk",
    expectedSize = 25 * 1024 * 1024,
    expectedHash = "e3b0c44298fc1c..."
)
if (result.isValid) installApk() else deleteAndRetry()

// ZIP 完整性检查
val zipResult = ArchiveVerifier.verifyZip("/sdcard/download.zip")
if (zipResult.isValid) unzip()

// 异步版本
ArchiveVerifier.verifyZipAsync("/sdcard/download.zip") { result ->
    TaskExecutor.main { showResult(result) }
}
```

---

## 架构概览

### 📂 按功能快速跳转

| 分类 | 工具类 | 一句话描述 |
|------|--------|-----------|
| 🔐 哈希 | `HashVerifier` | MD5/SHA/CRC32 校验 + 批量校验 + 校验文件解析 |
| 📏 完整性 | `IntegrityVerifier` | 存在/非空/尺寸/Magic Number/权限检查 |
| 📦 压缩包 | `ArchiveVerifier` | ZIP/GZIP 完整性 + Zip Bomb 检测 + APK 签名 |
| ⬇️ 下载 | `DownloadVerifier` | 大小验证 + 校验文件配对 + 断点续传 + 综合验证 |
| ⚖️ 比对 | `FileComparator` | 逐字节/哈希比对 + 目录差异 + 重复文件查找 |

### 📦 包结构

```
com.itg.itg_erification
├── hash/
│   └── HashVerifier.kt           — 哈希校验
├── integrity/
│   └── IntegrityVerifier.kt      — 文件完整性校验
├── archive/
│   └── ArchiveVerifier.kt        — 压缩包/归档验证
├── download/
│   └── DownloadVerifier.kt       — 下载专项验证
└── compare/
    └── FileComparator.kt         — 文件比对/去重

依赖: itg-thread-pools + itg-file
```

---

## 详细教程

### 1. 哈希校验 — HashVerifier

#### 1.1 单文件哈希校验

```kotlin
// MD5
val r1 = HashVerifier.verifyMd5("/sdcard/app.apk", "a1b2c3d4...")
println(r1)  // PASS (MD5) 或 FAIL (MD5) expected=... actual=...

// SHA-256
val r2 = HashVerifier.verifySha256("/sdcard/app.apk", "e3b0c44...")

// CRC32（快速变化检测）
val r3 = HashVerifier.verifyCrc32("/sdcard/data.bin", "a1b2c3d4")

// 通用（指定算法字符串）
val r4 = HashVerifier.verifyHash(path, expectedHash, "SHA-512")

// 异步
HashVerifier.verifyMd5Async(path, expectedMd5) { result -> }
```

#### 1.2 批量校验

```kotlin
val results = HashVerifier.batchVerify(mapOf(
    "/sdcard/file1.bin" to "SHA-256:e3b0c44...",
    "/sdcard/file2.bin" to "MD5:d41d8cd...",
    "/sdcard/file3.bin" to "a1b2c3d4..."  // 默认 SHA-256
))
results.forEach { (path, result) -> println("$path: $result") }

// 全部通过才返回 true
val allPassed = HashVerifier.batchVerifyAll(files)

// 异步
HashVerifier.batchVerifyAsync(files) { results -> }
```

#### 1.3 校验文件读取

```kotlin
// 从 .md5 文件验证
val r1 = HashVerifier.verifyFromMd5File(
    "/sdcard/download.iso",
    "/sdcard/download.iso.md5")

// 从 .sha256 文件验证
val r2 = HashVerifier.verifyFromChecksumFile(
    "/sdcard/download.iso",
    "/sdcard/download.iso.sha256")

// 手动解析校验文件
val hash = HashVerifier.parseChecksumFile("/sdcard/file.md5")
// 支持格式: "d41d8cd...  file.zip" 或纯 "d41d8cd..."
```

---

### 2. 完整性校验 — IntegrityVerifier

#### 2.1 基础检查

```kotlin
// 存在性
IntegrityVerifier.verifyExists("/sdcard/file.txt")

// 非空
IntegrityVerifier.verifyNotEmpty("/sdcard/file.txt")

// 尺寸检查
IntegrityVerifier.verifyMinSize("/sdcard/photo.jpg", 1024)        // ≥ 1KB
IntegrityVerifier.verifyMaxSize("/sdcard/photo.jpg", 10*1024*1024) // ≤ 10MB
IntegrityVerifier.verifySizeRange(path, minBytes = 1024, maxBytes = 10*1024*1024)

// 一站式
IntegrityVerifier.verify(path,
    checks = listOf("exists", "notEmpty", "minSize", "maxSize", "isFile"),
    minBytes = 1024,
    maxBytes = 100 * 1024 * 1024)

// 权限
IntegrityVerifier.verifyReadable(path)
IntegrityVerifier.verifyWritable(path)
IntegrityVerifier.verifyExecutable(path)
```

#### 2.2 文件头校验

```kotlin
// 内置常见 Magic Number
val pngOk = IntegrityVerifier.verifyFileHeader(path, IntegrityVerifier.PNG_HEADER)
val jpegOk = IntegrityVerifier.verifyFileHeader(path, IntegrityVerifier.JPEG_HEADER)
val zipOk = IntegrityVerifier.verifyFileHeader(path, IntegrityVerifier.ZIP_HEADER)
val pdfOk = IntegrityVerifier.verifyFileHeader(path, IntegrityVerifier.PDF_HEADER)
val gzipOk = IntegrityVerifier.verifyFileHeader(path, IntegrityVerifier.GZIP_HEADER)
val dexOk = IntegrityVerifier.verifyFileHeader(path, IntegrityVerifier.DEX_HEADER)

// 自定义偏移
val mp4Ok = IntegrityVerifier.verifyFileHeader(
    path, IntegrityVerifier.MP4_HEADER_OFFSET_4, offset = 4)

// 自定义 Magic
val ok = IntegrityVerifier.verifyFileHeader(path, byteArrayOf(0x42, 0x4D), offset = 0)
```

---

### 3. 压缩包验证 — ArchiveVerifier

#### 3.1 ZIP 完整性

```kotlin
// 完整 CRC 校验
val result = ArchiveVerifier.verifyZip("/sdcard/download.zip")
println("Valid: ${result.isValid}, entries: ${result.entries}")

// 异步
ArchiveVerifier.verifyZipAsync(path) { result -> }

// 单条目 CRC 验证
val entryOk = ArchiveVerifier.verifyZipEntry(
    "/sdcard/archive.zip", "data/config.json", expectedCrc = 123456789L)

// 列出所有条目
val entries = ArchiveVerifier.listZipEntries("/sdcard/archive.zip")
entries.forEach { println("${it["name"]}: ${it["size"]} bytes") }
```

#### 3.2 解压前安全检查

```kotlin
// 防 Zip Bomb + 路径穿越
val safety = ArchiveVerifier.verifyZipSafety("/sdcard/untrusted.zip")
if (!safety.isValid) {
    Log.e("Unzip", "Unsafe archive: ${safety.reason}")
    return  // 拒绝解压
}
// 安全，可以解压

// 自定义阈值
ArchiveVerifier.verifyZipSafety(path,
    maxCompressionRatio = 50,   // 压缩比 > 50 视为 Zip Bomb
    maxEntries = 10000,         // 最多 10000 个条目
    checkZipSlip = true)        // 检查路径穿越（"../"）
```

#### 3.3 APK 签名验证

```kotlin
val sig = ArchiveVerifier.verifyApkSignature(context, "/sdcard/app.apk")
if (sig.isValid) {
    println("Package: ${sig.packageName}")
    println("Version: ${sig.versionName} (${sig.versionCode})")
    println("Signatures: ${sig.signatures}")
    // Signatures: [SHA-256: AA:BB:CC:...]
} else {
    println("Invalid: ${sig.reason}")
}

// 异步
ArchiveVerifier.verifyApkSignatureAsync(context, path) { result -> }
```

#### 3.4 GZIP 校验

```kotlin
val ok = ArchiveVerifier.verifyGzip("/sdcard/data.json.gz")
ArchiveVerifier.verifyGzipAsync(path) { result -> }
```

---

### 4. 下载验证 — DownloadVerifier

#### 4.1 尺寸验证

```kotlin
// 对比 Content-Length
val result = DownloadVerifier.verifySize("/sdcard/download.apk", 25 * 1024 * 1024)

// 至少达到最小尺寸（防止截断）
val minOk = DownloadVerifier.verifyMinSize("/sdcard/partial.zip", 100 * 1024)
```

#### 4.2 断点续传验证

```kotlin
val resume = DownloadVerifier.verifyResumePossible(
    "/sdcard/partial_download.zip",
    expectedTotal = 100 * 1024 * 1024)
when {
    resume.isComplete -> println("已完整，无需下载")
    resume.isValid -> println("可从 ${resume.actualSize} 续传")
    else -> println("异常: ${resume.messages}")
}
```

#### 4.3 校验文件配对

```kotlin
// 自动查找同名 .md5 文件
val r1 = DownloadVerifier.verifyWithMd5File("/sdcard/download.iso")

// 显式指定
val r2 = DownloadVerifier.verifyWithMd5File(
    "/sdcard/download.iso", "/sdcard/checksums/download.iso.md5")

// SHA-256
val r3 = DownloadVerifier.verifyWithShaFile(
    "/sdcard/download.iso", algorithm = "SHA-256")
```

#### 4.4 综合验证（推荐）

```kotlin
// 一次验证: 存在性 + 尺寸 + 哈希
val result = DownloadVerifier.verifyDownload(
    "/sdcard/app.apk",
    expectedSize = 25 * 1024 * 1024,
    expectedHash = "e3b0c44298fc1c...",
    hashAlgo = "SHA-256"
)
println(result)
// PASS | size=26214400/26214400 | hash=OK
// 或 FAIL | Size mismatch: expected=26214400, actual=0

// 异步
DownloadVerifier.verifyDownloadAsync(path, 25*1024*1024, "e3b0c...") { result -> }
```

#### 4.5 分片下载验证

```kotlin
// 检查 file.part0 ~ file.part9 是否全部到位
val missing = DownloadVerifier.verifyParts(
    "/sdcard/parts/", prefix = "download", expectedCount = 10)
if (missing.isEmpty()) {
    mergeParts()  // 所有分片就绪
} else {
    println("Missing parts: $missing")
}
```

---

### 5. 文件比对 — FileComparator

#### 5.1 两文件比对

```kotlin
// 逐字节（100% 精确，适合中小文件）
val r1 = FileComparator.compareBytes("/sdcard/a.txt", "/sdcard/b.txt")

// 按 SHA-256 哈希（快速，适合大文件）
val r2 = FileComparator.compareByHash("/sdcard/large1.iso", "/sdcard/large2.iso")

// 异步
FileComparator.compareBytesAsync(path1, path2) { result -> }
FileComparator.compareByHashAsync(path1, path2, "MD5") { result -> }
```

#### 5.2 目录比对

```kotlin
val diff = FileComparator.compareDirectories(
    "/sdcard/backup/v1/", "/sdcard/current/v2/")
println("""
    新增: ${diff.added.size}
    删除: ${diff.removed.size}
    修改: ${diff.modified.size}
    未变: ${diff.unchanged.size}
""".trimIndent())

// 异步
FileComparator.compareDirectoriesAsync(dir1, dir2) { diff ->
    if (diff.hasDifference) {
        println("${diff.totalChanged} files changed")
    }
}
```

#### 5.3 重复文件查找

```kotlin
// 扫描目录重复文件
val duplicates = FileComparator.findDuplicates("/sdcard/Downloads/")
val totalWaste = duplicates.sumOf { it.wastedBytes }

println("${duplicates.size} 组重复文件，共浪费 ${totalWaste / (1024*1024)}MB")

duplicates.forEach { group ->
    println("SHA-256: ${group.hash.take(8)}... ×${group.files.size}")
    // 保留第一个，删除其余
    group.files.drop(1).forEach { duplicate ->
        java.io.File(duplicate).delete()
    }
}

// 异步
FileComparator.findDuplicatesAsync("/sdcard/Downloads/") { groups -> }

// 指定文件列表中查找重复
val result = FileComparator.findDuplicatesInList(
    listOf("/sdcard/a.jpg", "/sdcard/b.jpg", "/sdcard/c.jpg"))
```

---

## 组合实战

### 场景 1: APK 下载 → 验证 → 安装

```kotlin
class ApkDownloadManager(private val context: Context) {

    fun downloadAndVerify(
        url: String,
        destPath: String,
        expectedSize: Long,
        expectedSha256: String,
        onResult: (Boolean, String) -> Unit
    ) {
        TaskExecutor.io {
            try {
                // 1. 下载
                downloadFile(url, destPath)

                // 2. 综合验证
                val verifyResult = DownloadVerifier.verifyDownload(
                    destPath, expectedSize, expectedSha256, "SHA-256")
                if (!verifyResult.isValid) {
                    FileUtils.delete(destPath)
                    TaskExecutor.main { onResult(false, "验证失败: ${verifyResult.messages}") }
                    return@io
                }

                // 3. APK 签名验证
                val sigResult = ArchiveVerifier.verifyApkSignature(context, destPath)
                if (!sigResult.isValid) {
                    FileUtils.delete(destPath)
                    TaskExecutor.main { onResult(false, "签名无效: ${sigResult.reason}") }
                    return@io
                }

                TaskExecutor.main { onResult(true, "${sigResult.packageName} v${sigResult.versionName}") }
            } catch (e: Exception) {
                TaskExecutor.main { onResult(false, e.message ?: "Unknown error") }
            }
        }
    }

    private fun downloadFile(url: String, destPath: String) { /* 实现下载 */ }
}
```

### 场景 2: ZIP 安全解压

```kotlin
fun safeUnzip(zipPath: String, destDir: String): Boolean {
    // 1. 安全预检（防 Zip Bomb + 路径穿越）
    val safety = ArchiveVerifier.verifyZipSafety(
        zipPath,
        maxCompressionRatio = 100,
        checkZipSlip = true)
    if (!safety.isValid) {
        Log.e("Unzip", "Safety check failed: ${safety.reason}")
        return false
    }

    // 2. ZIP 完整性校验
    val integrity = ArchiveVerifier.verifyZip(zipPath)
    if (!integrity.isValid) {
        Log.e("Unzip", "ZIP corrupted: ${integrity.reason}")
        return false
    }

    // 3. 解压
    try {
        unzipToDir(zipPath, destDir)
        return true
    } catch (e: Exception) {
        Log.e("Unzip", "Extraction failed", e)
        return false
    }
}
```

### 场景 3: 备份完整性审计

```kotlin
fun auditBackup(originalDir: String, backupDir: String): String {
    val diff = FileComparator.compareDirectories(originalDir, backupDir)

    return buildString {
        appendLine("备份审计报告")
        appendLine("================")
        appendLine("新增文件: ${diff.added.size}")
        diff.added.take(10).forEach { appendLine("  + $it") }
        appendLine("删除文件: ${diff.removed.size}")
        diff.removed.take(10).forEach { appendLine("  - $it") }
        appendLine("修改文件: ${diff.modified.size}")
        diff.modified.take(10).forEach { appendLine("  ~ $it") }
        appendLine("未变文件: ${diff.unchanged.size}")
        appendLine("状态: ${if (diff.hasDifference) "⚠ 存在差异" else "✅ 完全一致"}")
    }
}
```

### 场景 4: 存储空间清理（重复文件）

```kotlin
fun cleanDuplicates(directory: String, dryRun: Boolean = true): Long {
    val duplicates = FileComparator.findDuplicates(directory)
    var freed = 0L

    duplicates.forEach { group ->
        val keep = group.files.first()
        val remove = group.files.drop(1)

        if (dryRun) {
            println("[DRY RUN] 保留: $keep")
            remove.forEach { println("  删除: $it") }
            freed += group.wastedBytes
        } else {
            remove.forEach { java.io.File(it).delete() }
            freed += group.wastedBytes
        }
    }

    return freed
}
```

---

## 常见问题

### Q: 和 itg-file 的 FileHashUtils 有什么区别？

A: `FileHashUtils` 负责**计算**哈希（how），`HashVerifier` 负责**验证**哈希（is it correct?）。验证器提供结构化的 `HashResult`、批量校验、校验文件解析等高层 API。

### Q: ZIP 验证和 Zip Bomb 检测的性能？

A: `verifyZipSafety()` 仅遍历 ZIP 目录条目（不解压数据），即使 GB 级 ZIP 也能在毫秒内完成。`verifyZip()` 需要遍历所有条目的数据流以触发 CRC 校验，耗时与文件大小成正比。

### Q: 什么是"路径穿越" (Zip Slip)？

A: 攻击者在 ZIP 条目名中嵌入 `../`，解压时可能覆盖系统文件。例如条目名为 `../../etc/hosts` 的 ZIP。`verifyZipSafety(checkZipSlip=true)` 会检测并拒绝此类条目。

---

## API 参考

### HashVerifier

| 方法 | 说明 |
|------|------|
| `verifyMd5(path, expected)` / `verifyMd5Async(...)` | MD5 校验 |
| `verifySha1(path, expected)` / `verifySha1Async(...)` | SHA-1 校验 |
| `verifySha256(path, expected)` / `verifySha256Async(...)` | SHA-256 校验 |
| `verifySha512(path, expected)` | SHA-512 校验 |
| `verifyHash(path, expected, algorithm)` / `verifyHashAsync(...)` | 通用哈希校验 |
| `verifyCrc32(path, expected)` / `verifyCrc32Async(...)` | CRC32 校验 |
| `batchVerify(files)` / `batchVerifyAsync(...)` | 批量校验 |
| `batchVerifyAll(files)` | 批量校验，全部通过返回 true |
| `verifyFromMd5File(file, md5File)` | 从 .md5 文件校验 |
| `verifyFromChecksumFile(file, checksumFile, algo)` | 从校验文件校验 |
| `parseChecksumFile(path)` | 解析校验文件 |

### IntegrityVerifier

| 方法 | 说明 |
|------|------|
| `verifyExists(path)` | 存在性检查 |
| `verifyNotEmpty(path)` | 非空检查 |
| `verifyMinSize(path, minBytes)` | 最小尺寸 |
| `verifyMaxSize(path, maxBytes)` | 最大尺寸 |
| `verifySizeRange(path, minBytes, maxBytes)` | 尺寸范围 |
| `verifyFileHeader(path, magic, offset)` / `...Async(...)` | Magic Number |
| `verify(path, checks, minBytes, maxBytes)` / `verifyAsync(...)` | 一站式 |
| `verifyReadable(path)` | 可读 |
| `verifyWritable(path)` | 可写 |
| `verifyExecutable(path)` | 可执行 |
| **(常量)** PNG/JPEG/GIF/PDF/ZIP/GZIP/MP3/DEX_HEADER | 内置 Magic |

### ArchiveVerifier

| 方法 | 说明 |
|------|------|
| `verifyZip(path)` / `verifyZipAsync(...)` | ZIP CRC 完整性 |
| `verifyZipEntry(path, entryName, expectedCrc)` | 单条目 CRC |
| `verifyZipSafety(path, maxRatio, maxEntries, zipSlip)` | 安全预检 |
| `verifyZipSafetyAsync(...)` | 异步安全预检 |
| `verifyGzip(path)` / `verifyGzipAsync(...)` | GZIP 校验 |
| `verifyApkSignature(context, apkPath)` / `...Async(...)` | APK 签名 |
| `listZipEntries(path)` / `listZipEntriesAsync(...)` | 列出条目 |

### DownloadVerifier

| 方法 | 说明 |
|------|------|
| `verifySize(filePath, expectedSize)` / `verifySizeAsync(...)` | 尺寸验证 |
| `verifyMinSize(filePath, minBytes)` | 最小尺寸 |
| `verifyWithMd5File(filePath, md5File)` / `...Async(...)` | .md5 文件验证 |
| `verifyWithShaFile(filePath, algo, shaFile)` | .sha 文件验证 |
| `verifyResumePossible(existingFile, total)` | 断点续传判断 |
| `verifyDownload(path, size, hash, algo)` / `verifyDownloadAsync(...)` | 综合验证 |
| `verifyParts(dir, prefix, count)` / `verifyPartsAsync(...)` | 分片验证 |

### FileComparator

| 方法 | 说明 |
|------|------|
| `compareBytes(path1, path2)` / `compareBytesAsync(...)` | 逐字节比对 |
| `compareByHash(path1, path2, algo)` / `compareByHashAsync(...)` | 哈希比对 |
| `compareDirectories(dir1, dir2, content)` / `...Async(...)` | 目录差异 |
| `findDuplicates(directory)` / `findDuplicatesAsync(...)` | 重复文件查找 |
| `findDuplicatesInList(paths)` | 列表内查重 |

---

## 许可证

```
MIT License — Copyright (c) 2026 ITG Team
```
