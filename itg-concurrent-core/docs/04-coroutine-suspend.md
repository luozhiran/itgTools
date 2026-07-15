# 04. 协程内切线程

本节解决已经在协程中时，如何通过 `itg-concurrent-core` 切换执行上下文。

## 适用条件

- 当前已经在 `lifecycleScope`、`viewModelScope` 或其他 `CoroutineScope` 中。
- 需要在统一后端配置下执行 I/O、计算、后台或主线程任务。
- 希望线程池后端和协程后端使用同一套业务 API。

## 基本用法

```kotlin
lifecycleScope.launch {
    val user = Concurrent.ioSuspend {
        api.getUser(userId)
    }

    val state = Concurrent.computeSuspend {
        buildViewState(user)
    }

    Concurrent.mainSuspend {
        render(state)
    }
}
```

## API 选择

| 场景 | API |
| --- | --- |
| 协程内执行 I/O | `Concurrent.ioSuspend { }` |
| 协程内执行计算 | `Concurrent.computeSuspend { }` |
| 协程内执行普通后台逻辑 | `Concurrent.backgroundSuspend { }` |
| 协程内切回主线程 | `Concurrent.mainSuspend { }` |

## 为什么线程池后端也能用

`suspend` API 内部会先拿到对应 `TaskDispatcher`：

- 如果是 `CoroutineTaskDispatcher`，直接使用原生协程 dispatcher。
- 如果是普通线程池 `TaskDispatcher`，通过 `toCoroutineDispatcher()` 桥接成 `CoroutineDispatcher`。

所以全局切到 `THREAD_POOL` 时，`ioSuspend` 和 Flow 仍然能运行。

## 注意事项

- `ioSuspend` 等 API 不创建新的顶层生命周期，它们跟随调用方协程取消。
- 不要在 `mainSuspend` 中执行阻塞任务。
- 如果需要在普通类里启动协程，看 [05. 托管 Scope 与 Flow](./05-managed-scope-flow.md)。

[返回 README](../README.md)