package com.itg.itg_thread_pools.manager

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Handler 线程管理器
 *
 * 基于 Android [HandlerThread] + [Looper] 的线程管理组件。
 * 与 [ThreadPoolManager]（基于 Java Executor 线程池）互为补充：
 *
 * | 特性 | ThreadPoolManager | HandlerManager |
 * |------|-------------------|----------------|
 * | 底层机制 | Java Executor | Android Looper/HandlerThread |
 * | 线程复用 | 线程池自动管理 | 每个 HandlerThread 独占一个线程 |
 * | 任务排序 | 取决于队列策略 | 严格 FIFO（按 post 顺序） |
 * | Message 支持 | 无 | 支持 what/arg1/arg2/obj |
 * | 延迟/定时 | 通过 ScheduledExecutor | 通过 sendMessageDelayed/Handler.postDelayed |
 * | 适用场景 | 通用并发任务 | 需要 Looper 的场景、序列化 UI 无关任务 |
 *
 * 核心特性:
 * - 命名 HandlerThread 的创建与复用
 * - 主线程 Handler 便捷访问
 * - 基于 Message 的消息传递（what/arg1/arg2/obj）
 * - 延迟执行和定时消息
 * - 空闲时处理 (IdleHandler)
 * - 安全的 Looper 退出管理
 * - 与 ThreadPoolManager 统一生命周期联动
 *
 * @author ITG Team
 * @since 1.0.0
 */
object HandlerManager {

    private const val TAG = "HandlerManager"

    /** 主线程 Handler */
    @JvmField
    val mainHandler: Handler = Handler(Looper.getMainLooper())

    /** 已创建的 HandlerThread 缓存 */
    private val handlerThreads = ConcurrentHashMap<String, HandlerThread>()

    /** 已创建的 Handler 缓存 */
    private val handlers = ConcurrentHashMap<String, Handler>()

    // ==================== HandlerThread 创建/获取 ====================

    /**
     * 获取或创建一个命名的后台 HandlerThread
     *
     * 如果指定名称的 HandlerThread 已存在且存活，直接返回其 Handler；
     * 否则创建新的 HandlerThread 并启动。
     *
     * @param name     线程名（用于调试）
     * @param priority Android 线程优先级，默认 [Process.THREAD_PRIORITY_BACKGROUND]
     * @return 与该 HandlerThread 关联的 [Handler]
     *
     * 使用示例:
     * ```kotlin
     * // 获取数据库写入专用线程的 Handler
     * val dbHandler = HandlerManager.getOrCreate("db-writer")
     * dbHandler.post { database.insert(record) }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun getOrCreate(
        name: String,
        priority: Int = Process.THREAD_PRIORITY_BACKGROUND
    ): Handler {
        // 已存在且存活，直接返回
        val existingHandler = handlers[name]
        if (existingHandler != null &&
            handlerThreads[name]?.isAlive == true
        ) {
            return existingHandler
        }

        // 清理已退出的旧实例
        handlerThreads.remove(name)?.let { old ->
            if (old.isAlive) old.quitSafely()
        }

        // 创建新的 HandlerThread
        val thread = HandlerThread(name, priority).apply {
            start()
        }
        val handler = Handler(thread.looper)

        handlerThreads[name] = thread
        handlers[name] = handler

        return handler
    }

    /**
     * 获取指定名称的 HandlerThread 实例
     *
     * @param name 线程名
     * @return [HandlerThread] 实例，不存在时返回 null
     */
    @JvmStatic
    fun getHandlerThread(name: String): HandlerThread? {
        return handlerThreads[name]
    }

    /**
     * 获取指定名称的 Handler 实例
     *
     * @param name 线程名
     * @return [Handler] 实例，不存在时返回 null
     */
    @JvmStatic
    fun getHandler(name: String): Handler? {
        return handlers[name]
    }

