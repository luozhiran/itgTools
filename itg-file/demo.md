# ITG File 使用场景实例

本文档覆盖 `itg-file` 模块所有核心使用场景，每个场景同时展示 **java.io 模式** 和 **Okio 模式** 两种实现方式。

## 两种模式对比

| 维度 | java.io 模式 | Okio 模式 |
|---|---|---|
| **底层实现** | `java.io.File` / `FileInputStream` / `FileOutputStream` | `okio.Source` / `okio.Sink` / `Buffer` / `ByteString` |
| **入口类** | `FileUtils`, `FileReadUtils`, `FileWriteUtils`, `FileHashUtils`, `AssetUtils` | `OkioFileUtils`, `OkioReadUtils`, `OkioWriteUtils`, `OkioHashUtils`, `OkioAssetUtils` |
| **缓冲机制** | 标准 `byte[]` 缓冲区（8KB），每次 `read/write` 拷贝 | Okio `Buffer` 分段零拷贝，`writeAll` 直接传递 segment 引用 |
| **超时控制** | 不支持（需手动 `Future.get(timeout)`） | 内置 `source.timeout()` / `sink.timeout()`，I/O 层面精确超时 |
| **进度追踪** | 手动循环 + 逐块回调 | `ForwardingSource` / `ForwardingSink` 拦截，无侵入进度 |
| **不可变字节** | `byte[]`（可变，需手动拷贝防护） | `ByteString`（不可变，安全共享，内置 hex/base64/md5） |
| **Gzip 支持** | 需额外使用 `GZIPInputStream` | 内置 `GzipSource` / `GzipSink`，流式压缩/解压 |
| **流式哈希** | 两次遍历（先读再算哈希） | 一次遍历（`HashingSource`/`HashingSink` 边读边算） |
| **组合操作** | 需手动组合（先复制，再单独算哈希） | 内置 `copyAndHash`、`gzipAndHash`、`copyAndVerify` 组合 |
| **性能特点** | 小文件足够，无额外依赖 | 大文件 2-3x 吞吐提升，segment 池减少 GC |
| **适用场景** | 简单读写、对依赖敏感、小文件 | 大文件、频繁 I/O、需要超时/流式哈希/进度追踪 |
| **依赖** | 仅 JDK | `com.squareup.okio:okio:2.9.0` |

---

## 1. 文件基础操作

### 1.1 检查文件是否存在

```kotlin
// === java.io 模式 ===
val exists = FileUtils.exists("/sdcard/demo.txt")
val isFile = FileUtils.isFile("/sdcard/demo.txt")
val isDir  = FileUtils.isDirectory("/sdcard/demo.txt")
val empty  = FileUtils.isEmpty("/sdcard/demo.txt")

// 异步
FileUtils.existsAsync("/sdcard/demo.txt") { exists -> println("exists=$exists") }

// === Okio 模式 ===
val exists2 = OkioFileUtils.exists("/sdcard/demo.txt")
val isFile2 = OkioFileUtils.isRegularFile("/sdcard/demo.txt")
val isDir2  = OkioFileUtils.isDirectory("/sdcard/demo.txt")

// 异步
OkioFileUtils.existsAsync("/sdcard/demo.txt") { exists -> println("exists=$exists") }
```

### 1.2 创建文件/目录

```kotlin
// === java.io 模式 ===
FileUtils.createFile("/sdcard/MyApp/config.json")       // 自动创建父目录
FileUtils.createDirectory("/sdcard/MyApp/cache")         // 递归创建
val temp = FileUtils.createTempFile(prefix = "itg_", suffix = ".tmp")

// 异步
FileUtils.createFileAsync("/sdcard/MyApp/data.txt") { success -> }
FileUtils.createDirectoryAsync("/sdcard/MyApp/out") { success -> }

// === Okio 模式 ===
OkioFileUtils.createDirectory("/sdcard/MyApp/cache")     // Okio 创建目录
// 注：OkioFileUtils 不单独提供 createFile，可通过 OkioWriteUtils 写入时自动创建
```

### 1.3 删除文件/目录

