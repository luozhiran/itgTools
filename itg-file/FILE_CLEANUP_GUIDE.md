# FileCleanupManager 使用指南

> **文档导航：** [← 返回 README](./README.md) ｜ [使用场景实例（含完整代码示例）](./demo.md#6-文件清理生命周期驱动)

`FileCleanupManager` 用来按条件清理本地文件或目录。适合缓存、临时文件、过期导出文件、日志轮转等场景。

## 能做什么

- 应用启动后立即清理
- 应用切到后台时清理
- 延迟一段时间后清理
- 到指定时间点后清理
- 进入 Activity / Fragment 生命周期后自动释放任务
- 处理权限不足时的重试流程

## 快速使用

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppStart("cache_startup", cacheDir.absolutePath)
    .clearOnAppBackground("cache_bg", File(cacheDir, "preview").absolutePath)
    .deleteAfterDelay("temp_export", File(cacheDir, "export.zip").absolutePath, 30_000L)
    .clearAfterDays("logs_7d", File(filesDir, "logs").absolutePath, days = 7, persistAcrossRestarts = true)
    .build()

FileCleanupManager.register(application, config) { result ->
    Log.d("Cleanup", result.message)
}
```

## 触发器

| 触发器 | 说明 |
|---|---|
| `OnAppStart` | 应用启动后立即执行 |
| `OnAppBackground` | 应用进入后台时执行 |
| `AfterDelay` | 延迟指定毫秒后执行 |
| `AfterDays` | 按天数到期后执行 |
| `AtTimeMillis` | 到达指定时间点后执行 |

## 动作

| 动作 | 说明 |
|---|---|
| `CLEAR_DIRECTORY` | 清空目录内容，保留目录本身 |
| `DELETE_TARGET` | 直接删除目标文件或目录 |

## 常用场景

### 启动即清理缓存

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppStart("startup_cache", cacheDir.absolutePath)
    .build()
```

### 后台清理临时目录

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppBackground("bg_cache", File(cacheDir, "preview").absolutePath)
    .build()
```

### 延迟删除导出文件

```kotlin
val config = FileCleanupManager.builder()
    .deleteAfterDelay("export_file", File(cacheDir, "export.zip").absolutePath, 10_000L)
    .build()
```

### 保留 N 天后清理

```kotlin
val config = FileCleanupManager.builder()
    .clearAfterDays(
        key = "logs_7d",
        path = File(filesDir, "logs").absolutePath,
        days = 7,
        persistAcrossRestarts = true
    )
    .build()
```

### 指定时间点清理

```kotlin
val targetTime = System.currentTimeMillis() + 60_000L
val config = FileCleanupManager.builder()
    .deleteAtTime("one_minute_later", File(cacheDir, "temp.tmp").absolutePath, targetTime)
    .build()
```

### 立即执行

```kotlin
FileCleanupManager.runNow(config) { result ->
    Log.d("Cleanup", "success=${result.success}, deleted=${result.deletedEntries}")
}
```

## 权限处理

清理外部存储或受限路径时，可能会收到 `onPermissionRequired` 回调。建议在回调里：

1. 保存 `CleanupPermissionRequest`
2. 申请运行时权限或引导用户打开特殊权限页面
3. 授权成功后调用 `request.retry()`
4. 拒绝或用户取消时调用 `request.cancel()`

示例：

```kotlin
val callbacks = CleanupCallbacks(
    onPermissionRequired = { request ->
        if (request.suggestedPermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(request.suggestedPermissions.toTypedArray())
        } else {
            request.cancel()
        }
    },
    onResult = { result ->
        if (!result.success) {
            Log.e("Cleanup", result.message, result.error)
        }
    }
)
```

## 资源释放

- `cancel(key)`：取消未执行任务
- `cancelAll()`：取消全部任务
- `release(key)`：取消并释放单个规则
- `release(config)`：释放整组规则
- `release(activity)` / `release(fragment, scope)`：释放生命周期绑定任务

## 设计建议

- 只把可丢弃的数据交给清理器，比如缓存、临时文件、导出中间产物。
- 不要把用户不可恢复的数据交给自动清理。
- 需要跨重启保留到期时间时，使用 `persistAcrossRestarts = true`。
- 系统级长期定时任务更适合 `WorkManager`，不是这个管理器的职责。

## 结果字段

`CleanupResult` 常见字段：

- `success`
- `deletedEntries`
- `existedBefore`
- `existedAfter`
- `message`
- `error`

## 入口建议

优先在 `Application.onCreate()` 完成注册。`Activity` 和 `Fragment` 绑定场景适合页面级临时任务，页面销毁后会自动释放关联资源。
