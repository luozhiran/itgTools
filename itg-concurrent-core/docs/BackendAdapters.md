# Backend 适配器

将不同并发实现（线程池/协程）统一桥接到 `TaskDispatcher` 接口。

## 架构

```
ConcurrentFactory
  ├── THREAD_POOL → ThreadPoolAdapter → itg-thread-pools
  ├── COROUTINE   → CoroutineAdapter  → itg-coroutine-pools
  └── AUTO        → 自动检测 classpath
```

## ThreadPoolAdapter

包装 `itg-thread-pools`，**不改原代码**：

```kotlin
ThreadPoolAdapter.create(DispatcherType.IO)
// → 内部使用 ThreadPoolManager.ioPool
```

## CoroutineAdapter

包装 `itg-coroutine-pools`，支持 `suspend`：

```kotlin
CoroutineAdapter.create(DispatcherType.IO)
// → 内部使用 CoroutineDispatcherManager.ioDispatcher
// → supportsCoroutineNative = true
```

## 扩展方法

### toCoroutineDispatcher()

```kotlin
val td = Concurrent.get(DispatcherType.IO)
val cd = if (td is CoroutineTaskDispatcher) td.coroutineDispatcher
         else td.toCoroutineDispatcher()
withContext(cd) { doWork() }
```

### Deferred.asFuture()

```kotlin
val d = scope.async { fetch() }
val f: Future<Data> = d.asFuture()
// f.get() 内部用 runBlocking，不可在主线程调用
```

## 自定义适配器

```kotlin
class VirtualThreadAdapter : TaskDispatcher {
    override val name = "vt-io"
    override val supportsCoroutineNative = false
    override fun execute(task: () -> Unit) { Thread.startVirtualThread(task) }
    override fun <T> submit(task: () -> T): Future<T> {
        val f = FutureTask(Callable { task() })
        Thread.startVirtualThread { f.run() }
        return f
    }
    override fun schedule(task: () -> Unit, ms: Long): Future<*> {
        val f = FutureTask<Void>(Callable { Thread.sleep(ms); task(); null })
        Thread.startVirtualThread { f.run() }
        return f
    }
}
ConcurrentFactory.register(DispatcherType.IO, VirtualThreadAdapter())
```