```kotlin
// === java.io 模式 ===
FileUtils.delete("/sdcard/MyApp/cache/")                 // 递归删除目录
FileUtils.clearDirectory("/sdcard/MyApp/temp/")          // 清空目录但保留目录自身
FileUtils.deleteAsync("/sdcard/old_data.bin") { success -> }

// === Okio 模式 ===
OkioFileUtils.delete("/sdcard/MyApp/cache/")             // 内部调用 File.deleteRecursively()
OkioFileUtils.deleteAsync("/sdcard/old_data.bin") { success -> }
```

### 1.4 重命名

```kotlin
// === java.io 模式 ===
val newPath = FileUtils.rename("/sdcard/old_name.txt", "new_name.txt")
FileUtils.renameAsync("/sdcard/old.txt", "new.txt") { newPath -> }
```

### 1.5 复制文件/目录

```kotlin
// === java.io 模式 ===
// 简单复制
FileUtils.copy("/sdcard/src.jpg", "/sdcard/backup/dst.jpg")
FileUtils.copy("/sdcard/src.jpg", "/sdcard/dst.jpg", overwrite = false)

// 带进度复制（适合大文件）
FileUtils.copyWithProgress("/sdcard/large.zip", "/sdcard/backup/large.zip",
    onProgress = { copied, total ->
        val percent = copied * 100 / total
        updateProgressBar(percent.toInt())
    })

// 复制整个目录
FileUtils.copyDirectory("/sdcard/MyApp/data", "/sdcard/backup/data")

// 异步
FileUtils.copyAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }

// === Okio 模式 ===
// Okio Buffer 零拷贝复制（比 java.io 快 2-3x）
OkioFileUtils.copy("/sdcard/large.zip", "/sdcard/backup/large.zip")
OkioFileUtils.copyAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }
```

### 1.6 移动文件/目录

```kotlin
// === java.io 模式（优先 rename，跨分区自动退化为 copy+delete） ===
FileUtils.move("/sdcard/temp/file.txt", "/sdcard/dest/file.txt")
FileUtils.moveAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }

// === Okio 模式（同样先 rename 再 copy+delete） ===
OkioFileUtils.move("/sdcard/temp/file.txt", "/sdcard/dest/file.txt")
OkioFileUtils.moveAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }
```

### 1.7 列出文件

```kotlin
// === java.io 模式 ===
// 列出直接子项
val files = FileUtils.listFiles("/sdcard/DCIM")

// 按扩展名过滤
val jpgs = FileUtils.listFilesByExtension("/sdcard/DCIM", "jpg")

// 自定义过滤
val largeFiles = FileUtils.listFiles("/sdcard/") { file ->
    file.isFile && file.length() > 1024 * 1024
}

// 递归列出所有文件
val all = FileUtils.listFilesRecursive("/sdcard/MyApp/")

// === Okio 模式 ===
// 列出直接子项
val list = OkioFileUtils.list("/sdcard/DCIM")

// 递归列出
val all2 = OkioFileUtils.listRecursively("/sdcard/MyApp/")
OkioFileUtils.listRecursivelyAsync("/sdcard/MyApp/") { paths -> }
```

### 1.8 获取文件信息

```kotlin
// === java.io 模式 ===
val size = FileUtils.getSize("/sdcard/photo.jpg")              // 字节数
val formatted = FileUtils.getSizeFormatted("/sdcard/photo.jpg") // "2.50 MB"
val ext = FileUtils.getExtension("/sdcard/photo.jpg")           // "jpg"
val name = FileUtils.getFileName("/sdcard/photo.jpg")           // "photo.jpg"
val nameOnly = FileUtils.getFileNameWithoutExtension("/sdcard/photo.jpg") // "photo"
val mime = FileUtils.getMimeType("/sdcard/photo.jpg")           // "image/jpeg"
val modified = FileUtils.getLastModified("/sdcard/photo.jpg")   // "2026-07-10 15:30:00"

// 获取完整信息 Map
val info: Map<String, Any> = FileUtils.getFileInfo("/sdcard/photo.jpg")

// === Okio 模式 ===
val size2 = OkioFileUtils.getSize("/sdcard/photo.jpg")
val modified2 = OkioFileUtils.getLastModifiedMillis("/sdcard/photo.jpg")
```

### 1.9 存储空间查询

