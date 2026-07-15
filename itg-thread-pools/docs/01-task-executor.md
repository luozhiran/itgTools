# 01. TaskExecutor

本节说明如何用 `TaskExecutor` 提交任务、切主线程、延迟执行和等待结果。

## 适用条件

- 需要 Java 线程池和 `Future` 模型。
- 后台任务和 UI 更新要明确分离。
- 需要取消、超时等待或批量等待。

## 推荐做法

```kotlin
TaskExecutor.io {
    val data = loadData()
    TaskExecutor.main { render(data) }
}
```

## 可复制 Demo

```kotlin
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.util.concurrent.TimeUnit

val future = TaskExecutor.compute {
    (1..100_000).sum()
}

TaskExecutor.io {
    val result = TaskExecutor.await(future, timeout = 3, unit = TimeUnit.SECONDS)
    TaskExecutor.main {
        textView.text = "result=$result"
    }
}
```

## 关键说明

- `io/compute/background` 有返回值重载，返回 `Future<T>`。
- `await()` 会阻塞当前线程，不能在主线程调用。
- `main()` 当前已在主线程时直接执行，否则 post 到主线程。
- `mainDelayed()` 返回 `Runnable`，用 `cancelMain(runnable)` 取消。
- `scheduleAtFixedRate` 以任务开始时间为周期基准，`scheduleWithFixedDelay` 以任务结束后延迟为基准。

## 验证方式

- 后台任务中的 `Thread.currentThread().name` 应包含对应线程池前缀。
- UI 更新必须发生在主线程。

[返回 README](../README.md)