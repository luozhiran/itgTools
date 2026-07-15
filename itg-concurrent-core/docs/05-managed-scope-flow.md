# 05. 托管 Scope 与 Flow

本节解决不在 Activity、Fragment、ViewModel 中时，如何启动 suspend 任务或收集 Flow。

## 一次性 suspend 任务

```kotlin
val job = Concurrent.launchIo(name = "preload-config") {
    val config = api.fetchConfig()
    Concurrent.mainSuspend {
        callback(config)
    }
}
```

取消：

```kotlin
job.cancel()
```

一次性 `launch` 内部会创建临时 `ConcurrentScope`，任务完成或取消后释放临时 scope。

## 长期持有 ConcurrentScope

普通类会多次启动任务时，推荐持有 scope：

```kotlin
class PreloadManager {
    private val scope = Concurrent.createScope(
        type = DispatcherType.IO,
        name = "preload-manager"
    )

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

`ConcurrentScope` 内部使用 `SupervisorJob`，一个子任务失败不会自动取消其他兄弟任务。

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

如果最终需要更新 UI：

```kotlin
Concurrent.launchIo {
    flow.collect { value ->
        val state = buildState(value)
        Concurrent.mainSuspend {
            render(state)
        }
    }
}
```

## 线程说明

- `Concurrent.launchMain { flow.collect { } }` 的 `collect` 下游默认在主线程执行。
- 如果 Flow 上游由 `callbackFlow`、OkHttp callback 或 `flowOn` 控制，上游线程可能不是 collect 所在线程。
- 耗时处理优先放在 `launchIo` 或 `launchBackground`，只把 UI 更新切到主线程。

[返回 README](../README.md)