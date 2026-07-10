# ITG File

Android 本地文件处理工具库，提供 `java.io` 和 `Okio 2.9.0` 两套实现，覆盖文件读写、复制移动、哈希校验、资源读取和生命周期清理。

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Okio](https://img.shields.io/badge/Okio-2.9.0-orange.svg)](https://square.github.io/okio/)

## 文档导航

- [快速开始](#快速开始)
- [模块总览](#模块总览)
- [选择建议](#选择建议)
- [安全与性能](#安全与性能)
- [清理指南](./FILE_CLEANUP_GUIDE.md)
- [旧版说明](./README1.md)

## 快速开始

```kotlin
dependencies {
    implementation(project(":itg-file"))
}
```

### 同步调用

```kotlin
import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.write.FileWriteUtils

FileWriteUtils.writeText("/sdcard/demo.txt", "hello")
val text = FileReadUtils.readText("/sdcard/demo.txt")
val info = FileUtils.getFileInfo("/sdcard/demo.txt")
```

### 异步调用

```kotlin
FileReadUtils.readTextAsync("/sdcard/demo.txt") { content, error ->
    if (error == null) {
        TaskExecutor.main { textView.text = content }
    }
}
```

## 模块总览

| 模块 | 作用 |
|---|---|
| `core/FileUtils` | 文件和目录基础操作，含复制、移动、列表、信息查询 |
| `core/OkioFileUtils` | 基于 Okio 的高效文件操作 |
| `read/FileReadUtils` | `java.io` 读取文本、字节、按行、分块读取 |
| `read/OkioReadUtils` | Okio 读取、超时、Gzip、进度读取 |
| `write/FileWriteUtils` | `java.io` 写入、追加、原子写、URI 写入 |
| `write/OkioWriteUtils` | Okio 写入、超时、Gzip、原子写 |
| `hash/FileHashUtils` | MD5/SHA/CRC32、验证、文件比较 |
| `hash/OkioHashUtils` | 流式哈希、复制+哈希、Gzip+哈希 |
| `resource/AssetUtils` | Assets / Raw 资源读取与复制 |
| `resource/OkioAssetUtils` | Okio 版资源读取与复制 |
| `cleanup/FileCleanupManager` | 基于生命周期的本地文件清理 |

## 选择建议

| 场景 | 推荐 |
|---|---|
| 小文件、简单读写、对依赖敏感 | `java.io` 版本 |
| 大文件、频繁 I/O、需要超时或流式哈希 | `Okio` 版本 |
| 需要资源文件复制、按行读取 | `AssetUtils` / `OkioAssetUtils` |
| 需要自动清理缓存、临时文件、过期数据 | `FileCleanupManager` |

## 使用示例

### 读写文本

```kotlin
val saved = FileWriteUtils.writeTextAtomic("/sdcard/config.json", json)
val json = FileReadUtils.readText("/sdcard/config.json")
```

### 分块处理大文件

```kotlin
FileReadUtils.readChunks("/sdcard/big.bin", chunkSize = 1024 * 1024) { chunk, index, total ->
    uploadChunk(chunk, index, total)
    true
}
```

### 计算哈希

```kotlin
val sha256 = FileHashUtils.sha256("/sdcard/app.apk")
val ok = FileHashUtils.verify("/sdcard/app.apk", expectedHash)
```

### 复制资源到文件

```kotlin
AssetUtils.copyRawToFile(context, R.raw.license, "/sdcard/license.txt")
```

## 安全与性能

- 默认优先使用原子写入，避免写到一半崩溃导致文件损坏。
- 大文件读取请用 `readChunks`、`readLinesStreaming` 或 Okio 的流式接口，不要直接全量读入内存。
- 所有异步接口都返回 `Future<*>`，回调建议在主线程重新分发 UI。
- 回调里不要抛异常；库内部已尽量做了兜底，但业务侧仍应做最小化处理。
- 处理外部存储或受用户授权的 URI 时，优先走 `ContentResolver` / `DocumentFile` 这类受控路径。

## 版本说明

- 当前 `Okio` 依赖：`2.9.0`
- 异步执行：`itg-thread-pools`
- 文档中较详细的文件清理说明单独放在 [FILE_CLEANUP_GUIDE.md](./FILE_CLEANUP_GUIDE.md)

## 许可证

MIT License
