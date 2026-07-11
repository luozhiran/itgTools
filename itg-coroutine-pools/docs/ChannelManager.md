# ChannelManager — 协程消息通道管理器

## 概述

对标 `HandlerManager`，用 Kotlin `Channel` + `CoroutineScope` 替代 Android `HandlerThread` + `Looper` 机制。提供严格串行的消息处理，更轻量（不需要额外的物理线程）。

---

## 对比 HandlerManager

| 特性 | HandlerManager | ChannelManager |
|------|:---:|:---:|
| 底层机制 | HandlerThread + Looper | Channel + Coroutine |
| 线程占用 | 每个名称独占一个物理线程 | 协程轻量级，复用线程 |
| 任务排序 | 严格 FIFO | 严格 FIFO |
| Message 支持 | what/arg1/arg2/obj | Message(what, arg1, arg2, obj) |
| 延迟/定时 | Handler.postDelayed | delay() + Channel.send |
| 资源释放 | quit() / quitSafely() | Channel.close() + scope.cancel() |

---

## 教程

### 1. 创建通道并提交任务

```kotlin
import com.itg.itg_coroutine_pools.channel.ChannelManager

// 创建一个命名的串行通道
ChannelManager.getOrCreate("db-writer")

// 提交任务（严格按提交顺序串行执行）
ChannelManager.post("db-writer") {
    database.insert(record1)
}
ChannelManager.post("db-writer") {
    database.insert(record2)   // 一定在 record1 之后执行
}
ChannelManager.post("db-writer") {
    database.insert(record3)   // 一定在 record2 之后执行
}
```

### 2. 延迟提交

```kotlin
// 500ms 后执行
ChannelManager.postDelayed("db-writer", delayMs = 500L) {
    database.clearCache()
}

// 多个延迟任务，仍然严格串行
ChannelManager.postDelayed("db-writer", delayMs = 100L) {
    Log.d("Order", "Task A")  // 100ms 后先执行
}
ChannelManager.postDelayed("db-writer", delayMs = 500L) {
    Log.d("Order", "Task B")  // 等 Task A 完成后 500ms 执行
}
```

### 3. Message 消息传递

```kotlin
// 定义消息类型
const val MSG_SAVE = 1
const val MSG_DELETE = 2
const val MSG_QUIT = 3

// 创建带 Message 处理的通道
ChannelManager.getOrCreate(
    name = "order-processor",
    messageProcessor = { msg ->
        when (msg.what) {
            MSG_SAVE -> {
                val order = msg.obj as Order
                saveOrder(order)
            }
            MSG_DELETE -> {
                deleteOrder(msg.arg1)  // arg1 = orderId
            }
            MSG_QUIT -> {
                cleanup()
                ChannelManager.quit("order-processor")
            }
        }
    }
)

// 发送 Message
ChannelManager.sendMessage(
    name = "order-processor",
    what = MSG_SAVE,
    obj = orderData
)

ChannelManager.sendMessage(
    name = "order-processor",
    what = MSG_DELETE,
    arg1 = 42  // orderId
)

// 延迟发送 Message
ChannelManager.sendMessageDelayed(
    name = "order-processor",
    what = MSG_SAVE,
    delayMs = 3000L,
    obj = orderData
)
```

### 4. 主线程便捷方法

```kotlin
// 在任意线程中切回主线程
ChannelManager.postToMain {
    textView.text = "Updated"
}

// 主线程延迟执行
val runnable = ChannelManager.postToMainDelayed(delayMs = 1000L) {
    Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
}

// 主线程发送 Message (和 Handler 风格一致)
ChannelManager.sendMainMessage(
    what = MSG_REFRESH,
    arg1 = userId,
    obj = userData
)
```

### 5. 状态监控

```kotlin
// 检查通道是否存活
if (ChannelManager.isAlive("db-writer")) {
    ChannelManager.post("db-writer") { saveData() }
}

// 列出所有通道
println(ChannelManager.getAllNames())  // [db-writer, order-processor]

// 通道数量
println(ChannelManager.getCount())     // 2

// 获取通道详情
val info = ChannelManager.getInfo("db-writer")
// { name=db-writer, exists=true, isActive=true,
//   taskChannelClosed=false, messageChannelClosed=false }

// 打印所有通道状态
ChannelManager.printAllInfo()
```

### 6. 实战：串行任务处理器

```kotlin
class OrderProcessor {
    private val channelName = "order-processor"

    fun start() {
        ChannelManager.getOrCreate(
            name = channelName,
            messageProcessor = { msg ->
                when (msg.what) {
                    MSG_PROCESS_ORDER -> {
                        val order = msg.obj as Order
                        processOrder(order)
                        // 处理完成后通知主线程
                        ChannelManager.postToMain {
                            updateOrderList()
                        }
                    }
                    MSG_CANCEL_ORDER -> {
                        cancelOrder(msg.arg1)
                    }
                    MSG_QUIT -> {
                        cleanup()
                        ChannelManager.quit(channelName)
                    }
                }
            }
        )
    }

    fun processOrder(order: Order) {
        ChannelManager.sendMessage(
            name = channelName,
            what = MSG_PROCESS_ORDER,
            obj = order
        )
    }

    fun cancelOrder(orderId: Int) {
        ChannelManager.sendMessage(
            name = channelName,
            what = MSG_CANCEL_ORDER,
            arg1 = orderId
        )
    }

    fun stop() {
        ChannelManager.sendMessage(channelName, MSG_QUIT)
    }
}
```

### 7. 在 Java 中使用

```java
import com.itg.itg_coroutine_pools.channel.ChannelManager;

// 创建通道
ChannelManager.getOrCreate("java-worker", msg -> {
    switch (msg.getWhat()) {
        case 1: handleSave(msg.getObj()); break;
        case 2: ChannelManager.quit("java-worker"); break;
    }
    return Unit.INSTANCE;
});

// 发送任务
ChannelManager.post("java-worker", () -> {
    doWork();
    return Unit.INSTANCE;
});

// 发送消息
ChannelManager.sendMessage("java-worker", 1, 0, 0, dataObject);
```

---

## API 参考

| 方法 | 说明 |
|------|------|
| `getOrCreate(name, messageProcessor)` | 获取或创建通道 |
| `post(name, task)` | 提交 Runnable 任务 |
| `postDelayed(name, delayMs, task)` | 延迟提交 Runnable |
| `sendMessage(name, what, arg1, arg2, obj)` | 发送 Message |
| `sendMessageDelayed(name, what, delayMs, ...)` | 延迟发送 Message |
| `postToMain(task)` | 主线程执行 |
| `postToMainDelayed(task, delayMs)` | 主线程延迟执行 |
| `sendMainMessage(what, ...)` | 主线程发送 Message |
| `quit(name)` | 关闭指定通道 |
| `quitAll()` | 关闭所有通道 |
| `isAlive(name)` | 检查通道是否存活 |
| `getAllNames()` | 获取所有通道名称 |
| `getCount()` | 获取通道数量 |
| `getInfo(name)` | 获取通道详情 |
| `printAllInfo()` | 打印所有通道状态 |

### Message 结构

```kotlin
data class Message(
    val what: Int,       // 消息类型标识
    val arg1: Int = 0,   // 参数1
    val arg2: Int = 0,   // 参数2
    val obj: Any? = null // 附带对象
)
```
