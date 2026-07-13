package com.itg.itg_coroutine_pools.utils

import android.os.Looper
import android.os.Process

/**
 * 协程工具类 — 对标 [com.itg.itg_thread_pools.utils.ThreadUtils]
 *
 * 提供线程检测、断言、延迟等辅助功能。
 * 与 ThreadUtils 的区别：
 * - `sleep()` → `delay()`（非阻塞挂起，不占用线程）
 * - 去掉 Looper 相关操作（协程不需要手动管理 Looper）
 *
 * @author ITG Team
 * @since 1.0.0
 */
object CoroutineUtils {

    // ==================== 线程检测 ====================

    /**
     * 判断当前是否为主线程 — 对标 ThreadUtils.isMainThread()
     */
    @JvmStatic
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * 判断当前是否为后台线程 — 对标 ThreadUtils.isBackgroundThread()
     */
    @JvmStatic
    fun isBackgroundThread(): Boolean {
        return !isMainThread()
    }

    // ==================== 线程断言 ====================

    /**
     * 断言当前在主线程 — 对标 ThreadUtils.assertMainThread()
     */
    @JvmStatic
    @JvmOverloads
    fun assertMainThread(message: String = "Must be called on the main thread") {
        if (!isMainThread()) {
            throw IllegalStateException("$message (current: ${Thread.currentThread().name})")
        }
    }

    /**
     * 断言当前在后台线程 — 对标 ThreadUtils.assertBackgroundThread()
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
     * 在主线程执行任务 — 对标 ThreadUtils.runOnUiThread()
     */
    @JvmStatic
    fun runOnUiThread(task: () -> Unit) {
        if (isMainThread()) {
            task()
        } else {
            android.os.Handler(Looper.getMainLooper()).post(task)
        }
    }

    /**
     * 延迟在主线程执行 — 对标 ThreadUtils.runOnUiThreadDelayed()
     */
    @JvmStatic
    fun runOnUiThreadDelayed(task: () -> Unit, delayMs: Long) {
        android.os.Handler(Looper.getMainLooper()).postDelayed(task, delayMs)
    }

    // ==================== 延迟（非阻塞）====================

    /**
     * 非阻塞延迟（协程挂起）— 优于 ThreadUtils.sleep()
     *
     * 注意: 这是 suspend 函数，需要在协程中调用。
     *
     * ```kotlin
     * CoroutineExecutor.io {
     *     CoroutineUtils.delay(500)  // 挂起 500ms，线程被释放处理其他任务
     *     doWork()
     * }
     * ```
     */
    @JvmStatic
    suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }

    /**
     * 安全休眠（阻塞当前线程）— 对标 ThreadUtils.sleep()
     *
     * 不可在主线程调用，会导致 ANR。
     * 推荐使用 [delay] 代替（非阻塞）。
     */
    @JvmStatic
    fun sleep(ms: Long) {
        if (isMainThread()) {
            android.util.Log.w(
                "CoroutineUtils",
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

    // ==================== 线程信息 ====================

    /**
     * 获取当前线程详细信息 — 对标 ThreadUtils.getCurrentThreadInfo()
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
     * 获取当前线程简要描述 — 对标 ThreadUtils.getCurrentThreadDescription()
     */
    @JvmStatic
    fun getCurrentThreadDescription(): String {
        val t = Thread.currentThread()
        return "${t.name} (id=${t.id}, main=${isMainThread()})"
    }

    // ==================== 线程优先级 ====================

    /**
     * 设置当前线程的 Java 优先级 — 对标 ThreadUtils.setCurrentThreadPriority()
     */
    @JvmStatic
    fun setCurrentThreadPriority(priority: Int) {
        Thread.currentThread().priority = priority.coerceIn(
            Thread.MIN_PRIORITY,
            Thread.MAX_PRIORITY
        )
    }

    /**
     * 设当前线程为低优先级 — 对标 ThreadUtils.setLowPriority()
     */
    @JvmStatic
    fun setLowPriority() {
        setCurrentThreadPriority(Thread.MIN_PRIORITY)
    }

    /**
     * 设置当前线程的 Android 进程优先级 — 对标 ThreadUtils.setProcessThreadPriority()
     */
    @JvmStatic
    fun setProcessThreadPriority(priority: Int) {
        Process.setThreadPriority(priority)
    }

    /**
     * 将当前线程设为后台进程优先级 — 对标 ThreadUtils.setBackgroundPriority()
     */
    @JvmStatic
    fun setBackgroundPriority() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
    }

    // ==================== 调试 ====================

    /**
     * 打印当前线程调用栈 — 对标 ThreadUtils.logStackTrace()
     */
    @JvmStatic
    @JvmOverloads
    fun logStackTrace(tag: String = "CoroutineUtils", maxDepth: Int = 15) {
        val stackTrace = Thread.currentThread().stackTrace
            .drop(3)  // 跳过 getStackTrace、logStackTrace、调用者
            .take(maxDepth)
            .joinToString("\n") { "  at $it" }
        android.util.Log.d(tag, "Thread: ${Thread.currentThread().name}\n$stackTrace")
    }

    /**
     * 获取当前所有活跃线程的概要信息 — 对标 ThreadUtils.getActiveThreadSummary()
     */
    @JvmStatic
    fun getActiveThreadSummary(): String {
        val current = Thread.currentThread()
        val group = current.threadGroup
        val estimated = group?.activeCount() ?: -1
        return buildString {
            appendLine("Thread Group: ${group?.name ?: "unknown"}")
            appendLine("Active Threads (est.): $estimated")
            appendLine("Current: $current")
        }
    }
}
