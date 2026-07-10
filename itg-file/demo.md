# ITG File 使用场景实例

本文档覆盖 `itg-file` 模块所有核心使用场景，每个场景同时展示 **java.io 模式** 和 **Okio 模式** 两种实现方式，并详细说明大文件处理策略、错误处理、性能考量和适用边界。

---

## 两种模式对比

| 维度 | java.io 模式 | Okio 模式 |
|---|---|---|
| **底层实现** | `java.io.File` / `FileInputStream` / `FileOutputStream` | `okio.Source` / `okio.Sink` / `Buffer` / `ByteString` |
| **入口类** | `FileUtils`, `FileReadUtils`, `FileWriteUtils`, `FileHashUtils`, `AssetUtils` | `OkioFileUtils`, `OkioReadUtils`, `OkioWriteUtils`, `OkioHashUtils`, `OkioAssetUtils` |
| **缓冲机制** | 标准 `byte[]` 缓冲区（8KB），每次 `read/write` 拷贝 | Okio `Buffer` 分段零拷贝（segment 池复用），`writeAll` 直接传递 segment 引用 |
| **超时控制** | 不支持 I/O 层面超时（仅可 `Future.get(timeout)` 取消等待） | 内置 `source.timeout()` / `sink.timeout()`，在每次 `read/write` 系统调用层面精确超时 |
| **进度追踪** | 手动循环 + 逐块回调（侵入式，和业务逻辑混在一起） | `ForwardingSource` / `ForwardingSink` 装饰器模式，拦截 I/O 操作无侵入上报进度 |
| **不可变字节** | `byte[]`（可变，多线程共享需手动 `copyOf` 防护） | `ByteString`（不可变，线程安全，内置 `hex()`/`base64()`/`md5()`/`sha256()` 等便捷方法） |
| **Gzip 支持** | 需额外使用 `GZIPInputStream`/`GZIPOutputStream` 包装流 | 内置 `GzipSource` / `GzipSink`，流式压缩/解压，可与哈希、进度等组合 |
| **流式哈希** | 两次遍历：先读数据，再单独算哈希 | 一次遍历：`HashingSource`/`HashingSink` 边读/写边更新 MessageDigest |
| **组合操作** | 需手动编排：先复制 → 再单独打开文件算哈希 → 再比对 | 内置 `copyAndHash`、`gzipAndHash`、`copyAndVerify` 等一步到位的组合操作 |
| **性能特点** | 小文件足够，无额外依赖；大文件受限于 Buffer 拷贝开销 | 大文件 2-3x 吞吐提升，segment 池减少 GC 压力，适合频繁 I/O |
| **适用场景** | 简单读写、小文件（< 1MB）、对 APK 体积敏感 | 大文件（> 10MB）、频繁 I/O、需要超时/流式哈希/进度追踪 |
| **依赖** | 仅 JDK | `com.squareup.okio:okio:2.9.0` |

### 核心差异总结

**1. 零拷贝 Segment 机制**：Okio 的 `Buffer` 由双向链表连接的 segment 组成。`writeAll` 操作将源 `Buffer` 的 segment 直接"借给"目标 `Buffer`，不复制数据本身。对于 GB 级文件复制，这意味着 CPU 时间主要用于文件系统 I/O，而非内存拷贝。

**2. 超时的本质区别**：java.io 的 `InputStream.read()` 是无限阻塞的——如果读取网络文件系统上的文件时网络断开，线程将永久挂起。Okio 的 `source.timeout()` 在每次 `read()` 系统调用时设置 `SO_TIMEOUT`，超时会抛出 `InterruptedIOException`，线程得以恢复。

**3. 流式哈希的意义**：传统方式复制 4GB 文件并计算 SHA-256 需要读取文件两次（共 8GB I/O）。Okio 的 `copyAndHash` 只需一次读取，`HashingSource` 装饰器在数据流经时同步更新 `MessageDigest`——时间几乎减半。

---

## 1. 文件基础操作

### 1.1 检查文件是否存在

**场景说明**：在进行任何文件操作前，应先判断目标路径是否存在以及其类型（文件/目录），避免对不存在的路径操作导致异常。

```kotlin
// === java.io 模式 ===
// 同步检查——适合在 I/O 线程直接调用
val exists = FileUtils.exists("/sdcard/demo.txt")          // 文件或目录是否存在
val isFile = FileUtils.isFile("/sdcard/demo.txt")          // 是否为普通文件
val isDir  = FileUtils.isDirectory("/sdcard/demo.txt")      // 是否为目录
val empty  = FileUtils.isEmpty("/sdcard/demo.txt")          // 是否为空（文件0字节 或 目录无子项）

// 异步检查——回调在 I/O 线程执行，更新 UI 需切主线程
FileUtils.existsAsync("/sdcard/demo.txt") { exists ->
    TaskExecutor.main { updateUI(exists) }
}

// === Okio 模式 ===
// OkioFileUtils 的 exists 底层仍走 java.io.File，提供统一接口
val exists2 = OkioFileUtils.exists("/sdcard/demo.txt")
val isFile2 = OkioFileUtils.isRegularFile("/sdcard/demo.txt")
val isDir2  = OkioFileUtils.isDirectory("/sdcard/demo.txt")
```

**注意事项**：
- `exists()` 对 `null` 或空字符串安全返回 `false`
- 符号链接指向不存在的目标时，`exists()` 返回 `false`
- 所有异步方法返回 `Future<*>`，可用于 `future.cancel(true)` 取消

---

### 1.2 创建文件/目录

**场景说明**：应用初始化时需要创建配置目录、缓存目录或写入默认配置文件。`createFile` 和 `createDirectory` 会自动创建不存在的父目录。

```kotlin
// === java.io 模式 ===
// 创建文件——父目录 /sdcard/MyApp 不存在时自动创建
val created = FileUtils.createFile("/sdcard/MyApp/config.json")

// 创建目录——递归创建所有不存在的父目录（等效 mkdir -p）
FileUtils.createDirectory("/sdcard/MyApp/cache/images")

// 创建临时文件——系统自动保证文件名不冲突
val tempFile = FileUtils.createTempFile(
    prefix = "upload_",       // 文件名前缀
    suffix = ".jpg",          // 后缀
    directory = cacheDir      // 存放目录，默认系统临时目录
)
// 临时文件示例输出：/data/.../cache/upload_123456789.tmp

// 异步
FileUtils.createFileAsync("/sdcard/MyApp/data.txt") { success ->
    if (success) Log.d("File", "创建成功")
}

// === Okio 模式 ===
// OkioFileUtils 提供目录创建，文件创建走 OkioWriteUtils（写入时自动创建父目录）
OkioFileUtils.createDirectory("/sdcard/MyApp/cache")
OkioFileUtils.createDirectoryAsync("/sdcard/MyApp/out") { success -> }
```

**注意事项**：
- `createFile()` 如果文件已存在，返回 `true`（不覆盖内容）
- `createDirectory()` 如果目录已存在，返回 `true`（不报错）
- 临时文件不会自动删除，使用者负责清理
- 路径为空字符串时返回 `false`

---

### 1.3 删除文件/目录

**场景说明**：清理缓存、删除过期数据、卸载残留等场景。目录删除是递归的（先删子项再删自身），符号链接只删除链接本身不跟踪目标。

```kotlin
// === java.io 模式 ===
// 删除——自动判断文件/目录，目录递归删除
val deleted = FileUtils.delete("/sdcard/MyApp/cache/")

// 清空目录——保留目录自身，只删除内部内容
FileUtils.clearDirectory("/sdcard/MyApp/temp/")

// 异步
FileUtils.deleteAsync("/sdcard/old_data.bin") { success ->
    if (success) Log.d("File", "已删除")
}
FileUtils.clearDirectoryAsync("/sdcard/temp") { success -> }

// === Okio 模式 ===
// OkioFileUtils.delete 优先使用 Kotlin 的 File.deleteRecursively()，失败回退到 FileUtils
OkioFileUtils.delete("/sdcard/MyApp/cache/")
OkioFileUtils.deleteAsync("/sdcard/old_data.bin") { success -> }
```

**安全注意事项**：
- 符号链接：`deleteRecursive` 通过 `canonicalFile != absoluteFile` 检测符号链接，只删除链接自身
- 对根目录的保护：`FileUtils.delete("/")` 返回 `false`
- 清空目录时必须传入目录路径，传文件路径返回 `false`

---

### 1.4 重命名

**场景说明**：文件重命名（同一分区下是元数据操作，极快）。如果需要"移动"到不同分区，应使用 `move()` 而非 `rename()`。

```kotlin
// === java.io 模式 ===
// 返回值是新路径，失败返回 null
val newPath: String? = FileUtils.rename("/sdcard/old_name.txt", "new_name.txt")
if (newPath != null) {
    // newPath = "/sdcard/new_name.txt"
}

// 异步
FileUtils.renameAsync("/sdcard/draft.txt", "final.txt") { newPath ->
    if (newPath != null) Log.d("File", "已重命名: $newPath")
}
```

**注意事项**：
- `newName` 参数只需文件名，不含路径，新文件在同级目录
- 跨文件系统（如内部存储 → SD 卡）rename 会失败，此时应使用 `move()`
- 目标已存在时行为取决于底层 OS（Android 上通常覆盖）

---

### 1.5 复制文件/目录

**场景说明**：备份文件、导出数据、复制资源到外部存储等。这是最常用的 I/O 操作，也是两种模式性能差异最大的场景。

#### 小文件复制（< 10MB）

```kotlin
// === java.io 模式（简单直接） ===
val ok = FileUtils.copy("/sdcard/src.jpg", "/sdcard/backup/dst.jpg")
// overwrite = false 时目标存在则跳过
FileUtils.copy("/sdcard/src.jpg", "/sdcard/dst.jpg", overwrite = false)

// 异步
FileUtils.copyAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }

// === Okio 模式 ===
OkioFileUtils.copy("/sdcard/src.jpg", "/sdcard/backup/dst.jpg")
```

