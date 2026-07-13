package com.itg.concurrent.util

import android.os.Looper
import java.util.concurrent.*

/**
 * 并发工具类 — 底层后端无关的公共方法
 *
 * 无论底层是线程池还是协程，这些方法的行为一致。
 * 对标 [com.itg.itg_thread_pools.utils.ThreadUtils] 和
 * [com.itg.itg_coroutine_pools.utils.CoroutineUtils]。
 */
object ConcurrentUtils {

    // ==================== 线程检测 ====================

    @JvmStatic
    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    @JvmStatic
    fun isBackgroundThread(): Boolean = !isMainThread()

    // ==================== 线程断言 ====================

    @JvmStatic
    @JvmOverloads
    fun assertMainThread(message: String = "Must be called on the main thread") {
        if (!isMainThread()) {
            throw IllegalStateException("$message (current: ${Thread.currentThread().name})")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun assertBackgroundThread(message: String = "Must be called on a background thread") {
        if (isMainThread()) {
            throw IllegalStateException("$message (current: main thread)")
        }
    }

    // ==================== Future 辅助 ====================

    /**
     * 阻塞等待 Future 完成并返回结果
     *
     * 注意: 不可在主线程调用。
     */
    @JvmStatic
    @JvmOverloads
    fun <T> await(
        future: Future<T>,
        timeoutMs: Long = 0
    ): T? {
        return try {
            if (timeoutMs <= 0) future.get()
            else future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: CancellationException) {
            null
        }
    }

    /**
     * 取消 Future 任务
     */
    @JvmStatic
    @JvmOverloads
    fun cancel(future: Future<*>, mayInterrupt: Boolean = true): Boolean {
        return future.cancel(mayInterrupt)
    }

    // ==================== 安全的阻塞延迟 ====================

    /**
     * 安全的阻塞延迟 — 不可在主线程调用
     *
     * 在协程中请使用 [kotlinx.coroutines.delay]（非阻塞）。
     */
    @JvmStatic
    fun sleep(ms: Long) {
        if (isMainThread()) {
            android.util.Log.w(
                "ConcurrentUtils",
                "sleep() called on main thread — this will cause ANR!"
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

    @JvmStatic
    fun getCurrentThreadDescription(): String {
        val t = Thread.currentThread()
        return "${t.name} (id=${t.id}, main=${isMainThread()})"
    }

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
            "threadGroup" to (thread.threadGroup?.name ?: "none")
        )
    }
}
