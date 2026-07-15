# 02. 普通任务与主线程

本节解决普通非 suspend 任务应该使用哪个入口。

## API 选择

| 场景 | API | 说明 |
| --- | --- | --- |
| I/O 密集任务 | `Concurrent.io { }` | 网络同步调用、文件、数据库 |
| CPU 密集任务 | `Concurrent.compute { }` | 图片处理、加解密、大量数据转换 |
| 通用后台任务 | `Concurrent.background { }` | 清理、统计、预处理 |
| 主线程任务 | `Concurrent.main { }` | UI 更新或主线程 API 调用 |

## I/O 任务

```kotlin
Concurrent.io {
    val config = loadConfigFromDisk()
    Concurrent.main {
        render(config)
    }
}
```

## CPU 计算任务

```kotlin
Concurrent.compute {
    val digest = md5(bytes)
    Concurrent.main {
        showDigest(digest)
    }
}
```

## 通用后台任务

```kotlin
Concurrent.background {
    cleanupExpiredCache()
}
```

## 主线程任务

```kotlin
Concurrent.main {
    textView.text = "done"
}
```

`Concurrent.main { }` 会先判断当前线程：

- 已经在主线程：直接执行。
- 不在主线程：分发到 `DispatcherType.MAIN`。

## 常见错误

不要在 `Concurrent.main { }` 中执行耗时任务：

```kotlin
Concurrent.main {
    // 错误：可能导致 ANR
    Thread.sleep(3000L)
}
```

正确做法是后台处理后切回主线程：

```kotlin
Concurrent.io {
    val data = blockingLoad()
    Concurrent.main { render(data) }
}
```

[返回 README](../README.md)