package com.itg.itg_coroutine_pools.channel

import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 协程消息通道管理器 — 对标 [com.itg.itg_thread_pools.manager.HandlerManager]
 *
 * 用 [Channel] + [CoroutineScope] 替代 HandlerThread + Looper 机制。
 * 提供严格串行的消息处理，API 和 HandlerManager 保持一致。
 *
 * 对比 HandlerManager:
 * | 特性 | HandlerManager | ChannelManager |
 * |------|---------------|----------------|
 * | 底层机制 | Android HandlerThread + Looper | Kotlin Channel + Coroutine |
 * | 线程占用 | 每个名称独占一个物理线程 | 协程轻量级，复用线程 |
 * | 任务排序 | 严格 FIFO | 严格 FIFO |
 * | Message 支持 | what/arg1/arg2/obj | Message(what, arg1, arg2, obj) |
 * | 延迟/定时 | Handler.postDelayed | delay() + Channel.send |
 * | 空闲检测 | IdleHandler | Channel.isEmpty |
 *
 * 核心特性:
 * - 命名 Channel 的创建与复用
 * - 基于 Message 的消息传递（what/arg1/arg2/obj）
 * - 延迟执行
 * - 安全的 Channel 关闭管理
 *
 * @author ITG Team
 * @since 1.0.0
 */
object ChannelManager {

    /**
     * 消息结构 — 对标 Android Message
     */
    data class Message(
        val what: Int,
        val arg1: Int = 0,
        val arg2: Int = 0,
        val obj: Any? = null
    )

    /** 内部管理结构 */
    private data class NamedChannel(
        val scope: CoroutineScope,
        val taskChannel: Channel<() -> Unit>,
        val messageChannel: Channel<Message>
    )

    /** 已创建的 Channel 缓存 */
    private val channels = ConcurrentHashMap<String, NamedChannel>()

    // ==================== 创建 / 获取 ====================

    /**
     * 获取或创建命名通道 — 对标 HandlerManager.getOrCreate()
     *
     * 如果指定名称的 Channel 已存在，直接返回；
     * 否则创建新的协程作用域和 Channel 对。
     *
     * @param name             通道名称（用于调试）
     * @param messageProcessor 可选的消息处理器（处理 Message what/arg1/arg2/obj）
     *
     * 使用示例:
     * ```kotlin
     * // 创建带消息处理的通道
     * ChannelManager.getOrCreate("worker") { msg ->
     *     when (msg.what) {
     *         MSG_SAVE -> saveData(msg.obj)
     *         MSG_QUIT -> ChannelManager.quit("worker")
     *     }
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun getOrCreate(
        name: String,
        messageProcessor: ((Message) -> Unit)? = null
    ) {
        require(name.isNotBlank()) { "name must not be blank" }
        if (channels.containsKey(name)) return

        channels.computeIfAbsent(name) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
            val taskChannel = Channel<() -> Unit>(Channel.UNLIMITED)
            val msgChannel = Channel<Message>(Channel.UNLIMITED)

            // 消费循环（严格串行处理）
            scope.launch {
                // 优先处理 Runnable
                val taskJob = launch {
                    for (task in taskChannel) {
                        try { task() } catch (e: Exception) {
                            android.util.Log.e("ChannelManager-$name", "Task error", e)
                        }
                    }
                }
                // 并行监听 Message（和 Runnable 在同一个单线程 dispatcher 上）
                if (messageProcessor != null) {
                    launch {
                        for (msg in msgChannel) {
                            try { messageProcessor(msg) } catch (e: Exception) {
                                android.util.Log.e("ChannelManager-$name", "Message error", e)
                            }
                        }
                    }
                }
                taskJob.join()
            }

            NamedChannel(scope, taskChannel, msgChannel)
        }
    }

    // ==================== 提交 Runnable ====================

    /**
     * 向指定通道提交 Runnable 任务 — 对标 HandlerManager.post()
     */
    @JvmStatic
    fun post(name: String, task: () -> Unit) {
        runCatching {
            getOrCreate(name)
            channels[name]?.let { named ->
                named.scope.launch {
                    named.taskChannel.send(task)
                }
            }
        }.onFailure { e ->
            android.util.Log.e("ChannelManager", "post to '$name' failed", e)
        }
    }

    /**
     * 向指定通道延迟提交 Runnable 任务 — 对标 HandlerManager.postDelayed()
     */
    @JvmStatic
    fun postDelayed(name: String, delayMs: Long, task: () -> Unit) {
        runCatching {
            getOrCreate(name)
            channels[name]?.let { named ->
                named.scope.launch {
                    delay(delayMs)
                    named.taskChannel.send(task)
                }
            }
        }.onFailure { e ->
            android.util.Log.e("ChannelManager", "postDelayed to '$name' failed", e)
        }
    }

    // ==================== 发送 Message ====================

