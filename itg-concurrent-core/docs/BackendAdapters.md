# Backend 适配器

## 概述

适配器层将不同的并发实现（线程池、协程）统一桥接到 `TaskDispatcher` 接口。用户无需关心适配器细节，通过 `ConcurrentFactory` 即可自动选择。

---

## 架构

```
ConcurrentFactory
      │
      ├── BackendType.THREAD_POOL → ThreadPoolAdapter.create(type)
      │                                └── ThreadPoolDispatcher (包装 ThreadPoolManager)
      │
      ├── BackendType.COROUTINE   → CoroutineAdapter.create(type)
      │                                └── CoroutinePoolDispatcher (包装 CoroutineDispatcherManager)
      │
      └── BackendType.AUTO        → 自动检测 classpath
                                       ├── 有 itg-coroutine-pools → CoroutineAdapter
                                       └── fallback → ThreadPoolAdapter
```

---

## ThreadPoolAdapter

将 `itg-thread-pools` 的 `ThreadPoolManager` 和 `TaskExecutor` 包装为 `TaskDispatcher` 接口。

```kotlin
// 内部实现（用户不需要直接调用）
val ioDispatcher: TaskDispatcher = ThreadPoolAdapter.create(DispatcherType.IO)
// → 内部使用 ThreadPoolManager.ioPool

ioDispatcher.execute { doWork() }
// → 等价于 ThreadPoolManager.ioPool.execute { doWork() }
```

**关键点：**
- 不改动 `itg-thread-pools` 一行代码
- 仅当用户选择 `THREAD_POOL` 后端或 classpath 上只有线程池时才使用
- `supportsCoroutineNative = false` — 无 suspend 支持（通过 `runBlocking` 桥接）
- 调度任务使用 `ThreadPoolManager.scheduledPool`

## CoroutineAdapter

将 `itg-coroutine-pools` 的 `CoroutineDispatcherManager` 包装为 `TaskDispatcher` 接口。

```kotlin
// 内部实现（用户不需要直接调用）
val ioDispatcher: TaskDispatcher = CoroutineAdapter.create(DispatcherType.IO)
// → 内部使用 CoroutineDispatcherManager.ioDispatcher

ioDispatcher.execute { doWork() }
// → 实际在 Dispatchers.IO 上执行
```

**关键点：**
- `supportsCoroutineNative = true` — 支持 `executeSuspend()`
- `Future<T>` 通过 `Deferred<T>.asFuture()` 桥接（内部用 `runBlocking` 阻塞等待）
- 推荐使用 `ioSuspend { }` 而不是 `io<T> { }: Future<T>`（避免 `runBlocking`）

## 扩展方法

### TaskDispatcher.toCoroutineDispatcher()

将任意 `TaskDispatcher` 转为标准 `CoroutineDispatcher`，使其可用于 `withContext()` 等协程 API：

```kotlin
val dispatcher = Concurrent.get(DispatcherType.IO)
val coroutineDispatcher = if (dispatcher is CoroutineTaskDispatcher) {
    dispatcher.coroutineDispatcher
} else {
    dispatcher.toCoroutineDispatcher()  // 线程池 → Executor.asCoroutineDispatcher()
}

// 直接用于协程
withContext(coroutineDispatcher) {
    doWork()
}
```

### Deferred<T>.asFuture()

将协程 `Deferred` 包装为 Java `Future`：

```kotlin
val deferred = scope.async { fetchData() }
val future: Future<Data> = deferred.asFuture()

// 阻塞等待（内部用 runBlocking）
val result = future.get()
```

**注意：** `Future.get()` 内部使用 `runBlocking`，会阻塞调用线程，不应在主线程调用。

---

## 自定义适配器（高级用法）

如果未来需要支持新的并发模型（如 Java 21 Virtual Threads），只需实现 `TaskDispatcher` 接口：

```kotlin
class VirtualThreadAdapter : TaskDispatcher {
    override val name = "virtual-thread-io"
    override val supportsCoroutineNative = false

    override fun execute(task: () -> Unit) {
        Thread.startVirtualThread { task() }
    }

    override fun <T> submit(task: () -> T): Future<T> {
        val future = FutureTask(Callable { task() })
        Thread.startVirtualThread { future.run() }
        return future
    }

    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
        val future = FutureTask<Void>(Callable {
            Thread.sleep(delayMs)
            task()
            null
        })
        Thread.startVirtualThread { future.run() }
        return future
    }
}

// 注册
ConcurrentFactory.register(DispatcherType.IO, VirtualThreadAdapter())
// 此后 Concurrent.io { } 使用 Virtual Threads
```
