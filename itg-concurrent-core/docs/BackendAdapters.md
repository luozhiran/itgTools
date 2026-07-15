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

## 推荐做法

获取底层分发器：

```kotlin
val dispatcher = Concurrent.get(DispatcherType.IO)
dispatcher.execute { doWork() }
```

获取协程 dispatcher：

```kotlin
val ioDispatcher = Concurrent.getCoroutineDispatcher(DispatcherType.IO)
```

## 可复制 Demo

下面示例注册一个日志分发器，用于统计 IO 任务耗时。需要替换 `Log.d` 的 tag 或上报逻辑。

```kotlin
import android.util.Log
import com.itg.concurrent.Concurrent
import com.itg.concurrent.ConcurrentFactory
import com.itg.concurrent.DispatcherType
import com.itg.concurrent.TaskDispatcher
import java.util.concurrent.Future

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
                Log.d("Concurrent", "$name execute cost=${System.currentTimeMillis() - start}ms")
            }
        }
    }

    override fun <T> submit(task: () -> T): Future<T> {
        return delegate.submit {
            val start = System.currentTimeMillis()
            try {
                task()
            } finally {
                Log.d("Concurrent", "$name submit cost=${System.currentTimeMillis() - start}ms")
            }
        }
    }

    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
        return delegate.schedule(task, delayMs)
    }
}

fun installIoLoggingDispatcher() {
    val origin = Concurrent.get(DispatcherType.IO)
    ConcurrentFactory.register(DispatcherType.IO, LoggingDispatcher(origin))
}
```

## 关键说明

- `register` 只覆盖指定 `DispatcherType`。
- 如果自定义分发器要原生支持 suspend，需要实现 `CoroutineTaskDispatcher`。
- `getCoroutineDispatcher(type)` 在线程池后端下返回桥接 dispatcher。

## 验证方式

- 调用 `installIoLoggingDispatcher()` 后执行 `Concurrent.io { }`，日志应打印耗时。
- 如果调用 `switchTo(...)`，手动注册表会被清空，需要重新注册。

[返回 README](../README.md)