#### 大文件复制（带进度回调）

**场景说明**：复制几百 MB 到几 GB 的文件（如视频、安装包、数据库备份）。必须带进度回调让用户感知，且应避免阻塞 UI 线程。底层使用 `FileChannel.transferTo` 零拷贝传递数据。

```kotlin
// === java.io 模式 ===
// copyWithProgress 每读取一个 8KB buffer 就回调一次进度
// 底层使用 FileChannel.transferTo 减少用户态/内核态拷贝
FileUtils.copyWithProgress(
    srcPath = "/sdcard/Movies/large_video.mp4",
    destPath = "/sdcard/backup/large_video.mp4",
    overwrite = true,
    onProgress = { copied: Long, total: Long ->
        // 此回调在 I/O 线程！更新 UI 需切换
        val percent = if (total > 0) (copied * 100 / total).toInt() else 0
        TaskExecutor.main {
            progressBar.progress = percent
            statusText.text = "${formatSize(copied)} / ${formatSize(total)}"
        }
    }
)

// 异步 + 进度
FileUtils.copyWithProgressAsync(
    srcPath = "/sdcard/huge.iso",
    destPath = "/sdcard/backup/huge.iso",
    onProgress = { copied, total -> updateProgress(copied, total) },
    onResult = { success ->
        if (success) showToast("复制完成") else showToast("复制失败")
    }
)

// === Okio 模式 ===
// Okio 的 Buffer.writeAll 在 segment 层面传递引用，避免 8KB buffer 拷贝
// 比 java.io 快 2-3x（尤其在频繁小 I/O 场景差异更明显）
OkioFileUtils.copy("/sdcard/large.zip", "/sdcard/backup/large.zip")
```

#### 复制整个目录（递归）

**场景说明**：备份整个数据目录、导出应用数据。递归复制所有子文件和子目录，保持目录结构。

```kotlin
// === java.io 模式 ===
// 自动处理目录结构、符号链接检测（防止无限循环）、同目录检测
val success = FileUtils.copyDirectory(
    srcDir = "/sdcard/MyApp/data",
    destDir = "/sdcard/backup/MyApp/data",
    overwrite = true
)
if (!success) {
    Log.e("File", "目录复制失败，请检查存储空间和权限")
}
```

**大文件复制最佳实践**：

| 策略 | 说明 |
|---|---|
| **始终用异步** | 即使小文件也走 `*Async`，避免 ANR |
| **检查存储空间** | 复制前用 `FileUtils.getAvailableSpace()` 确保目标分区有足够空间 |
| **用进度回调** | 大于 10MB 的文件务必提供进度回调 |
| **原子写入** | 内部使用 atomic write（先写 temp → flush → rename），崩溃不会产生半成品文件 |
| **考虑 Okio 超时** | 复制网络存储上的文件时用 `OkioFileUtils.withTimeout()` 防止永久挂起 |

---

### 1.6 移动文件/目录

**场景说明**：将文件从临时目录移到正式目录，或移动整个缓存目录。`move()` 优先尝试 `rename()`（同分区 O(1) 操作），失败后自动退化为 `copy + delete`。

```kotlin
// === java.io 模式 ===
// 内部逻辑：
// 1. 同一文件系统 → File.renameTo()（仅修改 inode 指针，极快）
// 2. 跨文件系统 → copy + delete（完整复制后删除源文件）
val moved = FileUtils.move(
    srcPath = "/sdcard/temp/export.zip",
    destPath = "/sdcard/Documents/export.zip",
    overwrite = true
)

// 移动整个目录
FileUtils.move("/sdcard/temp_cache", "/sdcard/permanent_cache")

// 异步
FileUtils.moveAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }

// === Okio 模式 ===
OkioFileUtils.move("/sdcard/temp/file.txt", "/sdcard/dest/file.txt")
OkioFileUtils.moveAsync("/sdcard/a.txt", "/sdcard/b.txt") { success -> }
```

**注意事项**：
- 同一文件系统上移动 GB 级目录几乎瞬间完成（只改元数据）
- 跨文件系统移动大目录时等同于"复制+删除"，耗时较长，应走异步
- 源和目标指向同一文件（`canonicalFile` 相同）直接返回 `true`

---

### 1.7 列出文件

**场景说明**：扫描目录获取文件列表，按扩展名过滤，递归遍历整个目录树。常用于文件管理器、清理工具、媒体扫描等。

```kotlin
// === java.io 模式 ===
// 1. 列出直接子项
val children = FileUtils.listFiles("/sdcard/DCIM")

// 2. 按扩展名过滤——内部用 File.extension 比较（忽略大小写）
val jpgs = FileUtils.listFilesByExtension("/sdcard/DCIM", "jpg")

// 3. 自定义过滤器——lambda 返回 true 表示保留
val largeFiles = FileUtils.listFiles("/sdcard/Download") { file ->
    file.isFile &&
    file.length() > 50 * 1024 * 1024 &&     // > 50MB
    file.lastModified() > sevenDaysAgo       // 最近 7 天
}

// 4. 递归列出所有文件——内部用 File.walkTopDown() 深度优先遍历
val allFiles = FileUtils.listFilesRecursive("/sdcard/MyApp/")
println("共找到 ${allFiles.size} 个文件")

// 统计目录总大小
val totalSize = allFiles.sumOf { it.length() }

// 异步递归（大目录推荐）
FileUtils.listFilesRecursiveAsync("/sdcard/") { files ->
    TaskExecutor.main { textView.text = "找到 ${files.size} 个文件" }
}

// === Okio 模式 ===
// 直接子项
val list = OkioFileUtils.list("/sdcard/DCIM")

// 递归列出
val allFiles2 = OkioFileUtils.listRecursively("/sdcard/MyApp/")
OkioFileUtils.listRecursivelyAsync("/sdcard/MyApp/") { paths ->
    // paths 是 List<String>（绝对路径）
}
```

**性能提示**：
- `listFilesRecursive` 对包含数万文件的目录可能耗时数秒，请使用异步版本
- 需要频繁扫描同一目录时，考虑缓存结果或使用 `FileObserver` 增量更新
- `walkTopDown()` 优于 `walkBottomUp()` 因为不需要等子目录完成

---

### 1.8 获取文件信息

**场景说明**：获取文件大小、MIME 类型、修改时间、权限等元数据，用于文件管理器、上传前置检查等。

```kotlin
// === java.io 模式 ===
// 单项查询
val size = FileUtils.getSize("/sdcard/photo.jpg")                  // 字节数（目录递归统计）
val formatted = FileUtils.getSizeFormatted("/sdcard/photo.jpg")     // "2.50 MB"
val ext = FileUtils.getExtension("/sdcard/photo.jpg")              // "jpg"（小写）
val name = FileUtils.getFileName("/sdcard/photo.jpg")              // "photo.jpg"
val nameOnly = FileUtils.getFileNameWithoutExtension("/sdcard/photo.jpg") // "photo"
val mime = FileUtils.getMimeType("/sdcard/photo.jpg")              // "image/jpeg"
val modified = FileUtils.getLastModified("/sdcard/photo.jpg")       // "2026-07-10 15:30:00"
val modifiedMs = FileUtils.getLastModifiedMillis("/sdcard/photo.jpg") // 时间戳
val parent = FileUtils.getParentPath("/sdcard/photo.jpg")          // "/sdcard"

// 批量查询——一次获取所有信息
val info: Map<String, Any> = FileUtils.getFileInfo("/sdcard/photo.jpg")
// info 包含: exists, name, nameWithoutExtension, extension, path, parent,
//           isFile, isDirectory, isHidden, size, sizeFormatted,
//           lastModified, canRead, canWrite, canExecute, mimeType

// URI 获取 MIME（Android ContentResolver 方式）
val uriMime = FileUtils.getMimeType(context, uri)

// 异步
FileUtils.getSizeAsync("/sdcard/photo.jpg") { size -> }
FileUtils.getFileInfoAsync("/sdcard/photo.jpg") { info -> }

// === Okio 模式 ===
val size2 = OkioFileUtils.getSize("/sdcard/photo.jpg")
val modifiedMs2 = OkioFileUtils.getLastModifiedMillis("/sdcard/photo.jpg")
```

---

### 1.9 存储空间查询

**场景说明**：写入大文件前检查磁盘空间是否充足，避免写到一半抛出 `IOException`。

```kotlin
// === java.io 模式 ===
// 指定路径所在分区的可用空间
val available = FileUtils.getAvailableSpace("/sdcard/")
val total = FileUtils.getTotalSpace("/sdcard/")

// 内置常用快捷方法
val internalFree = FileUtils.getInternalAvailableSpace()    // 内部存储
val externalFree = FileUtils.getExternalAvailableSpace()    // SD 卡（无卡返回 -1）

// 典型用法：写入前检查空间
val fileSize = 500 * 1024 * 1024L  // 500MB
if (FileUtils.getAvailableSpace("/sdcard/backup/") > fileSize + 100 * 1024 * 1024L) {
    startCopy()   // 预留 100MB 余量
} else {
    showError("存储空间不足")
}

// === Okio 模式 ===
val available2 = OkioFileUtils.getAvailableSpace("/sdcard/")
val total2 = OkioFileUtils.getTotalSpace("/sdcard/")
```

**注意事项**：
- `StatFs.availableBytes` 在 API 18+ 可用，该库 minSdk 为 24
- 返回 `-1` 表示查询失败（路径不存在或权限不足）
- 可用空间会动态变化（其他进程在写），复制前检查只能作为参考

---

## 2. 文件读取

### 2.1 读取为字符串

**场景说明**：读取配置文件、JSON 数据、文本日志等。自动限制最大 10MB（可调整），防止将 GB 级文件意外加载到内存。

