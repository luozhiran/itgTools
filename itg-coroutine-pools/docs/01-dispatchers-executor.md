# 01. 调度器与执行器

本节说明协程调度器和与线程池兼容的执行 API。

## 适用条件

- 新代码使用 Kotlin 协程。
- 迁移旧 `TaskExecutor` 时希望保留类似方法名。
- 需要 `Future` 兼容已有调用方。

## 推荐做法

```kotlin
CoroutineExecutor.io {
    val data = loadData()
    CoroutineExecutor.main { render(data) }
}
```

## 可复制 Demo

```kotlin
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

val future = CoroutineExecutor.compute {
    (1..100_000).sum()
}

CoroutineExecutor.io {
    val result = CoroutineExecutor.await(future, timeoutMs = 3_000)
    CoroutineExecutor.main {
        textView.text = "result=$result"
    }
}
```

## 关键说明

- `CoroutineExecutor.io/compute/background` 有返回值重载，返回 `Future<T>`。
- `await()` 本质会阻塞当前线程，不要在主线程调用。
- `main()` 当前已在主线程时直接执行。
- `CoroutineDispatcherManager.singleDispatcher` 适合串行协程任务。
- `CoroutineExecutor.shutdown()` 会取消内部全局 scope，调用后不应继续复用。

## 验证方式

- 后台任务中 `Looper.myLooper()` 不应是主 Looper。
- UI 更新应通过 `CoroutineExecutor.main` 或 `mainSuspend`。

[返回 README](../README.md)