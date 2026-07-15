# 06. 后端选择与桥接

本节说明 `itg-concurrent-core` 如何在线程池后端和协程后端之间切换。

## 后端类型

| 后端 | 说明 |
| --- | --- |
| `AUTO` | 自动检测 classpath，优先协程后端，其次线程池后端 |
| `COROUTINE` | 使用 `itg-coroutine-pools` |
| `THREAD_POOL` | 使用 `itg-thread-pools` |

## 全局切换

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
```

## 混合模式

```kotlin
ConcurrentFactory.useMixed(
    mapOf(
        DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
        DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL,
        DispatcherType.BACKGROUND to ConcurrentFactory.BackendType.COROUTINE,
        DispatcherType.MAIN to ConcurrentFactory.BackendType.COROUTINE
    )
)
```

业务调用不变：

```kotlin
Concurrent.io { readFile() }
Concurrent.compute { resizeBitmap() }
Concurrent.launchIo { flow.collect { } }
```

## 线程池桥接协程

`Concurrent.getCoroutineDispatcher(type)` 会处理两种情况：

- 协程后端：返回原生 `CoroutineDispatcher`。
- 线程池后端：通过 `TaskDispatcher.toCoroutineDispatcher()` 桥接。

因此线程池后端下仍可运行：

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

Concurrent.launchIo {
    flow.collect { value ->
        handle(value)
    }
}
```

## 可用性检测

```kotlin
ConcurrentFactory.isCoroutineAvailable()
ConcurrentFactory.isThreadPoolAvailable()
ConcurrentFactory.getAvailableBackends()
```

## 资源释放

```kotlin
ConcurrentFactory.shutdown()
```

`shutdown()` 会关闭注册表中支持 `AutoCloseable` 的分发器并清空注册表。业务自己创建的 `ConcurrentScope` 仍需要 owner 调用 `cancel()`。

[返回 README](../README.md)