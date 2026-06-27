package com.itg.itg_thread_pools.utils

import android.os.Looper
import android.os.MessageQueue
import android.os.Process
import com.itg.itg_thread_pools.manager.ThreadPoolManager
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 线程工具类
 *
 * 提供线程相关的检测、断言、信息获取和便捷操作，以及
 * Android Looper 相关的检测与操作。
 *
 * 核心功能:
 * - 线程检测: 判断当前是否为主线程 / 后台线程
 * - Looper 检测: 判断当前线程是否有 Looper / 是否为指定 Looper
 * - 线程断言: 在调试阶段强制要求线程上下文
 * - 线程信息: 获取当前线程的详细信息（含 Looper 信息）
 * - 便捷操作: sleep、主线程执行、Looper 准备/退出等
 *
 * @author ITG Team
 * @since 1.0.0
 */
object ThreadUtils {

    // ==================== 线程检测 ====================

    /**
     * 判断当前是否为主线程 (UI 线程)
     *
     * @return true 表示当前在主线程
     *
     * 使用示例:
     * ```kotlin
     * if (ThreadUtils.isMainThread()) {
     *     // 直接更新 UI
     * } else {
     *     // 切到主线程
     *     ThreadUtils.runOnUiThread { updateUI() }
     * }
     * ```
     */
    @JvmStatic
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * 判断当前是否为后台线程（非主线程）
     *
     * @return true 表示在后台线程
     */
    @JvmStatic
    fun isBackgroundThread(): Boolean {
        return !isMainThread()
    }

    // ==================== 线程断言 ====================

    /**
     * 断言当前在主线程，否则抛出 [IllegalStateException]
     *
     * 适用于需要确保在主线程执行的方法（如 View 操作）。
     *
     * @param message 自定义错误消息，可选
     * @throws IllegalStateException 如果不在主线程
     *
     * 使用示例:
     * ```kotlin
     * fun updateView() {
     *     ThreadUtils.assertMainThread("updateView must be called on main thread")
     *     // ... 更新 UI
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun assertMainThread(message: String = "Must be called on the main thread") {
        if (!isMainThread()) {
            throw IllegalStateException("$message (current: ${Thread.currentThread().name})")
        }
    }

    /**
     * 断言当前在后台线程，否则抛出 [IllegalStateException]
     *
     * 适用于需要确保在后台执行的方法（如网络请求）。
     *
     * @param message 自定义错误消息
     * @throws IllegalStateException 如果在主线程
     */
    @JvmStatic
    @JvmOverloads
    fun assertBackgroundThread(message: String = "Must be called on a background thread") {
        if (isMainThread()) {
            throw IllegalStateException("$message (current: main thread)")
        }
    }

    // ==================== 主线程切换 ====================

    /**
     * 在主线程执行任务（便捷方法）
     *
     * 如果当前已在主线程则直接执行。
     *
     * @param task 要执行的任务
     *
     * 使用示例:
     * ```kotlin
     * ThreadUtils.runOnUiThread {
     *     textView.text = "Updated from background"
     * }
     * ```
     */
    @JvmStatic
    fun runOnUiThread(task: () -> Unit) {
        ThreadPoolManager.mainExecutor.execute(task)
    }

    /**
     * 在主线程延迟执行任务
     *
     * @param task    要执行的任务
     * @param delayMs 延迟毫秒数
     */
    @JvmStatic
    fun runOnUiThreadDelayed(task: () -> Unit, delayMs: Long) {
        android.os.Handler(Looper.getMainLooper()).postDelayed(task, delayMs)
    }

    // ==================== 线程信息 ====================

    /**
     * 获取当前线程的详细信息
     *
     * @return 包含线程名、ID、优先级、是否主线程等的 Map
     *
     * 使用示例:
     * ```kotlin
     * val info = ThreadUtils.getCurrentThreadInfo()
     * println(info)
     * // {
     * //   name=itg-io-3,
     * //   id=142,
     * //   priority=5,
     * //   isMain=false,
     * //   isDaemon=false,
     * //   state=RUNNABLE,
     * //   stackTrace=...
     * // }
     * ```
     */
    @JvmStatic
    fun getCurrentThreadInfo(): Map<String, Any> {
        val thread = Thread.currentThread()
        return mapOf(
            "name" to thread.name,
            "id" to thread.id,
            "priority" to thread.priority,
            "isMain" to isMainThread(),
            "isDaemon" to thread.isDaemon,
            "isAlive" to thread.isAlive,
            "isInterrupted" to thread.isInterrupted,
            "state" to thread.state.name,
            "threadGroup" to (thread.threadGroup?.name ?: "none"),
            "stackTrace" to thread.stackTrace
                .take(10)
                .joinToString("\n") { "  at $it" }
        )
    }

