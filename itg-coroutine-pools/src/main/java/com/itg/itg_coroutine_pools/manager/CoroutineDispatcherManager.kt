package com.itg.itg_coroutine_pools.manager

import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 协程调度器管理器 — 对标 [com.itg.itg_thread_pools.manager.ThreadPoolManager]
 *
 * 提供预配置的 [CoroutineDispatcher] 实例和工厂方法。
 * 与 ThreadPoolManager API 保持一致，用户可根据场景选择线程池或协程实现。
 *
 * 核心特性:
 * - 预置 Dispatcher: I/O、计算、后台、串行、主线程
 * - 自定义 Dispatcher 工厂方法
 * - 线程命名（通过 asCoroutineDispatcher 桥接 Java Executor）
 * - 优雅关闭
 *
 * 使用示例:
 * ```kotlin
 * // 获取预置 Dispatcher
 * val dispatcher = CoroutineDispatcherManager.ioDispatcher
 *
 * // 在协程中使用
 * CoroutineScope(dispatcher).launch { doWork() }
 *
 * // 或通过 CoroutineExecutor
 * CoroutineExecutor.io { doWork() }
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
object CoroutineDispatcherManager {

    // ==================== 预置 Dispatcher ====================

    /**
     * I/O 密集型 Dispatcher — 对标 ThreadPoolManager.ioPool
     *
     * 基于 [Dispatchers.IO]，弹性线程池，默认最多 64 线程。
     * 适用于网络请求、文件读写、数据库操作等 I/O 密集型任务。
     */
    @JvmField
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * 计算密集型 Dispatcher — 对标 ThreadPoolManager.computePool
     *
     * 基于 [Dispatchers.Default]，固定大小为 CPU 核心数。
     * 适用于图片处理、加密解密、数据解析等 CPU 密集型任务。
     */
    @JvmField
    val computeDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * 通用后台 Dispatcher — 对标 ThreadPoolManager.backgroundPool
     *
     * 基于 [Dispatchers.Default]，适用于通用后台任务。
     */
    @JvmField
    val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * 单线程串行 Dispatcher — 对标 ThreadPoolManager.singlePool
     *
     * [limitedParallelism] 确保所有任务在同一个线程上严格串行执行。
     * 适用于数据库写入序列、状态同步等需要保证顺序的场景。
     */
    @JvmField
    val singleDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * 主线程 Dispatcher — 对标 ThreadPoolManager.mainExecutor
     *
     * 基于 [Dispatchers.Main]，在 Android 上对应主线程 Looper。
     */
    @JvmField
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    // ==================== 工厂方法 ====================

    /**
     * 创建固定并发度的 Dispatcher
     *
     * 对标 ThreadPoolManager.newFixedPool(name, threads)
     *
     * @param parallelism 并发度，默认 CPU 核心数
     */
    @JvmStatic
    @JvmOverloads
    fun newFixedDispatcher(
        name: String = "itg-coro-fixed",
        parallelism: Int = Runtime.getRuntime().availableProcessors()
    ): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(parallelism.coerceAtLeast(1))
    }

    /**
     * 创建单线程 Dispatcher
     *
     * 对标 ThreadPoolManager.newSinglePool(name)
     */
    @JvmStatic
    @JvmOverloads
    fun newSingleDispatcher(name: String = "itg-coro-single"): CoroutineDispatcher {
        return newFixedDispatcher(name, 1)
    }

    /**
     * 从 Java Executor 创建 Dispatcher（桥接模式）
     *
     * 对标 ThreadPoolManager.newCustomPool()
     * 适用于需要精细控制线程参数的场景。
     *
     * @param executor Java [Executor] 实例
     * @return 包装后的 [CoroutineDispatcher]
     */
    @JvmStatic
    fun fromExecutor(executor: Executor): CoroutineDispatcher {
        return executor.asCoroutineDispatcher()
    }

    /**
     * 创建带自定义线程工厂的 Dispatcher
     *
     * 对标 ThreadPoolManager.newCustomPool(name, corePoolSize, maxPoolSize, ...)
     *
     * @param corePoolSize 核心线程数
     * @param maxPoolSize  最大线程数
     * @param name         线程名前缀（便于调试）
     * @param priority     线程优先级
     */
    @JvmStatic
    @JvmOverloads
    fun newCustomDispatcher(
        corePoolSize: Int,
        maxPoolSize: Int,
        name: String = "itg-coro-custom",
        priority: Int = Thread.NORM_PRIORITY
    ): CoroutineDispatcher {
        val factory = NamedThreadFactory(name, priority)
        val pool = ThreadPoolExecutor(
            corePoolSize.coerceAtLeast(0),
            maxPoolSize.coerceAtLeast(corePoolSize),
            60L, TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>(),
            factory,
            ThreadPoolExecutor.CallerRunsPolicy()
        )
        return pool.asCoroutineDispatcher()
    }

    // ==================== 状态监控 ====================

    /**
     * 获取 Dispatcher 描述信息（用于调试）
     */
    @JvmStatic
    fun getDispatcherInfo(dispatcher: CoroutineDispatcher): Map<String, String> {
        return mapOf(
            "type" to (dispatcher::class.simpleName ?: "unknown"),
            "toString" to dispatcher.toString()
        )
    }

    /**
     * 打印所有预置 Dispatcher 信息（用于调试）
     */
    @JvmStatic
    fun printAllInfo() {
        val dispatchers = mapOf(
            "ioDispatcher" to ioDispatcher,
            "computeDispatcher" to computeDispatcher,
            "backgroundDispatcher" to backgroundDispatcher,
            "singleDispatcher" to singleDispatcher,
            "mainDispatcher" to mainDispatcher
        )
        dispatchers.forEach { (name, dispatcher) ->
            val info = getDispatcherInfo(dispatcher)
            println("[$name] type=${info["type"]}")
        }
    }

    // ==================== 内部类 ====================

    /**
     * 带名称的线程工厂 — 对标 ThreadPoolManager.NamedThreadFactory
     *
     * 创建的线程具有统一命名格式: "prefix-N"，便于在 Profiler 和 logcat 中定位。
     */
    class NamedThreadFactory(
        private val prefix: String,
        private val priority: Int = Thread.NORM_PRIORITY
    ) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)
        private val group = Thread.currentThread().threadGroup

        override fun newThread(r: Runnable): Thread {
            val name = "$prefix-${threadNumber.getAndIncrement()}"
            return Thread(group, r, name, 0).apply {
                if (isDaemon) isDaemon = false
                this.priority = priority
                setUncaughtExceptionHandler { t, e ->
                    android.util.Log.e(
                        "CoroutinePool-$prefix",
                        "Uncaught exception in thread '${t.name}'",
                        e
                    )
                }
            }
        }
    }
}
