# ChannelManager — 协程消息通道管理器

对标 `HandlerManager`，用 Kotlin `Channel` + `CoroutineScope` 替代 `HandlerThread` + `Looper`。

## 对比

| 特性 | HandlerManager | ChannelManager |
|------|:---:|:---:|
| 底层 | HandlerThread + Looper | Channel + Coroutine |
| 线程占用 | 每名称独占线程 | 协程复用线程 |
| Message | what/arg1/arg2/obj | Message(what, arg1, arg2, obj) |
| 延迟 | Handler.postDelayed | delay() + send |

## 教程

### 创建通道并提交任务

```kotlin
ChannelManager.getOrCreate("db-writer")
ChannelManager.post("db-writer") { database.insert(record1) }
ChannelManager.post("db-writer") { database.insert(record2) } // 严格串行
ChannelManager.postDelayed("db-writer", 500L) { database.clearCache() }
```

### Message 消息传递

```kotlin
ChannelManager.getOrCreate("processor", messageProcessor = { msg ->
    when (msg.what) {
        MSG_SAVE -> save(msg.obj as Order)
        MSG_QUIT -> ChannelManager.quit("processor")
    }
})
ChannelManager.sendMessage("processor", MSG_SAVE, obj = order)
```

### 主线程

```kotlin
ChannelManager.postToMain { updateUI() }
ChannelManager.postToMainDelayed(1000L) { showToast() }
ChannelManager.sendMainMessage(MSG_REFRESH, arg1 = userId)
```

### 生命周期

```kotlin
ChannelManager.quit("db-writer")   // 关闭单个
ChannelManager.quitAll()           // 全部关闭
ChannelManager.isAlive("db-writer")
ChannelManager.printAllInfo()
```

## API

| 方法 | 说明 |
|------|------|
| `getOrCreate(name, processor)` | 创建通道 |
| `post(name, task)` / `postDelayed(name, ms, task)` | 提交任务 |
| `sendMessage(name, what, ...)` / `sendMessageDelayed(...)` | 发送 Message |
| `postToMain(task)` / `postToMainDelayed(ms, task)` | 主线程 |
| `sendMainMessage(what, ...)` | 主线程 Message |
| `quit(name)` / `quitAll()` | 关闭 |
| `isAlive(name)` / `getCount()` / `printAllInfo()` | 状态 |