    /**
     * 获取指定 HandlerThread 的 Looper
     *
     * @param name 线程名
     * @return [Looper] 实例，不存在时返回 null
     */
    @JvmStatic
    fun getLooper(name: String): Looper? {
        return handlerThreads[name]?.looper
    }

    // ==================== 发送 Runnable ====================

    /**
     * 向指定名前线程提交 Runnable 任务
     *
     * @param name 线程名
     * @param task 要执行的任务
     *
     * 使用示例:
     * ```kotlin
     * HandlerManager.post("db-writer") {
     *     database.deleteOldRecords()
     * }
     * ```
     */
    @JvmStatic
    fun post(name: String, task: () -> Unit) {
        runCatching {
            val handler = getOrCreate(name)
            handler.post(task)
        }.onFailure { e ->
            Log.e(TAG, "post to '$name' failed", e)
        }
    }

    /**
     * 向指定名前线程延迟提交 Runnable 任务
     *
     * @param name    线程名
     * @param delayMs 延迟毫秒数
     * @param task    要执行的任务
     */
    @JvmStatic
    fun postDelayed(name: String, delayMs: Long, task: () -> Unit) {
        runCatching {
            val handler = getOrCreate(name)
            handler.postDelayed(task, delayMs)
        }.onFailure { e ->
            Log.e(TAG, "postDelayed to '$name' failed", e)
        }
    }

    /**
     * 在指定时间点执行任务
     *
     * @param name     线程名
     * @param uptimeMs 执行时间点 (基于 [android.os.SystemClock.uptimeMillis])
     * @param task     要执行的任务
     */
    @JvmStatic
    fun postAtTime(name: String, uptimeMs: Long, task: () -> Unit) {
        runCatching {
            val handler = getOrCreate(name)
            handler.postAtTime(task, uptimeMs)
        }.onFailure { e ->
            Log.e(TAG, "postAtTime to '$name' failed", e)
        }
    }

    /**
     * 将任务插入到消息队列最前端
     *
     * @param name 线程名
     * @param task 要执行的任务
     */
    @JvmStatic
    fun postAtFront(name: String, task: () -> Unit) {
        runCatching {
            val handler = getOrCreate(name)
            handler.postAtFrontOfQueue(task)
        }.onFailure { e ->
            Log.e(TAG, "postAtFront to '$name' failed", e)
        }
    }

    // ==================== 发送 Message ====================

    /**
     * 向指定线程发送空 Message
     *
     * @param name 线程名
     * @param what Message.what 标识
     *
     * 使用示例:
     * ```kotlin
     * // 配合自定义 Handler.Callback 处理
     * HandlerManager.sendMessage("worker", MSG_SAVE_DATA)
     * ```
     */
    @JvmStatic
    fun sendMessage(name: String, what: Int) {
        runCatching {
            val handler = getOrCreate(name)
            handler.sendEmptyMessage(what)
        }.onFailure { e ->
            Log.e(TAG, "sendMessage to '$name' failed", e)
        }
    }

    /**
     * 向指定线程发送带参数的 Message
     *
     * @param name 线程名
     * @param what Message.what
     * @param arg1 Message.arg1
     * @param arg2 Message.arg2
     * @param obj  Message.obj
     *
     * 使用示例:
     * ```kotlin
     * HandlerManager.sendMessage("worker", MSG_PROCESS, userId, 0, userData)
     * ```
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
            val handler = getOrCreate(name)
            handler.sendMessage(
                handler.obtainMessage(what, arg1, arg2, obj)
            )
        }.onFailure { e ->
            Log.e(TAG, "sendMessage to '$name' failed", e)
        }
    }

    /**
     * 向指定线程延迟发送带参数的 Message
     *
     * @param name    线程名
     * @param what    Message.what
     * @param delayMs 延迟毫秒数
     * @param arg1    Message.arg1
     * @param arg2    Message.arg2
     * @param obj     Message.obj
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
            val handler = getOrCreate(name)
            handler.sendMessageDelayed(
                handler.obtainMessage(what, arg1, arg2, obj),
                delayMs
            )
        }.onFailure { e ->
            Log.e(TAG, "sendMessageDelayed to '$name' failed", e)
        }
    }

    /**
     * 向指定线程发送自定义 Message 对象
     *
     * @param name    线程名
     * @param message 自定义 Message
     */
    @JvmStatic
    fun sendCustomMessage(name: String, message: Message) {
        runCatching {
            val handler = getOrCreate(name)
            handler.sendMessage(message)
        }.onFailure { e ->
            Log.e(TAG, "sendCustomMessage to '$name' failed", e)
        }
    }