```kotlin
// === java.io 模式 ===
// 默认 UTF-8，最大 10MB
val text: String? = FileReadUtils.readText("/sdcard/data.json")
text?.let { parseJson(it) }

// 指定其他编码（如读取遗留系统的 GBK 文件）
val gbkText = FileReadUtils.readText("/sdcard/legacy.txt", Charset.forName("GBK"))

// 提高内存限制到 50MB
val largeText = FileReadUtils.readText("/sdcard/large_report.txt",
    charset = Charsets.UTF_8,
    maxBytes = 50 * 1024 * 1024)

// 异步——回调带 error 参数便于错误处理
FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
    when {
        error != null -> showError("读取失败: ${error.message}")
        content == null -> showError("文件不存在或过大")
        else -> parseAndDisplay(content)
    }
}

// === Okio 模式 ===
// 使用 BufferedSource.readString()，内部一次性分配空间
val text2 = OkioReadUtils.readUtf8("/sdcard/data.json")
val gbkText2 = OkioReadUtils.readUtf8("/sdcard/legacy.txt", Charset.forName("GBK"))

// 异步
OkioReadUtils.readUtf8Async("/sdcard/data.json") { content, error -> }
```

**内存安全**：两种模式的 `readText` 都会先检查文件大小是否超过 `maxBytes` 限制。超过限制时返回 `null` 而非 OOM。对于超大文本文件，应使用流式逐行读取（见 2.4）。

---

### 2.2 读取为字节数组

**场景说明**：读取图片、音频、加密数据等二进制文件。`ByteArray` 适合进一步处理，但大文件应分块读取。

```kotlin
// === java.io 模式 ===
val bytes: ByteArray? = FileReadUtils.readBytes("/sdcard/photo.jpg")
// 限制最大读取 5MB
val limited = FileReadUtils.readBytes("/sdcard/file.bin", maxBytes = 5 * 1024 * 1024)

FileReadUtils.readBytesAsync("/sdcard/photo.jpg") { data, error -> }

// === Okio 模式（推荐 ByteString） ===
// ByteString 是不可变对象，线程安全，支持便捷转换
val byteStr = OkioReadUtils.readByteString("/sdcard/photo.jpg")
// 快捷输出
println("Hex: ${byteStr?.hex()}")           // 十六进制
println("Base64: ${byteStr?.base64()}")     // Base64 编码
println("MD5: ${byteStr?.md5()?.hex()}")    // MD5 哈希（直接对 ByteString 运算）

// 读取到可变 Buffer（适合需要修改数据的场景）
val buffer = OkioReadUtils.readToBuffer("/sdcard/template.xml")
buffer?.apply {
    writeUtf8("<!-- generated -->\n")  // 在开头插入注释
    // 之后可以用 OkioWriteUtils.writeFromBuffer 写回文件
}
```

**ByteString vs ByteArray 选择**：
- `ByteString`：需要在线程间共享、需要 hex/base64 编码、只读使用
- `ByteArray`：需要对数据做修改、传递给使用 `byte[]` 的第三方 API
- `Buffer`：需要追加/插入/删除数据，或拼接多个来源

---

### 2.3 按行读取（小文件）

**场景说明**：读取配置文件每行、解析 CSV、处理词表等。适合文件行数在万级以内、总大小不超过 `maxBytes` 限制的场景。

```kotlin
// === java.io 模式 ===
val lines: List<String>? = FileReadUtils.readLines("/sdcard/words.txt")
lines?.forEachIndexed { index, line ->
    println("第 $index 行: $line")
}

// 异步 + 错误处理
FileReadUtils.readLinesAsync("/sdcard/config.ini") { lines, error ->
    if (error != null) {
        Log.e("File", "读取失败", error)
    } else {
        lines?.forEach { parseConfigLine(it) }
    }
}

// === Okio 模式 ===
val lines2 = OkioReadUtils.readLines("/sdcard/words.txt")
```

**限制**：`readLines` 会将整个文件读入内存再分割。如果文件有数百万行或几百 MB，应使用 `readLinesStreaming`（见下方）。

---

### 2.4 流式逐行读取（大文件友好）

**场景说明**：处理几百 MB 的日志文件、CSV 导出、数据库 dump 等。边读边处理，内存中始终只保留当前行。可通过回调返回 `false` 提前终止。

```kotlin
// === java.io 模式 ===
// 典型场景：解析大 CSV，只取前 1000 行预览
val count = FileReadUtils.readLinesStreaming(
    path = "/sdcard/huge_table.csv",
    charset = Charsets.UTF_8,
    onEachLine = { line: String, index: Int ->
        // 处理当前行
        val columns = line.split(",")
        processRow(columns)
        // index < 999 → 继续读取第 1000 行及之前
        // index >= 999 → 停止（提前终止，不会读完整个文件）
        index < 999
    }
)
if (count >= 0) {
    Log.d("File", "成功处理了 $count 行")
} else {
    Log.e("File", "读取失败")
}

// 异步流式读取
FileReadUtils.readLinesStreamingAsync(
    path = "/sdcard/huge.log",
    onEachLine = { line, index ->
        // 只处理包含 "ERROR" 的行
        if ("ERROR" in line) collectError(line)
        true  // 继续读取直到文件末尾
    },
    onComplete = { totalLines, error ->
        if (error == null) {
            Log.d("File", "处理完成，共 $totalLines 行")
        }
    }
)

// === Okio 模式 ===
// 使用 BufferedSource.readUtf8Line()，UTF-8 解码效率更高
val count2 = OkioReadUtils.readLinesStreaming(
    path = "/sdcard/huge.csv",
    onEachLine = { line, index ->
        processLine(line)
        true  // 继续
    }
)
```

**性能对比**：

| 方式 | 2GB 日志文件内存占用 | 处理时间 |
|---|---|---|
| `readLines()` | OOM（无法完成） | - |
| `readLinesStreaming()` | ~几 KB（当前行） | 线性 O(n) |

---

### 2.5 分块读取（超大二进制文件）

**场景说明**：上传几百 MB 的文件时，需要将文件切分成固定大小的块（chunk），逐块上传并报告进度。每次内存中只有 `chunkSize` 字节。

```kotlin
// === java.io 模式 ===
// 将 500MB 文件按 1MB 每块上传
val totalRead = FileReadUtils.readChunks(
    path = "/sdcard/large_video.mp4",
    chunkSize = 1024 * 1024,  // 1MB 每块
    onChunk = { chunk: ByteArray, chunkIndex: Int, totalChunks: Int ->
        // chunk: 当前块的数据（可能小于 chunkSize，最后一块）
        // chunkIndex: 从 0 开始
        // totalChunks: 总块数
        val ok = uploadChunk(chunk, chunkIndex, totalChunks)
        if (!ok) {
            Log.e("Upload", "第 $chunkIndex 块上传失败，取消后续读取")
            false  // 返回 false 提前终止
        } else {
            val progress = ((chunkIndex + 1) * 100) / totalChunks
            TaskExecutor.main {
                progressBar.progress = progress
                statusText.text = "上传中 $progress% (${chunkIndex + 1}/$totalChunks)"
            }
            true  // 继续下一块
        }
    }
)
if (totalRead < 0) {
    showError("文件读取失败")
}

// 异步分块——推荐用于上传场景
FileReadUtils.readChunksAsync(
    path = "/sdcard/huge_file.bin",
    chunkSize = 512 * 1024,  // 512KB
    onChunk = { chunk, index, total -> upload(chunk, index); true },
    onComplete = { totalBytes, error ->
        if (error == null) showToast("上传完成，共 $totalBytes 字节")
        else showToast("上传失败: ${error.message}")
    }
)
```

**chunkSize 选择建议**：
- 太小（如 4KB）：回调过于频繁，CPU 开销浪费在函数调用上
- 太大（如 100MB）：内存占用高，进度回调间隔长，用户体验差
- 推荐范围：64KB ~ 1MB，根据网络/存储延迟调整
- 上传场景建议和网络 MTU 对齐（如 64KB）

---

### 2.6 读取文件头部/尾部

**场景说明**：读取文件头 Magic Number 判断真实类型（防止仅靠扩展名误判），读取日志文件尾部查看最新记录。

```kotlin
// === java.io 模式 ===
// 1. 读取文件头部字节——识别文件真实类型
val header = FileReadUtils.readHeadBytes("/sdcard/unknown.bin", numBytes = 8)
if (header != null) {
    // PNG 文件头: 89 50 4E 47 0D 0A 1A 0A
    // JPEG 文件头: FF D8 FF E0 (或 FF D8 FF E1)
    // PDF 文件头:  25 50 44 46 (即 "%PDF")
    val magic = header.take(4).joinToString(" ") { "%02X".format(it) }
    Log.d("File", "文件头 Magic: $magic")
}

// 2. 读取文件尾部字节——检查文件尾部是否有特定标记
val tail = FileReadUtils.readTailBytes("/sdcard/log.txt", numBytes = 512)

// 3. 读取文件最后 N 行——日志查看器常用
val last10Lines: List<String>? = FileReadUtils.readTailLines(
    path = "/sdcard/app.log",
    numLines = 50  // 取最后 50 行
)
last10Lines?.forEach { line -> logViewer.append("$line\n") }
```

---

### 2.7 从 URI / InputStream 读取

**场景说明**：Android 存储框架（SAF）返回的 Content URI、网络下载流、其他应用分享的数据流。此类数据来源不一定是本地文件路径。

```kotlin
// === java.io 模式 ===
// 1. 从 Content URI 读取——适用于 SAF / MediaStore
//    内部通过 ContentResolver.openInputStream() 获取流
val uriBytes: ByteArray? = FileReadUtils.readBytes(context, uri)
// 限制大小，防止恶意超大 URI
val safeBytes = FileReadUtils.readBytes(context, uri, maxBytes = 5 * 1024 * 1024)

// 2. 从 InputStream 读取——适用于网络流、加解密流等
//    注意：调用方负责关闭 InputStream！
httpClient.download(url).use { inputStream ->
    val bytes = FileReadUtils.readBytes(inputStream)
    // 对 bytes 做处理...
}

// 3. 从 InputStream 读取为字符串
socket.getInputStream().use { stream ->
    val text = FileReadUtils.readText(stream, Charsets.UTF_8, maxBytes = 1024 * 1024)
    text?.let { parseResponse(it) }
}
```

