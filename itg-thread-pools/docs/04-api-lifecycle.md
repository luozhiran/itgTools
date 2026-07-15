# 04. API 与生命周期

本节汇总线程池模块 API 和关闭规则。

## API 速查

| 类型 | API | 说明 |
| --- | --- | --- |
| `TaskExecutor` | `io`、`compute`、`background`、`single`、`main` | 语义化任务提交 |
| `TaskExecutor` | `mainDelayed`、`ioDelayed`、`backgroundDelayed` | 延迟执行 |
| `TaskExecutor` | `scheduleAtFixedRate`、`scheduleWithFixedDelay` | 定时任务 |
| `TaskExecutor` | `cancel`、`cancelMain`、`await`、`awaitAll`、`awaitAny` | 取消和等待 |
| `TaskExecutor` | `background(Priority, task)`、`submitWithPriority` | 优先级任务 |
| `ThreadPoolManager` | `ioPool`、`computePool`、`backgroundPool`、`singlePool` | 预置线程池 |
| `ThreadPoolManager` | `newCachedPool`、`newFixedPool`、`newSinglePool`、`newCustomPool` | 工厂方法 |
| `ThreadPoolManager` | `shutdown`、`shutdownNow`、`awaitTermination` | 预置池生命周期 |
| `HandlerManager` | `getOrCreate`、`post`、`sendMessage`、`quit` | HandlerThread 管理 |
| `ThreadUtils` | `isMainThread`、`assertMainThread`、`getCurrentThreadInfo` | 线程诊断 |

## 可复制 Demo

```kotlin
import com.itg.itg_thread_pools.manager.ThreadPoolManager
import java.util.concurrent.TimeUnit

fun shutdownThreadPoolsOnExit() {
    ThreadPoolManager.shutdown()
    if (!ThreadPoolManager.awaitTermination(5, TimeUnit.SECONDS)) {
        ThreadPoolManager.shutdownNow()
    }
}
```

## 关键说明

- App 常驻进程通常不需要频繁关闭预置池。
- 测试或进程退出时可调用 `shutdown()`。
- `shutdownNow()` 会尝试中断任务，只适合紧急场景。
- 提交到已关闭线程池会触发拒绝策略。

## 验证方式

- 关闭后 `getPoolStats(pool)["isShutdown"]` 应为 true。
- 业务自定义线程池也应有独立关闭策略。

[返回 README](../README.md)