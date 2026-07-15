# 06. 后端选择与桥接

本节说明 `itg-concurrent-core` 如何在线程池后端和协程后端之间切换。

## 后端类型

| 后端 | 说明 |
| --- | --- |
| `AUTO` | 自动检测 classpath，优先协程后端，其次线程池后端 |
| `COROUTINE` | 使用 `itg-coroutine-pools` |
| `THREAD_POOL` | 使用 `itg-thread-pools` |

## 推荐做法

全局切换：

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
```

混合模式：

```kotlin
ConcurrentFactory.useMixed(
    mapOf(
        DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
        DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL
    )
)
```

## 可复制 Demo

下面示例可以放在 `Application.onCreate()` 中。它配置 IO 使用协程后端、计算使用线程池后端，并打印当前可用后端。

```kotlin
import android.app.Application
import android.util.Log
import com.itg.concurrent.ConcurrentFactory
import com.itg.concurrent.DispatcherType

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ConcurrentFactory.useMixed(
            mapOf(
                DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
                DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL,
                DispatcherType.BACKGROUND to ConcurrentFactory.BackendType.COROUTINE,
                DispatcherType.MAIN to ConcurrentFactory.BackendType.COROUTINE
            )
        )

        Log.d("Concurrent", "available=${ConcurrentFactory.getAvailableBackends()}")
        Log.d("Concurrent", "current=${ConcurrentFactory.currentBackend}")
    }
}
```

## 线程池桥接协程

线程池后端下仍可运行 suspend 和 Flow：

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

Concurrent.launchIo {
    flow.collect { value ->
        handle(value)
    }
}
```

## 关键说明

- `switchTo` 会清空手动注册表，所有分发器按新后端重新解析。
- `useMixed` 只覆盖传入的 `DispatcherType`，未配置类型继续走当前后端或自动检测。
- `getCoroutineDispatcher(type)` 在线程池后端下会通过 `TaskDispatcher.toCoroutineDispatcher()` 桥接。

## 验证方式

```kotlin
ConcurrentFactory.isCoroutineAvailable()
ConcurrentFactory.isThreadPoolAvailable()
ConcurrentFactory.getAvailableBackends()
```

如果 demo 使用 `THREAD_POOL`，确认 app 依赖中提供了 `itg-thread-pools`。

[返回 README](../README.md)