```kotlin
// === java.io 模式 ===
val available = FileUtils.getAvailableSpace("/sdcard/")
val total = FileUtils.getTotalSpace("/sdcard/")
val internalFree = FileUtils.getInternalAvailableSpace()
val externalFree = FileUtils.getExternalAvailableSpace()

// === Okio 模式 ===
val available2 = OkioFileUtils.getAvailableSpace("/sdcard/")
val total2 = OkioFileUtils.getTotalSpace("/sdcard/")
```

---

## 2. 文件读取

### 2.1 读取为字符串

```kotlin
// === java.io 模式 ===
val text = FileReadUtils.readText("/sdcard/data.json")
val gbkText = FileReadUtils.readText("/sdcard/legacy.txt", Charset.forName("GBK"))

// 异步
FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
    if (error == null) parseJson(content!!)
}

// === Okio 模式 ===
val text2 = OkioReadUtils.readUtf8("/sdcard/data.json")
val gbkText2 = OkioReadUtils.readUtf8("/sdcard/legacy.txt", Charset.forName("GBK"))

// 异步
OkioReadUtils.readUtf8Async("/sdcard/data.json") { content, error -> }
```

### 2.2 读取为字节数组

```kotlin
// === java.io 模式 ===
val bytes = FileReadUtils.readBytes("/sdcard/photo.jpg")
FileReadUtils.readBytesAsync("/sdcard/photo.jpg") { data, error -> }

// === Okio 模式（推荐用 ByteString，不可变更安全） ===
val byteStr = OkioReadUtils.readByteString("/sdcard/photo.jpg")
println("Hex: ${byteStr?.hex()}")
println("Base64: ${byteStr?.base64()}")
println("MD5: ${byteStr?.md5()?.hex()}")

// 读取到可变 Buffer
val buffer = OkioReadUtils.readToBuffer("/sdcard/data.bin")
buffer?.writeUtf8("-- APPENDED --")  // 可修改
```

### 2.3 按行读取

```kotlin
// === java.io 模式 ===
val lines = FileReadUtils.readLines("/sdcard/log.txt")
lines?.forEachIndexed { i, line -> println("$i: $line") }

// === Okio 模式 ===
val lines2 = OkioReadUtils.readLines("/sdcard/log.txt")
```

### 2.4 流式逐行读取（大文件友好）

```kotlin
// === java.io 模式 ===
// readLinesStreaming 边读边处理，不会一次性加载全部到内存
val count = FileReadUtils.readLinesStreaming("/sdcard/large.csv",
    onEachLine = { line, index ->
        processLine(line)
        index < 1000  // 返回 false 可提前终止
    })

// === Okio 模式 ===
val count2 = OkioReadUtils.readLinesStreaming("/sdcard/large.csv",
    onEachLine = { line, index ->
        processLine(line)
        true  // 继续读取
    })
```

### 2.5 分块读取（超大文件）

```kotlin
// === java.io 模式 ===
val totalRead = FileReadUtils.readChunks("/sdcard/huge.bin",
    chunkSize = 1024 * 1024,  // 1MB 每块
    onChunk = { chunk, index, total ->
        uploadChunk(chunk, index)
        val progress = (index + 1) * 100 / total
        updateProgress(progress)
        true
    })
```

### 2.6 读取文件头部/尾部

```kotlin
// === java.io 模式 ===
// 读取头部 4 字节（如 Magic Number）
val header = FileReadUtils.readHeadBytes("/sdcard/file.bin", 4)

// 读取尾部 1024 字节
val tail = FileReadUtils.readTailBytes("/sdcard/log.txt", 1024)

// 读取文件最后 10 行（日志场景）
val lastLines = FileReadUtils.readTailLines("/sdcard/app.log", 10)
```

### 2.7 从 URI / InputStream 读取

```kotlin
// === java.io 模式 ===
// 从 Content URI 读取
val uriBytes = FileReadUtils.readBytes(context, uri)

// 从 InputStream 读取
val streamBytes = FileReadUtils.readBytes(inputStream)
val streamText = FileReadUtils.readText(inputStream, Charsets.UTF_8)
```

### 2.8 超时读取（Okio 独有）

