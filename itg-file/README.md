# ITG File — Android 文件处理工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/android-5.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](./LICENSE)

ITG File 是一个功能全面的 Android 文件处理工具库，提供文件的创建、删除、复制、移动、读写、哈希校验等一站式解决方案。**同时提供 java.io 传统 API 和 Okio 现代 API 两套实现**，所有操作均支持同步和异步双模式，异步基于 [itg-thread-pools](../itg-thread-pools/) 线程池实现。

---

## 目录

- [核心特性](#核心特性)
- [快速开始](#快速开始)
- [架构概览](#架构概览)
- [同步与异步模式](#同步与异步模式)
- [java.io vs Okio 双引擎](#javaio-vs-okio-双引擎)
- [详细教程](#详细教程)
  - [java.io 引擎](#javaio-引擎)
    - [1. 文件基础操作 — FileUtils](#1-文件基础操作--fileutils)
    - [2. 文件读取 — FileReadUtils](#2-文件读取--filereadutils)
    - [3. 文件写入 — FileWriteUtils](#3-文件写入--filewriteutils)
    - [4. 文件哈希 — FileHashUtils](#4-文件哈希--filehashutils)
  - [Okio 引擎](#okio-引擎)
    - [5. Okio 文件操作 — OkioFileUtils](#5-okio-文件操作--okiofileutils)
    - [6. Okio 读取 — OkioReadUtils](#6-okio-读取--okioreadutils)
    - [7. Okio 写入 — OkioWriteUtils](#7-okio-写入--okiowriteutils)
    - [8. Okio 流式哈希 — OkioHashUtils](#8-okio-流式哈希--okiohashutils)
- [组合实战](#组合实战)
- [性能建议](#性能建议)
- [常见问题](#常见问题)
- [API 参考](#api-参考)
- [更新日志](#更新日志)
- [许可证](#许可证)

---

## 核心特性

### java.io 引擎

| 分类 | 工具类 | 功能概述 | 同步 | 异步 |
|------|--------|----------|------|------|
| 🗂️ 基础 | `FileUtils` | 创建/删除/复制/移动/重命名/列表/信息/存储空间 | ✅ | ✅ |
| 📖 读取 | `FileReadUtils` | 文本/字节/按行/分块/流读取/头尾部 | ✅ | ✅ |
| ✏️ 写入 | `FileWriteUtils` | 文本/字节/追加/流写入/URI写入/原子写入 | ✅ | ✅ |
| 🔐 哈希 | `FileHashUtils` | MD5/SHA1/SHA256/SHA512/CRC32/完整性校验 | ✅ | ✅ |

### Okio 引擎 (基于 Okio 3.17.0)

| 分类 | 工具类 | 功能概述 | 同步 | 异步 |
|------|--------|----------|------|------|
| 🚀 基础 | `OkioFileUtils` | 快速复制/原子移动/递归删除/目录遍历/超时控制 | ✅ | ✅ |
| 📖 读取 | `OkioReadUtils` | ByteString/流式读取/行读取/Gzip解压/超时/进度 | ✅ | ✅ |
| ✏️ 写入 | `OkioWriteUtils` | Buffer写入/Gzip压缩/追加/流写入/超时/进度/原子 | ✅ | ✅ |
| 🔐 哈希 | `OkioHashUtils` | 流式哈希(边读边算)/写入时哈希/复制+哈希/Gzip+哈希 | ✅ | ✅ |

### 异步模式特点

- 基于 `itg-thread-pools` 的 `TaskExecutor.io`（I/O 线程池）
- 所有异步方法返回 `Future<*>`，可取消
- 回调在 I/O 线程执行，使用 `TaskExecutor.main {}` 切回 UI 线程
- 大文件操作支持进度回调

---

## 快速开始

### 1. 添加依赖

在 app 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":itg-file"))
    // itg-thread-pools 会自动传递依赖
}
```

### 2. 基本使用

#### 同步模式

```kotlin
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.write.FileWriteUtils

// 写入文件
FileWriteUtils.writeText("/sdcard/data.txt", "Hello World")

// 读取文件
val content = FileReadUtils.readText("/sdcard/data.txt")
println(content)  // Hello World

// 获取信息
val info = FileUtils.getFileInfo("/sdcard/data.txt")
```

#### 异步模式

```kotlin
import com.itg.itg_thread_pools.executor.TaskExecutor

// 异步读取
FileReadUtils.readTextAsync("/sdcard/data.txt") { content, error ->
    if (error != null) {
        Log.e("File", "Read failed", error)
    } else {
        // content 可用，但这里在 I/O 线程
        TaskExecutor.main {
            textView.text = content  // 切回 UI 线程
        }
    }
}

// 异步写入（返回 Future 可取消）
val future = FileWriteUtils.writeTextAsync("/sdcard/data.txt", "Hello") { success ->
    TaskExecutor.main {
        if (success) Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
    }
}

// 如果需要取消
TaskExecutor.cancel(future)
```

---

## 架构概览

### 📂 按功能快速跳转

> 点击下方链接，直达你需要的工具类教程。

| 你想做什么？ | java.io 引擎 | Okio 引擎 (更快) |
|-------------|-------------|------------------|
| 🔍 **检查文件** (存在? 大小? 类型?) | [FileUtils](#1-文件基础操作--fileutils) | [OkioFileUtils](#5-okio-文件操作--okiofileutils) |
| 📁 **创建/删除/重命名** 文件或目录 | [FileUtils](#12-创建) | [OkioFileUtils](#51-快速复制与原子移动) |
| 📋 **复制/移动** 文件或目录 | [FileUtils.copy/move](#14-复制与移动) | [OkioFileUtils.copy/move](#51-快速复制与原子移动) |
| 📜 **列出目录** 中的所有文件 | [FileUtils.listFiles](#15-文件列表) | [OkioFileUtils.list/listRecursively](#53-目录遍历) |
| 📖 **读取文本** (JSON/XML/CSV) | [FileReadUtils.readText](#21-读取文本) | [OkioReadUtils.readUtf8](#62-读取为字符串) |
| 📖 **读取二进制** (图片/音视频) | [FileReadUtils.readBytes](#22-读取字节) | [OkioReadUtils.readByteString](#61-bytestring--不可变字节序列) |
| 📄 **逐行读取** 大文件 (日志/CSV) | [FileReadUtils.readLinesStreaming](#23-按行读取) | [OkioReadUtils.readLinesStreaming](#67-流式按行读取) |
| 📦 **分块读取** 超大数据 (>100MB) | [FileReadUtils.readChunks](#24-大文件分块读取) | [OkioReadUtils.readWithProgress](#65-带进度读取) |
| ✏️ **覆盖写入** 字符串/字节 | [FileWriteUtils.writeText](#31-写入文本) | [OkioWriteUtils.writeUtf8](#72-写入与追加) |
| ➕ **追加写入** 日志/增量数据 | [FileWriteUtils.appendText](#31-写入文本) | [OkioWriteUtils.appendUtf8](#72-写入与追加) |
| 🔒 **原子写入** (防崩溃数据损坏) | [FileWriteUtils.writeTextAtomic](#34-原子写入防数据损坏) | [OkioWriteUtils.writeAtomic](#76-原子写入okio-版本) |
| 🗜️ **Gzip 压缩/解压** | — | [OkioReadUtils.readGzip](#66-gzip-解压读取) / [OkioWriteUtils.writeGzip](#75-gzip-压缩写入) |
| ⏱️ **带超时** 的文件 I/O | — | [OkioFileUtils.withTimeout](#52-超时控制的文件操作) |
| 📊 **带进度条** 的大文件操作 | [FileUtils.copyWithProgress](#14-复制与移动) | [OkioReadUtils.readWithProgress](#65-带进度读取) / [OkioWriteUtils.writeWithProgress](#74-带进度写入) |
| 🔐 **计算 MD5/SHA** 校验文件 | [FileHashUtils.md5/sha256](#41-常用哈希) | [OkioHashUtils.hashFile](#86-基础哈希不带组合操作) |
| ✅ **校验文件完整性** (对比哈希) | [FileHashUtils.verify](#45-文件完整性校验) | [OkioHashUtils.copyAndVerify](#84-复制--自动校验) |
| 🔄 **复制+同时算哈希** (一步到位) | — | [OkioHashUtils.copyAndHash](#83-复制--哈希--进度-一步到位) |
| 🚀 **边写边算哈希** (流式) | — | [OkioHashUtils.hashWhileWriting](#82-边写边算哈希) |
| 💾 **保存到相册** (MediaStore) | [FileWriteUtils.writeToUri](#33-从流写入--写入到-uri) | — |
| 📂 **获取存储空间** | [FileUtils.getAvailableSpace](#17-存储空间) | [OkioFileUtils.getAvailableSpace](#54-path-类型) |
| 📝 **获取文件信息** (MIME/时间/权限) | [FileUtils.getFileInfo](#16-文件信息) | [OkioFileUtils.getSize/getLastModifiedMillis](#54-path-类型) |

### 📦 包结构

```
com.itg.itg_file
├── core/
│   ├── FileUtils.kt            — java.io 文件基础操作
│   └── OkioFileUtils.kt        — Okio 文件操作 (更快更现代)
├── read/
│   ├── FileReadUtils.kt        — java.io 文件读取
│   └── OkioReadUtils.kt        — Okio 读取 (ByteString/超时/Gzip)
├── write/
│   ├── FileWriteUtils.kt       — java.io 文件写入
│   └── OkioWriteUtils.kt       — Okio 写入 (超时/进度/原子/Gzip)
└── hash/
    ├── FileHashUtils.kt        — java.io 文件哈希
    └── OkioHashUtils.kt        — Okio 流式哈希 (边读写边计算)

依赖: itg-thread-pools + Okio 3.17.0
```

---

## 同步与异步模式

每个工具类的方法都提供两种调用方式：

### 同步模式

```kotlin
// 阻塞当前线程，直接返回结果
val content = FileReadUtils.readText(path)
if (content != null) {
    // 立即使用结果
}
```

- **适用**: 你已经在后台线程，或操作很快的小文件
- **注意**: 不要在 UI 线程调用，会导致 ANR

### 异步模式

```kotlin
// 非阻塞，在 I/O 线程池执行，通过回调返回结果
FileReadUtils.readTextAsync(path) { content, error ->
    if (error == null) {
        TaskExecutor.main { updateUI(content!!) }
    }
}
```

- **适用**: UI 线程发起调用、大文件操作
- **Future 管理**: 返回 `Future<*>`，可随时取消

### 命名约定

| 同步方法 | 异步方法 |
|----------|----------|
| `readText(path)` | `readTextAsync(path, onResult)` |
| `writeText(path, content)` | `writeTextAsync(path, content, onResult)` |
| `copy(src, dest)` | `copyAsync(src, dest, onResult)` |
| `delete(path)` | `deleteAsync(path, onResult)` |
| `hashFile(path)` | `hashFileAsync(path, onResult)` |

---

## java.io vs Okio 双引擎

ITG File 同时提供两套 API，可根据场景选择：

| 维度 | java.io 引擎 | Okio 引擎 |
|------|-------------|----------|
| 依赖 | Android SDK 内置 | Okio 3.17.0 (~200KB) |
| 性能 | 标准 I/O 性能 | 更快（Segment 池、零拷贝 Buffer、更少 GC） |
| 超时控制 | 无内建支持 | Source/Sink.timeout() |
| 不可变字节 | byte[] (需拷贝) | ByteString (零拷贝子序列) |
| 流式哈希 | 读完整文件再算 | 边读边算 (HashingSource) |
| Gzip | java.util.zip | Okio 内建 (代码更简洁) |
| 进度追踪 | 手动循环计数 | ForwardingSource/Sink 拦截器模式 |
| 原子移动 | 无 (手动 copy+delete) | FileSystem.atomicMove() |
| 跨平台 | Android only | JVM/Android 通用 |
| API 风格 | 传统 java.io | 现代 Kotlin 风格 |

### 选择建议

```
你需要什么？
│
├─ 简单场景 (小文件、普通读写) → java.io 引擎
│   └─ 足够好，零额外依赖
│
├─ 高性能场景 (大文件、频繁 I/O) → Okio 引擎
│   └─ 更少内存分配，更快
│
├─ 需要超时控制 → Okio 引擎
│   └─ source.timeout().timeout(30, SECONDS)
│
├─ 需要边读边算哈希 / 边写边算哈希 → OkioHashUtils
│   └─ 一次 I/O 完成两个操作
│
├─ 需要 Gzip 压缩/解压 → Okio 引擎更方便
│   └─ Okio.gzip() 一行搞定
│
└─ 两者随意混用，api 完全兼容
```

---

## 详细教程

### java.io 引擎

### 1. 文件基础操作 — FileUtils

`FileUtils` 提供文件和目录的 CRUD 操作、信息获取以及存储空间查询。

#### 1.1 文件/目录判断

```kotlin
// 存在性
FileUtils.exists("/sdcard/data.txt")       // true/false
FileUtils.existsAsync("/sdcard/data.txt") { exists -> }  // 异步

// 类型判断
FileUtils.isFile("/sdcard/data.txt")       // true
FileUtils.isDirectory("/sdcard/Pictures")  // true
FileUtils.isEmpty("/sdcard/empty_dir/")    // true

// 文件大小
val bytes = FileUtils.getSize("/sdcard/photo.jpg")
val formatted = FileUtils.getSizeFormatted("/sdcard/photo.jpg")  // "2.50 MB"
```

#### 1.2 创建

```kotlin
// 创建文件（自动创建父目录）
FileUtils.createFile("/sdcard/MyApp/cache/temp.txt")
FileUtils.createFileAsync(path) { success -> }

// 创建目录（递归）
FileUtils.createDirectory("/sdcard/MyApp/data/subdir")
FileUtils.createDirectoryAsync(path) { success -> }

// 创建临时文件
val tmpFile = FileUtils.createTempFile(prefix = "upload_", suffix = ".tmp")
// 生成: /data/.../cache/upload_1234567890.tmp
```

#### 1.3 删除

```kotlin
// 删除文件或目录（递归）
FileUtils.delete("/sdcard/MyApp/cache/")
FileUtils.deleteAsync(path) { success -> }

// 清空目录（保留目录自身）
FileUtils.clearDirectory("/sdcard/MyApp/cache/")
FileUtils.clearDirectoryAsync(path) { success -> }
```

#### 1.4 复制与移动

```kotlin
// 复制文件
FileUtils.copy("/sdcard/a.txt", "/sdcard/backup/a.txt")
FileUtils.copyAsync(src, dest) { success -> }

// 复制文件（带进度）
FileUtils.copyWithProgress("/sdcard/large.zip", "/sdcard/backup/large.zip",
    onProgress = { copied, total ->
        val percent = copied * 100 / total
        println("Progress: $percent%")
    })

// 异步 + 进度
FileUtils.copyWithProgressAsync(src, dest,
    onProgress = { copied, total -> updateProgressBar(copied, total) },
    onResult = { success -> println("Done: $success") })

// 复制整个目录
FileUtils.copyDirectory("/sdcard/MyApp/", "/sdcard/backup/MyApp/")

// 移动（先尝试 rename，跨分区走 copy+delete）
FileUtils.move("/sdcard/old.txt", "/sdcard/new.txt")
FileUtils.moveAsync(src, dest) { success -> }
```

#### 1.5 文件列表

```kotlin
// 列出所有文件
val all = FileUtils.listFiles("/sdcard/DCIM/")

// 过滤列表
val jpgs = FileUtils.listFiles("/sdcard/DCIM/") { file ->
    file.extension.equals("jpg", ignoreCase = true)
}

// 按扩展名过滤
val pngs = FileUtils.listFilesByExtension("/sdcard/DCIM/", "png")

// 递归列出（含子目录）
val allRecursive = FileUtils.listFilesRecursive("/sdcard/MyApp/")
FileUtils.listFilesRecursiveAsync(path) { files -> /* 异步 */ }
```

#### 1.6 文件信息

```kotlin
// 获取完整信息
val info = FileUtils.getFileInfo("/sdcard/photo.jpg")
// {
//   exists=true, name=photo.jpg, extension=jpg,
//   size=2621440, sizeFormatted=2.50 MB,
//   lastModified=2026-06-27 14:30:00,
//   mimeType=image/jpeg, canRead=true, canWrite=true, ...
// }

// 单项获取
FileUtils.getExtension("/sdcard/photo.jpg")      // "jpg"
FileUtils.getFileName("/sdcard/photo.jpg")        // "photo.jpg"
FileUtils.getFileNameWithoutExtension(path)        // "photo"
FileUtils.getMimeType("/sdcard/photo.jpg")         // "image/jpeg"
FileUtils.getLastModified(path)                    // "2026-06-27 14:30:00"
FileUtils.getParentPath(path)                      // "/sdcard"
```

#### 1.7 存储空间

```kotlin
// 可用空间
val available = FileUtils.getAvailableSpace("/sdcard/")
println("${FileUtils.formatFileSize(available)} available")
// 12.30 GB available

// 内置存储
FileUtils.getInternalAvailableSpace()

// SD 卡（无 SD 卡返回 -1）
FileUtils.getExternalAvailableSpace()

// 总空间
FileUtils.getTotalSpace("/sdcard/")
```

---

### 2. 文件读取 — FileReadUtils

`FileReadUtils` 提供多种读取方式，适配不同场景。

#### 2.1 读取文本

```kotlin
// 同步读取
val text = FileReadUtils.readText("/sdcard/data.json")
val textUtf16 = FileReadUtils.readText(path, Charset.forName("UTF-16"))

// 异步读取
FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
    if (error == null) {
        TaskExecutor.main { textView.text = content }
    }
}
```

#### 2.2 读取字节

```kotlin
// 同步（小文件 OK，大文件用 readChunks）
val bytes = FileReadUtils.readBytes("/sdcard/photo.jpg")
FileReadUtils.readBytesAsync(path) { bytes, error -> /* ... */ }

// 从 URI 读取（带大小限制）
val bytes = FileReadUtils.readBytes(context, uri, maxBytes = 5 * 1024 * 1024)  // 5MB 限制

// 从 InputStream 读取
val bytes = FileReadUtils.readBytes(inputStream, maxBytes = 1024 * 1024)  // 1MB 限制
```

#### 2.3 按行读取

```kotlin
// 一次性全部读入
val lines = FileReadUtils.readLines("/sdcard/log.txt")
lines?.forEach { println(it) }

// 异步
FileReadUtils.readLinesAsync(path) { lines, error -> /* ... */ }

// 流式逐行读取（适合大文件，边读边处理）
val lineCount = FileReadUtils.readLinesStreaming("/sdcard/huge.csv",
    onEachLine = { line, index ->
        if (index % 10000 == 0) println("Processing line $index")
        processCsvLine(line)
        true  // 继续读取
    })

// 异步流式 + 截停（只读前 1000 行）
FileReadUtils.readLinesStreamingAsync("/sdcard/huge.csv",
    onEachLine = { line, index ->
        parseLine(line)
        index < 999  // false = 停止
    },
    onComplete = { totalLines, error ->
        println("Read $totalLines lines")
    })
```

#### 2.4 大文件分块读取

```kotlin
// 每次读取 1MB，带进度
FileReadUtils.readChunks("/sdcard/large.bin", chunkSize = 1024 * 1024,
    onChunk = { chunk, index, total ->
        uploadChunk(chunk, index)  // 上传分片
        val progress = (index + 1) * 100 / total
        TaskExecutor.main { progressBar.progress = progress }
        true  // 继续读取
    })

// 异步分块
FileReadUtils.readChunksAsync(path, chunkSize = 1024 * 1024,
    onChunk = { chunk, index, total -> /* 处理分块 */ true },
    onComplete = { totalBytes, error -> /* 完成 */ })
```

#### 2.5 文件头部/尾部

```kotlin
// 读取文件头 4 字节（Magic Number 识别）
val header = FileReadUtils.readHeadBytes("/sdcard/file.bin", 4)
val isPng = header?.let { it[0] == 0x89.toByte() && it[1] == 0x50.toByte() }

// 读取末尾 100 字节
val tail = FileReadUtils.readTailBytes("/sdcard/file.bin", 100)

// 读取最后 10 行（日志查看）
val lastLines = FileReadUtils.readTailLines("/sdcard/log.txt", 10)
```

---

### 3. 文件写入 — FileWriteUtils

`FileWriteUtils` 提供多种写入方式，包括原子写入保护。

#### 3.1 写入文本

```kotlin
// 覆盖写入
FileWriteUtils.writeText("/sdcard/data.txt", "New content")

// 指定编码
FileWriteUtils.writeText(path, content, Charset.forName("GBK"))

// 异步
FileWriteUtils.writeTextAsync(path, content) { success -> }

// 追加写入（不覆盖原有内容）
FileWriteUtils.appendText("/sdcard/log.txt", "[INFO] App started at ${Date()}\n")
FileWriteUtils.appendTextAsync(path, logLine) { success -> }
```

#### 3.2 写入字节

```kotlin
// 覆盖
FileWriteUtils.writeBytes("/sdcard/photo.jpg", imageBytes)
FileWriteUtils.writeBytesAsync(path, bytes) { success -> }

// 追加
FileWriteUtils.appendBytes("/sdcard/data.bin", extraBytes)
```

#### 3.3 从流写入 / 写入到 URI

```kotlin
// 从 InputStream 写入文件（如网络下载）
FileWriteUtils.writeFromStream("/sdcard/download.zip", inputStream,
    onProgress = { written, total ->
        // written: 已写入字节, total: 预估总量（可能为 -1）
    })

// 写入到 Content URI（适配 Android 10+ Scoped Storage）
FileWriteUtils.writeToUri(context, uri, imageBytes, "image/jpeg")
FileWriteUtils.writeStreamToUri(context, uri, inputStream, "video/mp4")
```

#### 3.4 原子写入（防数据损坏）

```kotlin
// 原子写入：先写临时文件，成功后再 rename
// 防止写入过程中进程崩溃导致原文件损坏

// 原子写入文本
FileWriteUtils.writeTextAtomic("/data/config.json", configJson)

// 异步原子写入
FileWriteUtils.writeTextAtomicAsync(path, content) { success -> }

// 原子写入字节
FileWriteUtils.writeBytesAtomic("/data/database.db", dbBytes)
```

#### 3.5 大文件分块写入

```kotlin
// 分块写入（带进度回调）
FileWriteUtils.writeBytesInChunks(
    "/sdcard/big_file.bin",
    hugeData,
    chunkSize = 1024 * 1024,  // 每块 1MB
    onProgress = { written, total ->
        val percent = (written * 100 / total).toInt()
        updateProgressBar(percent)
    })

// 异步
FileWriteUtils.writeBytesInChunksAsync(path, data,
    onProgress = { written, total -> /* progress */ },
    onResult = { success -> /* done */ })
```

---

### 4. 文件哈希 — FileHashUtils

`FileHashUtils` 提供多种哈希算法计算文件摘要。

#### 4.1 常用哈希

```kotlin
// MD5 — 快速，适合去重
val md5 = FileHashUtils.md5("/sdcard/photo.jpg")
// "d41d8cd98f00b204e9800998ecf8427e"

// SHA-256 — 安全，推荐
val sha256 = FileHashUtils.sha256("/sdcard/photo.jpg")
// "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

// SHA-1 — Git 风格
val sha1 = FileHashUtils.sha1(path)

// SHA-512
val sha512 = FileHashUtils.sha512(path)

// CRC32 — 快速变化检测
val crc32 = FileHashUtils.crc32(path)
```

#### 4.2 异步哈希计算

```kotlin
// 异步（带错误处理）
FileHashUtils.md5Async(path) { hash, error ->
    if (hash != null) {
        TaskExecutor.main { hashTextView.text = hash }
    }
}

FileHashUtils.sha256Async(path) { hash, error -> }
FileHashUtils.crc32Async(path) { hash, error -> }
```

#### 4.3 通用哈希（指定算法）

```kotlin
// 指定算法
val hash = FileHashUtils.hashFile(path, FileHashUtils.Algorithm.SHA256)
val hash2 = FileHashUtils.hashFile(path, FileHashUtils.Algorithm.MD5)

// 异步
FileHashUtils.hashFileAsync(path, FileHashUtils.Algorithm.SHA512) { hash, error -> }

// 从字符串解析算法
val algo = FileHashUtils.Algorithm.fromString("sha-256")  // SHA256
```

#### 4.4 带进度的大文件哈希

```kotlin
FileHashUtils.hashFileWithProgress("/sdcard/large.iso", Algorithm.SHA256,
    onProgress = { processed, total ->
        val percent = (processed * 100 / total).toInt()
        updateProgress(percent)
    })

// 异步 + 进度
FileHashUtils.hashFileWithProgressAsync(path, Algorithm.SHA256,
    onProgress = { p, t -> updateProgress(p, t) },
    onResult = { hash, error -> showHash(hash) })
```

#### 4.5 文件完整性校验

```kotlin
// 对比文件哈希与期望值
val valid = FileHashUtils.verify("/sdcard/download.apk",
    expectedHash = "a1b2c3d4...",
    algorithm = FileHashUtils.Algorithm.SHA256)
if (valid) installApk() else showError("文件已损坏，请重新下载")

// 异步校验
FileHashUtils.verifyAsync(path, expectedHash, Algorithm.SHA256) { valid, actualHash ->
    TaskExecutor.main {
        if (valid) showSuccess() else showMismatch(actualHash)
    }
}

// 比较两个文件是否相同
val same = FileHashUtils.compareFiles(path1, path2)
FileHashUtils.compareFilesAsync(path1, path2) { same -> }
```

#### 4.6 字符串/字节哈希

```kotlin
// 字符串哈希
val hash = FileHashUtils.hashString("hello world", Algorithm.SHA256)

// 字节哈希
val hash = FileHashUtils.hashBytes(byteArrayOf(0x01, 0x02, 0x03))

// 字符串 CRC32
val crc = FileHashUtils.crc32("hello world".toByteArray())
```

---

### Okio 引擎

### 5. Okio 文件操作 — OkioFileUtils

`OkioFileUtils` 基于 Okio `FileSystem` 提供比 java.io 更高效的文件操作。

#### 5.1 快速复制与原子移动

```kotlin
// Okio 复制（利用 Buffer 零拷贝，通常比 java.io 快 2-3x）
OkioFileUtils.copy("/sdcard/large.bin", "/sdcard/backup/large.bin")
OkioFileUtils.copyAsync(src, dest) { success -> }

// 原子移动（同一文件系统下无拷贝，仅修改指针）
OkioFileUtils.move("/sdcard/temp.bin", "/sdcard/final.bin")
OkioFileUtils.moveAsync(src, dest) { success -> }
```

#### 5.2 超时控制的文件操作

```kotlin
// 任意文件操作带超时
val size = OkioFileUtils.withTimeout(5000) {
    OkioFileUtils.getSize("/mnt/nfs/slow_file.bin")
}
if (size == null) {
    println("Operation timed out")
}

// 异步超时
OkioFileUtils.withTimeoutAsync(5000, {
    OkioFileUtils.copy("/mnt/nfs/src.bin", "/mnt/nfs/dest.bin")
}) { result -> }
```

#### 5.3 目录遍历

```kotlin
// 递归列出（Okio 实现，快于 java.io File.walk）
val paths = OkioFileUtils.listRecursively("/sdcard/MyApp/")
paths.forEach { println(it) }

// 异步
OkioFileUtils.listRecursivelyAsync("/sdcard/MyApp/") { pathStrings -> }

// 仅直接子项
val children = OkioFileUtils.list("/sdcard/DCIM/")
```

#### 5.4 Path 类型

```kotlin
// String ↔ Okio Path 互转
val okioPath = OkioFileUtils.toPath("/sdcard/file.txt")
val strPath = OkioFileUtils.fromPath(okioPath)

// 检查元数据
if (OkioFileUtils.isRegularFile("/sdcard/file.txt")) {
    val size = OkioFileUtils.getSize("/sdcard/file.txt")
    val modified = OkioFileUtils.getLastModifiedMillis("/sdcard/file.txt")
}
```

---

### 6. Okio 读取 — OkioReadUtils

`OkioReadUtils` 提供比 `FileReadUtils` 更高效的读取方式，核心亮点是 `ByteString`。

#### 6.1 ByteString — 不可变字节序列

```kotlin
// 读取为 ByteString（不可变，零拷贝子序列）
val byteStr = OkioReadUtils.readByteString("/sdcard/photo.jpg")

// ByteString 自带编码方法
println("Hex: ${byteStr?.hex()}")           // 十六进制
println("Base64: ${byteStr?.base64()}")     // Base64
println("Base64Url: ${byteStr?.base64Url()}") // URL 安全 Base64
println("UTF-8: ${byteStr?.utf8()}")        // UTF-8 解码

// MD5 / SHA 直接计算
println("MD5: ${byteStr?.md5()?.hex()}")
println("SHA256: ${byteStr?.sha256()?.hex()}")

// 零拷贝子序列（不分配内存）
val header = byteStr?.substring(0, 16)  // 前 16 字节

// 异步版本
OkioReadUtils.readByteStringAsync(path) { byteStr, error -> }
```

#### 6.2 读取为字符串

```kotlin
// UTF-8 读取（Okio 版本，比 BufferedReader 更快）
val text = OkioReadUtils.readUtf8("/sdcard/data.json")
OkioReadUtils.readUtf8Async(path) { text, error -> }

// 指定编码
val gbk = OkioReadUtils.readUtf8("/sdcard/legacy.txt", Charset.forName("GBK"))
```

#### 6.3 读取到 Buffer

```kotlin
// Buffer 是可变的字节缓冲区
val buffer = OkioReadUtils.readToBuffer("/sdcard/data.bin")
buffer?.let {
    // 支持 Python 风格的 read 方法
    val firstByte = it.readByte()
    val int32 = it.readInt()
    it.skip(16)  // 跳过 16 字节
    val rest = it.readByteArray()
}
```

#### 6.4 超时读取

```kotlin
// 30 秒超时读取（适用于慢速文件系统/网络文件系统）
val data = OkioReadUtils.readWithTimeout("/mnt/nfs/huge.log", 30_000)
OkioReadUtils.readWithTimeoutAsync(path, 30_000) { data, error -> }
```

#### 6.5 带进度读取

```kotlin
// ForwardingSource 拦截器模式 — 不修改业务代码即可添加进度
val data = OkioReadUtils.readWithProgress("/sdcard/large.dat",
    onProgress = { read, total ->
        val percent = read * 100 / total
        updateProgressBar(percent.toInt())
    })

// 异步
OkioReadUtils.readWithProgressAsync(path,
    onProgress = { read, total -> updateUI(read, total) },
    onResult = { data, error -> process(data) })
```

#### 6.6 Gzip 解压读取

```kotlin
// 直接读取 .gz 文件并解压
val decompressed = OkioReadUtils.readGzip("/sdcard/data.json.gz")
OkioReadUtils.readGzipAsync(path) { bytes, error -> }

// 解压为字符串
val text = OkioReadUtils.readGzipAsText("/sdcard/data.json.gz")
```

#### 6.7 流式按行读取

```kotlin
// 逐行处理（大文件友好）
val lineCount = OkioReadUtils.readLinesStreaming("/sdcard/huge.log") { line, index ->
    if (line.contains("ERROR")) logError(line)
    true  // 继续
}
```

---

### 7. Okio 写入 — OkioWriteUtils

`OkioWriteUtils` 提供高效的 Okio 写入方式，亮点是超时控制、进度追踪和 Gzip 压缩。

#### 7.1 写入 ByteString

```kotlin
// 构建 ByteString 并写入
val byteStr = ByteString.encodeUtf8("Hello Okio")
OkioWriteUtils.writeByteString("/sdcard/hello.txt", byteStr)
```

#### 7.2 写入与追加

```kotlin
// 覆盖写入
OkioWriteUtils.writeUtf8("/sdcard/data.txt", "New content")

// 追加写入
OkioWriteUtils.appendUtf8("/sdcard/log.txt", "[INFO] App started\n")

// 异步
OkioWriteUtils.writeUtf8Async(path, content) { success -> }
OkioWriteUtils.appendUtf8Async(path, line) { success -> }
```

#### 7.3 超时写入

```kotlin
// 写入操作 10 秒超时
val ok = OkioWriteUtils.writeWithTimeout("/mnt/nfs/remote.bin", data, 10_000)
```

#### 7.4 带进度写入

```kotlin
// ForwardingSink 进度拦截器
OkioWriteUtils.writeWithProgress("/sdcard/large.bin", hugeData,
    onProgress = { written, total ->
        updateProgressBar((written * 100 / total).toInt())
    })

// 异步
OkioWriteUtils.writeWithProgressAsync(path, data,
    onProgress = { w, t -> updateUI(w, t) },
    onResult = { ok -> showResult(ok) })
```

#### 7.5 Gzip 压缩写入

```kotlin
// 压缩并写入
OkioWriteUtils.writeGzip("/sdcard/data.json.gz", jsonData.toByteArray())
OkioWriteUtils.writeGzipText("/sdcard/data.json.gz", jsonString)  // 字符串版

// 异步
OkioWriteUtils.writeGzipAsync(path, data) { success -> }
```

#### 7.6 原子写入（Okio 版本）

```kotlin
// 使用 Okio FileSystem.atomicMove，更可靠
OkioWriteUtils.writeAtomic("/data/config.json", configBytes)
OkioWriteUtils.writeAtomicUtf8("/data/config.json", configJson)

// 异步
OkioWriteUtils.writeAtomicAsync(path, data) { success -> }
```

#### 7.7 从 InputStream 写入

```kotlin
// Okio 桥接 java.io
OkioWriteUtils.writeFromStream("/sdcard/download.zip", inputStream)
OkioWriteUtils.writeFromStreamAsync(path, inputStream) { success -> }
```

---

### 8. Okio 流式哈希 — OkioHashUtils

`OkioHashUtils` 是 Okio 引擎最具特色的模块：**一次 I/O 完成读取/写入 + 哈希计算**。

#### 8.1 边读边算哈希

```kotlin
// 读取文件内容到 Buffer 的同时计算 SHA-256
val buffer = Buffer()
val hash = OkioHashUtils.hashWhileReading("/sdcard/file.bin",
    MessageDigest.getInstance("SHA-256"),
    onRead = { buf, bytesRead -> buffer.writeAll(buf) })
// 此时 buffer 中有文件内容，hash 就是 SHA-256

// 异步
OkioHashUtils.hashWhileReadingAsync(path, digest) { hash, error -> }
```

#### 8.2 边写边算哈希

```kotlin
// 写入文件的同时计算 MD5
val (hash, ok) = OkioHashUtils.hashWhileWriting(
    "/sdcard/data.bin",
    imageBytes,
    MessageDigest.getInstance("MD5")
) ?: return
if (ok) println("Written with MD5: $hash")
```

#### 8.3 复制 + 哈希 + 进度 一步到位

```kotlin
// 这是 Okio 最独特的组合操作：一次 I/O 完成三个任务
val (hash, ok) = OkioHashUtils.copyAndHash(
    "/sdcard/large.iso",
    "/sdcard/backup/large.iso",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { copied, total ->
        val percent = copied * 100 / total
        TaskExecutor.main { progressBar.progress = percent.toInt() }
    }
) ?: return
if (ok) println("Copied. SHA-256: $hash")

// 异步版本
OkioHashUtils.copyAndHashAsync(src, dest, digest,
    onProgress = { c, t -> updateProgress(c, t) },
    onResult = { hash, success -> showResult(hash, success) })
```

#### 8.4 复制 + 自动校验

```kotlin
// 复制文件并自动验证（确保复制正确性）
val (hash, success, verified) = OkioHashUtils.copyAndVerify(
    "/sdcard/important.db",
    "/sdcard/backup/important.db",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { copied, total -> updateProgress(copied, total) }
) ?: return

if (verified) {
    println("复制成功且校验通过: $hash")
} else {
    println("校验失败! 文件可能损坏")
    FileUtils.delete(destPath)  // 删除损坏的副本
}
```

#### 8.5 Gzip 压缩 + 哈希

```kotlin
// 压缩并同时计算哈希
val (hash, ok) = OkioHashUtils.gzipAndHash(
    "/sdcard/raw_data.json",
    "/sdcard/raw_data.json.gz",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { processed, total -> updateProgress(processed, total) }
) ?: return
```

#### 8.6 基础哈希（不带组合操作）

```kotlin
// 纯哈希计算（Okio HashingSource 实现）
val sha256 = OkioHashUtils.hashFile("/sdcard/file.bin",
    MessageDigest.getInstance("SHA-256"))

// 带进度
OkioHashUtils.hashFileWithProgress(path, digest,
    onProgress = { p, t -> updateProgress(p, t) })

// 异步
OkioHashUtils.hashFileAsync(path, digest) { hash, error -> }

// 字符串/ByteString 哈希
OkioHashUtils.hashString("hello", MessageDigest.getInstance("MD5"))
OkioHashUtils.hashByteString(byteString, MessageDigest.getInstance("SHA-256"))
```

---

## 组合实战

### 场景 1: 配置文件读/写

```kotlin
class ConfigManager(private val configPath: String) {

    fun loadConfig(): Config? {
        val json = FileReadUtils.readText(configPath) ?: return null
        return Gson().fromJson(json, Config::class.java)
    }

    fun saveConfig(config: Config, callback: (Boolean) -> Unit) {
        val json = Gson().toJson(config)
        // 原子写入 → 防止写入中断导致配置丢失
        FileWriteUtils.writeTextAtomicAsync(configPath, json, onResult = callback)
    }
}
```

### 场景 2: 带进度的大文件下载 + 完整性校验

```kotlin
class DownloadManager(private val context: Context) {

    private var downloadFuture: Future<*>? = null

    fun downloadAndVerify(
        url: String,
        destPath: String,
        expectedMd5: String,
        onProgress: (Int) -> Unit,
        onResult: (Boolean, String) -> Unit
    ) {
        downloadFuture = TaskExecutor.io {
            try {
                // 1. 从网络获取流
                val connection = java.net.URL(url).openConnection()
                val totalSize = connection.contentLength
                connection.getInputStream().use { input ->

                    // 2. 流写入文件（带进度）
                    FileWriteUtils.writeFromStream(destPath, input,
                        onProgress = { written, _ ->
                            if (totalSize > 0) {
                                val percent = (written * 100 / totalSize).toInt()
                                TaskExecutor.main { onProgress(percent) }
                            }
                        })

                    // 3. 校验 MD5
                    val valid = FileHashUtils.verify(destPath, expectedMd5, FileHashUtils.Algorithm.MD5)
                    if (!valid) FileUtils.delete(destPath)  // 校验失败删除
                    TaskExecutor.main { onResult(valid, destPath) }
                }
            } catch (e: Exception) {
                TaskExecutor.main { onResult(false, "Error: ${e.message}") }
            }
        }
    }

    fun cancel() {
        downloadFuture?.let { TaskExecutor.cancel(it) }
    }
}
```

### 场景 3: 批量文件哈希去重

```kotlin
class DedupScanner(private val directory: String) {

    data class FileHash(val path: String, val md5: String)

    fun scan(onProgress: (Int, Int) -> Unit, onResult: (Map<String, List<String>>) -> Unit) {
        TaskExecutor.io {
            val allFiles = FileUtils.listFilesRecursive(directory)
            val hashMap = mutableMapOf<String, MutableList<String>>()
            val total = allFiles.size

            allFiles.forEachIndexed { index, file ->
                val md5 = FileHashUtils.md5(file.absolutePath)
                if (md5 != null) {
                    hashMap.getOrPut(md5) { mutableListOf() }.add(file.absolutePath)
                }
                TaskExecutor.main { onProgress(index + 1, total) }
            }

            // 找出重复文件（同一 MD5 有多个路径）
            val duplicates = hashMap.filter { it.value.size > 1 }
            TaskExecutor.main { onResult(duplicates) }
        }
    }
}
```

### 场景 4: 日志文件的追加写入 + 尾部读取

```kotlin
class AppLogger(private val logPath: String) {

    fun log(level: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$timestamp] [$level] $message\n"
        FileWriteUtils.appendText(logPath, line)
    }

    fun getLastLogs(n: Int = 50): List<String>? {
        return FileReadUtils.readTailLines(logPath, n)
    }

    fun getLastLogsAsync(n: Int = 50, onResult: (List<String>?) -> Unit) {
        TaskExecutor.io {
            val logs = FileReadUtils.readTailLines(logPath, n)
            TaskExecutor.main { onResult(logs) }
        }
    }
}
```

---

## 性能建议

### 1. 选择合适的读取方式

| 文件大小 | 推荐方法 | 说明 |
|----------|----------|------|
| < 1MB | `readText()` / `readBytes()` | 一次性读入，简单快速 |
| 1MB–50MB | `readLines()` / `readLinesStreaming()` | 按行处理，减少峰值内存 |
| > 50MB | `readChunks()` | 分块处理，内存可控 |

### 2. 大文件操作使用分块 + 进度

```kotlin
// ✅ 好: 分块处理 + 进度回调
FileReadUtils.readChunks("/sdcard/large.bin", chunkSize = 1024 * 1024) { chunk, index, total ->
    processChunk(chunk)
    updateProgress(index, total)
    true
}

// ✅ 好: 写入也带进度
FileWriteUtils.writeBytesInChunks(path, data,
    onProgress = { written, total -> updateProgressBar(written, total) })
```

### 3. 重要数据使用原子写入

```kotlin
// ✅ 推荐: 原子写入防止数据损坏
FileWriteUtils.writeTextAtomic("/data/config.json", configJson)

// ❌ 不推荐: 普通写入，进程崩溃会导致文件损坏
FileWriteUtils.writeText("/data/config.json", configJson)
```

### 4. 合理使用异步

```kotlin
// ✅ 从 UI 线程调用:
fun onSaveClick() {
    FileWriteUtils.writeTextAsync(path, content) { success -> /* ... */ }
}

// ✅ 你已经在后台线程:
fun processInBackground() {
    TaskExecutor.io {
        // 直接用同步方法，减少回调嵌套
        val content = FileReadUtils.readText(path)
        val processed = transform(content)
        FileWriteUtils.writeText(outPath, processed)
    }
}
```

### 5. 及时取消不再需要的异步操作

```kotlin
class MyViewModel : ViewModel() {
    private var copyFuture: Future<*>? = null

    fun copyFile(src: String, dest: String) {
        copyFuture?.let { TaskExecutor.cancel(it) }
        copyFuture = FileUtils.copyAsync(src, dest) { success -> }
    }

    override fun onCleared() {
        copyFuture?.let { TaskExecutor.cancel(it) }
    }
}
```

---

## 常见问题

### Q: 异步方法的回调在哪个线程执行？

A: 在 `itg-thread-pools` 的 I/O 线程池中执行。如需更新 UI，在回调中使用 `TaskExecutor.main {}` 切换。

```kotlin
FileReadUtils.readTextAsync(path) { content, error ->
    // 这里在 I/O 线程
    TaskExecutor.main {
        // 这里在 UI 线程
        textView.text = content
    }
}
```

### Q: 如何取消一个异步操作？

A: 所有异步方法返回 `Future<*>`，使用 `TaskExecutor.cancel(future)` 取消。

```kotlin
val future = FileUtils.copyAsync(src, dest) { success -> }
// ...
TaskExecutor.cancel(future)
```

### Q: 原子写入 (writeAtomic) 和普通写入有什么区别？

A: 原子写入使用"写临时文件→重命名"策略：
1. 写入数据到 `xxx.tmp` 临时文件
2. 写入成功后删除原文件
3. 将临时文件重命名为目标文件名

如果步骤 1 中进程崩溃，原文件不受影响。

### Q: 大文件 (>100MB) 用什么方法读？

A: 使用 `FileReadUtils.readChunks()` 分块读取，每次只加载一个 chunk 到内存。

### Q: 从 Content URI 怎么读取/写入？

A:
```kotlin
// 读取
val bytes = FileReadUtils.readBytes(context, uri, maxBytes = 10 * 1024 * 1024)

// 写入
FileWriteUtils.writeToUri(context, uri, data, mimeType)
FileWriteUtils.writeStreamToUri(context, uri, inputStream, mimeType)
```

---

## API 参考

### FileUtils

| 方法 | 说明 |
|------|------|
| `exists(path)` / `existsAsync(path, cb)` | 检查文件是否存在 |
| `isFile(path)` / `isDirectory(path)` | 类型判断 |
| `isEmpty(path)` | 是否为空 |
| `createFile(path)` / `createFileAsync(path, cb)` | 创建文件 |
| `createDirectory(path)` / `createDirectoryAsync(path, cb)` | 创建目录 |
| `createTempFile(prefix, suffix, dir)` | 创建临时文件 |
| `delete(path)` / `deleteAsync(path, cb)` | 删除 |
| `clearDirectory(path)` / `clearDirectoryAsync(path, cb)` | 清空目录 |
| `rename(path, newName)` / `renameAsync(path, name, cb)` | 重命名 |
| `copy(src, dest, overwrite)` / `copyAsync(...)` | 复制文件 |
| `copyWithProgress(src, dest, ..., onProgress)` | 复制（带进度） |
| `copyWithProgressAsync(...)` | 异步复制（带进度） |
| `copyDirectory(src, dest, overwrite)` | 复制目录 |
| `move(src, dest, overwrite)` / `moveAsync(...)` | 移动 |
| `listFiles(path)` | 列出文件 |
| `listFiles(path, filter)` | 条件过滤 |
| `listFilesByExtension(path, ext)` | 按扩展名过滤 |
| `listFilesRecursive(path)` / `...Async(path, cb)` | 递归列出 |
| `getSize(path)` / `getSizeAsync(path, cb)` | 获取大小 |
| `getSizeFormatted(path)` | 格式化大小 |
| `getExtension(path)` | 获取扩展名 |
| `getFileName(path)` | 获取文件名 |
| `getFileNameWithoutExtension(path)` | 获取不含扩展名 |
| `getMimeType(path)` | 获取 MIME 类型 |
| `getLastModified(path, format)` | 获取修改时间 |
| `getParentPath(path)` | 获取父目录路径 |
| `getFileInfo(path)` / `getFileInfoAsync(path, cb)` | 获取详细信息 |
| `getAvailableSpace(path)` | 获取可用空间 |
| `getTotalSpace(path)` | 获取总空间 |
| `getInternalAvailableSpace()` | 内置存储可用空间 |
| `getExternalAvailableSpace()` | 外部存储可用空间 |

### FileReadUtils

| 方法 | 说明 |
|------|------|
| `readText(path, charset)` / `readTextAsync(path, cs, cb)` | 读取为字符串 |
| `readBytes(path)` / `readBytesAsync(path, cb)` | 读取为字节数组 |
| `readBytes(context, uri, maxBytes)` | 从 URI 读取字节 |
| `readBytes(inputStream, maxBytes)` | 从流读取字节 |
| `readLines(path, charset)` / `readLinesAsync(path, cs, cb)` | 读取所有行 |
| `readLinesStreaming(path, cs, onEachLine)` | 流式逐行读取 |
| `readLinesStreamingAsync(path, cs, onEach, onComplete)` | 异步流式逐行 |
| `readChunks(path, chunkSize, onChunk)` | 分块读取 |
| `readChunksAsync(path, chunkSize, onChunk, onComplete)` | 异步分块读取 |
| `readHeadBytes(path, numBytes)` | 读取文件头 |
| `readTailBytes(path, numBytes)` | 读取文件尾 |
| `readTailLines(path, numLines)` | 读取最后 N 行 |

### FileWriteUtils

| 方法 | 说明 |
|------|------|
| `writeText(path, content, charset)` / `writeTextAsync(...)` | 写入字符串 |
| `appendText(path, content, charset)` / `appendTextAsync(...)` | 追加字符串 |
| `writeBytes(path, bytes)` / `writeBytesAsync(...)` | 写入字节 |
| `appendBytes(path, bytes)` | 追加字节 |
| `writeFromStream(path, is, overwrite, onProgress)` | 从流写入 |
| `writeFromStreamAsync(...)` | 异步从流写入 |
| `writeToUri(context, uri, bytes, mimeType)` | 写入到 Content URI |
| `writeStreamToUri(context, uri, is, mimeType)` | 流写入到 URI |
| `writeTextAtomic(path, content, cs)` / `writeTextAtomicAsync(...)` | 原子写入文本 |
| `writeBytesAtomic(path, bytes)` | 原子写入字节 |
| `writeBytesInChunks(path, data, chunkSize, ow, onProgress)` | 分块写入 |
| `writeBytesInChunksAsync(...)` | 异步分块写入 |

### FileHashUtils

| 方法 | 说明 |
|------|------|
| `hashFile(path, algorithm)` / `hashFileAsync(path, algo, cb)` | 文件哈希 |
| `hashFileWithProgress(path, algo, onProgress)` | 带进度哈希 |
| `hashFileWithProgressAsync(...)` | 异步带进度哈希 |
| `md5(path)` / `md5Async(path, cb)` | MD5 |
| `sha1(path)` / `sha1Async(path, cb)` | SHA-1 |
| `sha256(path)` / `sha256Async(path, cb)` | SHA-256 |
| `sha512(path)` / `sha512Async(path, cb)` | SHA-512 |
| `crc32(path)` / `crc32Async(path, cb)` | CRC32 |
| `hashBytes(data, algorithm)` | 字节哈希 |
| `hashString(text, algorithm)` | 字符串哈希 |
| `crc32(data)` | 字节 CRC32 |
| `verify(path, expectedHash, algo, ignoreCase)` | 校验文件完整性 |
| `verifyAsync(path, expectedHash, algo, ic, cb)` | 异步校验 |
| `compareFiles(path1, path2, algo)` / `compareFilesAsync(...)` | 比较两个文件 |

---

### OkioFileUtils

| 方法 | 说明 |
|------|------|
| `exists(path)` / `existsAsync(path, cb)` | 检查路径存在性 |
| `getSize(path)` | 获取文件大小 |
| `getLastModifiedMillis(path)` | 获取最后修改时间戳 |
| `isRegularFile(path)` / `isDirectory(path)` | 文件/目录类型判断 |
| `copy(src, dest, overwrite)` / `copyAsync(...)` | 快速复制（Okio Buffer） |
| `move(src, dest, overwrite)` / `moveAsync(...)` | 移动（优先原子移动） |
| `delete(path)` / `deleteAsync(path, cb)` | 递归删除 |
| `createDirectory(path)` / `createDirectoryAsync(path, cb)` | 递归创建目录 |
| `list(path)` | 列出直接子项 |
| `listRecursively(path)` / `listRecursivelyAsync(path, cb)` | 递归列出所有文件 |
| `toPath(path)` / `fromPath(path)` | String ↔ Path 互转 |
| `getAvailableSpace(path)` / `getTotalSpace(path)` | 磁盘空间 |
| `withTimeout(ms, block)` / `withTimeoutAsync(...)` | 超时控制 |

### OkioReadUtils

| 方法 | 说明 |
|------|------|
| `readByteString(path)` / `readByteStringAsync(path, cb)` | 读取为不可变 ByteString |
| `readUtf8(path, charset)` / `readUtf8Async(path, cs, cb)` | 读取为字符串 |
| `readToBuffer(path)` / `readToBufferAsync(path, cb)` | 读取到可变 Buffer |
| `readLines(path, charset)` / `readLinesAsync(...)` | 读取所有行 |
| `readLinesStreaming(path, onEachLine)` | 流式逐行读取 |
| `readWithTimeout(path, ms)` / `readWithTimeoutAsync(...)` | 超时读取 |
| `readWithProgress(path, chunkSize, onProgress)` | 带进度读取 |
| `readWithProgressAsync(...)` | 异步带进度读取 |
| `readGzip(path)` / `readGzipAsync(path, cb)` | Gzip 解压读取 |
| `readGzipAsText(path, charset)` | Gzip 解压为文本 |

### OkioWriteUtils

| 方法 | 说明 |
|------|------|
| `writeByteString(path, byteString)` / `writeByteStringAsync(...)` | 写入 ByteString |
| `writeUtf8(path, content, cs)` / `writeUtf8Async(...)` | 写入字符串 |
| `appendUtf8(path, content, cs)` / `appendUtf8Async(...)` | 追加字符串 |
| `writeFromBuffer(path, buffer)` | Buffer→文件 |
| `writeFromStream(path, is, overwrite)` / `writeFromStreamAsync(...)` | 流→文件 |
| `writeWithTimeout(path, bytes, ms)` / `writeWithTimeoutAsync(...)` | 超时写入 |
| `writeWithProgress(path, data, chunkSize, onProgress)` | 带进度写入 |
| `writeWithProgressAsync(...)` | 异步带进度写入 |
| `writeGzip(path, data)` / `writeGzipAsync(path, data, cb)` | Gzip 压缩写入 |
| `writeGzipText(path, content, cs)` | Gzip 压缩文本 |
| `writeAtomic(path, data)` / `writeAtomicAsync(path, data, cb)` | 原子写入 |
| `writeAtomicUtf8(path, content, cs)` | 原子写入文本 |

### OkioHashUtils

| 方法 | 说明 |
|------|------|
| `hashFile(path, digest)` / `hashFileAsync(path, digest, cb)` | 计算文件哈希 |
| `hashFileWithProgress(path, digest, onProgress)` | 带进度哈希 |
| `hashFileWithProgressAsync(...)` | 异步带进度哈希 |
| `hashWhileReading(path, digest, onRead)` | 边读边算哈希 |
| `hashWhileReadingAsync(...)` | 异步边读边算 |
| `hashWhileWriting(path, data, digest)` | 边写边算哈希 |
| `hashWhileWritingAsync(...)` | 异步边写边算 |
| `copyAndHash(src, dest, digest, ow, onProgress)` | 复制+哈希+进度一步到位 |
| `copyAndHashAsync(...)` | 异步三合一 |
| `copyAndVerify(src, dest, digest, ow, onProgress)` | 复制+自动校验 |
| `gzipAndHash(src, dest, digest, onProgress)` | Gzip压缩+哈希 |
| `hashByteString(data, digest)` | ByteString 哈希 |
| `hashString(text, digest)` | 字符串哈希 |

---

## 更新日志

### v1.0.0 (2026-06)
- 🎉 初始版本发布
- ✨ java.io 引擎: FileUtils / FileReadUtils / FileWriteUtils / FileHashUtils
- ✨ Okio 引擎: OkioFileUtils / OkioReadUtils / OkioWriteUtils / OkioHashUtils
- ✨ 全部方法支持同步 + 异步双模式
- ✨ 异步方法基于 itg-thread-pools I/O 线程池
- ✨ Okio ByteString: 不可变字节序列 + hex/base64/utf8
- ✨ Okio 流式哈希: 边读边算 / 边写边算 / 复制+哈希一步到位
- ✨ Okio 超时控制: Source/Sink.timeout()
- ✨ Okio Gzip 压缩/解压
- ✨ Okio 原子移动: FileSystem.atomicMove()
- ✨ 大文件分块读写 + 进度回调
- ✨ 原子写入（防止数据损坏）
- ✨ MD5 / SHA-1 / SHA-256 / SHA-512 / CRC32 哈希
- ✨ 文件完整性校验 / 文件对比
- ✨ Content URI 读写支持
- ✨ 存储空间查询
- ✨ 所有异步操作返回 Future（可取消）

---

## 许可证

```
MIT License

Copyright (c) 2026 ITG Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