    // ==================== 移除任务 ====================

    /**
     * 从指定线程的消息队列中移除指定 Runnable
     *
     * @param name   线程名
     * @param task   要移除的 Runnable
     */
    @JvmStatic
    fun removeCallbacks(name: String, task: Runnable) {
        handlers[name]?.removeCallbacks(task)
    }

    /**
     * 从指定线程的消息队列中移除指定 what 的所有 Message
     *
     * @param name 线程名
     * @param what 要移除的 Message.what
     */
    @JvmStatic
    fun removeMessages(name: String, what: Int) {
        handlers[name]?.removeMessages(what)
    }

    /**
     * 从指定线程的消息队列中移除所有 callback 和 message
     *
     * @param name 线程名
     * @param token 匹配的 token 对象，null 表示全部移除
     */
    @JvmStatic
    @JvmOverloads
    fun removeAll(name: String, token: Any? = null) {
        handlers[name]?.removeCallbacksAndMessages(token)
    }

    // ==================== Looper 空闲处理 ====================

    /**
     * 向指定线程添加 IdleHandler
     *
     * IdleHandler 在消息队列空闲时被调用（当前没有需要立即处理的消息时）。
     * 适用于低优先级的延迟操作，如预加载、GC 触发等。
     *
     * @param name         线程名
     * @param idleHandler  空闲处理器，返回 true 保持活跃，false 执行一次后移除
     *
     * 使用示例:
     * ```kotlin
     * HandlerManager.addIdleHandler("worker") {
     *     // 消息队列空闲时执行
     *     preloadNextPageCache()
     *     false  // 仅执行一次
     * }
     * ```
     */
    @JvmStatic
    fun addIdleHandler(name: String, idleHandler: MessageQueue.IdleHandler) {
        val looper = getLooper(name) ?: return
        looper.queue.addIdleHandler(idleHandler)
    }

    /**
     * 从指定线程移除 IdleHandler
     *
     * @param name         线程名
     * @param idleHandler  要移除的 IdleHandler
     */
    @JvmStatic
    fun removeIdleHandler(name: String, idleHandler: MessageQueue.IdleHandler) {
        val looper = getLooper(name) ?: return
        looper.queue.removeIdleHandler(idleHandler)
    }

    // ==================== 生命周期 ====================

    /**
     * 退出指定 HandlerThread 的 Looper
     *
     * 调用后该线程将停止处理消息，HandlerThread 线程退出。
     *
     * @param name   线程名
     * @param safely 是否安全退出（等待已调度的消息处理完毕），默认 true
     * @return true 表示成功发出退出指令
     */
    @JvmStatic
    @JvmOverloads
    fun quit(name: String, safely: Boolean = true): Boolean {
        val thread = handlerThreads.remove(name)
        val handler = handlers.remove(name)
        handler?.removeCallbacksAndMessages(null)

        return if (thread != null && thread.isAlive) {
            if (safely) thread.quitSafely() else thread.quit()
            true
        } else {
            false
        }
    }

    /**
     * 退出所有已创建的 HandlerThread
     *
     * 建议在 Application.onTerminate() 中调用。
     */
    @JvmStatic
    fun quitAll() {
        handlerThreads.keys.forEach { name ->
            quit(name, safely = true)
        }
        handlerThreads.clear()
        handlers.clear()
    }

