# 03. 工具与 API 速查

本节说明 `CoroutineUtils` 和协程池模块 API。

## 可复制 Demo

```kotlin
import com.itg.itg_coroutine_pools.utils.CoroutineUtils

CoroutineUtils.assertBackgroundThread()
val info = CoroutineUtils.getCurrentThreadInfo()
val desc = CoroutineUtils.getCurrentThreadDescription()
```

## API 速查

| 类型 | API | 说明 |
| --- | --- | --- |
| `CoroutineDispatcherManager` | `ioDispatcher`、`computeDispatcher`、`backgroundDispatcher`、`singleDispatcher`、`mainDispatcher` | 预置调度器 |
| `CoroutineDispatcherManager` | `newFixedDispatcher`、`newSingleDispatcher`、`fromExecutor`、`newCustomDispatcher` | 自定义调度器 |
| `CoroutineExecutor` | `io`、`compute`、`background`、`single`、`main` | 类线程池 API |
| `CoroutineExecutor` | `ioSuspend`、`computeSuspend`、`backgroundSuspend`、`mainSuspend` | suspend 原生 API |
| `CoroutineExecutor` | `mainDelayed`、`ioDelayed`、`scheduleAtFixedRate`、`cancel`、`await` | 延迟、定时、取消、等待 |
| `ChannelManager` | `getOrCreate`、`post`、`sendMessage`、`quit`、`getInfo` | 命名通道 |
| `CoroutineUtils` | `isMainThread`、`assertMainThread`、`runOnUiThread`、`sleep` | 线程辅助 |
| `CoroutineUtils` | `setLowPriority`、`setBackgroundPriority`、`logStackTrace` | 调试和优先级 |

## 关键说明

- 自定义 dispatcher 如果底层是新线程池，调用方要负责关闭。
- `CoroutineExecutor` 内部 scope 是全局对象，不等同于 Activity/Fragment 生命周期。
- 页面级任务优先用自己的 `lifecycleScope/viewModelScope` 搭配 suspend API。
- `CoroutineUtils.sleep(ms)` 会阻塞线程，不是 `delay(ms)`。

## 验证方式

- 页面销毁时不应留下仍引用 View 的全局任务。
- 自定义 dispatcher 的线程名前缀应能在调试器中看到。

[返回 README](../README.md)