package com.example.itg_dialog

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executor

/** 消息无人处理时的策略。 */
enum class DialogDeliveryPolicy {
    /** 保留消息，直到有消费者成功处理或生产者主动取消。 */
    REQUIRE_CONSUMER,

    /** 只向当前消费者尝试一次；全部拒绝或展示失败后仍无人处理则丢弃。 */
    DROP_IF_UNHANDLED,
}

/** 由生产者提交的弹窗消息。[id] 用于去重；优先级越大越先展示，同优先级按入队顺序展示。 */
data class DialogMessage<T>(
    val id: String,
    val priority: Int,
    val payload: T,
    val deliveryPolicy: DialogDeliveryPolicy = DialogDeliveryPolicy.REQUIRE_CONSUMER,
) {
    init {
        require(id.isNotBlank()) { "弹窗消息 id 不能为空。" }
    }
}

/**
 * 消费者先判断是否能处理消息，再执行自己的弹窗展示逻辑。
 * [show] 接受消息后，必须在弹窗关闭时调用 [DialogDelivery.finish]；无法展示时调用 [DialogDelivery.retry]。
 */
interface DialogConsumer<T> {
    fun canHandle(message: DialogMessage<T>): Boolean

    fun show(message: DialogMessage<T>, delivery: DialogDelivery)
}

/** 当前展示任务的完成凭据。重复调用或对已经失效的凭据调用均无效。 */
interface DialogDelivery {
    /** 弹窗关闭后调用。消息最终移除，并尝试展示下一条。 */
    fun finish()

    /** 展示失败时调用。先尝试其他消费者；无人处理时按消息投递策略保留或丢弃。 */
    fun retry()
}

/**
 * 线程安全的弹窗调度器。生产者可以从任意线程入队；消费者回调在 [callbackExecutor] 上串行执行。
 * 默认使用 Android 主线程，适合直接展示 Dialog。调度器同一时刻只允许一条消息处于展示中。
 *
 * 消费者按注册顺序收到消息；某个消费者返回 false 时，继续询问下一个消费者。
 * 所有消费者都拒绝时，按消息的 [DialogMessage.deliveryPolicy] 保留或丢弃。
 * 展示失败并调用 [DialogDelivery.retry] 时，会继续尝试其他消费者。
 * 注册或注销消费者、添加新消息、完成展示、调用 [retryPending] 时，会重试未处理消息。
 *
 * ```kotlin
 * val subscription = DialogScheduler.register(object : DialogConsumer<Ad> {
 *     override fun canHandle(message: DialogMessage<Ad>) =
 *         lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
 *     override fun show(message: DialogMessage<Ad>, delivery: DialogDelivery) {
 *         AlertDialog.Builder(this@MainActivity)
 *             .setMessage(message.payload.title)
 *             .setOnDismissListener { delivery.finish() }
 *             .show()
 *     }
 * })
 * DialogScheduler.enqueue(DialogMessage(id = "ad-42", priority = 100, payload = ad))
 * // 宿主销毁时 subscription.close()，并关闭已展示的弹窗
 * ```
 */
