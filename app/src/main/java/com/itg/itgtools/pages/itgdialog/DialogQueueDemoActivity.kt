package com.itg.itgtools.pages.itgdialog

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.itg_dialog.DialogConsumer
import com.example.itg_dialog.DialogDelivery
import com.example.itg_dialog.DialogDeliveryPolicy
import com.example.itg_dialog.DialogMessage
import com.example.itg_dialog.DialogScheduler
import com.itg.itgtools.databinding.ActivityItgDialogDemoBinding
import java.util.UUID
import kotlin.concurrent.thread

/** 用按钮模拟网络生产者，并用两个消费者展示优先级和拒绝后的交接。 */
class DialogQueueDemoActivity : AppCompatActivity() {
    private enum class Kind { PROMOTION, NOTICE, UNSUPPORTED }

    private data class DemoPayload(val title: String, val content: String, val kind: Kind)

    private lateinit var binding: ActivityItgDialogDemoBinding
    private lateinit var promotionSubscription: AutoCloseable
    private lateinit var fallbackSubscription: AutoCloseable
    private val events = mutableListOf<String>()
    private var fallbackEnabled = true
    private var canShowDialogs = false
    private var tearingDown = false
    private var currentDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItgDialogDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        promotionSubscription = DialogScheduler.register(object : DialogConsumer<DemoPayload> {
            override fun canHandle(message: DialogMessage<DemoPayload>): Boolean =
                canShowDialogs && message.payload.kind == Kind.PROMOTION

            override fun show(message: DialogMessage<DemoPayload>, delivery: DialogDelivery) {
                showDialog("广告消费者", message, delivery)
            }
        })
        fallbackSubscription = DialogScheduler.register(object : DialogConsumer<DemoPayload> {
            override fun canHandle(message: DialogMessage<DemoPayload>): Boolean =
                canShowDialogs && fallbackEnabled && message.payload.kind == Kind.NOTICE

            override fun show(message: DialogMessage<DemoPayload>, delivery: DialogDelivery) {
                showDialog("兜底消费者", message, delivery)
            }
        })

        binding.enqueueBatch.setOnClickListener {
            enqueue("普通广告", 10, Kind.PROMOTION)
            enqueue("紧急通知", 100, Kind.NOTICE)
            enqueue("活动广告", 50, Kind.PROMOTION)
            appendEvent("批量入队完成：100 → 50 → 10")
        }
        binding.enqueueUrgent.setOnClickListener {
            enqueue("插队的紧急通知", 200, Kind.NOTICE)
        }
        binding.enqueueNotice.setOnClickListener {
            enqueue("需要兜底的通知", 80, Kind.NOTICE)
        }
        binding.enqueueOnce.setOnClickListener {
            enqueue(
                "一次性消息（无消费者）",
                90,
                Kind.UNSUPPORTED,
                DialogDeliveryPolicy.DROP_IF_UNHANDLED,
            )
            binding.root.post { if (!tearingDown) renderState() }
        }
        binding.enqueueFromThread.setOnClickListener {
            val message = newMessage("后台线程收到的广告", 60, Kind.PROMOTION)
            thread(name = "dialog-demo-producer") {
                DialogScheduler.enqueue(message)
                runOnUiThread {
                    if (!tearingDown) appendEvent("后台线程入队：${message.payload.title}")
                }
            }
        }
        binding.toggleFallback.setOnClickListener {
            fallbackEnabled = !fallbackEnabled
            binding.toggleFallback.text = if (fallbackEnabled) "停用兜底消费者" else "启用兜底消费者"
            appendEvent(if (fallbackEnabled) "兜底消费者已启用" else "兜底消费者已停用")
            DialogScheduler.retryPending()
            renderState()
        }
        binding.retryPending.setOnClickListener {
            DialogScheduler.retryPending()
            appendEvent("已重试等待队列")
        }
        renderState()
    }

    override fun onResume() {
        super.onResume()
        canShowDialogs = true
        DialogScheduler.retryPending()
    }

    override fun onPause() {
        canShowDialogs = false
        super.onPause()
    }

    override fun onDestroy() {
        tearingDown = true
        canShowDialogs = false
        promotionSubscription.close()
        fallbackSubscription.close()
        currentDialog?.dismiss()
        currentDialog = null
        super.onDestroy()
    }

    private fun newMessage(
        title: String,
        priority: Int,
        kind: Kind,
        policy: DialogDeliveryPolicy = DialogDeliveryPolicy.REQUIRE_CONSUMER,
    ) =
        DialogMessage(
            id = "demo-${UUID.randomUUID()}",
            priority = priority,
            payload = DemoPayload(title, "来自接口或长连接的模拟消息，优先级 $priority。", kind),
            deliveryPolicy = policy,
        )

    private fun enqueue(
        title: String,
        priority: Int,
        kind: Kind,
        policy: DialogDeliveryPolicy = DialogDeliveryPolicy.REQUIRE_CONSUMER,
    ) {
        val message = newMessage(title, priority, kind, policy)
        val accepted = DialogScheduler.enqueue(message)
        appendEvent(if (accepted) "入队：$title（优先级 $priority）" else "未入队：$title")
    }

    private fun showDialog(
        consumer: String,
        message: DialogMessage<DemoPayload>,
        delivery: DialogDelivery,
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(message.payload.title)
            .setMessage("${message.payload.content}\n处理者：$consumer\n消息 ID：${message.id}")
            .setPositiveButton("关闭并展示下一条", null)
            .create()
        dialog.setOnDismissListener {
            if (currentDialog === dialog) currentDialog = null
            delivery.finish()
            if (!tearingDown) {
                appendEvent("完成：${message.payload.title}")
                binding.root.post { if (!tearingDown) renderState() }
            }
        }
        dialog.show()
        currentDialog = dialog
        appendEvent("展示：${message.payload.title}，由${consumer}处理")
    }

    private fun appendEvent(event: String) {
        events.add(0, event)
        if (events.size > 12) events.removeAt(events.lastIndex)
        binding.eventLog.text = events.joinToString("\n")
        renderState()
    }

    private fun renderState() {
        val showing = DialogScheduler.showingMessage()?.payload
        binding.showingState.text = when (showing) {
            is DemoPayload -> "正在展示：${showing.title}"
            null -> "正在展示：无"
            else -> "正在展示：其他业务弹窗"
        }
        val waiting = DialogScheduler.pendingSnapshot().filter { it.payload is DemoPayload }
        binding.pendingState.text = if (waiting.isEmpty()) {
            "等待队列：空"
        } else {
            "等待队列：\n" + waiting.joinToString("\n") {
                "${(it.payload as DemoPayload).title}（优先级 ${it.priority}）"
            }
        }
    }
}
