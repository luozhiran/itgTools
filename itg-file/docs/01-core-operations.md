# 01. 核心文件操作

本节说明 `FileUtils` 的文件和目录管理能力。

## 适用条件

- 操作 app 私有目录、缓存目录或已授权路径。
- 需要创建、复制、移动、重命名、删除、清空目录。
- 需要读取大小、扩展名、MIME、修改时间或空间信息。

## 推荐做法

```kotlin
FileUtils.createDirectory(dir)
FileUtils.copy(src, dest, overwrite = true)
```

## 可复制 Demo

```kotlin
import com.itg.itg_file.core.FileUtils

val cacheDir = context.cacheDir.resolve("export").absolutePath
FileUtils.createDirectory(cacheDir)

val src = context.filesDir.resolve("input.txt").absolutePath
val dest = context.cacheDir.resolve("export/input.txt").absolutePath
val copied = FileUtils.copy(src, dest, overwrite = true)

if (copied) {
    val info = FileUtils.getFileInfo(dest)
    val size = FileUtils.getSizeFormatted(dest)
}
```

## 关键说明

- 删除、清空、移动都是破坏性操作，调用前必须确认路径范围。
- `copyDirectory` 源目录和目标目录不能互相嵌套，否则可能造成递归复制风险。
- `copyWithProgress` 回调线程取决于调用线程；异步版本回调不保证主线程。
- `getMimeType(context, uri)` 适合 `content://` Uri。
- 异步方法一般返回 `Future<*>`，可取消但取消不等于已回滚部分文件操作。

## 验证方式

- 写操作后用 `FileUtils.exists/isFile/isDirectory` 或 Java `File.exists()` 校验。
- 移动或删除前输出绝对路径，避免误删上级目录。

[返回 README](../README.md)