class DialogScheduler<T>(
    private val callbackExecutor: Executor = MainThreadExecutor,
    private val onError: (Throwable) -> Unit = { Log.e("DialogScheduler", "消费者回调失败", it) },
) {
    companion object {
        /** 全进程共享的队列。业务代码直接调用伴生对象方法即可跨页面生产和消费消息。 */
        private val shared = DialogScheduler<Any>()

        /** 向全局队列投递消息，可在任意线程调用。不同业务可使用不同的 payload 类型。 */
        fun <P : Any> enqueue(message: DialogMessage<P>): Boolean =
            shared.enqueue(DialogMessage(message.id, message.priority, message.payload, message.deliveryPolicy))

        /** 注册只接收 [payloadType] 类型消息的全局消费者，宿主销毁时关闭返回的订阅。 */
        fun <P : Any> register(
            payloadType: Class<P>,
            consumer: DialogConsumer<P>,
        ): AutoCloseable = shared.register(object : DialogConsumer<Any> {
            override fun canHandle(message: DialogMessage<Any>): Boolean {
                if (!payloadType.isInstance(message.payload)) return false
                return consumer.canHandle(message.withPayload(payloadType.cast(message.payload)))
            }

            override fun show(message: DialogMessage<Any>, delivery: DialogDelivery) {
                consumer.show(message.withPayload(payloadType.cast(message.payload)), delivery)
            }

            private fun DialogMessage<Any>.withPayload(payload: P) =
                DialogMessage(id, priority, payload, deliveryPolicy)
        })

        /** Kotlin 调用方可让消费者类型自动推断 payload 类型。 */
        inline fun <reified P : Any> register(consumer: DialogConsumer<P>): AutoCloseable =
            register(P::class.java, consumer)

        /** 删除全局队列中尚未展示的消息。 */
        fun cancel(id: String): Boolean = shared.cancel(id)

        /** 消费者可处理条件改变时，重试全局等待消息。 */
        fun retryPending() = shared.retryPending()

        /** 全局等待消息的优先级快照。 */
        fun pendingSnapshot(): List<DialogMessage<Any>> = shared.pendingSnapshot()

        /** 全局正在展示的消息。 */
        fun showingMessage(): DialogMessage<Any>? = shared.showingMessage()
    }

    private data class Entry<T>(val message: DialogMessage<T>, val sequence: Long)

    internal inner class Registration(val consumer: DialogConsumer<T>)

    private inner class Active(
        val entry: Entry<T>,
        val registration: Registration,
    ) : DialogDelivery {
        override fun finish() = release(this, requeue = false)

        override fun retry() = release(this, requeue = true)
    }

    /** 注销当前消费者。若它正在展示，消息重新入队供其他消费者处理。 */
    inner class Subscription internal constructor(private val registration: Registration) : AutoCloseable {
        override fun close() {
            synchronized(lock) {
                if (!consumers.remove(registration)) return
                val showing = active
                if (showing?.registration === registration) {
                    active = null
                    pending.add(showing.entry)
                    pending.sortWith(entryComparator)
                }
                deferredIds.clear()
                skippedConsumers.clear()
                dispatchRevision++
            }
            requestDispatch()
        }
    }

    private val lock = Any()
    private val entryComparator = compareByDescending<Entry<T>> { it.message.priority }
        .thenBy { it.sequence }
    private val pending = mutableListOf<Entry<T>>()
    private val consumers = mutableListOf<Registration>()
    private val deferredIds = mutableSetOf<String>()
    private val skippedConsumers = mutableMapOf<String, MutableSet<Registration>>()
    private var nextSequence = 0L
    private var dispatchRevision = 0L
    private var active: Active? = null
    private var dispatching = false

    /** 消息入队并排序。重复 id，或一次性消息没有任何注册消费者时返回 false。 */
    fun enqueue(message: DialogMessage<T>): Boolean {
        synchronized(lock) {
            if (pending.any { it.message.id == message.id } || active?.entry?.message?.id == message.id) {
                return false
            }
            if (message.deliveryPolicy == DialogDeliveryPolicy.DROP_IF_UNHANDLED && consumers.isEmpty()) {
                return false
            }
            pending.add(Entry(message, nextSequence++))
            pending.sortWith(entryComparator)
            deferredIds.clear()
            skippedConsumers.clear()
            dispatchRevision++
        }
        requestDispatch()
        return true
    }

    /** 注册消费者。用返回的 [Subscription.close] 在宿主销毁时注销。 */
    fun register(consumer: DialogConsumer<T>): Subscription {
        val registration = Registration(consumer)
        synchronized(lock) {
            consumers.add(registration)
            deferredIds.clear()
            skippedConsumers.clear()
            dispatchRevision++
        }
        requestDispatch()
        return Subscription(registration)
    }

    /** 删除尚未被消费者接收的消息；正在展示的消息需通过 [DialogDelivery.finish] 结束。 */
    fun cancel(id: String): Boolean {
        synchronized(lock) {
            val removed = pending.removeAll { it.message.id == id }
            if (removed) {
                deferredIds.remove(id)
                skippedConsumers.remove(id)
            }
            return removed
        }
    }

    /** 当消费者的可处理条件发生变化时，重新尝试仍在队列中的消息。 */
    fun retryPending() {
        synchronized(lock) {
            deferredIds.clear()
            skippedConsumers.clear()
            dispatchRevision++
        }
        requestDispatch()
    }

    /** 等待中的消息快照，按实际展示优先级排序；不含正在展示的消息。 */
    fun pendingSnapshot(): List<DialogMessage<T>> = synchronized(lock) { pending.map { it.message } }

    /** 正在展示的消息；没有时为 null。 */
    fun showingMessage(): DialogMessage<T>? = synchronized(lock) { active?.entry?.message }

    private fun release(delivery: Active, requeue: Boolean) {
        synchronized(lock) {
            if (active !== delivery) return
            active = null
            if (requeue) {
                pending.add(delivery.entry)
                pending.sortWith(entryComparator)
                deferredIds.remove(delivery.entry.message.id)
                skippedConsumers.getOrPut(delivery.entry.message.id) { mutableSetOf() }
                    .add(delivery.registration)
            } else {
                deferredIds.clear()
                skippedConsumers.clear()
                dispatchRevision++
            }
        }
        requestDispatch()
    }

    private fun requestDispatch() {
        val shouldStart = synchronized(lock) {
            if (dispatching || active != null) false else {
                dispatching = true
                true
            }
        }
        if (!shouldStart) return
        try {
            callbackExecutor.execute(::dispatchLoop)
        } catch (error: Throwable) {
            synchronized(lock) { dispatching = false }
            report(error)
        }
    }

    private fun dispatchLoop() {
        while (true) {
            val offer = synchronized(lock) {
                if (active != null) {
                    dispatching = false
                    return
                }
                val entry = pending.firstOrNull { it.message.id !in deferredIds }
                if (entry == null) {
                    dispatching = false
                    return
                }
                Triple(
                    entry,
                    consumers.filterNot { it in skippedConsumers[entry.message.id].orEmpty() },
                    dispatchRevision,
                )
            }

            val (entry, registrations, revision) = offer
            var claimed = false
            for (registration in registrations) {
                val eligible = synchronized(lock) {
                    active == null && entry in pending && registration in consumers
                }
                if (!eligible) continue
                val accepts = try {
                    registration.consumer.canHandle(entry.message)
                } catch (error: Throwable) {
                    report(error)
                    false
                }
                if (!accepts) continue

                val delivery = synchronized(lock) {
                    if (active != null || entry !in pending || registration !in consumers) null
                    else Active(entry, registration).also {
                        pending.remove(entry)
                        active = it
                        deferredIds.remove(entry.message.id)
                    }
                } ?: continue
                claimed = true
                if (synchronized(lock) { active !== delivery }) break
                try {
                    registration.consumer.show(entry.message, delivery)
                } catch (error: Throwable) {
                    delivery.retry()
                    report(error)
                }
                break
            }
            if (!claimed) synchronized(lock) {
                if (entry in pending && dispatchRevision == revision) {
                    if (entry.message.deliveryPolicy == DialogDeliveryPolicy.DROP_IF_UNHANDLED) {
                        pending.remove(entry)
                        skippedConsumers.remove(entry.message.id)
                    } else {
                        deferredIds.add(entry.message.id)
                    }
                }
            }
        }
    }

    private fun report(error: Throwable) {
        try {
            onError(error)
        } catch (_: Throwable) {
            // 错误处理回调不应破坏队列的调度状态。
        }
    }

    private object MainThreadExecutor : Executor {
        override fun execute(command: Runnable) {
            if (!Handler(Looper.getMainLooper()).post(command)) {
                throw IllegalStateException("无法向主线程投递弹窗任务。")
            }
        }
    }
}
