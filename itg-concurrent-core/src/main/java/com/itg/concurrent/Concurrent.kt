package com.itg.concurrent

import android.os.Looper
import com.itg.concurrent.backend.toCoroutineDispatcher
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 统一并发入口 — 中间件的门面
 *
 * 使用方式和 [com.itg.itg_thread_pools.executor.TaskExecutor] 几乎一致，
 * 但底层可在线程池 / 协程之间自由切换。
 *
 * ## 基本使用
 *
 * ```kotlin
 * // 和 TaskExecutor 一样的写法
 * Concurrent.io {
 *     val data = api.fetchData()
 *     Concurrent.main { textView.text = data }
 * }
 *
 * // 有返回值
 * val future = Concurrent.io<Int> { calculate() }
 * val result = ConcurrentUtils.await(future)
 * ```
 *
 * ## 协程原生 API
 *
 * ```kotlin
 * lifecycleScope.launch {
 *     val data = Concurrent.ioSuspend { api.fetchData() }
 *     Concurrent.main { updateUI(data) }
 * }
 * ```
 *
 * ## 切换后端
 *
 * ```kotlin
 * // 在 Application.onCreate() 中
 * ConcurrentFactory.switchTo(BackendType.COROUTINE)  // 全部用协程
 * ConcurrentFactory.switchTo(BackendType.THREAD_POOL) // 全部用线程池
 *
 * // 混合模式
 * ConcurrentFactory.useMixed(mapOf(
 *     DispatcherType.IO to BackendType.COROUTINE,
 *     DispatcherType.COMPUTE to BackendType.THREAD_POOL
 * ))
 * ```
 */
object Concurrent {

    // ==================== Fire-and-Forget ====================

    @JvmStatic fun io(task: () -> Unit)        = get(DispatcherType.IO).execute(task)
    @JvmStatic fun compute(task: () -> Unit)    = get(DispatcherType.COMPUTE).execute(task)
    @JvmStatic fun background(task: () -> Unit) = get(DispatcherType.BACKGROUND).execute(task)
    @JvmStatic fun single(task: () -> Unit)     = get(DispatcherType.SINGLE).execute(task)

    @JvmStatic
    fun main(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            get(DispatcherType.MAIN).execute(task)
        }
    }

    // ==================== 有返回值 Future ====================

    @JvmStatic fun <T> io(task: () -> T): Future<T> = get(DispatcherType.IO).submit(task)
    @JvmStatic fun <T> compute(task: () -> T): Future<T> = get(DispatcherType.COMPUTE).submit(task)
    @JvmStatic fun <T> background(task: () -> T): Future<T> = get(DispatcherType.BACKGROUND).submit(task)

    // ==================== 延迟执行 ====================

    @JvmStatic fun mainDelayed(task: () -> Unit, delayMs: Long): Future<*> =
        get(DispatcherType.MAIN).schedule(task, delayMs)

    @JvmStatic fun ioDelayed(task: () -> Unit, delayMs: Long): Future<*> =
        get(DispatcherType.IO).schedule(task, delayMs)

    @JvmStatic fun backgroundDelayed(task: () -> Unit, delayMs: Long): Future<*> =
        get(DispatcherType.BACKGROUND).schedule(task, delayMs)

    // ==================== 协程原生 API ====================

    @JvmStatic
    suspend fun <T> ioSuspend(task: suspend () -> T): T {
        val disp = get(DispatcherType.IO)
        return if (disp is CoroutineTaskDispatcher) {
            disp.executeSuspend(task)
        } else {
            withContext(disp.toCoroutineDispatcher()) { task() }
        }
    }

    @JvmStatic
    suspend fun <T> computeSuspend(task: suspend () -> T): T {
        val disp = get(DispatcherType.COMPUTE)
        return if (disp is CoroutineTaskDispatcher) {
            disp.executeSuspend(task)
        } else {
            withContext(disp.toCoroutineDispatcher()) { task() }
        }
    }

    @JvmStatic
    suspend fun <T> backgroundSuspend(task: suspend () -> T): T {
        val disp = get(DispatcherType.BACKGROUND)
        return if (disp is CoroutineTaskDispatcher) {
            disp.executeSuspend(task)
        } else {
            withContext(disp.toCoroutineDispatcher()) { task() }
        }
    }

    @JvmStatic
    suspend fun <T> mainSuspend(task: suspend () -> T): T {
        val disp = get(DispatcherType.MAIN)
        return if (disp is CoroutineTaskDispatcher) {
            disp.executeSuspend(task)
        } else {
            withContext(disp.toCoroutineDispatcher()) { task() }
        }
    }

    // ==================== 分发器访问 ====================

    @JvmStatic
    fun get(type: DispatcherType): TaskDispatcher = ConcurrentFactory.getDispatcher(type)

    @JvmStatic
    fun getCoroutineDispatcher(type: DispatcherType): CoroutineDispatcher {
        val disp = get(type)
        return if (disp is CoroutineTaskDispatcher) disp.coroutineDispatcher
        else disp.toCoroutineDispatcher()
    }
}