    /**
     * 获取当前线程的简要描述
     *
     * @return 格式如 "itg-io-3 (id=142, main=false)"
     */
    @JvmStatic
    fun getCurrentThreadDescription(): String {
        val t = Thread.currentThread()
        return "${t.name} (id=${t.id}, main=${isMainThread()})"
    }

    /**
     * 打印当前线程调用栈（用于调试）
     *
     * @param tag 日志标签
     * @param maxDepth 最大栈深度，默认 15
     */
    @JvmStatic
    @JvmOverloads
    fun logStackTrace(tag: String = "ThreadUtils", maxDepth: Int = 15) {
        val stackTrace = Thread.currentThread().stackTrace
            .drop(3)  // 跳过 getStackTrace、logStackTrace、调用者
            .take(maxDepth)
            .joinToString("\n") { "  at $it" }
        android.util.Log.d(tag, "Thread: ${Thread.currentThread().name}\n$stackTrace")
    }

    // ==================== 线程操作 ====================

    /**
     * 使当前线程休眠
     *
     * 注意: **不可在主线程调用**，会导致 ANR。
     *
     * @param ms 休眠毫秒数
     *
     * 使用示例:
     * ```kotlin
     * // 在后台线程轮询，间隔 500ms
     * while (!done) {
     *     ThreadUtils.sleep(500)
     * }
     * ```
     */
    @JvmStatic
    fun sleep(ms: Long) {
        if (isMainThread()) {
            android.util.Log.w(
                "ThreadUtils",
                "sleep() called on main thread — this will cause ANR!",
                IllegalStateException("sleep on main thread")
            )
            return
        }
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 安全地使当前线程休眠（响应中断）
     *
     * 与 [sleep] 区别: 被中断时不吞掉中断状态。
     *
     * @param ms 休眠毫秒数
     * @throws InterruptedException 如果被中断
     */
    @JvmStatic
    @Throws(InterruptedException::class)
    fun sleepInterruptibly(ms: Long) {
        Thread.sleep(ms)
    }

    /**
     * 设置当前线程的优先级
     *
     * @param priority 线程优先级 [Thread.MIN_PRIORITY]..[Thread.MAX_PRIORITY]
     */
    @JvmStatic
    fun setCurrentThreadPriority(priority: Int) {
        Thread.currentThread().priority = priority.coerceIn(
            Thread.MIN_PRIORITY,
            Thread.MAX_PRIORITY
        )
    }

    /**
     * 将当前线程设为低优先级（适用于后台预加载等低优先级任务）
     */
    @JvmStatic
    fun setLowPriority() {
        setCurrentThreadPriority(Thread.MIN_PRIORITY)
    }

    /**
     * 设置当前线程的 Android 进程优先级（用于降低后台线程对系统的抢占）
     *
     * @param priority 进程优先级，如 [Process.THREAD_PRIORITY_BACKGROUND]
     */
    @JvmStatic
    fun setProcessThreadPriority(priority: Int) {
        Process.setThreadPriority(priority)
    }

    /**
     * 将当前线程设为后台进程优先级
     *
     * 适用于对延迟不敏感的后台任务，降低对主线程的影响。
     */
    @JvmStatic
    fun setBackgroundPriority() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
    }

    // ==================== 线程池便捷方法 ====================

    /**
     * 获取当前所有活跃线程的概要信息
     *
     * @return 线程组简要描述
     */
    @JvmStatic
    fun getActiveThreadSummary(): String {
        val current = Thread.currentThread()
        val group = current.threadGroup
        // 估算活跃线程数
        val estimated = group?.activeCount() ?: -1
        return buildString {
            appendLine("Thread Group: ${group?.name ?: "unknown"}")
            appendLine("Active Threads (est.): $estimated")
            appendLine("Current: $current")
        }
    }

    // ==================== Looper 操作 ====================

    /**
     * 判断当前线程是否已准备好 Looper
     *
     * Android 中只有主线程默认有 Looper，后台线程需要手动调用
     * [Looper.prepare] 或使用 [android.os.HandlerThread]。
     *
     * @return true 表示当前线程有 Looper
     *
     * 使用示例:
     * ```kotlin
     * if (!ThreadUtils.hasLooper()) {
     *     Looper.prepare()
     *     // ... 创建 Handler ...
     *     Looper.loop()
     * }
     * ```
     */
    @JvmStatic
    fun hasLooper(): Boolean {
        return Looper.myLooper() != null
    }

    /**
     * 判断当前线程是否是某个指定 Looper 所在的线程
     *
     * @param looper 要检查的 Looper
     * @return true 表示当前正在该 Looper 的线程上运行
     *
     * 使用示例:
     * ```kotlin
     * val bgLooper = HandlerManager.getLooper("worker")
     * if (ThreadUtils.isCurrentLooper(bgLooper)) {
     *     // 当前在 worker 线程上
     * }
     * ```
     */
    @JvmStatic
    fun isCurrentLooper(looper: Looper?): Boolean {
        return looper != null && Looper.myLooper() == looper
    }

