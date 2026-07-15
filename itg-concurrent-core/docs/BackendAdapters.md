# BackendAdapters

本节说明底层适配器如何把线程池和协程统一成 `TaskDispatcher`。

## 核心抽象

```kotlin
interface TaskDispatcher {
    val name: String
    fun execute(task: () -> Unit)
    fun <T> submit(task: () -> T): Future<T>
    fun schedule(task: () -> Unit, delayMs: Long): Future<*>
    val supportsCoroutineNative: Boolean
}
```

协程原生分发器额外实现：

```kotlin
interface CoroutineTaskDispatcher : TaskDispatcher {
    suspend fun <T> executeSuspend(task: suspend () -> T): T
    val coroutineDispatcher: CoroutineDispatcher
}
```

## 适配器关系

```text
ConcurrentFactory
  -> CoroutineAdapter  -> itg-coroutine-pools
  -> ThreadPoolAdapter -> itg-thread-pools
```

## 获取底层分发器

```kotlin
val dispatcher = Concurrent.get(DispatcherType.IO)
dispatcher.execute { doWork() }
```

## 获取协程 Dispatcher

```kotlin
val ioDispatcher = Concurrent.getCoroutineDispatcher(DispatcherType.IO)

CoroutineScope(SupervisorJob() + ioDispatcher).launch {
    doSuspendWork()
}
```

协程后端返回原生 dispatcher；线程池后端通过 `toCoroutineDispatcher()` 桥接。

## 自定义分发器

```kotlin
class LoggingDispatcher(
    private val delegate: TaskDispatcher
) : TaskDispatcher {
    override val name: String = "logging-${delegate.name}"
    override val supportsCoroutineNative: Boolean = delegate.supportsCoroutineNative

    override fun execute(task: () -> Unit) {
        delegate.execute {
            val start = System.currentTimeMillis()
            try {
                task()
            } finally {
                Log.d("Concurrent", "$name cost=${System.currentTimeMillis() - start}ms")
            }
        }
    }

    override fun <T> submit(task: () -> T): Future<T> = delegate.submit(task)

    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
        return delegate.schedule(task, delayMs)
    }
}

val origin = Concurrent.get(DispatcherType.IO)
ConcurrentFactory.register(DispatcherType.IO, LoggingDispatcher(origin))
```

如果自定义分发器要原生支持 `suspend`，实现 `CoroutineTaskDispatcher`。

[返回 README](../README.md)