```kotlin
// === Okio 模式（独有特性） ===
// 读取网络文件系统上的大文件，30 秒超时
val data = OkioReadUtils.readWithTimeout("/mnt/nfs/huge.log", 30_000)

// 异步超时读取
OkioReadUtils.readWithTimeoutAsync("/mnt/nfs/file.bin", 30_000) { data, error -> }
```

### 2.9 带进度读取（Okio 推荐）

```kotlin
// === Okio 模式（ForwardingSource 无侵入进度追踪） ===
val data = OkioReadUtils.readWithProgress("/sdcard/large.zip",
    onProgress = { bytesRead, total ->
        val percent = bytesRead * 100 / total
        updateProgressBar(percent.toInt())
    })
```

### 2.10 Gzip 解压读取（Okio 独有）

```kotlin
// === Okio 模式（内置 GzipSource） ===
// 直接读取并解压 .gz 文件
val decompressed = OkioReadUtils.readGzip("/sdcard/data.json.gz")
val text = OkioReadUtils.readGzipAsText("/sdcard/data.json.gz")

OkioReadUtils.readGzipAsync("/sdcard/data.gz") { bytes, error -> }
```

---

## 3. 文件写入

### 3.1 写入字符串

```kotlin
// === java.io 模式 ===
// 覆盖写入（默认原子写入：先写临时文件再重命名）
FileWriteUtils.writeText("/sdcard/config.json", """{"name": "test"}""")

// 指定编码
FileWriteUtils.writeText("/sdcard/config.json", content, Charset.forName("GBK"))

// 异步
FileWriteUtils.writeTextAsync("/sdcard/data.txt", "hello") { success -> }

// === Okio 模式 ===
OkioWriteUtils.writeUtf8("/sdcard/config.json", """{"name": "test"}""")
OkioWriteUtils.writeUtf8Async("/sdcard/data.txt", "hello") { success -> }
```

### 3.2 追加字符串

```kotlin
// === java.io 模式 ===
FileWriteUtils.appendText("/sdcard/log.txt", "[INFO] App started\n")
FileWriteUtils.appendTextAsync("/sdcard/log.txt", "new line\n") { success -> }

// === Okio 模式 ===
OkioWriteUtils.appendUtf8("/sdcard/log.txt", "[INFO] App started\n")
OkioWriteUtils.appendUtf8Async("/sdcard/log.txt", "new line\n") { success -> }
```

### 3.3 写入字节数组

```kotlin
// === java.io 模式 ===
FileWriteUtils.writeBytes("/sdcard/photo.jpg", imageBytes)

// === Okio 模式 ===
// 直接写入 ByteString
val byteStr = ByteString.encodeUtf8("Hello Okio")
OkioWriteUtils.writeByteString("/sdcard/hello.txt", byteStr)

// 从 Buffer 写入
val buffer = Buffer().apply { writeUtf8("data") }
OkioWriteUtils.writeFromBuffer("/sdcard/buffer_out.bin", buffer)

// 普通字节写入
OkioWriteUtils.writeAtomic("/sdcard/photo.jpg", imageBytes)
```

### 3.4 从 InputStream 写入

```kotlin
// === java.io 模式 ===
FileWriteUtils.writeFromStream("/sdcard/download.bin", inputStream,
    onProgress = { written, total ->
        updateProgressBar((written * 100 / total).toInt())
    })

// === Okio 模式 ===
OkioWriteUtils.writeFromStream("/sdcard/download.bin", inputStream)
```

### 3.5 写入到 Content URI

```kotlin
// === java.io 模式（Android 10+ MediaStore / SAF） ===
FileWriteUtils.writeToUri(context, uri, imageBytes, "image/jpeg")

// 从 InputStream 写入 URI
FileWriteUtils.writeStreamToUri(context, uri, inputStream, "application/pdf")
```

### 3.6 原子写入

```kotlin
// === java.io 模式 ===
// 写入过程中崩溃不会损坏目标文件
FileWriteUtils.writeTextAtomic("/data/config.json", configJson)
FileWriteUtils.writeBytesAtomic("/data/data.bin", importantBytes)

// === Okio 模式 ===
OkioWriteUtils.writeAtomic("/data/config.json", configJson.toByteArray())
OkioWriteUtils.writeAtomicUtf8("/data/config.json", configJson)
```

