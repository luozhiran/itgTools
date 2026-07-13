package com.itg.itg_coroutine_pools.executor

import android.os.Looper
import com.itg.itg_coroutine_pools.manager.CoroutineDispatcherManager
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 协程任务执行器 — 对标 [com.itg.itg_thread_pools.executor.TaskExecutor]
 *
 * 封装 [CoroutineDispatcherManager]，提供语义化、类型安全的任务提交 API。
 * API 签名和 TaskExecutor 保持一致，用户只需改 import 即可从线程池切换到协程。
 *
 * 核心特性:
 * - 语义化 API: io { }, compute { }, main { } ...
 * - 返回 Future 兼容（通过 Deferred → Future 桥接）
 * - 延迟执行与定时任务
 * - 批量等待与超时控制
 * - **协程独有**: suspend 原生 API（ioSuspend, computeSuspend 等）
 *
 * 基本使用:
 * ```kotlin
 * // 和 TaskExecutor 完全一致的写法
 * CoroutineExecutor.io {
 *     val data = api.fetchData()
 *     CoroutineExecutor.main { textView.text = data }
 * }
 *
 * // 有返回值
 * val future = CoroutineExecutor.io<Int> { calculate() }
 * val result = CoroutineExecutor.await(future, timeoutMs = 5000)
 *
 * // 协程原生（推荐新代码使用）
 * CoroutineScope(...).launch {
 *     val data = CoroutineExecutor.ioSuspend { api.fetchData() }
 *     CoroutineExecutor.mainSuspend { updateUI(data) }
 * }
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
object CoroutineExecutor {

    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ==================== fire-and-forget（和 TaskExecutor 签名一致）====================

    @JvmStatic
    fun io(task: () -> Unit) {
        scope.launch(CoroutineDispatcherManager.ioDispatcher) { task() }
    }

    @JvmStatic
    fun compute(task: () -> Unit) {
        scope.launch(CoroutineDispatcherManager.computeDispatcher) { task() }
    }

    @JvmStatic
    fun background(task: () -> Unit) {
        scope.launch(CoroutineDispatcherManager.backgroundDispatcher) { task() }
    }

    @JvmStatic
    fun single(task: () -> Unit) {
        scope.launch(CoroutineDispatcherManager.singleDispatcher) { task() }
    }

    @JvmStatic
    fun main(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            scope.launch(Dispatchers.Main) { task() }
        }
    }

    // ==================== 有返回值 Future（和 TaskExecutor 签名一致）====================

    @JvmStatic
    fun <T> io(task: () -> T): Future<T> {
        val deferred = scope.async(CoroutineDispatcherManager.ioDispatcher) { task() }
        return deferred.toFuture()
    }

    @JvmStatic
    fun <T> compute(task: () -> T): Future<T> {
        val deferred = scope.async(CoroutineDispatcherManager.computeDispatcher) { task() }
        return deferred.toFuture()
    }

    @JvmStatic
    fun <T> background(task: () -> T): Future<T> {
        val deferred = scope.async(CoroutineDispatcherManager.backgroundDispatcher) { task() }
        return deferred.toFuture()
    }

    // ==================== 延迟执行 ====================

    @JvmStatic
    fun mainDelayed(task: () -> Unit, delayMs: Long): Runnable {
        val runnable = Runnable { task() }
        mainHandler.postDelayed(runnable, delayMs)
        return runnable
    }

    @JvmStatic
    fun ioDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        val deferred = scope.async(CoroutineDispatcherManager.ioDispatcher) {
            delay(delayMs)
            task()
        }
        return deferred.toFuture()
    }

    @JvmStatic
    fun backgroundDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        val deferred = scope.async(CoroutineDispatcherManager.backgroundDispatcher) {
            delay(delayMs)
            task()
        }
        return deferred.toFuture()
    }

    // ==================== 定时执行 ====================

    @JvmStatic
    @JvmOverloads
    fun scheduleAtFixedRate(
        task: () -> Unit,
        initialDelayMs: Long = 0,
        periodMs: Long
    ): Future<*> {
        val job = scope.launch(CoroutineDispatcherManager.backgroundDispatcher) {
            delay(initialDelayMs)
            while (isActive) {
                val start = System.nanoTime()
                task()
                val elapsed = (System.nanoTime() - start) / 1_000_000
                delay((periodMs - elapsed).coerceAtLeast(0))
            }
        }
        return job.toFuture()
    }

    @JvmStatic
    @JvmOverloads
    fun scheduleWithFixedDelay(
        task: () -> Unit,
        initialDelayMs: Long = 0,
        delayMs: Long
    ): Future<*> {
        val job = scope.launch(CoroutineDispatcherManager.backgroundDispatcher) {
            delay(initialDelayMs)
            while (isActive) {
                task()
                delay(delayMs)
            }
        }
        return job.toFuture()
    }

    // ==================== 取消 ====================

    @JvmStatic
    @JvmOverloads
    fun cancel(future: Future<*>, mayInterrupt: Boolean = true): Boolean {
        return future.cancel(mayInterrupt)
    }

    @JvmStatic
    fun cancelMain(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }

    @JvmStatic
    fun cancelMainAll(token: Any?) {
        mainHandler.removeCallbacksAndMessages(token)
    }

    // ==================== 等待 ====================

    @JvmStatic
    @JvmOverloads
    @Throws(TimeoutException::class, ExecutionException::class)
    fun <T> await(
        future: Future<T>,
        timeoutMs: Long = 0
    ): T? {
        return try {
            if (timeoutMs <= 0) {
                future.get()
            } else {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ExecutionException("Interrupted while waiting for future", e)
        }
    }

    @JvmStatic
    @JvmOverloads
    @Throws(TimeoutException::class)
    fun awaitAll(
        futures: List<Future<*>>,
        timeoutMs: Long = 0
    ) {
        val deadline = if (timeoutMs > 0)
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        else Long.MAX_VALUE

        futures.forEach { future ->
            val remaining = if (deadline == Long.MAX_VALUE) Long.MAX_VALUE
            else TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(0)
            if (remaining <= 0) throw TimeoutException("awaitAll timed out")
            try {
                if (remaining == Long.MAX_VALUE) future.get()
                else future.get(remaining, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ExecutionException("Interrupted", e)
            } catch (_: java.util.concurrent.CancellationException) {
                // 已取消的任务视为完成
            }
        }
    }

    @JvmStatic
    @Throws(TimeoutException::class)
    fun awaitAny(
        futures: List<Future<*>>,
        timeoutMs: Long
    ): Int {
        require(timeoutMs >= 0) { "timeoutMs must be non-negative" }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            futures.forEachIndexed { index, future ->
                if (future.isDone) return index
            }
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ExecutionException("Interrupted", e)
            }
        }
        throw TimeoutException("awaitAny timed out after ${timeoutMs}ms")
    }

    // ==================== 协程原生 API ====================

    @JvmStatic
    suspend fun <T> ioSuspend(task: suspend () -> T): T {
        return withContext(CoroutineDispatcherManager.ioDispatcher) { task() }
    }

    @JvmStatic
    suspend fun <T> computeSuspend(task: suspend () -> T): T {
        return withContext(CoroutineDispatcherManager.computeDispatcher) { task() }
    }

    @JvmStatic
    suspend fun <T> backgroundSuspend(task: suspend () -> T): T {
        return withContext(CoroutineDispatcherManager.backgroundDispatcher) { task() }
    }

    @JvmStatic
    suspend fun <T> mainSuspend(task: suspend () -> T): T {
        return withContext(Dispatchers.Main) { task() }
    }

    // ==================== 生命周期 ====================

    @JvmStatic
    fun shutdown() {
        scope.cancel()
    }

    // ==================== 内部桥接方法 ====================

    /** Deferred → Future */
    private fun <T> Deferred<T>.toFuture(): Future<T> = object : Future<T> {
        override fun cancel(mayInterrupt: Boolean): Boolean {
            this@toFuture.cancel()
            return this@toFuture.isCancelled
        }
        override fun isCancelled(): Boolean = this@toFuture.isCancelled
        override fun isDone(): Boolean = this@toFuture.isCompleted
        override fun get(): T = runBlocking { this@toFuture.await() }
        override fun get(timeout: Long, unit: TimeUnit): T {
            return runBlocking {
                withTimeout(unit.toMillis(timeout)) { this@toFuture.await() }
            }
        }
    }

    /** Job → Future<Unit>（用于定时/延迟任务，不需要返回值） */
    private fun Job.toFuture(): Future<*> = object : Future<Unit> {
        override fun cancel(mayInterrupt: Boolean): Boolean {
            this@toFuture.cancel()
            return this@toFuture.isCancelled
        }
        override fun isCancelled(): Boolean = this@toFuture.isCancelled
        override fun isDone(): Boolean = this@toFuture.isCompleted
        override fun get(): Unit = runBlocking { this@toFuture.join() }
        override fun get(timeout: Long, unit: TimeUnit): Unit {
            runBlocking {
                withTimeout(unit.toMillis(timeout)) { this@toFuture.join() }
            }
        }
    }
}
