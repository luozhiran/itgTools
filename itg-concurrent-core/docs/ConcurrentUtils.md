# ConcurrentUtils

`ConcurrentUtils` 提供后端无关的工具方法。

## 推荐做法

线程检测：

```kotlin
ConcurrentUtils.isMainThread()
ConcurrentUtils.isBackgroundThread()
```

线程断言：

```kotlin
ConcurrentUtils.assertMainThread("UI only")
ConcurrentUtils.assertBackgroundThread("Do not run on main thread")
```

Future 辅助：

```kotlin
val result = ConcurrentUtils.await(future, timeoutMs = 5_000L)
val cancelled = ConcurrentUtils.cancel(future)
```

## 可复制 Demo

下面示例展示后台等待任务结果、线程断言和主线程回调。需要传入你的 `loadUserName` 逻辑和 `onResult` 回调。

```kotlin
import com.itg.concurrent.Concurrent
import com.itg.concurrent.util.ConcurrentUtils

fun loadUserNameDemo(
    loadUserName: () -> String,
    onResult: (String) -> Unit
) {
    val future = Concurrent.io {
        ConcurrentUtils.assertBackgroundThread()
        loadUserName()
    }

    Concurrent.io {
        val name = ConcurrentUtils.await(future, timeoutMs = 5_000L)

        Concurrent.main {
            ConcurrentUtils.assertMainThread()
            onResult(name ?: "load failed")
        }
    }
}
```

## 关键说明

- `await` 会阻塞当前线程，不要在主线程调用。
- `sleep` 只适合后台线程；主线程调用会记录警告并返回。
- `getCurrentThreadInfo()` 适合调试和日志上报，不建议作为业务分支的复杂判断依据。

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

## 验证方式

- `assertBackgroundThread()` 不抛异常，说明当前不在主线程。
- `assertMainThread()` 不抛异常，说明 UI 更新在主线程。
- 超时时 `await` 返回 `null`，UI 展示失败兜底。

[返回 README](../README.md)
