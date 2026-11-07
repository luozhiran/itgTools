# 弹窗调度器

`DialogScheduler` 的伴生对象提供全进程共享的弹窗队列。项目中的任意模块都可以调用 `DialogScheduler.enqueue(...)` 生产消息，Activity、Fragment 或其他宿主可以调用 `DialogScheduler.register(...)` 消费消息。默认情况下，消费者在 Android 主线程收到回调，自己创建和展示弹窗。

```kotlin
data class Ad(val title: String)
var currentDialog: AlertDialog? = null

// Activity 或 Fragment 注册消费者，按注册顺序尝试。
val subscription = DialogScheduler.register(object : DialogConsumer<Ad> {
    override fun canHandle(message: DialogMessage<Ad>): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun show(message: DialogMessage<Ad>, delivery: DialogDelivery) {
        val dialog = AlertDialog.Builder(this@MainActivity)
            .setMessage(message.payload.title)
            .create()
        dialog.setOnDismissListener {
            if (currentDialog === dialog) currentDialog = null
            delivery.finish()
        }
        dialog.show()
        currentDialog = dialog
    }
})

// 网络请求、长连接等生产者线程均可入队。
DialogScheduler.enqueue(DialogMessage(id = "ad-42", priority = 100, payload = Ad("活动")))

// 临时消息只尝试当前消费者；无人处理就丢弃。
DialogScheduler.enqueue(DialogMessage(
    id = "tip-1",
    priority = 20,
    payload = Ad("临时提示"),
    deliveryPolicy = DialogDeliveryPolicy.DROP_IF_UNHANDLED,
))

// 宿主销毁时，注销消费者并关闭它正在展示的弹窗。
subscription.close()
currentDialog?.dismiss()
```

全局消费者按 `payload` 的类型匹配消息，例如 `DialogConsumer<Ad>` 只会收到 `Ad` 消息；不同业务可以共用同一优先级队列。若消费者状态变化，调用 `DialogScheduler.retryPending()`。`DialogScheduler.pendingSnapshot()` 和 `DialogScheduler.showingMessage()` 可查看全局状态。

需要独立队列时，仍可创建 `DialogScheduler<Payload>()` 实例；它与全局队列互不影响。

## 调度规则

- 优先级大的消息先展示；同优先级按入队先后顺序展示。新消息入队后会重新排序。
- 同一时刻只有一条消息处于展示中。消费者接受后，消息从等待队列移到展示中；`delivery.finish()` 在弹窗关闭后释放展示位。
- 默认的 `REQUIRE_CONSUMER` 会一直保留无人处理的消息，直到有消费者处理或生产者调用 `cancel(id)`。`DROP_IF_UNHANDLED` 只尝试当时可用的消费者；全部拒绝或展示失败后仍无人接手时丢弃。完全没有注册消费者时，后者的 `enqueue` 直接返回 `false`。
- `canHandle` 返回 `false` 时继续询问下一个消费者；未处理的高优先级消息不会挡住其他可处理消息。
- `show` 无法展示时调用 `delivery.retry()`；如果 `show` 抛异常，框架按相同流程重试其他消费者，并通过 `onError` 报告。不会立即向同一个失败的消费者无限重试。
- 注册或注销消费者、新消息入队、当前弹窗结束时会重试等待消息。仅消费者内部状态变化时，调用 `retryPending()`。
- 同一 `id` 已在等待或展示中时，`enqueue` 返回 `false`。`cancel(id)` 只能删除等待中的消息。

消费者的 `canHandle` 和 `show` 应快速返回，不要在主线程阻塞。`show` 可以异步启动展示，但必须最终调用 `finish()` 或 `retry()`。消费者注销时应立即关闭自己正在展示的弹窗；注销会把该消费者持有的消息重新入队。

队列仅保存在内存中。若要在进程被系统终止后恢复未展示的消息，生产者需要将消息持久化，并在应用重启后重新入队。