    // ==================== 状态查询 ====================

    /**
     * 检查指定名称的 HandlerThread 是否存活
     *
     * @param name 线程名
     * @return true 表示存活
     */
    @JvmStatic
    fun isAlive(name: String): Boolean {
        return handlerThreads[name]?.isAlive == true
    }

    /**
     * 获取所有已注册的 HandlerThread 名称
     */
    @JvmStatic
    fun getAllNames(): Set<String> {
        return handlerThreads.keys.toSet()
    }

    /**
     * 已注册的 HandlerThread 数量
     */
    @JvmStatic
    fun getCount(): Int {
        return handlerThreads.size
    }

    /**
     * 获取指定 HandlerThread 的详细信息
     *
     * @param name 线程名
     * @return 包含线程信息、Looper 状态、队列状态等的 Map
     */
    @JvmStatic
    fun getInfo(name: String): Map<String, Any> {
        val thread = handlerThreads[name]
        val handler = handlers[name]

        if (thread == null) {
            return mapOf("name" to name, "exists" to false)
        }

        return mutableMapOf<String, Any>().apply {
            put("name", name)
            put("exists", true)
            put("isAlive", thread.isAlive)
            put("threadId", thread.id)
            put("threadName", thread.name)
            put("priority", thread.priority)
            put("looper" to (thread.looper?.toString() ?: "null"), value = TODO())
            put("isCurrentThread", thread.id == Thread.currentThread().id)
            put("handlerExists", handler != null)
            // 消息队列信息
            thread.looper?.let { looper ->
                put("queueIdle", looper.queue.isIdle)
                // isIdle 需要 API 23+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    put("queueIdle23", looper.queue.isIdle)
                }
            }
        }
    }

    fun put(key: Pair<String, String>, value: Nothing) {}

    /**
     * 打印所有 HandlerThread 的状态信息（用于调试）
     */
    @JvmStatic
    fun printAllInfo() {
        val names = getAllNames()
        if (names.isEmpty()) {
            Log.d(TAG, "No HandlerThreads registered")
            return
        }
        Log.d(TAG, "=== HandlerManager ($names.size threads) ===")
        names.forEach { name ->
            val info = getInfo(name)
            Log.d(TAG, "  [$name] alive=${info["isAlive"]}, " +
                    "threadId=${info["threadId"]}, queueIdle=${info["queueIdle"]}")
        }
    }

    // ==================== 主线程便捷方法 ====================

    /**
     * 在主线程执行任务
     *
     * 等效于 Handler(Looper.getMainLooper()).post(task)
     *
     * @param task 要执行的任务
     */
    @JvmStatic
    fun postToMain(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            mainHandler.post(task)
        }
    }

    /**
     * 延迟在主线程执行任务
     *
     * @param task    要执行的任务
     * @param delayMs 延迟毫秒数
     * @return 用于取消的 Runnable
     */
    @JvmStatic
    fun postToMainDelayed(task: () -> Unit, delayMs: Long): Runnable {
        val runnable = Runnable { task() }
        mainHandler.postDelayed(runnable, delayMs)
        return runnable
    }

    /**
     * 在主线程发送空 Message
     *
     * @param what Message.what 标识
     */
    @JvmStatic
    fun sendMainMessage(what: Int) {
        mainHandler.sendEmptyMessage(what)
    }

    /**
     * 在主线程发送带参数的 Message
     *
     * @param what Message.what
     * @param arg1 Message.arg1
     * @param arg2 Message.arg2
     * @param obj  Message.obj
     */
    @JvmStatic
    fun sendMainMessage(what: Int, arg1: Int = 0, arg2: Int = 0, obj: Any? = null) {
        mainHandler.sendMessage(
            mainHandler.obtainMessage(what, arg1, arg2, obj)
        )
    }
}