**安全提醒**：从外部 URI / 流读取时务必设置 `maxBytes`，防止恶意数据导致 OOM。

---

### 2.8 超时读取（Okio 独有）

**场景说明**：从网络文件系统（NFS/SMB）、慢速 SD 卡、或管道文件读取时，`read()` 可能无限阻塞。Okio 的超时机制在每次底层 `read()` 调用设置时限，超时抛出 `InterruptedIOException` 让线程恢复响应。

```kotlin
// === Okio 模式（独有） ===
// 场景：读取挂载的网络存储上的大文件，设置 30 秒超时
// 超时后返回 null，不会永久阻塞线程
val data: ByteString? = OkioReadUtils.readWithTimeout(
    path = "/mnt/nfs_share/large_database.db",
    timeoutMs = 30_000,         // 30 秒
    maxBytes = 100 * 1024 * 1024 // 最大读取 100MB
)
if (data == null) {
    Log.w("File", "读取超时或失败，请检查网络存储连接")
}

// 异步超时
OkioReadUtils.readWithTimeoutAsync(
    path = "/mnt/smb/remote_file.bin",
    timeoutMs = 60_000
) { data, error ->
    if (error is java.io.InterruptedIOException) {
        showError("读取超时")
    } else if (error != null) {
        showError("读取失败: ${error.message}")
    }
}

// 对比：java.io 模式只能用 Future.get(timeout) 取消等待
// 但这只能取消 Future 的阻塞等待，无法中断底层 InputStream.read()
// 被阻塞的 I/O 线程会一直挂起直到进程被杀
```

---

### 2.9 带进度读取（Okio 推荐）

**场景说明**：读入大文件到内存时展示进度条。Okio 通过 `ForwardingSource` 装饰器在每次 segment 读取后回调，无需在业务代码中夹带进度逻辑。

```kotlin
// === Okio 模式 ===
// ForwardingSource 在每次 read() 后自动回调进度
val data: ByteArray? = OkioReadUtils.readWithProgress(
    path = "/sdcard/large_model.tflite",
    chunkSize = 8192L,  // 每 8KB 回调一次进度（控制回调频率）
    onProgress = { bytesRead: Long, totalBytes: Long ->
        val percent = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt() else 0
        TaskExecutor.main {
            loadingBar.progress = percent
            loadingText.text = "加载中 $percent%"
        }
    }
)

// 异步版本
OkioReadUtils.readWithProgressAsync(
    path = "/sdcard/large_file.bin",
    onProgress = { read, total -> updateUI(read, total) },
    onResult = { data, error -> processData(data) }
)
```

---

### 2.10 Gzip 解压读取（Okio 独有）

**场景说明**：读取 `.gz` 压缩文件（日志归档、数据导出常见），无需先解压到临时文件再读——直接流式解压到内存。

```kotlin
// === Okio 模式（Okio.gzip / GzipSource） ===
// 1. 读取并解压为字节数组
val jsonBytes: ByteArray? = OkioReadUtils.readGzip("/sdcard/data.json.gz")

// 2. 解压后直接转字符串（内部 readGzip + String 构造）
val jsonText: String? = OkioReadUtils.readGzipAsText(
    path = "/sdcard/data.json.gz",
    charset = Charsets.UTF_8
)

// 3. 异步
OkioReadUtils.readGzipAsync("/sdcard/large_log.gz") { bytes, error ->
    if (bytes != null) {
        // 流式解压完成，bytes 为解压后内容
    }
}

// === java.io 模式下的等效做法（对比参考） ===
// 需要额外使用 GZIPInputStream 手动包装：
// GZIPInputStream(FileInputStream(File(path))).use { ... }
// 无内置解压方法，需自行写循环读取
```

**注意**：解压后的数据大小可能远大于压缩文件（如 100MB 的 `.gz` 可能解压出 1GB 数据）。默认限制 10MB，超过限制的压缩包不会解压。

---

## 3. 文件写入

### 3.1 写入字符串

**场景说明**：保存配置文件、写入 JSON 数据、生成文本报告。内部使用原子写入保证数据一致性。

```kotlin
// === java.io 模式 ===
// 覆盖写入——内部流程：写临时文件 → fd.sync() 强制落盘 → Os.rename 原子替换
val ok: Boolean = FileWriteUtils.writeText(
    path = "/sdcard/config.json",
    content = """{"theme": "dark", "language": "zh"}"""
)

// 指定编码
FileWriteUtils.writeText("/sdcard/legacy.txt", gbkContent, Charset.forName("GBK"))

// 异步——适合在 UI 事件中调用
FileWriteUtils.writeTextAsync("/sdcard/note.txt", userInput) { success ->
    if (success) showToast("已保存") else showToast("保存失败")
}

// === Okio 模式 ===
// BufferedSink.writeString 内部处理 UTF-8 编码
OkioWriteUtils.writeUtf8("/sdcard/config.json", """{"theme": "dark"}""")
OkioWriteUtils.writeUtf8Async("/sdcard/data.txt", content) { success -> }
```

**原子写入原理**（两种模式均采用）：

```
1. 创建临时文件: /sdcard/config.json_123456.tmp
2. 写入全部内容到临时文件
3. FileOutputStream.fd.sync()   ← 强制将内核缓冲区刷入磁盘
4. Os.rename(tmp, dest)         ← POSIX 原子重命名
5. 如果步骤 3-4 之间崩溃：临时文件残留，目标文件完整无损
6. finally 块清理临时文件
```

---

### 3.2 追加字符串

**场景说明**：写日志文件、追加 CSV 行、记录事件序列。追加模式不覆盖已有内容，文件不存在时自动创建。

```kotlin
// === java.io 模式 ===
FileWriteUtils.appendText("/sdcard/app.log", "[2026-07-10 15:30:00] App started\n")
FileWriteUtils.appendText("/sdcard/app.log", "[2026-07-10 15:30:05] User logged in\n")

// 异步追加
FileWriteUtils.appendTextAsync("/sdcard/events.csv", "click,button_login,${System.currentTimeMillis()}\n") { success -> }

// === Okio 模式 ===
// 通过 FileOutputStream(path, true).sink() 以追加模式打开
OkioWriteUtils.appendUtf8("/sdcard/app.log", "[INFO] Background sync completed\n")
OkioWriteUtils.appendUtf8Async("/sdcard/log.txt", "new entry\n") { success -> }
```

**注意事项**：
- 追加操作**不是**原子的——如果在追加过程中崩溃，最后一行可能不完整
- 每分钟写几百次的高频日志应使用专门的日志库（如 `timber`），而非直接文件追加
- 追加模式下不会检查 `maxBytes` 限制

---

### 3.3 写入字节数组

**场景说明**：保存图片、写入加密数据、导出二进制文件。

```kotlin
// === java.io 模式 ===
val imageBytes: ByteArray = capturePhoto()
FileWriteUtils.writeBytes("/sdcard/DCIM/photo.jpg", imageBytes)
FileWriteUtils.writeBytesAsync("/sdcard/data.bin", encryptedData) { success -> }

// 追加字节
FileWriteUtils.appendBytes("/sdcard/stream.bin", chunk)

// === Okio 模式 ===
// 方式 1：直接写 ByteString
val byteStr = ByteString.encodeUtf8("Hello World")
OkioWriteUtils.writeByteString("/sdcard/hello.txt", byteStr)

// 方式 2：从 Buffer 写（适合先构造 Buffer 再写入的场景）
val buffer = Buffer().apply {
    writeUtf8("Header Section\n")
    write(headerBytes)
    writeUtf8("\nBody Section\n")
    write(bodyBytes)
}
OkioWriteUtils.writeFromBuffer("/sdcard/documents/report.txt", buffer)

// 方式 3：最通用的字节写入
OkioWriteUtils.writeAtomic("/sdcard/data.bin", rawBytes)
```

---

### 3.4 从 InputStream 写入

**场景说明**：下载文件直接写入磁盘、将网络流保存为文件、复制 ContentProvider 返回的流。

```kotlin
// === java.io 模式 ===
// 典型下载场景：网络流 → 文件
httpClient.download(url).use { inputStream ->
    val ok = FileWriteUtils.writeFromStream(
        path = "/sdcard/Download/file.zip",
        inputStream = inputStream,
        overwrite = true,
        onProgress = { bytesWritten: Long, estimatedTotal: Long ->
            // estimatedTotal 可能为 -1（无法确定总大小，如 chunked 传输）
            if (estimatedTotal > 0) {
                val pct = (bytesWritten * 100 / estimatedTotal).toInt()
                TaskExecutor.main { progressBar.progress = pct }
            } else {
                TaskExecutor.main { progressBar.isIndeterminate = true }
            }
        }
    )
}

// 异步
FileWriteUtils.writeFromStreamAsync(
    path = "/sdcard/download.zip",
    inputStream = stream,
    onResult = { success -> if (success) notifyDownloadComplete() }
)

// === Okio 模式 ===
// Okio 通过 source() 将 InputStream 桥接为 Source，再用 writeAll 零拷贝写入
OkioWriteUtils.writeFromStream("/sdcard/download.bin", inputStream)
```

**注意**：`writeFromStream` 不会关闭传入的 `InputStream`，调用方负责关闭（或使用 `use`）。

---

### 3.5 写入到 Content URI（Android 10+ MediaStore / SAF）

**场景说明**：Android 10 引入 Scoped Storage，外部存储写入需通过 `MediaStore` 或 SAF 获取 Content URI。此方法通过 `ContentResolver.openOutputStream()` 写入。

