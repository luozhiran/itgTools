# ITG File — Android 文件处理工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Okio](https://img.shields.io/badge/Okio-3.17.0-orange.svg)](https://square.github.io/okio/)

ITG File 是 ItgTools 项目中的文件处理核心模块，提供 **java.io 传统引擎** 与 **Okio 现代引擎** 双实现，覆盖文件 CRUD、高效读写、流式哈希、原子操作、Gzip 压缩/解压、进度追踪、超时控制，以及 **生命周期感知的自动清理管理**。所有操作同步/异步双模式，异步基于 [itg-thread-pools](./itg-thread-pools/)。

---

## 目录

- [模块组成](#模块组成)
- [快速开始](#快速开始)
- [架构总览](#架构总览)
- [使用场景索引](#使用场景索引)
- [java.io 引擎](#javaio-引擎)
  - [FileUtils — 文件基础操作](#fileutils--文件基础操作)
  - [FileReadUtils — 文件读取](#filereadutils--文件读取)
  - [FileWriteUtils — 文件写入](#filewriteutils--文件写入)
  - [FileHashUtils — 文件哈希](#filehashutils--文件哈希)
- [Okio 引擎](#okio-引擎)
  - [OkioFileUtils — 高效文件操作](#okiofileutils--高效文件操作)
  - [OkioReadUtils — 高效读取](#okioreadutils--高效读取)
  - [OkioWriteUtils — 高效写入](#okiowriteutils--高效写入)
  - [OkioHashUtils — 流式哈希](#okiohashutils--流式哈希)
- [文件清理模块](#文件清理模块)
  - [FileCleanupManager — 自动清理管理](#filecleanupmanager--自动清理管理)
  - [CleanupConfig/Builder — 声明式构建](#cleanupconfigbuilder--声明式构建)
  - [CleanupTrigger — 触发机制](#cleanuptrigger--触发机制)
- [实战场景](#实战场景)
  - [场景 A: 下载文件 → 验证 → 保存](#场景-a-下载文件--验证--保存)
  - [场景 B: 大文件分块上传带进度和哈希](#场景-b-大文件分块上传带进度和哈希)
  - [场景 C: 应用缓存自动清理](#场景-c-应用缓存自动清理)
  - [场景 D: 日志文件追加写入 + 尾部读取](#场景-d-日志文件追加写入--尾部读取)
  - [场景 E: 备份目录比对审计](#场景-e-备份目录比对审计)
  - [场景 F: 复制文件并自动校验](#场景-f-复制文件并自动校验)
  - [场景 G: Gzip 压缩+哈希一步到位](#场景-g-gzip-压缩哈希一步到位)
- [API 完整参考](#api-完整参考)
- [性能指南](#性能指南)
- [线程模型](#线程模型)
- [许可证](#许可证)

---

## 模块组成

```
itg-file/
└── src/main/java/com/itg/itg_file/
    ├── core/
    │   ├── FileUtils.kt            (java.io  文件基础操作, 48个方法)
    │   └── OkioFileUtils.kt        (Okio     文件操作,      24个方法)
    ├── read/
    │   ├── FileReadUtils.kt        (java.io  文件读取,      16个方法)
    │   └── OkioReadUtils.kt        (Okio     文件读取,      16个方法)
    ├── write/
    │   ├── FileWriteUtils.kt       (java.io  文件写入,      17个方法)
    │   └── OkioWriteUtils.kt       (Okio     文件写入,      19个方法)
    ├── hash/
    │   ├── FileHashUtils.kt        (java.io  文件哈希,      22个方法)
    │   └── OkioHashUtils.kt        (Okio     流式哈希,      13个方法)
    └── cleanup/
        ├── CleanupModels.kt        (数据模型: 触发器/规则/结果)
        ├── CleanupExecutor.kt      (清理执行器)
        └── FileCleanupManager.kt   (生命周期感知管理器,  20+个方法)
```

**总计: 8 个工具 object, 3 个清理模块, 190+ 个公开方法, 全部 @JvmStatic 支持 Java 调用。**

---

## 快速开始

### 依赖

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":itg-file"))
}
// 自动传递: itg-thread-pools + Okio 3.17.0
```

### 同步模式

```kotlin
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.write.FileWriteUtils

// 写入
FileWriteUtils.writeText("/sdcard/data.txt", "Hello World")
// 读取
val content = FileReadUtils.readText("/sdcard/data.txt")
// 信息
val info = FileUtils.getFileInfo("/sdcard/data.txt")
```

### 异步模式

```kotlin
import com.itg.itg_thread_pools.executor.TaskExecutor

FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
    if (error == null) {
        TaskExecutor.main { textView.text = content }
    }
}
```

### 清理管理

```kotlin
import com.itg.itg_file.cleanup.FileCleanupManager

// 应用启动时清空缓存目录
FileCleanupManager.register(application, FileCleanupManager.builder()
    .clearOnAppStart("app-cache", context.cacheDir.absolutePath)
    .build()
)
```

---

## 架构总览

```
┌────────────────────────────────────────────────────────────────────┐
│                   itg-file  总体架构                               │
│                                                                    │
│  ┌──────────────────────┐   ┌──────────────────────────────┐      │
│  │   java.io 引擎        │   │      Okio 引擎 (更快)         │      │
│  │                       │   │                              │      │
│  │  FileUtils            │   │  OkioFileUtils               │      │
│  │  FileReadUtils        │   │  OkioReadUtils               │      │
│  │  FileWriteUtils       │   │  OkioWriteUtils              │      │
│  │  FileHashUtils        │   │  OkioHashUtils               │      │
│  └──────────┬───────────┘   └──────────────┬───────────────┘      │
│             │                               │                      │
│             └───────────┬───────────────────┘                      │
│                         │                                          │
│                ┌────────▼────────┐                                 │
│                │ TaskExecutor.io │  (itg-thread-pools 异步调度)    │
│                └────────┬────────┘                                 │
│                         │                                          │
│  ┌──────────────────────┴──────────────────────────────────┐      │
│  │              文件清理模块 (cleanup/)                     │      │
│  │                                                         │      │
│  │  FileCleanupManager  ── 生命周期感知的自动清理管理器     │      │
│  │  CleanupExecutor     ── 清理执行器                      │      │
│  │  CleanupModels       ── 规则/触发器/结果模型            │      │
│  └─────────────────────────────────────────────────────────┘      │
└────────────────────────────────────────────────────────────────────┘
```

### java.io vs Okio 双引擎对比

| 维度 | java.io 引擎 | Okio 引擎 |
|------|-------------|----------|
| 依赖 | Android SDK 内置 | Okio 3.17.0 (~200KB) |
| 性能 | 标准 I/O | 更快 (Segment 池, 零拷贝 Buffer) |
| 超时控制 | 无 | `Source.timeout()`, `Sink.timeout()` |
| 不可变字节 | `byte[]` (需拷贝) | `ByteString` (零拷贝子序列, 内置 hex/base64/utf8) |
| 流式哈希 | 读完再算 | 边读边算 (`DigestingSource`) |
| Gzip | `java.util.zip.GZIPInputStream` | 内建 (`GzipSource` / `GzipSink`) |
| 进度追踪 | 手动循环计数 | `ForwardingSource/Sink` 拦截器 |
| 原子移动 | 手动 copy+delete | `FileSystem.atomicMove()` |
| 选择建议 | 简单场景, 零额外依赖 | 高性能/超时/流式哈希/Gzip 场景 |

---

## 使用场景索引

| 你想做什么？ | Java.io 方案 | Okio 方案 (更快) |
|-------------|-------------|------------------|
| 文件存在/类型/空判断 | `FileUtils.exists/isFile/isEmpty` | `OkioFileUtils.exists/isRegularFile` |
| 创建文件/目录 | `FileUtils.createFile/createDirectory` | `OkioFileUtils.createDirectory` |
| 删除文件/递归删除 | `FileUtils.delete/clearDirectory` | `OkioFileUtils.delete` |
| 复制文件 | `FileUtils.copy/copyWithProgress` | `OkioFileUtils.copy` |
| 复制整个目录 | `FileUtils.copyDirectory` | — |
| 移动文件 (含跨分区) | `FileUtils.move` | `OkioFileUtils.move` (优先 atomicMove) |
| 重命名 | `FileUtils.rename` | — |
| 列出目录文件 | `FileUtils.listFiles/listFilesRecursive` | `OkioFileUtils.list/listRecursively` |
| 按扩展名过滤 | `FileUtils.listFilesByExtension` | — |
| 获取文件信息 | `FileUtils.getFileInfo/getSize/getMimeType` | `OkioFileUtils.getSize/getLastModifiedMillis` |
| 存储空间查询 | `FileUtils.getAvailableSpace/getTotalSpace` | `OkioFileUtils.getAvailableSpace/getTotalSpace` |
| 读取文本 (JSON/XML) | `FileReadUtils.readText` | `OkioReadUtils.readUtf8` |
| 读取二进制 (图片/音频) | `FileReadUtils.readBytes` | `OkioReadUtils.readByteString` |
| 逐行读取大文件 (日志/CSV) | `FileReadUtils.readLinesStreaming` | `OkioReadUtils.readLinesStreaming` |
| 分块读取超大文件 | `FileReadUtils.readChunks` | `OkioReadUtils.readWithProgress` |
| 读取文件头/尾 | `FileReadUtils.readHeadBytes/readTailBytes` | — |
| 从 Content URI 读取 | `FileReadUtils.readBytes(context, uri)` | — |
| 从 InputStream 读取 | `FileReadUtils.readBytes/readText(is)` | — |
| 覆盖写入文本 | `FileWriteUtils.writeText` | `OkioWriteUtils.writeUtf8` |
| 追加写入 (日志) | `FileWriteUtils.appendText` | `OkioWriteUtils.appendUtf8` |
| 写入字节数组 | `FileWriteUtils.writeBytes` | `OkioWriteUtils.writeByteString` |
| 从流写入文件 | `FileWriteUtils.writeFromStream` | `OkioWriteUtils.writeFromStream` |
| 写入到 Content URI | `FileWriteUtils.writeToUri/writeStreamToUri` | — |
| 原子写入 (防损坏) | `FileWriteUtils.writeTextAtomic/writeBytesAtomic` | `OkioWriteUtils.writeAtomic/writeAtomicUtf8` |
| 分块写入 (大文件) | `FileWriteUtils.writeBytesInChunks` | `OkioWriteUtils.writeWithProgress` |
| Gzip 压缩写入 | — | `OkioWriteUtils.writeGzip/writeGzipText` |
| Gzip 解压读取 | — | `OkioReadUtils.readGzip/readGzipAsText` |
| 超时控制 | — | `OkioFileUtils.withTimeout` / `OkioReadUtils.readWithTimeout` / `OkioWriteUtils.writeWithTimeout` |
| MD5/SHA 哈希 | `FileHashUtils.md5/sha1/sha256/sha512/crc32` | `OkioHashUtils.hashFile/hashFileWithProgress` |
| 流式哈希 (边读边算) | — | `OkioHashUtils.hashWhileReading` |
| 流式哈希 (边写边算) | — | `OkioHashUtils.hashWhileWriting` |
| 复制+哈希+进度一步到位 | — | `OkioHashUtils.copyAndHash` |
| 复制+自动校验 | — | `OkioHashUtils.copyAndVerify` |
| Gzip+哈希 | — | `OkioHashUtils.gzipAndHash` |
| 校验文件完整性 | `FileHashUtils.verify` | `OkioHashUtils.hashFile` + 手动比对 |
| 比较两个文件 | `FileHashUtils.compareFiles` | `OkioHashUtils.hashByteString` + 比对 |
| 字符串/字节哈希 | `FileHashUtils.hashString/hashBytes` | `OkioHashUtils.hashString/hashByteString` |
| 应用启动清缓存 | `FileCleanupManager.register(app, builder().clearOnAppStart(...).build())` |
| 应用退后台清临时目录 | `FileCleanupManager.register(app, builder().clearOnAppBackground(...).build())` |
| 延迟清理 (如30分钟后) | `FileCleanupManager.register(app, builder().clearAfterDelay("k", "/path", 30*60*1000L).build())` |
| 定时清理 (如每7天) | `FileCleanupManager.register(app, builder().clearAfterDays("k", "/path", days=7, persistAcrossRestarts=true).build())` |
| 跟随 Activity/Fragment 生命周期清理 | `FileCleanupManager.register(activity, builder().build())` |
| 取消/释放清理规则 | `FileCleanupManager.cancel(key)` / `FileCleanupManager.release(key)` |

---

## java.io 引擎

### FileUtils — 文件基础操作

**位置**: `com.itg.itg_file.core.FileUtils`  
**方法数**: 48 (24 同步, 18 异步, 6 个私有辅助)  
**异步线程**: `TaskExecutor.io`

#### 存在性判断

```kotlin
FileUtils.exists("/sdcard/data.txt")        // Boolean
FileUtils.existsAsync(path) { exists -> }    // 异步

FileUtils.isFile(path)        // true = 常规文件
FileUtils.isDirectory(path)   // true = 目录
FileUtils.isEmpty(path)       // true = 0字节文件 或 空目录
```

#### 创建

```kotlin
FileUtils.createFile("/sdcard/MyApp/config.json")   // 自动创建父目录
FileUtils.createFileAsync(path) { success -> }

FileUtils.createDirectory("/sdcard/MyApp/cache/")   // 递归创建
FileUtils.createDirectoryAsync(path) { success -> }

FileUtils.createTempFile(prefix="upload_", suffix=".tmp")  // 系统临时目录
```

#### 删除

```kotlin
FileUtils.delete("/sdcard/MyApp/cache/")     // 递归删除
FileUtils.deleteAsync(path) { success -> }

FileUtils.clearDirectory("/sdcard/temp/")    // 清空内容, 保留目录
FileUtils.clearDirectoryAsync(path) { success -> }
```

#### 重命名

```kotlin
val newPath = FileUtils.rename("/sdcard/old.txt", "new.txt")
FileUtils.renameAsync(path, "new.txt") { newPath -> }
```

#### 复制

```kotlin
// 基础复制
FileUtils.copy("/sdcard/a.txt", "/sdcard/b.txt")
FileUtils.copyAsync(src, dest) { success -> }

// 带进度 (大文件推荐)
FileUtils.copyWithProgress("/sdcard/large.zip", "/sdcard/backup.zip",
    onProgress = { copied, total ->
        val percent = copied * 100 / total
        updateProgressBar(percent)
    })

// 异步+进度
FileUtils.copyWithProgressAsync(src, dest, 
    onProgress = { c, t -> updateUI(c, t) },
    onResult = { success -> showDone(success) })

// 复制整个目录 (递归)
FileUtils.copyDirectory("/sdcard/MyApp/", "/sdcard/backup/MyApp/")
```

#### 移动 (自动选择 rename 或 copy+delete)

```kotlin
FileUtils.move("/sdcard/old.txt", "/sdcard/new.txt")
FileUtils.moveAsync(src, dest) { success -> }
```

#### 文件列表

```kotlin
// 全部列出
val files = FileUtils.listFiles("/sdcard/DCIM/")

// 条件过滤
val jpgs = FileUtils.listFiles("/sdcard/DCIM/") { 
    it.extension.equals("jpg", ignoreCase=true) 
}

// 按扩展名
val pngs = FileUtils.listFilesByExtension("/sdcard/DCIM/", "png")

// 递归列出 (含子目录)
val all = FileUtils.listFilesRecursive("/sdcard/MyApp/")
FileUtils.listFilesRecursiveAsync(path) { files -> }
```

#### 文件信息

```kotlin
val info = FileUtils.getFileInfo("/sdcard/photo.jpg")
// 返回 Map:
// { exists=true, name="photo.jpg", extension="jpg", 
//   size=2621440, sizeFormatted="2.50 MB",
//   lastModified="2026-06-28 14:30:00", 
//   mimeType="image/jpeg", canRead=true, canWrite=true }

FileUtils.getSize(path)                     // Long (字节)
FileUtils.getSizeFormatted(path)            // "2.50 MB"
FileUtils.getExtension(path)                // "jpg"
FileUtils.getFileName(path)                 // "photo.jpg"
FileUtils.getFileNameWithoutExtension(path) // "photo"
FileUtils.getMimeType(path)                 // "image/jpeg"
FileUtils.getMimeType(context, uri)         // 从 Content URI
FileUtils.getLastModified(path)             // "2026-06-28 14:30:00"
FileUtils.getLastModifiedMillis(path)       // 毫秒时间戳
FileUtils.getParentPath(path)               // "/sdcard/"

FileUtils.getFileInfoAsync(path) { info -> }
```

#### 存储空间

```kotlin
val available = FileUtils.getAvailableSpace("/sdcard/")
println("${FileUtils.formatFileSize(available)} 可用")

FileUtils.getTotalSpace("/sdcard/")         // 总空间
FileUtils.getInternalAvailableSpace()        // 内置存储可用
FileUtils.getExternalAvailableSpace()        // SD卡可用 (无则-1)
```

---

### FileReadUtils — 文件读取

**位置**: `com.itg.itg_file.read.FileReadUtils`  
**方法数**: 16 (8 同步, 7 异步, 1 私有)  
**异步线程**: `TaskExecutor.io`

#### 读取文本

```kotlin
// UTF-8
val json = FileReadUtils.readText("/sdcard/data.json")

// 指定编码
val gbk = FileReadUtils.readText("/sdcard/legacy.txt", Charset.forName("GBK"))

// 异步
FileReadUtils.readTextAsync(path) { content, error ->
    if (content != null) TaskExecutor.main { parseAndDisplay(content) }
}
```

#### 读取字节

```kotlin
val bytes = FileReadUtils.readBytes("/sdcard/photo.jpg")
FileReadUtils.readBytesAsync(path) { bytes, error -> }

// 从 Content URI 读取 (限制10MB)
val bytes = FileReadUtils.readBytes(context, uri, maxBytes = 10 * 1024 * 1024)

// 从 InputStream 读取 (限制1MB)
val bytes = FileReadUtils.readBytes(inputStream, maxBytes = 1024 * 1024)

// 从 InputStream 读取为文本
val text = FileReadUtils.readText(inputStream, charset = Charsets.UTF_8, maxBytes = 1024*1024)
```

#### 逐行读取

```kotlin
// 一次性全部读入 (小文件)
val lines = FileReadUtils.readLines("/sdcard/log.txt")
FileReadUtils.readLinesAsync(path) { lines, error -> }

// 流式逐行处理 (大文件, 边读边处理)
val count = FileReadUtils.readLinesStreaming("/sdcard/huge.csv",
    onEachLine = { line, index ->
        if (index % 10000 == 0) Log.d("CSV", "Processing line $index")
        processCsvLine(line)
        index < 9999  // false = 只读10000行
    })

// 异步流式
FileReadUtils.readLinesStreamingAsync(path,
    onEachLine = { line, idx -> parseLine(line); true },
    onComplete = { total, error -> Log.d("Done", "Read $total lines") })
```

#### 分块读取 (超大文件 >100MB)

```kotlin
FileReadUtils.readChunks("/sdcard/large.bin", chunkSize = 1024 * 1024,
    onChunk = { chunk, index, total ->
        uploadChunk(chunk, index)
        val progress = (index + 1) * 100 / total
        TaskExecutor.main { progressBar.progress = progress }
        true  // 继续读取
    })

// 异步
FileReadUtils.readChunksAsync(path, chunkSize = 1024*1024,
    onChunk = { data, idx, total -> /* 处理分块 */ true },
    onComplete = { totalBytes, error -> /* 完成 */ })
```

#### 文件头/尾部

```kotlin
// 读取文件头4字节 (Magic Number 识别)
val header = FileReadUtils.readHeadBytes("/sdcard/file.bin", 4)

// 读取尾部100字节
val tail = FileReadUtils.readTailBytes("/sdcard/data.bin", 100)

// 读取最后50行 (日志查看)
val lastLines = FileReadUtils.readTailLines("/sdcard/app.log", 50)
```

---

### FileWriteUtils — 文件写入

**位置**: `com.itg.itg_file.write.FileWriteUtils`  
**方法数**: 17 (9 同步, 8 异步, 2 私有)  
**异步线程**: `TaskExecutor.io`

#### 写入文本

```kotlin
FileWriteUtils.writeText("/sdcard/data.txt", "content")
FileWriteUtils.writeText(path, content, Charset.forName("GBK"))
FileWriteUtils.writeTextAsync(path, content) { success -> }

// 追加 (适合日志)
FileWriteUtils.appendText("/sdcard/log.txt", "[INFO] App started\n")
FileWriteUtils.appendTextAsync(path, logLine) { success -> }
```

#### 写入字节

```kotlin
FileWriteUtils.writeBytes("/sdcard/photo.jpg", imageBytes)
FileWriteUtils.writeBytesAsync(path, bytes) { success -> }

FileWriteUtils.appendBytes("/sdcard/data.bin", extraBytes)  // 追加
```

#### 从流写入

```kotlin
FileWriteUtils.writeFromStream("/sdcard/download.zip", inputStream,
    onProgress = { written, total ->
        // written: 已写入字节, total: -1 (未知)
        updateProgress(written)
    })
FileWriteUtils.writeFromStreamAsync(path, is) { success -> }
```

#### 写入到 Content URI

```kotlin
FileWriteUtils.writeToUri(context, uri, imageBytes, "image/jpeg")
FileWriteUtils.writeStreamToUri(context, uri, inputStream, "video/mp4")
```

#### 原子写入 (防数据损坏)

```kotlin
// 写临时文件 → 成功后重命名, 防止写入中断导致文件损坏
FileWriteUtils.writeTextAtomic("/data/config.json", configJson)
FileWriteUtils.writeTextAtomicAsync(path, content) { success -> }

FileWriteUtils.writeBytesAtomic("/data/database.db", dbBytes)
```

#### 分块写入 (大文件, 带进度)

```kotlin
FileWriteUtils.writeBytesInChunks("/sdcard/big_file.bin", hugeData,
    chunkSize = 1024 * 1024,  // 每块1MB
    onProgress = { written, total ->
        val percent = (written * 100 / total).toInt()
        updateProgressBar(percent)
    })

FileWriteUtils.writeBytesInChunksAsync(path, data,
    onProgress = { w, t -> updateUI(w, t) },
    onResult = { success -> showDone(success) })
```

---

### FileHashUtils — 文件哈希

**位置**: `com.itg.itg_file.hash.FileHashUtils`  
**方法数**: 22 (13 同步, 10 异步, 1 私有)  
**异步线程**: `TaskExecutor.io`

#### 算法枚举

```kotlin
FileHashUtils.Algorithm.MD5      // "MD5"
FileHashUtils.Algorithm.SHA1     // "SHA-1"
FileHashUtils.Algorithm.SHA256   // "SHA-256"
FileHashUtils.Algorithm.SHA512   // "SHA-512"
FileHashUtils.Algorithm.fromString("sha-256")  // 字符串→枚举
```

#### 计算哈希

```kotlin
// 通用 (默认SHA-256)
val hash = FileHashUtils.hashFile("/sdcard/photo.jpg")
val hash = FileHashUtils.hashFile(path, FileHashUtils.Algorithm.MD5)

// 便捷方法
val md5 = FileHashUtils.md5(path)
val sha1 = FileHashUtils.sha1(path)
val sha256 = FileHashUtils.sha256(path)
val sha512 = FileHashUtils.sha512(path)
val crc32 = FileHashUtils.crc32(path)

// 带进度 (大文件)
FileHashUtils.hashFileWithProgress(path, FileHashUtils.Algorithm.SHA256,
    onProgress = { processed, total ->
        updateProgress(processed, total)
    })

// 异步
FileHashUtils.md5Async(path) { hash, error -> }
FileHashUtils.sha256Async(path) { hash, error -> }
```

#### 字符串/字节哈希

```kotlin
FileHashUtils.hashString("hello", FileHashUtils.Algorithm.SHA256)
FileHashUtils.hashBytes(byteArrayOf(...), FileHashUtils.Algorithm.MD5)
FileHashUtils.crc32(byteArrayOf(...))
```

#### 完整性校验

```kotlin
// 校验文件哈希是否匹配
val valid = FileHashUtils.verify("/sdcard/download.apk",
    expectedHash = "e3b0c44298...",
    algorithm = FileHashUtils.Algorithm.SHA256,
    ignoreCase = true)

// 异步
FileHashUtils.verifyAsync(path, expectedHash, FileHashUtils.Algorithm.SHA256) { valid, actual ->
    if (!valid) Log.e("Verify", "Expected SHA-256 mismatch: $actual")
}

// 比较两个文件是否相同
val same = FileHashUtils.compareFiles("/sdcard/a.bin", "/sdcard/b.bin")
FileHashUtils.compareFilesAsync(path1, path2) { same -> }
```

---

## Okio 引擎

### OkioFileUtils — 高效文件操作

**位置**: `com.itg.itg_file.core.OkioFileUtils`  
**方法数**: 24  
**依赖**: Okio `FileSystem.SYSTEM`, `Path`

#### 快速复制与原子移动

```kotlin
// Okio 复制 (利用 Buffer 零拷贝, 通常比 java.io 快 2-3x)
OkioFileUtils.copy("/sdcard/large.bin", "/sdcard/backup/large.bin")
OkioFileUtils.copyAsync(src, dest) { success -> }

// 原子移动 (同一文件系统下无拷贝, 仅修改指针)
OkioFileUtils.move("/sdcard/temp.bin", "/sdcard/final.bin")
OkioFileUtils.moveAsync(src, dest) { success -> }
```

#### 删除/创建

```kotlin
OkioFileUtils.delete("/sdcard/old/")        // 递归
OkioFileUtils.deleteAsync(path) { ok -> }
OkioFileUtils.createDirectory("/sdcard/new/")
OkioFileUtils.createDirectoryAsync(path) { ok -> }
```

#### 超时控制 (Okio 独有)

```kotlin
// 任意文件操作在 5 秒内完成, 超时返回 null
val size = OkioFileUtils.withTimeout(5000L) {
    OkioFileUtils.getSize("/mnt/nfs/slow_file.bin")
}

// 异步超时
OkioFileUtils.withTimeoutAsync(5000L, { heavyOperation() }) { result -> }
```

#### 目录遍历

```kotlin
// 直接子项
val children = OkioFileUtils.list("/sdcard/DCIM/")

// 递归列出
val all = OkioFileUtils.listRecursively("/sdcard/MyApp/")
OkioFileUtils.listRecursivelyAsync(path) { pathStrings -> }
```

#### Path 类型互转

```kotlin
val okioPath = OkioFileUtils.toPath("/sdcard/file.txt")  // String→Path
val strPath = OkioFileUtils.fromPath(okioPath)             // Path→String
```

#### 元数据查询

```kotlin
OkioFileUtils.exists(path)
OkioFileUtils.isRegularFile(path)
OkioFileUtils.isDirectory(path)
OkioFileUtils.getSize(path)
OkioFileUtils.getLastModifiedMillis(path)
OkioFileUtils.getAvailableSpace(path)
OkioFileUtils.getTotalSpace(path)
```

---

### OkioReadUtils — 高效读取

**位置**: `com.itg.itg_file.read.OkioReadUtils`  
**方法数**: 16  
**依赖**: Okio `ByteString`, `BufferedSource`, `DigestingSource`

#### ByteString — 不可变字节序列 (Okio 独有)

```kotlin
val byteStr = OkioReadUtils.readByteString("/sdcard/photo.jpg")

// ByteString 内置能力
byteStr?.hex()         // 十六进制
byteStr?.base64()      // Base64
byteStr?.base64Url()   // URL安全 Base64
byteStr?.utf8()        // UTF-8 解码
byteStr?.md5()?.hex()  // MD5
byteStr?.sha256()?.hex()  // SHA-256
byteStr?.substring(0, 16) // 零拷贝子序列

OkioReadUtils.readByteStringAsync(path) { byteStr, error -> }
```

#### 读取文本

```kotlin
val text = OkioReadUtils.readUtf8("/sdcard/data.json")
val gbk = OkioReadUtils.readUtf8("/sdcard/legacy.txt", Charset.forName("GBK"))
OkioReadUtils.readUtf8Async(path) { text, error -> }
```

#### 读取到可变 Buffer

```kotlin
val buffer = OkioReadUtils.readToBuffer("/sdcard/data.bin")
buffer?.let {
    val firstByte = it.readByte()
    val int32 = it.readInt()
    it.skip(16)            // Python 风格 API
    val rest = it.readByteArray()
}
OkioReadUtils.readToBufferAsync(path) { buffer, error -> }
```

#### 超时读取

```kotlin
// 从慢速文件系统读取, 30秒超时
val data = OkioReadUtils.readWithTimeout("/mnt/nfs/huge.log", 30_000L)
OkioReadUtils.readWithTimeoutAsync(path, 30_000L) { data, error -> }
```

#### 带进度读取 (ForwardingSource 拦截器)

```kotlin
val data = OkioReadUtils.readWithProgress("/sdcard/large.dat",
    onProgress = { read, total ->
        val percent = read * 100 / total
        updateProgressBar(percent.toInt())
    })
```

#### Gzip 解压

```kotlin
val decompressed = OkioReadUtils.readGzip("/sdcard/data.json.gz")
OkioReadUtils.readGzipAsync(path) { bytes, error -> }

val text = OkioReadUtils.readGzipAsText("/sdcard/data.json.gz")
```

#### 流式逐行读取

```kotlin
OkioReadUtils.readLinesStreaming("/sdcard/huge.log") { line, index ->
    if (line.contains("ERROR")) logError(line)
    true  // 继续
}
```

---

### OkioWriteUtils — 高效写入

**位置**: `com.itg.itg_file.write.OkioWriteUtils`  
**方法数**: 19  
**依赖**: Okio `ByteString`, `BufferedSink`, `DigestingSink`, `ForwardingSink`

#### 写入 ByteString

```kotlin
val byteStr = ByteString.encodeUtf8("Hello Okio")
OkioWriteUtils.writeByteString("/sdcard/hello.txt", byteStr)
OkioWriteUtils.writeByteStringAsync(path, byteStr) { success -> }
```

#### 写入与追加

```kotlin
// 覆盖
OkioWriteUtils.writeUtf8("/sdcard/data.txt", "content")
OkioWriteUtils.writeUtf8Async(path, content) { success -> }

// 追加
OkioWriteUtils.appendUtf8("/sdcard/log.txt", "[INFO] App started\n")
OkioWriteUtils.appendUtf8Async(path, line) { success -> }

// Buffer→文件
val buffer = Buffer().apply { writeUtf8("data") }
OkioWriteUtils.writeFromBuffer("/sdcard/out.bin", buffer)
```

#### 从流写入

```kotlin
OkioWriteUtils.writeFromStream("/sdcard/download.zip", inputStream)
OkioWriteUtils.writeFromStreamAsync(path, is) { success -> }
```

#### 超时写入

```kotlin
// 写入到慢速设备, 10秒超时
OkioWriteUtils.writeWithTimeout("/mnt/nfs/remote.bin", data, 10_000L)
OkioWriteUtils.writeWithTimeoutAsync(path, data, 10_000L) { success -> }
```

#### 带进度写入

```kotlin
OkioWriteUtils.writeWithProgress("/sdcard/large.bin", hugeData,
    onProgress = { written, total ->
        updateProgressBar((written * 100 / total).toInt())
    })
```

#### Gzip 压缩写入

```kotlin
OkioWriteUtils.writeGzip("/sdcard/data.json.gz", jsonData.toByteArray())
OkioWriteUtils.writeGzipText("/sdcard/data.json.gz", jsonString)
OkioWriteUtils.writeGzipAsync(path, data) { success -> }
```

#### 原子写入 (Okio 版本)

```kotlin
OkioWriteUtils.writeAtomic("/data/config.json", configBytes)
OkioWriteUtils.writeAtomicUtf8("/data/config.json", configJson)
OkioWriteUtils.writeAtomicAsync(path, data) { success -> }
```

---

### OkioHashUtils — 流式哈希

**位置**: `com.itg.itg_file.hash.OkioHashUtils`  
**方法数**: 13  
**依赖**: Okio `DigestingSource`/`DigestingSink` (自定义 Forwarding 子类)

#### 边读边算哈希 (一次 I/O 完成两个操作)

```kotlin
// 读取文件到 Buffer 的同时计算 SHA-256
val buffer = Buffer()
val hash = OkioHashUtils.hashWhileReading("/sdcard/file.bin",
    MessageDigest.getInstance("SHA-256"),
    onRead = { buf, _ -> buffer.writeAll(buf) })
// buffer 中有文件内容, hash 就是 SHA-256
```

#### 边写边算哈希

```kotlin
val (hash, ok) = OkioHashUtils.hashWhileWriting(
    "/sdcard/data.bin", imageBytes,
    MessageDigest.getInstance("MD5")) ?: return
println("Written with MD5: $hash")
```

#### 复制 + 哈希 + 进度 — 一步到位 (Okio 最独特能力)

```kotlin
val (hash, ok) = OkioHashUtils.copyAndHash(
    "/sdcard/large.iso", "/sdcard/backup/large.iso",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { copied, total ->
        val percent = copied * 100 / total
        TaskExecutor.main { progressBar.progress = percent.toInt() }
    }) ?: return
if (ok) println("Copied. SHA-256: $hash")

// 异步
OkioHashUtils.copyAndHashAsync(src, dest, digest,
    onProgress = { c, t -> updateProgress(c, t) },
    onResult = { hash, success -> })
```

#### 复制 + 自动校验

```kotlin
val (hash, success, verified) = OkioHashUtils.copyAndVerify(
    "/sdcard/important.db", "/sdcard/backup/important.db",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { copied, total -> updateProgress(copied, total) }
) ?: return
if (verified) println("复制成功且校验通过") else println("校验失败!")
```

#### Gzip + 哈希

```kotlin
val (hash, ok) = OkioHashUtils.gzipAndHash(
    "/sdcard/raw_data.json", "/sdcard/raw_data.json.gz",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { processed, total -> updateProgress(processed, total) }
) ?: return
```

#### 基础哈希 (不带组合操作)

```kotlin
val sha256 = OkioHashUtils.hashFile("/sdcard/file.bin", MessageDigest.getInstance("SHA-256"))
OkioHashUtils.hashFileWithProgress(path, digest, onProgress = { p, t -> } )
OkioHashUtils.hashFileAsync(path, digest) { hash, error -> }
OkioHashUtils.hashString("hello", MessageDigest.getInstance("MD5"))
OkioHashUtils.hashByteString(byteString, MessageDigest.getInstance("SHA-256"))
```

---

## 文件清理模块

`cleanup/` 包提供**生命周期感知**的自动文件清理能力——声明规则 → 绑定生命周期 → 自动触发清理。

### FileCleanupManager — 自动清理管理

**位置**: `com.itg.itg_file.cleanup.FileCleanupManager`  
**方法数**: 20+  
**核心设计**: Builder 模式构建规则 + 绑定 Application/Activity/Fragment 生命周期  

#### 快速使用

```kotlin
// 应用启动时清空缓存目录
FileCleanupManager.register(application, FileCleanupManager.builder()
    .clearOnAppStart("app-cache", context.cacheDir.absolutePath)
    .clearOnAppBackground("app-temp", context.cacheDir.absolutePath + "/temp")
    .build()
)
```

#### 注册方式

```kotlin
// 绑定 Application (全局生命周期)
FileCleanupManager.register(application, config, callbacks)
FileCleanupManager.register(application, config) { result -> }

// 绑定 Activity (跟随 Activity)
FileCleanupManager.register(activity, config, callbacks)
FileCleanupManager.register(activity, config) { result -> }

// 绑定 Fragment (支持 lifecycleScope)
FileCleanupManager.register(fragment, config, CleanupLifecycleScope.FRAGMENT, callbacks)

// 立即执行 (不绑定生命周期)
val futures = FileCleanupManager.runNow(config) { result -> }
FileCleanupManager.runNow(activity, config) { result -> }
```

#### 取消与释放

```kotlin
FileCleanupManager.cancel("rule-key")          // 取消指定规则
FileCleanupManager.cancelAll()                  // 取消全部
FileCleanupManager.release("rule-key")          // 释放规则
FileCleanupManager.release(config)              // 释放配置中所有规则
FileCleanupManager.release(activity)            // 释放某 Activity 的所有规则
FileCleanupManager.release(fragment, scope)     // 释放某 Fragment 的规则
FileCleanupManager.releaseAll()                 // 释放全部
```

---

### CleanupConfig/Builder — 声明式构建

通过 `FileCleanupManager.builder()` 获得 Builder，链式构建规则。

```kotlin
val config = FileCleanupManager.builder()
    // 应用启动触发
    .clearOnAppStart("cache-cleaner", "/sdcard/MyApp/cache/")
    .deleteOnAppStart("old-logs", "/sdcard/MyApp/old_logs/")
    
    // 应用退后台触发
    .clearOnAppBackground("temp-cleaner", "/sdcard/MyApp/temp/")
    
    // 延迟触发 (毫秒)
    .clearAfterDelay("delayed-clean", "/sdcard/MyApp/downloads/", 
        delayMs = 30 * 60 * 1000L)  // 30分钟后
    .deleteAfterDelay("stale-files", "/sdcard/MyApp/cache/stale/",
        delayMs = 60 * 60 * 1000L,
        scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION)  // 循环执行
    
    // 按天数触发 (持久化, 跨重启)
    .clearAfterDays("weekly-clean", "/sdcard/MyApp/logs/", 
        days = 7, persistAcrossRestarts = true)
    .deleteAfterDays("monthly-clean", "/sdcard/MyApp/downloads/",
        days = 30, persistAcrossRestarts = true)
    
    // 指定时间点触发
    .clearAtTime("midnight-clean", "/sdcard/MyApp/cache/",
        triggerAtMillis = midnightTimestamp)
    
    // 添加自定义规则
    .add(CleanupRule("my-rule", "/custom/path/", CleanupAction.DELETE_TARGET, trigger))
    
    .build()

// 注册
FileCleanupManager.register(application, config)
```

**Builder 方法一览**:

| 方法 | 说明 |
|------|------|
| `clearOnAppStart(key, path)` | 应用启动 → 清空目录 |
| `deleteOnAppStart(key, path)` | 应用启动 → 删除目录 |
| `clearOnAppBackground(key, path)` | 应用退后台 → 清空目录 |
| `deleteOnAppBackground(key, path)` | 应用退后台 → 删除目录 |
| `clearAfterDelay(key, path, delayMs, mode, persist)` | 延迟 → 清空目录 |
| `deleteAfterDelay(key, path, delayMs, mode, persist)` | 延迟 → 删除目录 |
| `clearAfterDays(key, path, days, persist, mode)` | 按天数 → 清空目录 |
| `deleteAfterDays(key, path, days, persist, mode)` | 按天数 → 删除目录 |
| `clearAtTime(key, path, triggerAtMillis)` | 指定时间点 → 清空目录 |
| `deleteAtTime(key, path, triggerAtMillis)` | 指定时间点 → 删除目录 |
| `add(rule)` | 添加自定义规则 |
| `build()` | 构建 CleanupConfig |

---

### CleanupTrigger — 触发机制

所有触发类型定义在 `CleanupModels.kt` 中:

| 触发器 | 类型 | 何时触发 |
|--------|------|---------|
| `OnAppStart` | App 启动 (首个 Activity 进入前台) |
| `OnAppBackground` | App 退后台 (所有 Activity 进入后台) |
| `AfterDelay(delayMs, persist)` | 延迟指定毫秒后触发 |
| `AfterDays(days, persist)` | 超过指定天数后触发 (持久化, 跨重启) |
| `AtTimeMillis(triggerAtMillis)` | 到达指定时间戳时触发 |

**调度模式** (`CleanupScheduleMode`):

| 模式 | 说明 |
|------|------|
| `ONE_SHOT` | 单次执行, 执行后规则不再触发 |
| `RESTART_AFTER_EXECUTION` | 执行后重新计时/等待, 循环触发 |

**生命周期范围** (`CleanupLifecycleScope`):

| 范围 | 说明 |
|------|------|
| `ACTIVITY` | 跟随 Activity 生命周期 (onDestroy 时取消) |
| `FRAGMENT` | 跟随 Fragment 生命周期 (onDestroy 时取消) |

**回调**:

```kotlin
FileCleanupManager.register(activity, config, CleanupCallbacks(
    onResult = { result -> 
        println("${result.key}: ${if (result.success) "OK" else "FAIL"} - ${result.message}")
    },
    onPermissionRequired = { request ->
        // 权限不足时回调, 可调用 request.retry() 或 request.cancel()
        request.retry()
    }
))
```

---

## 实战场景

### 场景 A: 下载文件 → 验证 → 保存

```kotlin
fun downloadAndVerify(url: String, destPath: String, expectedSha256: String) {
    TaskExecutor.io {
        try {
            // 1. Okio 流写入 (带超时)
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            val totalSize = connection.contentLength
            connection.getInputStream().use { input ->
                OkioWriteUtils.writeFromStream(destPath, input)
            }
            
            // 2. 验证大小 (Content-Length 对比)
            val actualSize = FileUtils.getSize(destPath)
            if (totalSize > 0 && actualSize != totalSize) {
                FileUtils.delete(destPath)
                TaskExecutor.main { onError("Size mismatch") }
                return@io
            }
            
            // 3. 验证 SHA-256
            val valid = FileHashUtils.verify(destPath, expectedSha256, 
                FileHashUtils.Algorithm.SHA256)
            if (!valid) {
                FileUtils.delete(destPath)
                TaskExecutor.main { onError("SHA-256 mismatch") }
                return@io
            }
            
            TaskExecutor.main { onSuccess(destPath) }
        } catch (e: Exception) {
            FileUtils.delete(destPath)
            TaskExecutor.main { onError(e.message) }
        }
    }
}
```

### 场景 B: 大文件分块上传带进度和哈希

```kotlin
fun uploadLargeFile(filePath: String, uploadUrl: String) {
    TaskExecutor.io {
        // OkioHashUtils: 边读边算 SHA-256 + 进度
        val (hash, ok) = OkioHashUtils.copyAndHash(
            filePath, "/tmp/upload_checkpoint.tmp",
            MessageDigest.getInstance("SHA-256"),
            onProgress = { copied, total ->
                val percent = copied * 100 / total
                TaskExecutor.main { progressBar.progress = percent.toInt() }
            }
        ) ?: run { onError("Hash failed"); return@io }
        
        // 上传到服务器
        uploadToServer(filePath)
        
        // 用哈希值向服务器确认完整上传
        confirmUpload(uploadUrl, hash)
        
        // 清理临时文件
        FileUtils.delete("/tmp/upload_checkpoint.tmp")
    }
}
```

### 场景 C: 应用缓存自动清理

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initFileCleanup()
    }
    
    private fun initFileCleanup() {
        val cacheDir = cacheDir.absolutePath
        val filesDir = filesDir.absolutePath
        
        val config = FileCleanupManager.builder()
            // 每次启动清空临时目录
            .clearOnAppStart("clear-temp", "$cacheDir/temp")
            // 退后台清空图片缓存
            .clearOnAppBackground("clear-image-cache", "$cacheDir/images")
            // 每7天清理日志
            .deleteAfterDays("clean-old-logs", "$filesDir/logs", 
                days = 7, persistAcrossRestarts = true)
            // 30分钟后清空下载暂存
            .clearAfterDelay("clean-download-tmp", "$cacheDir/downloads",
                delayMs = 30 * 60 * 1000L)
            .build()
        
        FileCleanupManager.register(this, config, CleanupCallbacks(
            onResult = { result ->
                Log.d("Cleanup", "${result.key}: ${if (result.success) "OK(${result.deletedEntries} files)" else result.message}")
            },
            onPermissionRequired = { request ->
                Log.w("Cleanup", "Permission needed for ${request.key}: ${request.reason}")
                request.retry()
            }
        ))
    }
}
```

### 场景 D: 日志文件追加写入 + 尾部读取

```kotlin
class AppLogger(private val logPath: String) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun log(level: String, message: String) {
        val line = "[${dateFormat.format(Date())}] [$level] $message\n"
        // 异步追加, 不阻塞主线程
        FileWriteUtils.appendText(logPath, line)
    }
    
    // 查看最近的日志 (最后100行)
    fun getRecentLogs(lines: Int = 100): List<String>? {
        return FileReadUtils.readTailLines(logPath, lines)
    }
    
    // 异步获取
    fun getRecentLogsAsync(lines: Int = 100, callback: (List<String>?) -> Unit) {
        TaskExecutor.io {
            val logs = FileReadUtils.readTailLines(logPath, lines)
            TaskExecutor.main { callback(logs) }
        }
    }
}
```

### 场景 E: 备份目录比对审计

```kotlin
fun auditBackup(originalDir: String, backupDir: String): String {
    val diff = com.itg.itg_erification.compare.FileComparator.compareDirectories(
        originalDir, backupDir, compareContent = true)
    
    return buildString {
        appendLine("备份审计报告 - ${Date()}")
        appendLine("=" .repeat(40))
        appendLine("新增文件 (${diff.added.size}):")
        diff.added.take(20).forEach { appendLine("  + $it") }
        appendLine("删除文件 (${diff.removed.size}):")
        diff.removed.take(20).forEach { appendLine("  - $it") }
        appendLine("修改文件 (${diff.modified.size}):")
        diff.modified.take(20).forEach { appendLine("  ~ $it") }
        appendLine("未变文件 (${diff.unchanged.size}):")
        appendLine("状态: ${if (diff.hasDifference) "⚠ 存在差异" else "✅ 完全一致"}")
    }
}
```

### 场景 F: 复制文件并自动校验

```kotlin
fun safeCopy(srcPath: String, destPath: String): Boolean {
    // Okio: 复制+哈希+进度一步到位
    val (hash, success, verified) = OkioHashUtils.copyAndVerify(
        srcPath, destPath,
        MessageDigest.getInstance("SHA-256"),
        onProgress = { copied, total ->
            val percent = copied * 100 / total
            TaskExecutor.main { progressBar.progress = percent.toInt() }
        }
    ) ?: return false
    
    if (!verified) {
        // 校验失败, 删除损坏的副本
        FileUtils.delete(destPath)
        Log.e("Copy", "Verification failed, deleted corrupt copy")
        return false
    }
    
    Log.d("Copy", "Successfully copied and verified. SHA-256: $hash")
    return true
}
```

### 场景 G: Gzip 压缩+哈希一步到位

```kotlin
fun compressAndHash(srcPath: String, gzPath: String): String? {
    val (hash, ok) = OkioHashUtils.gzipAndHash(
        srcPath, gzPath,
        MessageDigest.getInstance("SHA-256"),
        onProgress = { processed, total -> updateProgress(processed, total) }
    ) ?: return null
    
    return if (ok) {
        Log.d("Compress", "Compressed to $gzPath, SHA-256: $hash")
        hash
    } else null
}
```

---

## API 完整参考

### FileUtils (core / java.io)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `exists(path: String?)` | `Boolean` | 文件/目录是否存在 |
| `existsAsync(path, onResult)` | `Future<*>` | 异步检查存在 |
| `isFile(path: String?)` | `Boolean` | 是常规文件 |
| `isDirectory(path: String?)` | `Boolean` | 是目录 |
| `isEmpty(path: String?)` | `Boolean` | 是否为空 (0字节/空目录) |
| `createFile(path: String)` | `Boolean` | 创建文件 (含父目录) |
| `createFileAsync(path, onResult)` | `Future<*>` | 异步创建文件 |
| `createDirectory(path: String)` | `Boolean` | 递归创建目录 |
| `createDirectoryAsync(path, onResult)` | `Future<*>` | 异步创建目录 |
| `createTempFile(prefix, suffix, directory)` | `File?` | 创建临时文件 |
| `delete(path: String?)` | `Boolean` | 递归删除 |
| `deleteAsync(path, onResult)` | `Future<*>` | 异步删除 |
| `clearDirectory(path: String)` | `Boolean` | 清空目录内容 |
| `clearDirectoryAsync(path, onResult)` | `Future<*>` | 异步清空 |
| `rename(path, newName)` | `String?` | 重命名, 返回新路径 |
| `renameAsync(path, newName, onResult)` | `Future<*>` | 异步重命名 |
| `copy(src, dest, overwrite=true)` | `Boolean` | 复制文件 |
| `copyWithProgress(src, dest, overwrite, onProgress)` | `Boolean` | 复制+进度 |
| `copyAsync(src, dest, overwrite, onResult)` | `Future<*>` | 异步复制 |
| `copyWithProgressAsync(src, dest, overwrite, onProgress, onResult)` | `Future<*>` | 异步复制+进度 |
| `copyDirectory(srcDir, destDir, overwrite)` | `Boolean` | 递归复制目录 |
| `move(src, dest, overwrite)` | `Boolean` | 移动 (优先rename) |
| `moveAsync(src, dest, overwrite, onResult)` | `Future<*>` | 异步移动 |
| `listFiles(path)` | `List<File>` | 列出直接子项 |
| `listFiles(path, filter)` | `List<File>` | 条件过滤 |
| `listFilesByExtension(path, ext)` | `List<File>` | 按扩展名过滤 |
| `listFilesRecursive(path)` | `List<File>` | 递归列出 |
| `listFilesRecursiveAsync(path, onResult)` | `Future<*>` | 异步递归列出 |
| `getSize(path)` | `Long` | 文件/目录大小 (字节) |
| `getSizeAsync(path, onResult)` | `Future<*>` | 异步获取大小 |
| `getSizeFormatted(path)` | `String` | 格式化大小 "2.50 MB" |
| `formatFileSize(bytes)` | `String` | 字节→格式化字符串 |
| `getExtension(path)` | `String` | "jpg" |
| `getFileName(path)` | `String` | "photo.jpg" |
| `getFileNameWithoutExtension(path)` | `String` | "photo" |
| `getMimeType(path)` | `String` | "image/jpeg" |
| `getMimeType(context, uri)` | `String` | Content URI→MIME |
| `getLastModified(path, format)` | `String` | 格式化修改时间 |
| `getLastModifiedMillis(path)` | `Long` | 修改时间戳 |
| `getParentPath(path)` | `String` | 父目录路径 |
| `getFileInfo(path)` | `Map<String,Any>` | 完整文件信息 |
| `getFileInfoAsync(path, onResult)` | `Future<*>` | 异步获取信息 |
| `getAvailableSpace(path)` | `Long` | 分区可用空间 |
| `getTotalSpace(path)` | `Long` | 分区总空间 |
| `getInternalAvailableSpace()` | `Long` | 内置存储可用 |
| `getExternalAvailableSpace()` | `Long` | 外部存储可用 |

### FileReadUtils (read / java.io)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `readText(path, charset=UTF-8)` | `String?` | 读取全部文本 |
| `readTextAsync(path, cs, onResult)` | `Future<*>` | 异步读取文本 |
| `readBytes(path)` | `ByteArray?` | 读取全部字节 |
| `readBytesAsync(path, onResult)` | `Future<*>` | 异步读取字节 |
| `readBytes(context, uri, maxBytes=10MB)` | `ByteArray?` | Content URI→字节 |
| `readBytes(inputStream, maxBytes=0)` | `ByteArray?` | InputStream→字节 |
| `readText(inputStream, cs, maxBytes=0)` | `String?` | InputStream→文本 |
| `readLines(path, charset=UTF-8)` | `List<String>?` | 读取所有行 |
| `readLinesAsync(path, cs, onResult)` | `Future<*>` | 异步读取所有行 |
| `readLinesStreaming(path, cs, onEachLine)` | `Int` | 流式逐行处理 |
| `readLinesStreamingAsync(path, cs, onEachLine, onComplete)` | `Future<*>` | 异步流式逐行 |
| `readChunks(path, chunkSize=64K, onChunk)` | `Long` | 分块读取 |
| `readChunksAsync(path, chunkSize, onChunk, onComplete)` | `Future<*>` | 异步分块读取 |
| `readHeadBytes(path, numBytes)` | `ByteArray?` | 读取文件头 |
| `readTailBytes(path, numBytes)` | `ByteArray?` | 读取文件尾 |
| `readTailLines(path, numLines=10)` | `List<String>?` | 读取最后N行 |

### FileWriteUtils (write / java.io)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `writeText(path, content, cs=UTF-8)` | `Boolean` | 覆盖写入文本 |
| `writeTextAsync(path, content, cs, onResult)` | `Future<*>` | 异步写入文本 |
| `appendText(path, content, cs=UTF-8)` | `Boolean` | 追加写入文本 |
| `appendTextAsync(path, content, cs, onResult)` | `Future<*>` | 异步追加 |
| `writeBytes(path, bytes)` | `Boolean` | 覆盖写入字节 |
| `writeBytesAsync(path, bytes, onResult)` | `Future<*>` | 异步写入字节 |
| `appendBytes(path, bytes)` | `Boolean` | 追加字节 |
| `writeFromStream(path, is, overwrite, onProgress)` | `Boolean` | 流→文件 |
| `writeFromStreamAsync(path, is, overwrite, onProgress, onResult)` | `Future<*>` | 异步流写入 |
| `writeToUri(context, uri, bytes, mimeType)` | `Boolean` | Content URI 写入 |
| `writeStreamToUri(context, uri, is, mimeType)` | `Boolean` | 流写入到 URI |
| `writeTextAtomic(path, content, cs)` | `Boolean` | 原子写入文本 |
| `writeTextAtomicAsync(path, content, cs, onResult)` | `Future<*>` | 异步原子写入文本 |
| `writeBytesAtomic(path, bytes)` | `Boolean` | 原子写入字节 |
| `writeBytesInChunks(path, data, chunkSize, overwrite, onProgress)` | `Boolean` | 分块写入 |
| `writeBytesInChunksAsync(path, data, chunkSize, overwrite, onProgress, onResult)` | `Future<*>` | 异步分块写入 |

### FileHashUtils (hash / java.io)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `hashFile(path, algorithm=SHA256)` | `String?` | 文件哈希 |
| `hashFileAsync(path, algo, onResult)` | `Future<*>` | 异步哈希 |
| `hashFileWithProgress(path, algo, onProgress)` | `String?` | 带进度哈希 |
| `hashFileWithProgressAsync(path, algo, onProgress, onResult)` | `Future<*>` | 异步带进度哈希 |
| `md5(path)` / `md5Async(path, onResult)` | `String?` / `Future<*>` | MD5 |
| `sha1(path)` / `sha1Async(path, onResult)` | `String?` / `Future<*>` | SHA-1 |
| `sha256(path)` / `sha256Async(path, onResult)` | `String?` / `Future<*>` | SHA-256 |
| `sha512(path)` / `sha512Async(path, onResult)` | `String?` / `Future<*>` | SHA-512 |
| `crc32(path)` / `crc32Async(path, onResult)` | `String?` / `Future<*>` | CRC32 |
| `hashBytes(data, algorithm)` | `String` | 字节哈希 |
| `hashString(text, algorithm)` | `String` | 字符串哈希 |
| `crc32(data: ByteArray)` | `String` | 字节 CRC32 |
| `verify(path, expectedHash, algo, ignoreCase)` | `Boolean` | 哈希验证 |
| `verifyAsync(path, expectedHash, algo, ic, onResult)` | `Future<*>` | 异步验证 |
| `compareFiles(path1, path2, algo)` / `...Async(...)` | `Boolean` / `Future<*>` | 文件比较 |

### OkioFileUtils (core / Okio)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `toPath(path)` / `fromPath(path)` | `Path` / `String` | Path 互转 |
| `exists(path)` / `existsAsync(...)` | `Boolean` / `Future<*>` | 存在判断 |
| `getSize(path)` / `getLastModifiedMillis(path)` | `Long` | 元数据 |
| `isRegularFile(path)` / `isDirectory(path)` | `Boolean` | 类型判断 |
| `copy(src, dest, ow)` / `copyAsync(...)` | `Boolean` / `Future<*>` | 复制 (Buffer优化) |
| `move(src, dest, ow)` / `moveAsync(...)` | `Boolean` / `Future<*>` | 移动 (优先atomicMove) |
| `delete(path)` / `deleteAsync(...)` | `Boolean` / `Future<*>` | 删除 |
| `createDirectory(path)` / `createDirectoryAsync(...)` | `Boolean` / `Future<*>` | 创建目录 |
| `list(path)` / `listRecursively(path)` | `List<Path>` | 列出/递归列出 |
| `listRecursivelyAsync(path, onResult)` | `Future<*>` | 异步递归列出 |
| `getAvailableSpace(path)` / `getTotalSpace(path)` | `Long` | 磁盘空间 |
| `withTimeout<T>(ms, block)` / `withTimeoutAsync(...)` | `T?` / `Future<*>` | 超时控制 |

### OkioReadUtils (read / Okio)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `readByteString(path)` / `...Async(...)` | `ByteString?` / `Future<*>` | 不可变字节 |
| `readUtf8(path, cs=UTF-8)` / `...Async(...)` | `String?` / `Future<*>` | 读取文本 |
| `readToBuffer(path)` / `...Async(...)` | `Buffer?` / `Future<*>` | 读取到Buffer |
| `readLines(path, cs)` / `...Async(...)` | `List<String>?` / `Future<*>` | 读取所有行 |
| `readLinesStreaming(path, onEachLine)` | `Int` | 流式逐行 |
| `readWithTimeout(path, ms)` / `...Async(...)` | `ByteString?` / `Future<*>` | 超时读取 |
| `readWithProgress(path, cs, onProgress)` / `...Async(...)` | `ByteArray?` / `Future<*>` | 带进度读取 |
| `readGzip(path)` / `...Async(...)` | `ByteArray?` / `Future<*>` | Gzip解压 |
| `readGzipAsText(path, cs)` | `String?` | Gzip解压为文本 |

### OkioWriteUtils (write / Okio)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `writeByteString(path, byteString)` / `...Async(...)` | `Boolean` / `Future<*>` | 写入ByteString |
| `writeUtf8(path, content, cs)` / `...Async(...)` | `Boolean` / `Future<*>` | 写入文本 |
| `appendUtf8(path, content, cs)` / `...Async(...)` | `Boolean` / `Future<*>` | 追加文本 |
| `writeFromBuffer(path, buffer)` | `Boolean` | Buffer→文件 |
| `writeFromStream(path, is, ow)` / `...Async(...)` | `Boolean` / `Future<*>` | 流→文件 |
| `writeWithTimeout(path, bytes, ms)` / `...Async(...)` | `Boolean` / `Future<*>` | 超时写入 |
| `writeWithProgress(path, data, cs, onP)` / `...Async(...)` | `Boolean` / `Future<*>` | 带进度写入 |
| `writeGzip(path, data)` / `...Async(...)` | `Boolean` / `Future<*>` | Gzip压缩 |
| `writeGzipText(path, content, cs)` | `Boolean` | Gzip压缩文本 |
| `writeAtomic(path, data)` / `...Async(...)` | `Boolean` / `Future<*>` | 原子写入 |
| `writeAtomicUtf8(path, content, cs)` | `Boolean` | 原子写入文本 |

### OkioHashUtils (hash / Okio)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `hashFile(path, digest)` / `...Async(...)` | `String?` / `Future<*>` | 计算哈希 |
| `hashFileWithProgress(path, digest, onP)` / `...Async(...)` | `String?` / `Future<*>` | 带进度哈希 |
| `hashWhileReading(path, digest, onRead)` / `...Async(...)` | `String?` / `Future<*>` | 边读边算 |
| `hashWhileWriting(path, data, digest)` / `...Async(...)` | `Pair?` / `Future<*>` | 边写边算 |
| `copyAndHash(src, dest, digest, ow, onP)` / `...Async(...)` | `Pair?` / `Future<*>` | 复制+哈希+进度 |
| `copyAndVerify(src, dest, digest, ow, onP)` | `Triple?` | 复制+自动校验 |
| `gzipAndHash(src, dest, digest, onP)` | `Pair?` | Gzip+哈希 |
| `hashByteString(data, digest)` | `String` | ByteString哈希 |
| `hashString(text, digest)` | `String` | 字符串哈希 |

### FileCleanupManager (cleanup)

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `builder()` | `Builder` | 创建规则构建器 |
| `register(app/config, onResult)` | `Unit` | Application 全局注册 |
| `register(app, config, callbacks)` | `Unit` | Application + 回调 |
| `register(activity, config, onResult)` | `Unit` | Activity 绑定注册 |
| `register(activity, config, callbacks)` | `Unit` | Activity + 回调 |
| `register(fragment, config, scope, onResult)` | `Unit` | Fragment 绑定注册 |
| `runNow(config, onResult)` | `List<Future<*>>` | 立即执行 |
| `runNow(activity, config, onResult)` | `List<Future<*>>` | Activity 范围立即执行 |
| `cancel(key)` | `Boolean` | 取消指定规则 |
| `cancelAll()` | `Unit` | 取消所有规则 |
| `release(key)` | `Boolean` | 释放规则 (取消+注销) |
| `release(config)` | `Int` | 释放配置中所有规则 |
| `release(activity)` | `Int` | 释放 Activity 关联规则 |
| `release(fragment, scope)` | `Int` | 释放 Fragment 关联规则 |
| `releaseAll()` | `Unit` | 释放全部 |

---

## 性能指南

| 场景 | 文件大小 | 推荐方法 | 理由 |
|------|---------|---------|------|
| JSON/XML 配置读取 | <1MB | `FileReadUtils.readText` | 简单快速 |
| 图片/音频读取 | 1-10MB | `FileReadUtils.readBytes` / `OkioReadUtils.readByteString` | ByteString 有内置编码 |
| 日志文件分析 | 10-100MB | `FileReadUtils.readLinesStreaming` | 边读边处理, 内存可控 |
| 超大文件处理 | >100MB | `FileReadUtils.readChunks` | 分块处理, 每块仅占 chunkSize 内存 |
| 文件复制 | 任意 | `OkioFileUtils.copy` | Okio Buffer 比 java.io 快 2-3x |
| 下载+哈希 | 大文件 | `OkioHashUtils.copyAndHash` | 一次 I/O 完成两操作 |
| 网络文件系统 | 任意 | Okio + `withTimeout` | 防止无限等待 |
| 重要数据写入 | 任意 | `writeTextAtomic` / `writeAtomic` | 防进程崩溃数据损坏 |

---

## 线程模型

| 操作类型 | 线程 | 说明 |
|----------|------|------|
| 同步方法 | 调用线程 | 直接阻塞, 不可在 UI 线程调用 |
| 异步方法 | `TaskExecutor.io` | I/O 线程池执行, 回调在 I/O 线程 |
| 切回 UI | `TaskExecutor.main` | 在异步回调中使用 |
| 清理延迟任务 | `TaskExecutor.ioDelayed` | FileCleanupManager 内部调度 |
| 清理权限回调 | `TaskExecutor.main` | Permission callbacks |

```kotlin
// 标准异步模式
FileReadUtils.readTextAsync(path) { content, error ->
    // 这里在 I/O 线程
    if (error == null && content != null) {
        TaskExecutor.main {
            // 这里在 UI 线程
            textView.text = content
        }
    }
}
```

---

## 许可证

```
MIT License — Copyright (c) 2026 ITG Team
```