### 3.7 分块写入（带进度）

```kotlin
// === java.io 模式 ===
FileWriteUtils.writeBytesInChunks("/sdcard/big_file.bin", hugeData,
    chunkSize = 64 * 1024,
    onProgress = { written, total ->
        updateProgressBar((written * 100 / total).toInt())
    })

// === Okio 模式（ForwardingSink 无侵入进度） ===
OkioWriteUtils.writeWithProgress("/sdcard/big_file.bin", data,
    onProgress = { written, total ->
        updateProgressBar((written * 100 / total).toInt())
    })
```

### 3.8 超时写入（Okio 独有）

```kotlin
// === Okio 模式（sink.timeout 层面精确超时） ===
val ok = OkioWriteUtils.writeWithTimeout("/sdcard/file.bin", bytes, 30_000)
```

### 3.9 Gzip 压缩写入（Okio 独有）

```kotlin
// === Okio 模式（内置 GzipSink 流式压缩） ===
// 压缩字节写入
OkioWriteUtils.writeGzip("/sdcard/data.json.gz", jsonBytes)

// 压缩字符串写入
OkioWriteUtils.writeGzipText("/sdcard/data.json.gz", jsonString)

// 异步
OkioWriteUtils.writeGzipAsync("/sdcard/data.gz", bytes) { success -> }
```

---

## 4. 文件哈希与校验

### 4.1 计算文件哈希

```kotlin
// === java.io 模式 ===
val md5    = FileHashUtils.md5("/sdcard/photo.jpg")
val sha1   = FileHashUtils.sha1("/sdcard/photo.jpg")
val sha256 = FileHashUtils.sha256("/sdcard/photo.jpg")
val sha512 = FileHashUtils.sha512("/sdcard/photo.jpg")

// 通用方法
val hash = FileHashUtils.hashFile("/sdcard/photo.jpg", FileHashUtils.Algorithm.SHA256)

// CRC32 快速校验
val crc = FileHashUtils.crc32("/sdcard/photo.jpg")

// 异步
FileHashUtils.sha256Async("/sdcard/photo.jpg") { hash, error ->
    if (hash != null) println("SHA256: $hash")
}

// === Okio 模式（HashingSource 流式计算，一次遍历） ===
val digest = MessageDigest.getInstance("SHA-256")
val hash2 = OkioHashUtils.hashFile("/sdcard/photo.jpg", digest)
```

### 4.2 大文件哈希（带进度）

```kotlin
// === java.io 模式 ===
val sha256 = FileHashUtils.hashFileWithProgress("/sdcard/large.iso",
    FileHashUtils.Algorithm.SHA256,
    onProgress = { processed, total ->
        updateProgressBar((processed * 100 / total).toInt())
    })

// === Okio 模式（ForwardingSource 边读边算 + 进度） ===
val digest = MessageDigest.getInstance("SHA-256")
val hash = OkioHashUtils.hashFileWithProgress("/sdcard/large.iso", digest,
    onProgress = { processed, total ->
        updateProgressBar((processed * 100 / total).toInt())
    })
```

### 4.3 校验文件完整性

```kotlin
// === java.io 模式 ===
val valid = FileHashUtils.verify("/sdcard/download.apk",
    "a1b2c3d4e5f6...", FileHashUtils.Algorithm.SHA256)
if (!valid) showError("文件已损坏")

// === Okio 模式 ===
// 通过 hashFile 配合自行比对即可
```

### 4.4 比较两个文件

```kotlin
// === java.io 模式 ===
val same = FileHashUtils.compareFiles("/sdcard/file1.bin", "/sdcard/file2.bin")
FileHashUtils.compareFilesAsync("/sdcard/a.bin", "/sdcard/b.bin") { same -> }
```

### 4.5 字节/字符串哈希

```kotlin
// === java.io 模式 ===
val bytesHash = FileHashUtils.hashBytes(byteArray, FileHashUtils.Algorithm.MD5)
val strHash = FileHashUtils.hashString("hello world")
val strCrc32 = FileHashUtils.crc32("hello".toByteArray())

// === Okio 模式 ===
val digest = MessageDigest.getInstance("SHA-256")
val bytesHash2 = OkioHashUtils.hashByteString(ByteString.of(*byteArray), digest)
val strHash2 = OkioHashUtils.hashString("hello world", digest)
```

