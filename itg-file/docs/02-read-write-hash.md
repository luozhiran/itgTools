# 02. 读写与 Hash

本节说明文件读取、写入和摘要计算。

## 适用条件

- 小文件可一次性读写，大文件应使用分块、流式或 Okio。
- 需要写配置、日志、缓存或二进制数据。
- 需要校验文件内容是否一致。

## 推荐做法

```kotlin
val text = FileReadUtils.readText(path)
FileWriteUtils.writeText(path, text)
val sha256 = FileHashUtils.sha256(path)
```

## 可复制 Demo

```kotlin
import com.itg.itg_file.hash.FileHashUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.write.FileWriteUtils

val file = context.filesDir.resolve("config.json").absolutePath
FileWriteUtils.writeText(file, "{\"debug\":true}")

val text = FileReadUtils.readText(file) ?: return
val sha256 = FileHashUtils.sha256(file)

check(text.contains("debug"))
check(!sha256.isNullOrBlank())
```

## 关键说明

- `readBytes(path, maxBytes)` 有内存上限，避免大文件一次性进入内存。
- 日志尾部读取可用 `readTailBytes/readTailLines`。
- 大文件处理优先用 `readChunks/readLinesStreaming`。
- Hash 计算需要完整读取文件，建议后台线程执行。
- 写入失败通常返回 `false` 或 `null`，业务要处理失败分支。

## 验证方式

- 写入后读取内容应与预期一致。
- 修改文件内容后 hash 应变化。

[返回 README](../README.md)