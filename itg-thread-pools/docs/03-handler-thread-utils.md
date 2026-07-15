# 03. Handler 与线程工具

本节说明 HandlerThread 管理和线程诊断工具。

## 适用条件

- 老代码基于 Android `Handler`、`Looper`、`Message`。
- 需要一个命名后台 Looper 长期处理消息。
- 需要断言当前线程、检查 Looper 或调整线程优先级。

## 推荐做法

```kotlin
HandlerManager.post("worker") { doWork() }
ThreadUtils.assertBackgroundThread()
```

## 可复制 Demo

```kotlin
import com.itg.itg_thread_pools.manager.HandlerManager
import com.itg.itg_thread_pools.utils.ThreadUtils

HandlerManager.getOrCreate("sync") { msg ->
    when (msg.what) {
        1 -> println("sync: ${msg.obj}")
    }
}

HandlerManager.post("sync") {
    ThreadUtils.assertBackgroundThread()
    // TODO: 串行后台任务
}

HandlerManager.sendMessage("sync", what = 1, obj = "done")
HandlerManager.quit("sync")
```

## 关键说明

- `HandlerManager` 适合需要 Looper/Message 的旧接口；新代码也可考虑协程版 `ChannelManager`。
- 命名 HandlerThread 要在不需要时 `quit(name)` 或 `quitAll()`。
- `ThreadUtils.runOnUiThread` 可安全切主线程。
- `ThreadUtils.prepareLooper/loop/quitLooper` 属于底层能力，普通业务慎用。

## 验证方式

- `HandlerManager.isAlive(name)` 可确认通道是否仍存在。
- `ThreadUtils.isMainThread()` 在 UI 线程应返回 true。

[返回 README](../README.md)