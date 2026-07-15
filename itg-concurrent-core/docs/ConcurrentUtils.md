# ConcurrentUtils

`ConcurrentUtils` 提供后端无关的工具方法。

## 线程检测

```kotlin
ConcurrentUtils.isMainThread()
ConcurrentUtils.isBackgroundThread()
```

## 线程断言

```kotlin
ConcurrentUtils.assertMainThread("UI only")
ConcurrentUtils.assertBackgroundThread("Do not run on main thread")
```

不满足条件时会抛出 `IllegalStateException`。

## Future 辅助

```kotlin
val result = ConcurrentUtils.await(future, timeoutMs = 5_000L)
val cancelled = ConcurrentUtils.cancel(future)
```

注意：`await` 会阻塞当前线程，不要在主线程调用。

## 阻塞休眠

```kotlin
Concurrent.io {
    ConcurrentUtils.sleep(1_000L)
}
```

主线程调用 `sleep` 会记录警告并返回，避免 ANR。

## 线程信息

```kotlin
val desc = ConcurrentUtils.getCurrentThreadDescription()
val info = ConcurrentUtils.getCurrentThreadInfo()
```

## API 速查

| 方法 | 说明 |
| --- | --- |
| `isMainThread()` | 当前是否主线程 |
| `isBackgroundThread()` | 当前是否后台线程 |
| `assertMainThread(message)` | 断言主线程 |
| `assertBackgroundThread(message)` | 断言后台线程 |
| `await(future, timeoutMs)` | 阻塞等待 Future |
| `cancel(future, mayInterrupt)` | 取消 Future |
| `sleep(ms)` | 后台阻塞休眠 |
| `getCurrentThreadDescription()` | 当前线程描述 |
| `getCurrentThreadInfo()` | 当前线程信息 Map |

[返回 README](../README.md)