### 4.6 复制并同时计算哈希（Okio 独有，一次 I/O）

```kotlin
// === Okio 模式（独有组合操作：复制+进度+哈希一步到位） ===
val (hash, ok) = OkioHashUtils.copyAndHash(
    "/sdcard/large.iso", "/sdcard/backup/large.iso",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { copied, total ->
        updateProgressBar((copied * 100 / total).toInt())
    })
if (ok) println("复制完成，SHA256: $hash")
```

### 4.7 复制并验证（Okio 独有）

```kotlin
// === Okio 模式（复制后自动比对源和目标哈希） ===
val (hash, success, verified) = OkioHashUtils.copyAndVerify(
    "/sdcard/important.db", "/sdcard/backup/important.db",
    MessageDigest.getInstance("SHA-256"))
if (verified) println("复制正确，哈希一致: $hash")
```

### 4.8 Gzip 压缩并同时计算哈希（Okio 独有）

```kotlin
// === Okio 模式（压缩+哈希一步完成） ===
val (hash, ok) = OkioHashUtils.gzipAndHash(
    "/sdcard/large.log", "/sdcard/large.log.gz",
    MessageDigest.getInstance("SHA-256"),
    onProgress = { processed, total ->
        updateProgressBar((processed * 100 / total).toInt())
    })
```

### 4.9 写入时哈希（Okio 独有）

```kotlin
// === Okio 模式（HashingSink 边写边算哈希） ===
val (hash, ok) = OkioHashUtils.hashWhileWriting("/sdcard/data.bin",
    imageBytes, MessageDigest.getInstance("MD5"))
if (ok) println("写入完成，MD5: $hash")
```

---

## 5. Assets / Raw 资源读取

### 5.1 读取 Assets 文件

```kotlin
// === java.io 模式 ===
// 检查是否存在
val exists = AssetUtils.assetExists(context, "config.json")

// 读取为字符串
val json = AssetUtils.readAssetText(context, "data/config.json")

// 读取为字节数组
val bytes = AssetUtils.readAssetBytes(context, "images/logo.png")

// 按行读取
val lines = AssetUtils.readAssetLines(context, "data/words.txt")

// 流式逐行（大文件）
AssetUtils.readAssetLinesStreaming(context, "data/large.csv",
    onEachLine = { line, index -> processLine(line); true })

// === Okio 模式 ===
// ByteString（不可变，支持 hex/base64/md5）
val byteStr = OkioAssetUtils.readAssetByteString(context, "images/photo.jpg")
println("Base64: ${byteStr?.base64()}")

// 字符串
val text = OkioAssetUtils.readAssetUtf8(context, "data/config.json")

// 可变 Buffer
val buffer = OkioAssetUtils.readAssetToBuffer(context, "data/template.txt")

// 流式逐行
OkioAssetUtils.readAssetLinesStreaming(context, "data/large.csv",
    onEachLine = { line, index -> processLine(line); true })
```

### 5.2 复制 Assets 到文件系统

```kotlin
// === java.io 模式 ===
// 复制单个文件
AssetUtils.copyAssetToFile(context, "templates/app.db", "/sdcard/MyApp/app.db")

// 带进度
AssetUtils.copyAssetToFileWithProgress(context, "bundled_data.bin",
    "/sdcard/data.bin",
    onProgress = { copied, total ->
        updateProgressBar((copied * 100 / total).toInt())
    })

// 复制整个目录
val count = AssetUtils.copyAssetDirToFile(context,
    "templates", "/sdcard/MyApp/templates")

// === Okio 模式（BufferedSink 高效写入） ===
OkioAssetUtils.copyAssetToFile(context, "models/model.tflite",
    "/sdcard/MyApp/model.tflite")

// 带进度（ForwardingSource 追踪）
OkioAssetUtils.copyAssetToFileWithProgress(context, "huge_data.bin",
    "/sdcard/data.bin",
    onProgress = { copied, total ->
        updateProgressBar((copied * 100 / total).toInt())
    })
```

### 5.3 读取 Raw 资源

