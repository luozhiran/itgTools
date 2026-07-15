# 04. 清理任务与 API

本节说明缓存清理能力和 API 分类。

## 适用条件

- 需要清理缓存目录、临时文件或过期文件。
- 清理规则需要按启动、后台、延迟、天数或指定时间触发。
- 需要绑定 Application、Activity 或 Fragment 生命周期。

## 推荐做法

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppStart("cache", context.cacheDir.absolutePath)
    .build()
FileCleanupManager.runNow(config) { result -> println(result.success) }
```

## 可复制 Demo

```kotlin
import com.itg.itg_file.cleanup.FileCleanupManager

val config = FileCleanupManager.builder()
    .clearAfterDays(
        key = "old-cache",
        path = context.cacheDir.absolutePath,
        days = 7,
        persistAcrossRestarts = true
    )
    .build()

FileCleanupManager.register(application, config) { result ->
    println("cleanup ${result.key}: success=${result.success}, deleted=${result.deletedEntries}")
}

// 需要立即执行时：
FileCleanupManager.runNow(config) { result ->
    println("runNow ${result.key}: ${result.message}")
}
```

## API 速查

| 分类 | 工具类 | 常用能力 |
| --- | --- | --- |
| 核心文件 | `FileUtils` | 创建、删除、复制、移动、列表、大小、MIME、空间 |
| 读取 | `FileReadUtils` | 文本、字节、行、分块、头尾读取、Uri/InputStream |
| 写入 | `FileWriteUtils` | 文本、字节、追加、流式、安全写入 |
| Hash | `FileHashUtils` / `OkioHashUtils` | MD5、SHA、CRC、通用 hash |
| Assets | `AssetUtils` / `OkioAssetUtils` | 读取 assets、复制 assets、进度复制 |
| Okio | `OkioFileUtils` / `OkioReadUtils` / `OkioWriteUtils` | Okio 版本文件操作 |
| 清理 | `FileCleanupManager` / `CleanupExecutor` | 规则清理、生命周期绑定、取消和释放 |

## 关键说明

- 清理是破坏性操作，必须确认目录属于 app 缓存或明确授权范围。
- `clear*` 动作清空目录内容但保留目录；`delete*` 会删除目标文件或目录。
- `OnAppBackground` 和跨重启计时需要通过 `register(application, config)` 注册。
- Activity/Fragment 重载会在宿主销毁时自动释放规则。
- 权限不足时可通过 `CleanupCallbacks.onPermissionRequired` 交给宿主申请权限。

## 验证方式

- 清理前后分别记录目录大小和文件数量。
- 关键文件应加入白名单或放在不会被清理的目录。
- 不再需要规则时调用 `cancel(key)`、`release(config)` 或 `releaseAll()`。

[返回 README](../README.md)