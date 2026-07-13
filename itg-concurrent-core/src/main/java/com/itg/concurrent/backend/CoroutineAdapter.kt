package com.itg.concurrent.backend

import android.os.Looper
import com.itg.concurrent.*
import com.itg.itg_coroutine_pools.manager.CoroutineDispatcherManager
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 将 itg-coroutine-pools 适配为 [TaskDispatcher] 接口
 *
 * 提供完整的协程原生支持，同时通过 Deferred→Future 桥接保持 API 兼容。
 * 仅当用户选择 COROUTINE 后端或 classpath 上有协程库时才被调用。
 *
 * 注意: 本适配器直接引用 itg-coroutine-pools，因此需要该模块在 classpath 上。
 * 如果 classpath 上无 itg-coroutine-pools，调用 create() 会抛出 NoClassDefFoundError。
 * 请先通过 [ConcurrentFactory.isCoroutineAvailable] 检查可用性。
 */
object CoroutineAdapter {

    fun create(type: DispatcherType): TaskDispatcher {
        return when (type) {
            DispatcherType.IO         -> CoroutinePoolDispatcher(
                "io",
                CoroutineDispatcherManager.ioDispatcher
            )
            DispatcherType.COMPUTE    -> CoroutinePoolDispatcher(
                "compute",
                CoroutineDispatcherManager.computeDispatcher
            )
            DispatcherType.BACKGROUND -> CoroutinePoolDispatcher(
                "bg",
                CoroutineDispatcherManager.backgroundDispatcher
            )
            DispatcherType.SINGLE     -> CoroutinePoolDispatcher(
                "single",
                CoroutineDispatcherManager.singleDispatcher
            )
            DispatcherType.MAIN       -> MainCoroutineDispatcher()
        }
    }

    private class CoroutinePoolDispatcher(
        override val name: String,
        override val coroutineDispatcher: CoroutineDispatcher,
        private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
    ) : CoroutineTaskDispatcher, AutoCloseable {

        override val supportsCoroutineNative: Boolean = true

        override fun execute(task: () -> Unit) {
            scope.launch { task() }
        }

        override fun <T> submit(task: () -> T): Future<T> {
            val deferred = scope.async { task() }
            return deferred.asFuture()
        }

        override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
            val deferred = scope.async {
                delay(delayMs)
                task()
            }
            return deferred.asFuture()
        }

        override suspend fun <T> executeSuspend(task: suspend () -> T): T {
            return withContext(coroutineDispatcher) { task() }
        }

        override fun close() {
            scope.cancel()
        }
    }

    private class MainCoroutineDispatcher : CoroutineTaskDispatcher, AutoCloseable {
        override val name: String = "main"
        override val supportsCoroutineNative: Boolean = true
        override val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        override fun execute(task: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                task()
            } else {
                scope.launch { task() }
            }
        }

        override fun <T> submit(task: () -> T): Future<T> {
            val deferred = scope.async { task() }
            return deferred.asFuture()
        }

        override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
            val deferred = scope.async {
                delay(delayMs)
                task()
            }
            return deferred.asFuture()
        }

        override suspend fun <T> executeSuspend(task: suspend () -> T): T {
            return withContext(Dispatchers.Main) { task() }
        }

        override fun close() { /* Dispatchers.Main 由 Android 管理，不需要手动关闭 */ }
    }
}

/**
 * Deferred → Future 桥接
 *
 * 将协程的 [Deferred] 包装为 Java [Future]，
 * 使得使用协程后端的代码仍能返回标准的 Future 对象。
 *
 * [Future.get] 内部使用 [runBlocking]，会阻塞调用线程，
 * 因此不应在主线程调用（和原 TaskExecutor.await 行为一致）。
 */
fun <T> Deferred<T>.asFuture(): Future<T> = object : Future<T> {
    override fun cancel(mayInterrupt: Boolean): Boolean {
        this@asFuture.cancel()
        return this@asFuture.isCancelled
    }
    override fun isCancelled(): Boolean = this@asFuture.isCancelled
    override fun isDone(): Boolean = this@asFuture.isCompleted
    override fun get(): T = runBlocking { this@asFuture.await() }
    override fun get(timeout: Long, unit: TimeUnit): T {
        return runBlocking {
            withTimeout(unit.toMillis(timeout)) { this@asFuture.await() }
        }
    }
}