```kotlin
// === java.io 模式 ===
val text = AssetUtils.readRawText(context, R.raw.license)
val bytes = AssetUtils.readRawBytes(context, R.raw.sound_effect)
val size = AssetUtils.getRawSize(context, R.raw.large_asset)

// === Okio 模式 ===
val byteStr = OkioAssetUtils.readRawByteString(context, R.raw.license)
val text2 = OkioAssetUtils.readRawUtf8(context, R.raw.license)
val buffer = OkioAssetUtils.readRawToBuffer(context, R.raw.data)
```

### 5.4 复制 Raw 资源到文件系统

```kotlin
// === java.io 模式 ===
AssetUtils.copyRawToFile(context, R.raw.default_avatar,
    "/sdcard/MyApp/default_avatar.png")

// === Okio 模式 ===
OkioAssetUtils.copyRawToFile(context, R.raw.default_avatar,
    "/sdcard/MyApp/default_avatar.png")

// 带进度
OkioAssetUtils.copyRawToFileWithProgress(context, R.raw.huge_data,
    "/sdcard/data.bin",
    onProgress = { copied, total -> updateProgressBar((copied * 100 / total).toInt()) })
```

### 5.5 Assets 超时读取与 Gzip 解压（Okio 独有）

```kotlin
// === Okio 模式 ===
// 超时读取（30 秒）
val data = OkioAssetUtils.readAssetWithTimeout(context, "large_data.bin", 30_000)

// Gzip 解压读取（APK 中存 .gz 节省体积）
val json = OkioAssetUtils.readAssetGzip(context, "data/data.json.gz")
val text = OkioAssetUtils.readAssetGzipAsText(context, "data/data.json.gz")

// 带进度的分块读取
val bytes = OkioAssetUtils.readAssetWithProgress(context, "huge_model.bin",
    onProgress = { read, total -> updateProgressBar((read * 100 / total).toInt()) })
```

---

## 6. 文件清理（生命周期驱动）

### 6.1 应用启动时清理

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppStart("cache_startup", cacheDir.absolutePath)       // 清空缓存目录
    .deleteOnAppStart("old_config", File(filesDir, "old.json").absolutePath) // 删除旧配置
    .build()

FileCleanupManager.register(application, config) { result ->
    Log.d("Cleanup", "${result.key}: ${result.message}")
}
```

### 6.2 应用进入后台时清理

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppBackground("preview_cache",
        File(cacheDir, "preview").absolutePath)
    .build()

FileCleanupManager.register(application, config)
```

### 6.3 延迟清理

```kotlin
val config = FileCleanupManager.builder()
    // 30 秒后删除导出文件
    .deleteAfterDelay("temp_export",
        File(cacheDir, "export.zip").absolutePath, 30_000L)
    // 一次性：执行完自动释放
    .clearAfterDelay("temp_files",
        File(cacheDir, "tmp").absolutePath, 60_000L,
        scheduleMode = CleanupScheduleMode.ONE_SHOT)
    // 循环：执行完后重新计时
    .clearAfterDelay("recurring_clean",
        File(cacheDir, "recurring").absolutePath, 3600_000L,
        scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION)
    .build()

FileCleanupManager.register(config) { result -> }
```

### 6.4 按天数清理（跨重启持久化）

```kotlin
val config = FileCleanupManager.builder()
    // 7 天后清理日志，跨重启计时
    .clearAfterDays("logs_7d",
        File(filesDir, "logs").absolutePath,
        days = 7,
        persistAcrossRestarts = true)
    // 一次性清理
    .deleteAfterDays("cache_3d",
        File(cacheDir, "expired").absolutePath,
        days = 3,
        persistAcrossRestarts = true,
        scheduleMode = CleanupScheduleMode.ONE_SHOT)
    .build()

FileCleanupManager.register(application, config)
```

### 6.5 指定时间点清理

```kotlin
val targetTime = System.currentTimeMillis() + 60_000L  // 1分钟后
val config = FileCleanupManager.builder()
    .deleteAtTime("one_minute", File(cacheDir, "temp.tmp").absolutePath, targetTime)
    .clearAtTime("midnight", File(cacheDir, "daily").absolutePath, midnightMillis)
    .build()
```

### 6.6 立即执行（不依赖触发条件）

