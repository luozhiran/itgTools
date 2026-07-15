# 05. 托管 Scope 与 Flow

本节解决不在 Activity、Fragment、ViewModel 中时，如何启动 suspend 任务或收集 Flow。

## 推荐做法

一次性 suspend 任务：

```kotlin
val job = Concurrent.launchIo(name = "preload-config") {
    val config = api.fetchConfig()
    Concurrent.mainSuspend {
        callback(config)
    }
}
```

长期持有 `ConcurrentScope`：

```kotlin
class PreloadManager {
    private val scope = Concurrent.createScope(DispatcherType.IO, "preload-manager")

    fun preload() {
        scope.launch {
            val config = api.fetchConfig()
            cache.save(config)
        }
    }

    fun release() {
        scope.cancel()
    }
}
```

## 可复制 Demo

下面示例演示普通类中收集 Flow，并在释放时取消。需要替换 `messageFlow` 来源和 `onMessage` 回调。

```kotlin
import com.itg.concurrent.Concurrent
import com.itg.concurrent.DispatcherType
import kotlinx.coroutines.flow.Flow

class MessageSubscriber(
    private val messageFlow: Flow<String>,
    private val onMessage: (String) -> Unit
) {
    private val scope = Concurrent.createScope(
        type = DispatcherType.IO,
        name = "message-subscriber"
    )

    fun start() {
        scope.launch {
            messageFlow.collect { message ->
                // 后台处理消息
                val normalized = message.trim()

                Concurrent.mainSuspend {
                    onMessage(normalized)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
```

## Flow collect

错误写法：

```kotlin
Concurrent.io {
    flow.collect { value ->
        handle(value)
    }
}
```

原因：`Concurrent.io { }` 接收普通 lambda，不是 `suspend` lambda。

正确写法：

```kotlin
Concurrent.launchIo {
    flow.collect { value ->
        handle(value)
    }
}
```

## 关键说明

- `Concurrent.launchMain { flow.collect { } }` 的 `collect` 下游默认在主线程执行。
- 如果 Flow 上游由 `callbackFlow`、OkHttp callback 或 `flowOn` 控制，上游线程可能不是 collect 所在线程。
- `ConcurrentScope` 内部使用 `SupervisorJob`，一个子任务失败不会自动取消其他兄弟任务。
- 长期持有的 scope 必须由 owner 调用 `cancel()`。

## 验证方式

- 调用 `start()` 后可以收到 Flow 数据。
- 调用 `stop()` 后不再收到数据。
- `onMessage` 中可以安全更新 UI。

[返回 README](../README.md)