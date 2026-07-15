# 02. ThreadPoolManager

本节说明预置线程池、自定义线程池和优先级任务。

## 适用条件

- 需要直接拿到 `ThreadPoolExecutor`。
- 需要业务专属线程池或固定并发量。
- 需要按任务优先级调度后台任务。

## 推荐做法

```kotlin
ThreadPoolManager.ioPool.execute { readFile() }
val pool = ThreadPoolManager.newFixedPool("image", threads = 2)
```

## 可复制 Demo

```kotlin
import com.itg.itg_thread_pools.executor.Priority
import com.itg.itg_thread_pools.executor.TaskExecutor
import com.itg.itg_thread_pools.manager.ThreadPoolManager

val imagePool = ThreadPoolManager.newFixedPool("image", threads = 2)

TaskExecutor.execute(imagePool, Priority.HIGH) {
    // TODO: 当前页面图片处理
}

TaskExecutor.background(Priority.LOW) {
    // TODO: 预加载或日志任务
}

val stats = ThreadPoolManager.getPoolStats(imagePool)
```

## 关键说明

- 预置池：`ioPool`、`computePool`、`backgroundPool`、`singlePool`、`scheduledPool`、`mainExecutor`。
- 预置线程池使用命名线程，便于 logcat 和 Profiler 定位。
- 默认拒绝策略是 `CallerRunsPolicy`，队列满时调用线程执行，形成背压。
- 优先级排序需要目标池队列支持；内置 `TaskExecutor.background(priority)` 使用 priorityPool。
- 自定义线程池由调用方负责关闭。

## 验证方式

- `ThreadPoolManager.getPoolStats(pool)` 能看到 active、queue、completed 等指标。
- 高优先级任务应先于同队列低优先级任务执行。

[返回 README](../README.md)