```kotlin
// 无视触发条件，立即在 I/O 线程执行
val futures = FileCleanupManager.runNow(config) { result ->
    Log.d("Cleanup", "success=${result.success}, deleted=${result.deletedEntries}")
}

// 绑定 Activity 生命周期（Activity 销毁后自动取消）
FileCleanupManager.runNow(activity, config) { result -> }

// 绑定 Fragment 生命周期
FileCleanupManager.runNow(fragment, config,
    lifecycleScope = CleanupLifecycleScope.FRAGMENT) { result -> }
```

### 6.7 权限处理

```kotlin
val callbacks = CleanupCallbacks(
    onResult = { result ->
        if (!result.success) Log.e("Cleanup", result.message, result.error)
    },
    onPermissionRequired = { request ->
        // 宿主应用负责权限申请
        if (request.suggestedPermissions.isNotEmpty()) {
            permissionLauncher.launch(request.suggestedPermissions.toTypedArray())
        }
        // 授权后重试: request.retry()
        // 放弃授权:   request.cancel()
    }
)

FileCleanupManager.register(application, config, callbacks)
```

### 6.8 与 Activity / Fragment 生命周期绑定

```kotlin
// 绑定 Activity（Activity 销毁时自动取消并释放资源）
FileCleanupManager.register(activity, config) { result -> }

// 绑定 Fragment
FileCleanupManager.register(fragment, config,
    lifecycleScope = CleanupLifecycleScope.FRAGMENT) { result -> }

// 绑定 Fragment 的宿主 Activity 生命周期
FileCleanupManager.register(fragment, config,
    lifecycleScope = CleanupLifecycleScope.ACTIVITY) { result -> }
```

### 6.9 取消与释放

```kotlin
// 取消单条未执行任务
FileCleanupManager.cancel("cache_startup")

// 取消全部
FileCleanupManager.cancelAll()

// 释放单条规则（取消 + 清持久化 + 释放回调）
FileCleanupManager.release("cache_startup")

// 释放整组配置
FileCleanupManager.release(config)

// 释放绑定到 Activity 的全部任务
FileCleanupManager.release(activity)

// 释放绑定到 Fragment 的全部任务
FileCleanupManager.release(fragment, CleanupLifecycleScope.FRAGMENT)
```

---

## 模式选择速查表

| 使用场景 | 推荐模式 | 原因 |
|---|---|---|
| 读写小型配置文件（< 1MB） | java.io | 简单直接，无额外依赖 |
| 复制/移动大文件（> 10MB） | Okio | Buffer 零拷贝，2-3x 吞吐提升 |
| 需要超时控制的 I/O | Okio | 内置 Timeout 机制，I/O 层面精确控制 |
| 需要进度回调的大文件操作 | Okio | ForwardingSource/Sink 无侵入追踪 |
| 边读边写边计算哈希 | Okio | HashingSource/Sink 一次遍历完成 |
| Gzip 压缩/解压 | Okio | 内置 GzipSource/GzipSink，流式处理 |
| 复制后自动验证哈希 | Okio | copyAndVerify 组合操作 |
| 不可变字节共享（多线程安全） | Okio | ByteString 不可变，避免拷贝防护 |
| 操作 APK 内置 Assets/Raw 资源 | 任选 | 小文件用 java.io，大文件用 Okio |
| 生命周期驱动的自动清理 | FileCleanupManager | 独立的清理子系统，两种模式通用 |
| 对 APK 体积敏感、不想引入 Okio | java.io | 零额外依赖 |

---

## 同步 vs 异步

两种模式的所有耗时方法均提供 `Async` 后缀的异步版本，通过 `TaskExecutor` 在 I/O 线程池执行：

```kotlin
// 同步（阻塞当前线程，在 I/O 线程中调用）
val text = FileReadUtils.readText("/sdcard/data.json")

// 异步（通过 TaskExecutor.io 调度，回调在 I/O 线程）
FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
    if (error == null && content != null) {
        // 切回主线程更新 UI
        TaskExecutor.main { textView.text = content }
    }
}
```

异步方法统一通过 `itg-thread-pools` 模块的 `TaskExecutor` 执行，回调默认在 I/O 线程，UI 更新需手动切换到主线程。