```kotlin
// === java.io 模式 ===
// 方式 1：写字节数组到 MediaStore URI
val contentValues = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${timestamp}.jpg")
    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
}
val uri = contentResolver.insert(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
)
if (uri != null) {
    val ok = FileWriteUtils.writeToUri(
        context = context,
        uri = uri,
        bytes = jpegBytes,
        mimeType = "image/jpeg"  // 可选，用于 ContentResolver 内部处理
    )
}

// 方式 2：从 InputStream 写入到 URI（如从网络下载直接写入 MediaStore）
httpClient.download(url).use { stream ->
    FileWriteUtils.writeStreamToUri(context, uri, stream, "application/pdf")
}

// === Okio 模式 ===
// Okio 系列未单独提供 writeToUri，可以使用 java.io 版本或如下手动实现：
// contentResolver.openOutputStream(uri)?.sink()?.buffer()?.use { ... }
```

---

### 3.6 原子写入详解

**场景说明**：写入关键数据（配置文件、数据库、用户数据）时，必须防止写入中途崩溃导致文件损坏。原子写入确保：要么是旧版本完整文件，要么是新版本完整文件，不会出现半截文件。

```kotlin
// === java.io 模式 ===
// writeText / writeBytes 默认就是原子写入（无需单独指定）
// 以下方法显式声明"原子"语义

// 原子写入字符串
FileWriteUtils.writeTextAtomic(
    path = "/data/data/com.app/shared_prefs/app_config.json",
    content = newConfigJson
)
// 执行过程中如果崩溃：
// - 旧文件 /data/.../app_config.json 完整保留
// - 临时文件 /data/.../app_config.json_xxxx.tmp 残留（下次启动可清理）

// 原子写入字节
FileWriteUtils.writeBytesAtomic(
    path = "/data/data/com.app/databases/user.db",
    bytes = newDbBytes
)

// 异步原子写入
FileWriteUtils.writeTextAtomicAsync("/data/important.dat", data) { success -> }

// === Okio 模式 ===
// 原子写入字节
OkioWriteUtils.writeAtomic("/data/config.bin", configBytes)

// 原子写入字符串
OkioWriteUtils.writeAtomicUtf8("/data/config.json", configJson)

// Okio 原子写入额外保证：在 rename 前先 flush 并 close BufferedSink
// 确保所有数据已从 Okio Buffer 到达文件系统
```

**原子写入的完整保证链**：

```
应用程序数据
    ↓ write() 到 Okio Buffer / java.io OutputStream
    ↓ flush() → 数据到达内核缓冲区（page cache）
    ↓ fd.sync() → 数据强制写入磁盘（fsync 系统调用）
    ↓ Os.rename(tmp, dest) → 原子替换（POSIX 保证）
目标文件 ← 要么是旧内容，要么是新内容，绝无半截
```

---

### 3.7 分块写入大文件（带进度）

**场景说明**：内存中有超大 `ByteArray`（如从相机捕获的 RAW 数据、拼接的视频帧），需要写入磁盘并展示进度。分块写入避免单次 `write()` 耗时过长。

```kotlin
// === java.io 模式 ===
// 500MB 数据，每次写 64KB，实时回调进度
val hugeData: ByteArray = generateVideoFrames()  // 假设 500MB
val ok = FileWriteUtils.writeBytesInChunks(
    path = "/sdcard/Movies/output.mp4",
    data = hugeData,
    chunkSize = 64 * 1024,    // 每块 64KB（共约 8000 次写入）
    overwrite = true,
    onProgress = { bytesWritten: Long, totalBytes: Long ->
        val percent = (bytesWritten * 100 / totalBytes).toInt()
        // 注意：回调仍在 I/O 线程
        TaskExecutor.main {
            progressBar.progress = percent
            statusText.text = "导出中 $percent%"
        }
    }
)

// 异步
FileWriteUtils.writeBytesInChunksAsync(
    path = "/sdcard/export.bin",
    data = hugeData,
    chunkSize = 128 * 1024,
    onProgress = { written, total -> updateUI(written, total) },
    onResult = { success -> showResult(success) }
)

// === Okio 模式 ===
// ForwardingSink 在每次 write() 后回调，无需手动在循环中插入进度代码
OkioWriteUtils.writeWithProgress(
    path = "/sdcard/big_file.bin",
    data = byteArray,
    chunkSize = 8192L,  // 每 8KB 触发一次进度回调
    onProgress = { bytesWritten, totalBytes ->
        updateProgressBar((bytesWritten * 100 / totalBytes).toInt())
    }
)
```

**chunkSize 的权衡**：

| chunkSize | 写入次数 | 进度回调频率 | CPU 开销 |
|---|---|---|---|
| 4KB | 很多 | 非常频繁 | 高（回调开销） |
| 64KB | 适中 | 适中（推荐） | 低 |
| 1MB | 较少 | 稀疏 | 极低 |

---

### 3.8 超时写入（Okio 独有）

**场景说明**：写入慢速设备（如 SD 卡、网络存储）时设置超时，防止 `write()` 无限阻塞。

```kotlin
// === Okio 模式 ===
// 写入 U 盘或慢速 SD 卡，超时 30 秒
val ok = OkioWriteUtils.writeWithTimeout(
    path = "/mnt/usb_otg/backup.bin",
    bytes = fileData,
    timeoutMs = 30_000
)
if (!ok) {
    Log.w("File", "写入超时，请检查存储设备")
}

// 异步超时
OkioWriteUtils.writeWithTimeoutAsync(
    path = "/mnt/sdcard_slow/file.bin",
    bytes = data,
    timeoutMs = 30_000
) { success -> }
```

---

### 3.9 Gzip 压缩写入（Okio 独有）

**场景说明**：将日志、JSON、CSV 等文本数据压缩为 `.gz` 文件，节省 70-90% 存储空间。Okio 的 `GzipSink` 在写入过程中流式压缩。

```kotlin
// === Okio 模式 ===
// 1. 压缩字节数组写入
val jsonData = buildLargeJson()  // 100MB JSON 字符串
val ok = OkioWriteUtils.writeGzip(
    path = "/sdcard/backups/data.json.gz",
    data = jsonData.toByteArray()
)
// 结果：100MB → 可能压缩到 10-20MB

// 2. 直接压缩字符串写入（省去手动 toByteArray）
OkioWriteUtils.writeGzipText(
    path = "/sdcard/logs/app_20260710.log.gz",
    content = hugeLogContent,
    charset = Charsets.UTF_8
)

// 3. 异步
OkioWriteUtils.writeGzipAsync("/sdcard/archive.gz", bytes) { success -> }

// 配套解压读取：
// OkioReadUtils.readGzip("/sdcard/backups/data.json.gz")
// OkioReadUtils.readGzipAsText("/sdcard/backups/data.json.gz")

// === java.io 模式的等效做法（对比参考） ===
// GZIPOutputStream(FileOutputStream(File(path))).buffered().use { gzip ->
//     gzip.write(data)
// }
// 需要手动管理流，无内置一键方法
```

---

## 4. 文件哈希与校验

### 4.1 计算文件哈希

**场景说明**：文件完整性校验、去重检测、生成文件标识符。不同算法各有适用场景。

| 算法 | 速度 | 碰撞概率 | 典型用途 |
|---|---|---|---|
| **MD5** | 最快 | 已被攻破（可碰撞） | 文件去重、缓存 key |
| **SHA-1** | 快 | 已被攻破 | Git 对象标识（兼容） |
| **SHA-256** | 中等 | 极低（推荐） | 安全校验、下载验证 |
| **SHA-512** | 慢 | 极低 | 高安全场景 |
| **CRC32** | 极快 | 高（非加密哈希） | 传输校验、快速变化检测 |

```kotlin
// === java.io 模式 ===
// 便捷方法
val md5    = FileHashUtils.md5("/sdcard/photo.jpg")      // MD5 去重
val sha1   = FileHashUtils.sha1("/sdcard/photo.jpg")      // SHA-1
val sha256 = FileHashUtils.sha256("/sdcard/photo.jpg")    // SHA-256（推荐）
val sha512 = FileHashUtils.sha512("/sdcard/photo.jpg")    // SHA-512
val crc    = FileHashUtils.crc32("/sdcard/photo.jpg")     // CRC32

// 通用方法（动态选择算法）
val algorithm = FileHashUtils.Algorithm.fromString("SHA-256")  // 从字符串解析
val hash = FileHashUtils.hashFile("/sdcard/file.bin", FileHashUtils.Algorithm.SHA256)

// 异步——区块链/下载场景常用
FileHashUtils.sha256Async("/sdcard/download.apk") { hash, error ->
    if (hash != null) {
        val isValid = (hash == expectedHashFromServer)
        if (isValid) install() else showCorrupted()
    }
}

// === Okio 模式 ===
// 使用 java.security.MessageDigest（更灵活，支持任意算法）
val digest = MessageDigest.getInstance("SHA-256")
val hash2 = OkioHashUtils.hashFile("/sdcard/photo.jpg", digest)

// ByteString 直接取哈希（无需打开文件）
val byteStr = OkioReadUtils.readByteString("/sdcard/small.txt")
println("MD5: ${byteStr?.md5()?.hex()}")      // Okio ByteString 内置 md5()
println("SHA256: ${byteStr?.sha256()?.hex()}") // Okio ByteString 内置 sha256()
```

---

### 4.2 大文件哈希（带进度回调）

**场景说明**：计算几 GB 的 ISO/视频文件的 SHA-256，必须带进度条，否则用户以为应用卡死。