    /**
     * 为当前线程准备 Looper
     *
     * 调用后可以通过 `Looper.myLooper()` 获取 Looper，
     * 并可创建 Handler。
     *
     * @throws RuntimeException 如果当前线程已有 Looper
     *
     * 使用示例:
     * ```kotlin
     * thread {
     *     ThreadUtils.prepareLooper()
     *     val handler = Handler(Looper.myLooper()) { msg ->
     *         // 处理消息
     *         true
     *     }
     *     Looper.loop()  // 开始消息循环（阻塞）
     * }
     * ```
     */
    @JvmStatic
    fun prepareLooper() {
        if (hasLooper()) {
            throw RuntimeException("Looper already prepared for thread '${Thread.currentThread().name}'")
        }
        Looper.prepare()
    }

    /**
     * 启动当前线程的 Looper 消息循环
     *
     * **警告: 此方法会阻塞当前线程**，直到 Looper 被退出。
     * 通常与 [prepareLooper] 配合使用。
     *
     * @throws RuntimeException 如果当前线程没有 Looper
     */
    @JvmStatic
    fun loop() {
        val looper = Looper.myLooper()
            ?: throw RuntimeException("No Looper; call prepareLooper() first")
        Looper.loop()
    }

    /**
     * 退出当前线程的 Looper
     *
     * @param safely 是否安全退出（处理完队列中已有消息），默认 true
     *
     * 使用示例:
     * ```kotlin
     * // 在 Handler 中处理退出消息
     * handler.post {
     *     ThreadUtils.quitLooper(safely = true)
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun quitLooper(safely: Boolean = true) {
        val looper = Looper.myLooper() ?: return
        if (safely) {
            looper.quitSafely()
        } else {
            looper.quit()
        }
    }

    /**
     * 启动 Looper 循环，并在指定任务完成后自动退出
     *
     * 适用于"启动线程处理一个任务然后退出"的场景。
     *
     * @param timeoutMs 超时毫秒数，0 表示无超时
     * @param onIdle    可选的消息队列空闲回调
     * @param body      要在 Looper 线程上执行的任务块，
     *                  入参 Handler 可用于发送消息，调用方负责在任务完成后调用 quit
     *
     * 使用示例:
     * ```kotlin
     * thread {
     *     ThreadUtils.runWithLooper(timeoutMs = 30_000) { handler ->
     *         handler.post {
     *             doWork()
     *             ThreadUtils.quitLooper()  // 完成后退�� Looper
     *         }
     *     }
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun runWithLooper(
        timeoutMs: Long = 0,
        onIdle: (() -> Unit)? = null,
        body: (android.os.Handler) -> Unit
    ) {
        prepareLooper()
        val handler = android.os.Handler(Looper.myLooper()!!)

        // 可选的超时自动退出
        if (timeoutMs > 0) {
            handler.postDelayed({
                android.util.Log.w(
                    "ThreadUtils",
                    "runWithLooper: timeout after ${timeoutMs}ms, quitting"
                )
                quitLooper(safely = true)
            }, timeoutMs)
        }

        // 可选的空闲处理
        onIdle?.let { callback ->
            Looper.myLooper()?.queue?.addIdleHandler(
                object : MessageQueue.IdleHandler {
                    override fun queueIdle(): Boolean {
                        callback()
                        return false  // 仅触发一次
                    }
                }
            )
        }

        try {
            body(handler)
            loop()
        } catch (e: Exception) {
            android.util.Log.e(
                "ThreadUtils",
                "Error in runWithLooper on thread '${Thread.currentThread().name}'",
                e
            )
        }
    }

    /**
     * 获取当前线程 Looper 的详细信息
     *
     * @return 包含 Looper 线程、队列空闲状态等的 Map，无 Looper 时返回空 Map
     */
    @JvmStatic
    fun getLooperInfo(): Map<String, Any> {
        val looper = Looper.myLooper() ?: return mapOf("hasLooper" to false)
        return mapOf(
            "hasLooper" to true,
            "thread" to looper.thread.name,
            "threadId" to looper.thread.id,
            "isMainLooper" to (looper == Looper.getMainLooper()),
            "queueIdle" to (looper.queue.isIdle)
        )
    }

    /**
     * 获取主线程 Looper
     *
     * @return 主线程的 [Looper]
     */
    @JvmStatic
    fun getMainLooper(): Looper {
        return Looper.getMainLooper()
    }

    /**
     * 获取当前线程的 Looper（可能为 null）
     *
     * @return 当前线程的 Looper，无则返回 null
     */
    @JvmStatic
    fun getMyLooper(): Looper? {
        return Looper.myLooper()
    }
}
