package com.itg.concurrent.backend

import android.os.Looper
import com.itg.concurrent.*
import com.itg.itg_thread_pools.manager.ThreadPoolManager
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 将 itg-thread-pools 适配为 [TaskDispatcher] 接口
 *
 * 纯粹的外层包装，不改动 itg-thread-pools 一行代码。
 * 仅当用户选择 THREAD_POOL 后端或 classpath 上无协程库时才被调用。
 *
 * 注意: 本适配器直接引用 itg-thread-pools，因此需要该模块在 classpath 上。
 * 如果 classpath 上无 itg-thread-pools，调用 create() 会抛出 NoClassDefFoundError。
 * 请先通过 [ConcurrentFactory.isThreadPoolAvailable] 检查可用性。
 */
object ThreadPoolAdapter {

    fun create(type: DispatcherType): TaskDispatcher {
        return when (type) {
            DispatcherType.IO         -> ThreadPoolDispatcher("io", ThreadPoolManager.ioPool)
            DispatcherType.COMPUTE    -> ThreadPoolDispatcher("compute", ThreadPoolManager.computePool)
            DispatcherType.BACKGROUND -> ThreadPoolDispatcher("bg", ThreadPoolManager.backgroundPool)
            DispatcherType.SINGLE     -> ThreadPoolDispatcher("single", ThreadPoolManager.singlePool)
            DispatcherType.MAIN       -> MainThreadDispatcher()
        }
    }

    private class ThreadPoolDispatcher(
        override val name: String,
        private val pool: ThreadPoolExecutor
    ) : TaskDispatcher, AutoCloseable {

        override val supportsCoroutineNative: Boolean = false

        override fun execute(task: () -> Unit) {
            pool.execute(task)
        }

        override fun <T> submit(task: () -> T): Future<T> {
            val future = FutureTask(Callable { task() })
            pool.execute(future)
            return future
        }

        override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
            return ThreadPoolManager.scheduledPool.schedule(
                task,
                delayMs,
                TimeUnit.MILLISECONDS
            )
        }

        override fun close() {
            pool.shutdown()
        }
    }

    private class MainThreadDispatcher : TaskDispatcher {
        override val name: String = "main"
        override val supportsCoroutineNative: Boolean = false

        override fun execute(task: () -> Unit) {
            ThreadPoolManager.mainExecutor.execute(task)
        }

        override fun <T> submit(task: () -> T): Future<T> {
            val future = FutureTask(Callable { task() })
            ThreadPoolManager.mainExecutor.execute(future)
            return future
        }

        override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
            val handler = android.os.Handler(Looper.getMainLooper())
            val future = FutureTask<Void>(Callable { task(); null })
            handler.postDelayed({ task(); future.run() }, delayMs)
            return future
        }
    }
}

/**
 * 将 [TaskDispatcher] 转为 [CoroutineDispatcher]
 *
 * 用于桥接线程池分发器到协程上下文，
 * 使得线程池后端也能运行 suspend 函数（通过阻塞线程池线程）。
 */
fun TaskDispatcher.toCoroutineDispatcher(): CoroutineDispatcher {
    if (this is CoroutineTaskDispatcher) return this.coroutineDispatcher
    val executor = Executor { command -> this.execute { command.run() } }
    return executor.asCoroutineDispatcher()
}