```kotlin
// === java.io 模式 ===
// DigestInputStream 自动将读取的字节送入 MessageDigest
// 外层循环逐块读取 + 手动回调进度
val sha256 = FileHashUtils.hashFileWithProgress(
    path = "/sdcard/large_ubuntu.iso",      // 假设 4.7 GB
    algorithm = FileHashUtils.Algorithm.SHA256,
    onProgress = { bytesProcessed: Long, totalBytes: Long ->
        val percent = (bytesProcessed * 100 / totalBytes).toInt()
        TaskExecutor.main {
            hashProgressBar.progress = percent
            hashStatusText.text = "校验中 $percent%"
        }
    }
)
if (sha256 != null) {
    Log.d("Hash", "SHA-256: $sha256")
}

// 异步
FileHashUtils.hashFileWithProgressAsync(
    path = "/sdcard/large.iso",
    algorithm = FileHashUtils.Algorithm.SHA256,
    onProgress = { processed, total -> updateUI(processed, total) },
    onResult = { hash, error ->
        if (hash != null) copyToClipboard(hash)
    }
)

// === Okio 模式 ===
// HashingSource 在数据流经时同步更新 digest，一次遍历
val digest = MessageDigest.getInstance("SHA-256")
val hash2 = OkioHashUtils.hashFileWithProgress(
    path = "/sdcard/large.iso",
    digest = digest,
    onProgress = { processed, total -> updateUI(processed, total) }
)
```

---

### 4.3 校验文件完整性

**场景说明**：下载 APK/固件/OBB 后与服务器公布的哈希比对，确保文件未被篡改或损坏。

```kotlin
// === java.io 模式 ===
// 典型下载校验流程
val expectedSha256 = "a1b2c3d4e5f6a7b8c9d0e1f2..."  // 从服务器获取
val apkPath = "/sdcard/Download/update.apk"

val isValid = FileHashUtils.verify(
    path = apkPath,
    expectedHash = expectedSha256,
    algorithm = FileHashUtils.Algorithm.SHA256,
    ignoreCase = true  // 忽略大小写（默认）
)
when {
    isValid -> installApk(apkPath)
    else -> {
        FileUtils.delete(apkPath)  // 删除损坏文件
        showError("下载文件已损坏，请重新下载")
    }
}

// 异步校验
FileHashUtils.verifyAsync(
    path = apkPath,
    expectedHash = expectedSha256,
    algorithm = FileHashUtils.Algorithm.SHA256
) { valid, actualHash ->
    if (!valid) {
        Log.e("Verify", "期望: $expectedSha256, 实际: $actualHash")
    }
}
```

---

### 4.4 比较两个文件

**场景说明**：去重（找出重复文件）、验证复制是否完整、增量备份时判断文件是否变化。

```kotlin
// === java.io 模式 ===
// 通过哈希对比（不逐字节比较，对大文件高效）
val areSame = FileHashUtils.compareFiles(
    path1 = "/sdcard/original.jpg",
    path2 = "/sdcard/backup/original.jpg",
    algorithm = FileHashUtils.Algorithm.SHA256
)
if (areSame) {
    FileUtils.delete("/sdcard/backup/original.jpg")  // 删除重复备份
}

// 异步
FileHashUtils.compareFilesAsync("/sdcard/a.bin", "/sdcard/b.bin") { same ->
    if (same) Log.d("Compare", "文件完全相同")
}
```

---

### 4.5 字节/字符串哈希

**场景说明**：不涉及文件 I/O，直接对内存中的数据进行哈希运算。

```kotlin
// === java.io 模式 ===
val bytesHash = FileHashUtils.hashBytes(byteArray, FileHashUtils.Algorithm.MD5)
val strHash = FileHashUtils.hashString("hello world", FileHashUtils.Algorithm.SHA256)
// 输出: "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"

// CRC32（快速校验，非加密场景首选）
val crc = FileHashUtils.crc32("payload".toByteArray())
// 输出: "3f08a48b"（8 位十六进制）

// === Okio 模式 ===
val digest = MessageDigest.getInstance("SHA-256")
val bytesHash2 = OkioHashUtils.hashByteString(ByteString.of(*byteArray), digest)
val strHash2 = OkioHashUtils.hashString("hello world", digest)
```

---

### 4.6 复制并同时计算哈希（Okio 独有）

**场景说明**：备份文件时需要确保"备份副本和原始文件完全一致"。传统做法是先复制 → 再分别算哈希 → 再比对，需要两次 I/O。Okio 的 `copyAndHash` 一次读取同时完成复制和哈希计算。

```kotlin
// === Okio 模式 ===
// 一次遍历：复制 + 进度 + 哈希，时间接近单次复制时间
val result = OkioHashUtils.copyAndHash(
    srcPath = "/sdcard/important_video.mp4",     // 源：2GB 视频
    destPath = "/sdcard/backup/important_video.mp4",
    digest = MessageDigest.getInstance("SHA-256"),
    overwrite = true,
    onProgress = { bytesCopied: Long, totalBytes: Long ->
        updateProgressBar((bytesCopied * 100 / totalBytes).toInt())
    }
)
if (result != null) {
    val (hash, success) = result
    if (success) {
        Log.d("Backup", "复制完成，SHA-256: $hash")
        // 可存储 hash 用于后续完整性验证
    }
}

// 异步版本
OkioHashUtils.copyAndHashAsync(
    srcPath = "/sdcard/large.db",
    destPath = "/sdcard/backup/large.db",
    digest = MessageDigest.getInstance("SHA-256"),
    onProgress = { copied, total -> updateUI(copied, total) },
    onResult = { hash, success -> if (success) storeHash(hash) }
)
```

---

### 4.7 复制并验证（Okio 独有）

**场景说明**：在 4.6 的基础上，复制完成后自动计算目标文件的哈希并与源文件哈希比对，返回验证结果。适用于零容错场景（数据库备份、固件烧录）。

```kotlin
// === Okio 模式 ===
// 三步合一：复制 + 哈希 + 验证
val (hash, success, verified) = OkioHashUtils.copyAndVerify(
    srcPath = "/data/data/com.app/databases/main.db",
    destPath = "/sdcard/backups/main_20260710.db",
    digest = MessageDigest.getInstance("SHA-256")
)
when {
    !success -> showError("复制失败，请检查存储空间")
    !verified -> showError("复制后验证不通过！副本可能与源文件不一致")
    else -> showToast("备份成功，SHA-256: $hash")
}
```

---

### 4.8 Gzip 压缩并计算哈希（Okio 独有）

**场景说明**：压缩日志文件同时计算压缩后数据的哈希值，用于归档上传场景。

```kotlin
// === Okio 模式 ===
// 压缩 500MB 日志为 .gz，同时算哈希，带进度
val (hash, ok) = OkioHashUtils.gzipAndHash(
    srcPath = "/sdcard/logs/app_20260710.log",       // 500MB 原始日志
    destPath = "/sdcard/logs/archive/app_20260710.log.gz",
    digest = MessageDigest.getInstance("SHA-256"),
    onProgress = { bytesProcessed: Long, totalBytes: Long ->
        val percent = (bytesProcessed * 100 / totalBytes).toInt()
        updateProgressBar(percent)
    }
)
if (ok) {
    // hash 是压缩后 .gz 文件的 SHA-256（用于上传后校验）
    uploadToServer("/sdcard/logs/archive/app_20260710.log.gz", hash!!)
}
```

---

### 4.9 写入时哈希（Okio 独有）

**场景说明**：边下载边算哈希，下载完成即获得哈希值，无需下载完再读一遍。

```kotlin
// === Okio 模式 ===
// HashingSink 在数据流入磁盘的同时更新 MessageDigest
val result = OkioHashUtils.hashWhileWriting(
    path = "/sdcard/downloads/file.bin",
    data = downloadedBytes,
    digest = MessageDigest.getInstance("MD5")
)
if (result != null) {
    val (hash, success) = result
    if (success) {
        Log.d("Download", "下载完成，MD5: $hash")
        // 可与服务器提供的 MD5 对比
    }
}
```

---

## 5. Assets / Raw 资源读取

### 5.1 列出与检查 Assets 资源

**场景说明**：APK 内置资源需要在运行时发现和遍历（如读取 `assets/plugins/` 下所有插件、读取 `assets/themes/` 下的主题列表）。

```kotlin
// === java.io 模式 ===
// 1. 列出 assets 根目录
val rootFiles = AssetUtils.listAssets(context, "")
// rootFiles = ["config.json", "data", "images", "fonts"]

// 2. 列出子目录
val dataFiles = AssetUtils.listAssets(context, "data")

// 3. 递归列出所有文件（最大深度 10 层）
val allAssets = AssetUtils.listAssetsRecursive(context, path = "", maxDepth = 10)
allAssets.forEach { path -> Log.d("Assets", path) }

// 4. 检查是否存在（通过尝试打开文件判断）
if (AssetUtils.assetExists(context, "templates/report_template.html")) {
    loadTemplate()
}

// 5. 判断是文件还是目录
val isDir = AssetUtils.isAssetDirectory(context, "data")

// 6. 获取信息
val info = AssetUtils.getAssetInfo(context, "images/logo.png")

// 异步
AssetUtils.listAssetsRecursiveAsync(context, "plugins") { files -> }
AssetUtils.assetExistsAsync(context, "config.json") { exists -> }
```

**已知限制**：Android `AssetManager.list()` 对深层目录可能返回空数组（即使确实有文件），这是 Android 平台的限制，非本库问题。

---

### 5.2 读取 Assets 文件内容