    /**
     * 向指定通道发送 Message — 对标 HandlerManager.sendMessage()
     *
     * @param name 通道名称
     * @param what Message.what 标识
     * @param arg1 Message.arg1
     * @param arg2 Message.arg2
     * @param obj  Message.obj
     */
    @JvmStatic
    @JvmOverloads
    fun sendMessage(
        name: String,
        what: Int,
        arg1: Int = 0,
        arg2: Int = 0,
        obj: Any? = null
    ) {
        runCatching {
            getOrCreate(name)
            channels[name]?.let { named ->
                named.scope.launch {
                    named.messageChannel.send(Message(what, arg1, arg2, obj))
                }
            }
        }.onFailure { e ->
            android.util.Log.e("ChannelManager", "sendMessage to '$name' failed", e)
        }
    }

    /**
     * 向指定通道延迟发送 Message — 对标 HandlerManager.sendMessageDelayed()
     */
    @JvmStatic
    @JvmOverloads
    fun sendMessageDelayed(
        name: String,
        what: Int,
        delayMs: Long,
        arg1: Int = 0,
        arg2: Int = 0,
        obj: Any? = null
    ) {
        runCatching {
            getOrCreate(name)
            channels[name]?.let { named ->
                named.scope.launch {
                    delay(delayMs)
                    named.messageChannel.send(Message(what, arg1, arg2, obj))
                }
            }
        }.onFailure { e ->
            android.util.Log.e("ChannelManager", "sendMessageDelayed to '$name' failed", e)
        }
    }

    // ==================== 主线程便捷方法 ====================

    /**
     * 在主线程执行任务 — 对标 HandlerManager.postToMain()
     */
    @JvmStatic
    fun postToMain(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            CoroutineScope(Dispatchers.Main).launch { task() }
        }
    }

    /**
     * 延迟在主线程执行任务 — 对标 HandlerManager.postToMainDelayed()
     */
    @JvmStatic
    fun postToMainDelayed(task: () -> Unit, delayMs: Long): Runnable {
        val runnable = Runnable { task() }
        android.os.Handler(Looper.getMainLooper()).postDelayed(runnable, delayMs)
        return runnable
    }

    /**
     * 向主线程发送 Message — 对标 HandlerManager.sendMainMessage()
     */
    @JvmStatic
    fun sendMainMessage(what: Int, arg1: Int = 0, arg2: Int = 0, obj: Any? = null) {
        val handler = android.os.Handler(Looper.getMainLooper())
        handler.sendMessage(handler.obtainMessage(what, arg1, arg2, obj))
    }

    // ==================== 取消 / 退出 ====================

    /**
     * 退出指定通道 — 对标 HandlerManager.quit()
     */
    @JvmStatic
    @Synchronized
    fun quit(name: String): Boolean {
        val named = channels.remove(name) ?: return false
        named.taskChannel.close()
        named.messageChannel.close()
        named.scope.cancel()
        return true
    }

    /**
     * 退出所有通道 — 对标 HandlerManager.quitAll()
     */
    @JvmStatic
    @Synchronized
    fun quitAll() {
        channels.keys.forEach { name -> quit(name) }
        channels.clear()
    }

    // ==================== 状态查询 ====================

    /**
     * 检查指定通道是否存活 — 对标 HandlerManager.isAlive()
     */
    @JvmStatic
    fun isAlive(name: String): Boolean {
        return channels.containsKey(name)
    }

    /**
     * 获取所有已注册的通道名称 — 对标 HandlerManager.getAllNames()
     */
    @JvmStatic
    fun getAllNames(): Set<String> {
        return channels.keys.toSet()
    }

    /**
     * 已注册通道数量 — 对标 HandlerManager.getCount()
     */
    @JvmStatic
    fun getCount(): Int {
        return channels.size
    }

    /**
     * 获取指定通道的详细信息 — 对标 HandlerManager.getInfo()
     */
    @JvmStatic
    fun getInfo(name: String): Map<String, Any> {
        val named = channels[name]
        if (named == null) {
            return mapOf("name" to name, "exists" to false)
        }
        return mapOf(
            "name" to name,
            "exists" to true,
            "isActive" to named.scope.isActive,
            "taskChannelClosed" to named.taskChannel.isClosedForSend,
            "messageChannelClosed" to named.messageChannel.isClosedForSend
        )
    }

    /**
     * 打印所有通道状态（用于调试）— 对标 HandlerManager.printAllInfo()
     */
    @JvmStatic
    fun printAllInfo() {
        val names = getAllNames()
        if (names.isEmpty()) {
            android.util.Log.d("ChannelManager", "No channels registered")
            return
        }
        android.util.Log.d("ChannelManager", "=== ChannelManager (${names.size} channels) ===")
        names.forEach { name ->
            val info = getInfo(name)
            android.util.Log.d("ChannelManager", "  [$name] exists=${info["exists"]}, active=${info["isActive"]}")
        }
    }
}
