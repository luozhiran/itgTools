# 02. suspend API 与 Channel

本节说明协程原生 API 和命名 Channel。

## 适用条件

- 代码已经在 `CoroutineScope.launch` 或 suspend 函数中。
- 需要结构化地切换 IO、计算或主线程上下文。
- 需要一个轻量 FIFO 通道替代 HandlerThread。

## 推荐做法

```kotlin
val data = CoroutineExecutor.ioSuspend { loadData() }
CoroutineExecutor.mainSuspend { render(data) }
```

## 可复制 Demo

```kotlin
import com.itg.itg_coroutine_pools.channel.ChannelManager
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

CoroutineScope(Dispatchers.Main).launch {
    val text = CoroutineExecutor.ioSuspend { readTextFromDisk() }
    CoroutineExecutor.mainSuspend { textView.text = text }
}

ChannelManager.getOrCreate("sync") { msg ->
    when (msg.what) {
        1 -> println("message=${msg.obj}")
    }
}
ChannelManager.post("sync") { saveToDisk() }
ChannelManager.sendMessage("sync", what = 1, obj = "done")
```

## 关键说明

- suspend API 使用 `withContext`，更适合新协程代码。
- `ChannelManager` 的命名通道会复用；不再使用时调用 `quit(name)`。
- `postDelayed/sendMessageDelayed` 内部使用 `delay`。
- `ChannelManager.postToMain` 可切回主线程。
- Channel 任务异常会被捕获并记录，不会直接终止调用方。

## 验证方式

- `ChannelManager.isAlive("sync")` 应能反映通道状态。
- 多个 `post` 到同一通道的任务应 FIFO 执行。

[返回 README](../README.md)