```kotlin
// === java.io 模式 ===
// 1. 读取为字符串——适用于 JSON/XML/HTML/文本
val configJson = AssetUtils.readAssetText(context, "config/default_config.json")
if (configJson != null) {
    val config = Gson().fromJson(configJson, AppConfig::class.java)
}

// 2. 读取为字节数组——适用于图片/字体/二进制
val fontBytes = AssetUtils.readAssetBytes(context, "fonts/Roboto-Regular.ttf")

// 3. 按行读取——适用于词表、字典
val words = AssetUtils.readAssetLines(context, "data/wordlist.txt")
words?.forEach { dictionary.add(it.trim()) }

// 4. 流式逐行——适用于 assets 中的大型 CSV
AssetUtils.readAssetLinesStreaming(
    context = context,
    assetPath = "data/cities.csv",
    onEachLine = { line, index ->
        if (line.isNotBlank()) {
            val (name, lat, lon) = line.split(",")
            cityIndex.add(City(name, lat.toDouble(), lon.toDouble()))
        }
        true  // 继续
    }
)

// 异步
AssetUtils.readAssetTextAsync(context, "data/config.json") { content, error -> }
AssetUtils.readAssetBytesAsync(context, "images/logo.png") { bytes, error -> }

// === Okio 模式 ===
// ByteString（不可变，带便捷编码方法）
val byteStr = OkioAssetUtils.readAssetByteString(context, "images/hero.jpg")
println("Base64: ${byteStr?.base64()}")   // 可直接用于 data URI

// 字符串
val text = OkioAssetUtils.readAssetUtf8(context, "data/config.json")

// 可变 Buffer（需要拼接或修改）
val buffer = OkioAssetUtils.readAssetToBuffer(context, "templates/base.html")
buffer?.apply {
    writeUtf8("\n<!-- rendered at ${System.currentTimeMillis()} -->")
}

// 流式逐行（BufferedSource.readUtf8Line() 提供更好的 UTF-8 解码性能）
OkioAssetUtils.readAssetLinesStreaming(context, "data/large.csv") { line, index ->
    processLine(line)
    true
}
```

---

### 5.3 复制 Assets 到文件系统

**场景说明**：将 APK 内置的数据库模板、模型文件、默认配置等复制到可读写的文件系统路径。

```kotlin
// === java.io 模式 ===
// 1. 复制单个文件（内部使用原子写入）
AssetUtils.copyAssetToFile(
    context = context,
    assetPath = "templates/app.db",
    destPath = "/sdcard/MyApp/databases/app.db"
)

// 2. 带进度复制——适用于 assets 中的大型文件（如 ML 模型）
AssetUtils.copyAssetToFileWithProgress(
    context = context,
    assetPath = "models/object_detection.tflite",  // 假设 50MB
    destPath = "/sdcard/MyApp/models/detect.tflite",
    onProgress = { bytesCopied: Long, totalBytes: Long ->
        val percent = (bytesCopied * 100 / totalBytes).toInt()
        TaskExecutor.main { initProgressBar.progress = percent }
    }
)

// 3. 复制整个 assets 目录
val copiedCount = AssetUtils.copyAssetDirToFile(
    context = context,
    assetDirPath = "templates",
    destDirPath = "/sdcard/MyApp/templates",
    overwrite = true,
    onProgress = { current: Int, estimatedTotal: Int ->
        // current: 已复制文件数, estimatedTotal: 预估总文件数
    }
)
Log.d("Asset", "复制了 $copiedCount 个文件")

// 异步
AssetUtils.copyAssetToFileAsync(context, "data/init.db", "/sdcard/app.db") { success -> }

// === Okio 模式 ===
// BufferedSink.writeAll 高效零拷贝写入
OkioAssetUtils.copyAssetToFile(context, "models/model.tflite", "/sdcard/model.tflite")

// 带进度（ForwardingSource 追踪）
OkioAssetUtils.copyAssetToFileWithProgress(
    context, "huge_data.bin", "/sdcard/data.bin",
    onProgress = { copied, total -> updateUI(copied, total) }
)
```

---

### 5.4 读取 Raw 资源

**场景说明**：读取 `res/raw/` 下的资源文件（通过 `R.raw.xxx` 资源 ID 访问）。

```kotlin
// === java.io 模式 ===
val licenseText = AssetUtils.readRawText(context, R.raw.license)
val soundBytes = AssetUtils.readRawBytes(context, R.raw.sound_effect)
val rawSize = AssetUtils.getRawSize(context, R.raw.large_asset)

// 异步
AssetUtils.readRawBytesAsync(context, R.raw.data) { bytes, error -> }

// === Okio 模式 ===
val byteStr = OkioAssetUtils.readRawByteString(context, R.raw.license)
val text2 = OkioAssetUtils.readRawUtf8(context, R.raw.license)
val buffer = OkioAssetUtils.readRawToBuffer(context, R.raw.data)
```

---

### 5.5 复制 Raw 资源到文件系统

```kotlin
// === java.io 模式 ===
AssetUtils.copyRawToFile(context, R.raw.default_avatar, "/sdcard/MyApp/avatar.png")

// 带进度
AssetUtils.copyRawToFileWithProgress(
    context, R.raw.large_asset, "/sdcard/data.bin",
    onProgress = { copied, total -> updateUI(copied, total) }
)

// === Okio 模式 ===
OkioAssetUtils.copyRawToFile(context, R.raw.default_avatar, "/sdcard/avatar.png")
OkioAssetUtils.copyRawToFileWithProgress(
    context, R.raw.huge_data, "/sdcard/data.bin",
    onProgress = { copied, total -> updateUI(copied, total) }
)
```

---

### 5.6 Assets 超时读取与 Gzip 解压（Okio 独有）

```kotlin
// === Okio 模式 ===
// 1. 超时读取——适用于慢速设备或大型 assets
val data = OkioAssetUtils.readAssetWithTimeout(context, "large_db.sql", 30_000)

// 2. Gzip 解压——APK 中存 .gz 节省体积，运行时解压
//    assets/data/records.json.gz (2MB) → 解压 → records.json (20MB)
val jsonBytes = OkioAssetUtils.readAssetGzip(context, "data/records.json.gz")
val jsonText = OkioAssetUtils.readAssetGzipAsText(context, "data/records.json.gz")

// 3. 带进度的分块读取
val bytes = OkioAssetUtils.readAssetWithProgress(
    context, "huge_model.bin",
    onProgress = { read, total -> updateProgressBar((read * 100 / total).toInt()) }
)
```

---

## 6. 文件清理（生命周期驱动）

`FileCleanupManager` 是一个独立的清理子系统，用来按时间或应用生命周期条件自动清理本地文件，适合缓存管理、临时文件清理、日志轮转等场景。

### 6.1 应用启动时清理

**场景说明**：每次启动时清空临时目录、删除上次运行遗留的中间文件。

```kotlin
// Application.onCreate() 中注册
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val startupConfig = FileCleanupManager.builder()
            // 清空缓存目录内容，保留目录自身
            .clearOnAppStart("clear_cache", cacheDir.absolutePath)
            // 删除上次崩溃遗留的临时文件
            .deleteOnAppStart("crash_temp",
                File(cacheDir, "crash_temp.txt").absolutePath)
            // 清空图片预览缓存
            .clearOnAppStart("image_preview",
                File(cacheDir, "image_preview").absolutePath)
            .build()

        FileCleanupManager.register(this, startupConfig) { result ->
            Log.d("Cleanup", "[${result.key}] ${result.message} " +
                "(deleted: ${result.deletedEntries})")
        }
    }
}
```

---

### 6.2 应用进入后台时清理

**场景说明**：用户切到后台时清理敏感数据、释放内存缓存对应的磁盘文件。需要传入 `Application` 实例以监听生命周期。

```kotlin
val bgConfig = FileCleanupManager.builder()
    // 后台时清理预览图缓存（用户离开后不再需要）
    .clearOnAppBackground("preview_cache",
        File(cacheDir, "preview").absolutePath)
    // 后台时删除草稿中的敏感图片
    .deleteOnAppBackground("draft_images",
        File(cacheDir, "draft_images").absolutePath)
    .build()

// 必须使用 Application 重载才能监听前后台切换
FileCleanupManager.register(application, bgConfig) { result ->
    Log.d("Cleanup", "[${result.key}] ${result.message}")
}
```

**原理**：`FileCleanupManager` 内部通过 `ActivityLifecycleCallbacks` 追踪 started Activities 计数。当计数归零且非配置变更时，判定应用进入后台，触发 `OnAppBackground` 规则。

---

### 6.3 延迟清理

**场景说明**：用户生成一个导出文件，提供"稍后自动删除"功能（如分享后的临时文件）。

```kotlin
// 场景：导出报告 → 分享到微信 → 30 秒后自动删除
val exportConfig = FileCleanupManager.builder()
    // 30 秒后删除——ONE_SHOT 模式，执行后自动释放
    .deleteAfterDelay(
        key = "shared_export",
        path = File(cacheDir, "export_${timestamp}.pdf").absolutePath,
        delayMs = 30_000L,
        scheduleMode = CleanupScheduleMode.ONE_SHOT
    )
    .build()

// 可选：不需要 Application 实例（不依赖前后台事件）
FileCleanupManager.register(exportConfig) { result ->
    Log.d("Cleanup", "临时文件已自动删除: ${result.message}")
}

// 场景：每 1 小时清空一次缓存（循环模式）
val recurringConfig = FileCleanupManager.builder()
    .clearAfterDelay(
        key = "hourly_cache_clean",
        path = File(cacheDir, "recurring").absolutePath,
        delayMs = 3600_000L,  // 1 小时
        scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
    )
    .build()
FileCleanupManager.register(recurringConfig) { result ->
    Log.d("Cleanup", "定时清空完成，下次执行: 1 小时后")
}
```

---

### 6.4 按天数清理（跨重启持久化）

**场景说明**：日志保留 7 天、下载文件保留 3 天。即使应用重启或设备重启，计时也不会重置（存储在 SharedPreferences 中）。

```kotlin
// 日志轮转：保留最近 7 天的日志
val logConfig = FileCleanupManager.builder()
    .clearAfterDays(
        key = "logs_7d",
        path = File(filesDir, "logs").absolutePath,
        days = 7,
        persistAcrossRestarts = true,   // 重启后继续计时
        scheduleMode = CleanupScheduleMode.ONE_SHOT
    )
    // 下载文件 3 天后删除
    .deleteAfterDays(
        key = "downloads_3d",
        path = File(filesDir, "downloads").absolutePath,
        days = 3,
        persistAcrossRestarts = true,
        scheduleMode = CleanupScheduleMode.ONE_SHOT
    )
    .build()

// 必须传 Application（跨重启持久化需要 SharedPreferences）
FileCleanupManager.register(application, logConfig)
```

**持久化原理**：

```
注册时：
  SharedPreferences.putLong("deadline_logs_7d", now + 7天的毫秒数)

每次启动时：
  storedDeadline = SharedPreferences.getLong("deadline_logs_7d")
  remainingMs = storedDeadline - System.currentTimeMillis()
  if (remainingMs <= 0) → 立即执行
  else → 继续等待 remainingMs

执行后（ONE_SHOT 模式）：
  SharedPreferences.remove("deadline_logs_7d")   // 清理持久化记录
```

---

### 6.5 指定时间点清理

**场景说明**：设置一个未来的绝对时间点执行清理（如每天凌晨 3 点清理）。

```kotlin
// 计算今天凌晨 3 点的时间戳
val calendar = Calendar.getInstance().apply {
    if (get(Calendar.HOUR_OF_DAY) >= 3) add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 3)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

val midnightConfig = FileCleanupManager.builder()
    .clearAtTime("daily_clean", cacheDir.absolutePath, calendar.timeInMillis)
    .build()

FileCleanupManager.register(midnightConfig)
```

**注意**：`AtTimeMillis` 不支持 `RESTART_AFTER_EXECUTION`，执行后自动释放。如需每天执行，用 `AfterDays(days = 1, scheduleMode = RESTART_AFTER_EXECUTION)` 替代。

---

### 6.6 立即执行（runNow）

**场景说明**：用户点击"清理缓存"按钮时立即执行，不等待触发条件。

```kotlin
// 最简单的用法——立即在 I/O 线程执行
fun onCleanCacheClicked() {
    val config = FileCleanupManager.builder()
        .clearOnAppStart("user_clean", cacheDir.absolutePath)
        .build()

    val futures = FileCleanupManager.runNow(config) { result ->
        TaskExecutor.main {
            if (result.success) {
                showToast("清理完成，释放了 ${result.deletedEntries} 个文件")
            } else {
                showToast("清理失败: ${result.message}")
            }
        }
    }
}

// 绑定 Activity——Activity 销毁时自动取消未完成的任务
class SettingsActivity : AppCompatActivity() {
    fun cleanCache() {
        val config = FileCleanupManager.builder()
            .clearOnAppStart("settings_clean", cacheDir.absolutePath)
            .build()

        FileCleanupManager.runNow(this, config) { result ->
            // Activity 被销毁后此回调不会执行
            TaskExecutor.main { updateUI(result) }
        }
    }
}

// 绑定 Fragment——Fragment 销毁时自动取消
FileCleanupManager.runNow(
    fragment = myFragment,
    config = config,
    lifecycleScope = CleanupLifecycleScope.FRAGMENT
) { result -> }
```

---

### 6.7 权限处理

**场景说明**：清理外部存储（如 `/sdcard/`）或 Android 11+ 的受限目录时可能因权限不足失败。`onPermissionRequired` 回调让宿主应用处理权限申请。

```kotlin
// 完整权限处理示例
class MyActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // 权限结果由 FileCleanupManager 的 retry/cancel 处理
    }

    fun setupCleanup() {
        val callbacks = CleanupCallbacks(
            onResult = { result ->
                Log.d("Cleanup", "success=${result.success}, ${result.message}")
                if (result.error != null) {
                    Log.e("Cleanup", "Error", result.error)
                }
            },
            onPermissionRequired = { request ->
                Log.w("Cleanup",
                    "权限不足: ${request.reason}, 需要: ${request.suggestedPermissions}")

                if (request.requiresSpecialSettings) {
                    // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
                    // 引导用户到系统设置页面
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                    // 用户从设置返回后调用 request.retry() 重试
                } else if (request.suggestedPermissions.isNotEmpty()) {
                    // 请求标准运行时权限
                    permissionLauncher.launch(request.suggestedPermissions.toTypedArray())
                    // 权限结果回调中调用 request.retry() 或 request.cancel()
                } else {
                    // 无法处理的权限问题，放弃
                    request.cancel()
                }
            }
        )

        val config = FileCleanupManager.builder()
            .clearOnAppStart("external_clean", "/sdcard/MyApp/temp")
            .build()

        FileCleanupManager.register(application, config, callbacks)
    }

    // 权限授予后
    fun onPermissionGranted() {
        // 需要在权限回调中拿到对应的 CleanupPermissionRequest 并调用 retry()
        // 建议在 onPermissionRequired 中保存 request 引用
    }
}
```

---

### 6.8 生命周期绑定详解

**场景说明**：不同生命周期的绑定策略适应不同使用场景。

```kotlin
// 1. Application 级别——全局任务，应用进程存活期间有效
FileCleanupManager.register(application, globalConfig) { result -> }

// 2. Activity 级别——Activity 销毁时自动取消
//    适合：页面级临时文件清理
class EditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = FileCleanupManager.builder()
            .deleteAfterDelay("editor_temp",
                File(cacheDir, "editor_${sessionId}.tmp").absolutePath,
                600_000L)
            .build()
        FileCleanupManager.register(this, config) { result -> }
        // Activity.onDestroy() 时自动取消此任务
    }
}

// 3. Fragment 级别——Fragment 销毁时自动取消
//    适合：Fragment 内部的临时文件
FileCleanupManager.register(
    fragment = imagePickerFragment,
    config = cameraConfig,
    lifecycleScope = CleanupLifecycleScope.FRAGMENT
) { result -> }

// 4. Fragment + Activity Scope——Fragment 存在期间有效
//    （跟随宿主 Activity 生命周期）
FileCleanupManager.register(
    fragment = viewerFragment,
    config = tempConfig,
    lifecycleScope = CleanupLifecycleScope.ACTIVITY
) { result -> }
```

---

### 6.9 取消与释放

**场景说明**：主动取消不需要的清理任务，释放线程、持久化存储等资源。

```kotlin
// 1. 取消单个任务（不再执行但保留持久化记录）
FileCleanupManager.cancel("cache_startup")
// 返回 true 表示成功取消了一个待执行任务

// 2. 取消全部
FileCleanupManager.cancelAll()

// 3. 释放单条规则（取消 + 清除 SharedPreferences 持久化 + 移除回调引用）
FileCleanupManager.release("logs_7d")

// 4. 释放整组配置中的所有规则
FileCleanupManager.release(config)  // 返回实际释放数

// 5. 释放绑定到 Activity 的全部规则
override fun onDestroy() {
    super.onDestroy()
    FileCleanupManager.release(this)  // 返回释放数量
}

// 6. 释放绑定到 Fragment 的全部规则
FileCleanupManager.release(fragment, CleanupLifecycleScope.FRAGMENT)

// 7. 释放所有资源
FileCleanupManager.releaseAll()  // 等同于 cancelAll()
```

**cancel vs release 的区别**：
- `cancel`：仅取消定时器/线程任务，不清除持久化记录。下次注册同 key 规则时仍会读取旧 deadline。
- `release`：取消 + 清除持久化 + 移除内部引用。下次注册同 key 规则时视为全新注册。

---

## 模式选择速查表

| 使用场景 | 推荐模式 | 原因 |
|---|---|---|
| 读写小型配置文件（< 1MB） | java.io | 简单直接，代码量少 |
| 复制/移动大文件（> 10MB） | Okio | Buffer 零拷贝，2-3x 吞吐 |
| 需要超时控制的 I/O | Okio | I/O 层面 Timeout，java.io 无法做 |
| 需要进度的读写操作 | 首选 Okio | ForwardingSource/Sink 无侵入追踪 |
| 边读边写边计算哈希 | Okio | HashingSource/Sink 一次遍历 |
| Gzip 压缩/解压 | Okio | 内置流式 Gzip |
| 复制后自动验证完整性 | Okio | copyAndVerify 组合操作 |
| 不可变字节共享 | Okio | ByteString 线程安全 |
| Assets/Raw 资源操作 | 任选 | 小用 java.io，大用 Okio |
| 生命周期驱动的自动清理 | FileCleanupManager | 独立子系统，与 I/O 模式无关 |
| 不想引入额外依赖（APK 体积） | java.io | 零额外依赖 |

---

## 同步 vs 异步

两种模式的所有耗时方法均提供 `Async` 后缀的异步版本，通过 `TaskExecutor` 在 I/O 线程池执行：

```kotlin
// === 同步（适合已在 I/O 线程的场景） ===
thread {
    val text = FileReadUtils.readText("/sdcard/data.json")
    // 当前已在后台线程，可直接处理
    processData(text)
}

// === 异步（适合从 UI 线程发起） ===
fun onButtonClick() {
    // 直接从 UI 线程调用异步方法
    FileReadUtils.readTextAsync("/sdcard/data.json") { content, error ->
        when {
            error != null -> {
                TaskExecutor.main { showError(error.message) }
            }
            content != null -> {
                TaskExecutor.main { textView.text = content }
            }
        }
    }

    // 生命周期管理：保存 Future 引用以便在 onDestroy 时取消
    pendingRead = FileReadUtils.readTextAsync("/sdcard/large.json") { content, error ->
        // 如果 Activity 已销毁，不再更新 UI
        if (isDestroyed) return@readTextAsync
        TaskExecutor.main { updateUI(content) }
    }
}

override fun onDestroy() {
    super.onDestroy()
    pendingRead?.cancel(true)  // 取消未完成的异步任务
}
```

**回调线程注意事项**：
- 所有 `Async` 方法的回调在 **I/O 线程** 执行（通过 `TaskExecutor.io`）
- 更新 UI 需要手动切换到主线程：`TaskExecutor.main { ... }`
- 回调中抛出的异常会被内部 `safeCallback` 捕获并打印堆栈，不会导致崩溃
