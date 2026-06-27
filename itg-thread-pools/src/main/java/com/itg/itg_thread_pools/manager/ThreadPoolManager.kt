package com.itg.itg_thread_pools.manager

import android.os.Handler
import android.os.Looper
import android.os.Process
import com.itg.itg_thread_pools.executor.PrioritizedTask
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 线程池管理器
 *
 * 提供统一的线程池创建、管理和生命周期控制。内置多种预配置线程池，
 * 覆盖 I/O 密集型、计算密集型、后台串行等典型场景。
 *
 * 核心特性:
 * - 预置线程池: I/O 池、计算池、后台池、单线程池、调度池
 * - 自定义线程池工厂方法
 * - 线程命名（便于调试和性能分析）
 * - 线程优先级设置
 * - 优雅关闭与强制关闭
 * - 主线程 Executor
 *
 * 使用示例:
 * ```kotlin
 * // 使用预置线程池
 * ThreadPoolManager.ioPool.execute { loadDataFromNetwork() }
 * ThreadPoolManager.computePool.execute { processImage() }
 *
 * // 在主线程执行
 * ThreadPoolManager.mainExecutor.execute { updateUI() }
 *
 * // 创建自定义线程池
 * val myPool = ThreadPoolManager.newFixedPool("my-task", threads = 4)
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
object ThreadPoolManager {

    private const val DEFAULT_QUEUE_CAPACITY = 2_048

    private const val TAG = "ThreadPoolManager"

    // ==================== 线程池配置 ====================

    /** I/O 密集型线程池 — 核心线程数 */
    private const val IO_CORE_SIZE = 4

    /** I/O 密集型线程池 — 最大线程数 */
    private const val IO_MAX_SIZE = 64

    /** I/O 密集型线程池 — 空闲线程存活时间 (秒) */
    private const val IO_KEEP_ALIVE_SEC = 60L

    /** 后台线程池 — 固定线程数 */
    private val BACKGROUND_POOL_SIZE = maxOf(2, Runtime.getRuntime().availableProcessors())

    /** 计算密集型线程池 — 线程数 (CPU 核心数) */
    private val COMPUTE_POOL_SIZE = Runtime.getRuntime().availableProcessors()

    /** 主线程 Handler */
    private val mainHandler = Handler(Looper.getMainLooper())

    // ==================== 预置线程池 ====================

    /**
     * I/O 密集型线程池 (Cached)
     *
     * 适用于网络请求、文件读写、数据库操作等 I/O 密集型任务。
     * 线程数动态伸缩，空闲线程 60 秒后回收。
     *
     * 配置:
     * - 核心线程: 0
     * - 最大线程: 64
     * - 队列: SynchronousQueue（无容量，直接交付）
     * - 超时: 60s
     */
    @JvmField
    val ioPool: ThreadPoolExecutor = ThreadPoolExecutor(
        IO_CORE_SIZE,
        IO_MAX_SIZE,
        IO_KEEP_ALIVE_SEC,
        TimeUnit.SECONDS,
        LinkedBlockingQueue<Runnable>(DEFAULT_QUEUE_CAPACITY),
        NamedThreadFactory("itg-io"),
        CallerRunsPolicy("itg-io")
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    /**
     * 计算密集型线程池 (Fixed)
     *
     * 适用于图片处理、加密解密、数据解析等 CPU 密集型任务。
     * 线程数等于 CPU 核心数，避免线程切换开销。
     *
     * 配置:
     * - 核心/最大线程: CPU 核心数
     * - 队列: 无界 LinkedBlockingQueue
     */
    @JvmField
    val computePool: ThreadPoolExecutor = ThreadPoolExecutor(
        COMPUTE_POOL_SIZE,
        COMPUTE_POOL_SIZE,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(DEFAULT_QUEUE_CAPACITY),
        NamedThreadFactory("itg-compute"),
        CallerRunsPolicy("itg-compute")
    )

    /**
     * 通用后台线程池 (Fixed)
     *
     * 适用于通用后台任务，线程数适中。
     *
     * 配置:
     * - 核心/最大线程: max(2, CPU 核心数)
     * - 队列: 无界 LinkedBlockingQueue
     */
    @JvmField
    val backgroundPool: ThreadPoolExecutor = ThreadPoolExecutor(
        BACKGROUND_POOL_SIZE,
        BACKGROUND_POOL_SIZE,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(DEFAULT_QUEUE_CAPACITY),
        NamedThreadFactory("itg-background"),
        CallerRunsPolicy("itg-background")
    )

    /**
     * 单线程串行池 (Single)
     *
     * 适用于需要保证顺序执行的任务（如数据库写入、状态同步）。
     *
     * 配置:
     * - 核心/最大线程: 1
     * - 队列: 无界 LinkedBlockingQueue
     */
    @JvmField
    val singlePool: ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(DEFAULT_QUEUE_CAPACITY),
        NamedThreadFactory("itg-single"),
        CallerRunsPolicy("itg-single")
    )

    internal val priorityPool: ThreadPoolExecutor = ThreadPoolExecutor(
        BACKGROUND_POOL_SIZE,
        BACKGROUND_POOL_SIZE,
        0L,
        TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(11) { first, second ->
            val a = first as PrioritizedTask
            val b = second as PrioritizedTask
            val priorityComparison = b.priority.value.compareTo(a.priority.value)
            if (priorityComparison != 0) priorityComparison
            else a.sequence.compareTo(b.sequence)
        },
        NamedThreadFactory("itg-priority"),
        CallerRunsPolicy("itg-priority")
    )

    /**
     * 定时调度线程池
     *
     * 适用于定时任务、周期性任务。
     *
     * 配置:
     * - 核心线程: 2
     * - 最大线程: 4
     */
    @JvmField
    val scheduledPool: ScheduledExecutorService = ScheduledThreadPoolExecutor(
        2,
        NamedThreadFactory("itg-scheduled"),
        CallerRunsPolicy("itg-scheduled")
    ).apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
        continueExistingPeriodicTasksAfterShutdownPolicy = false
    }

    /**
     * 主线程 Executor
     *
     * 通过 Handler 将任务切换到主线程执行。
     *
     * 使用示例:
     * ```kotlin
     * ThreadPoolManager.mainExecutor.execute {
     *     textView.text = "Updated"
     * }
     * ```
     */
    @JvmField
    val mainExecutor: Executor = Executor { command ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            command.run()
        } else {
            mainHandler.post(command)
        }
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建 Cached 线程池
     *
     * 适用于大量短期异步任务。线程数根据任务量自动伸缩。
     *
     * @param name      线程名前缀（便于调试时定位）
     * @param maxSize   最大线程数，默认 64
     * @param keepAlive 空闲线程存活时间，默认 60 秒
     * @param queue     任务队列，默认 SynchronousQueue
     * @param handler   拒绝策略，默认 CallerRunsPolicy
     * @return 配置好的 [ThreadPoolExecutor]
     */
    @JvmStatic
    @JvmOverloads
    fun newCachedPool(
        name: String,
        maxSize: Int = 64,
        keepAlive: Long = 60L,
        queue: BlockingQueue<Runnable> = SynchronousQueue(),
        handler: RejectedExecutionHandler = CallerRunsPolicy(name)
    ): ThreadPoolExecutor {
        return ThreadPoolExecutor(
            0,
            maxSize,
            keepAlive,
            TimeUnit.SECONDS,
            queue,
            NamedThreadFactory(name),
            handler
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    }

    /**
     * 创建 Fixed 线程池（核心线程数 = 最大线程数）
     *
     * 适用于已知并发量的长期任务。
     *
     * @param name      线程名前缀
     * @param threads   线程数
     * @param queue     任务队列，默认无界 LinkedBlockingQueue
     * @param handler   拒绝策略
     * @return 配置好的 [ThreadPoolExecutor]
     */
    @JvmStatic
    @JvmOverloads
    fun newFixedPool(
        name: String,
        threads: Int,
        queue: BlockingQueue<Runnable> = LinkedBlockingQueue(),
        handler: RejectedExecutionHandler = CallerRunsPolicy(name)
    ): ThreadPoolExecutor {
        val size = threads.coerceAtLeast(1)
        return ThreadPoolExecutor(
            size,
            size,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            NamedThreadFactory(name),
            handler
        )
    }

    /**
     * 创建 Single 线程池
     *
     * 适用于必须串行执行的任务。
     *
     * @param name    线程名前缀
     * @param queue   任务队列
     * @param handler 拒绝策略
     * @return 配置好的 [ThreadPoolExecutor]
     */
    @JvmStatic
    @JvmOverloads
    fun newSinglePool(
        name: String,
        queue: BlockingQueue<Runnable> = LinkedBlockingQueue(),
        handler: RejectedExecutionHandler = CallerRunsPolicy(name)
    ): ThreadPoolExecutor {
        return ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            NamedThreadFactory(name),
            handler
        )
    }

    /**
     * 创建 Scheduled 线程池
     *
     * 适用于定时任务和周期性任务。
     *
     * @param name     线程名前缀
     * @param coreSize 核心线程数
     * @param handler  拒绝策略
     * @return 配置好的 [ScheduledExecutorService]
     */
    @JvmStatic
    @JvmOverloads
    fun newScheduledPool(
        name: String,
        coreSize: Int = 2,
        handler: RejectedExecutionHandler = CallerRunsPolicy(name)
    ): ScheduledExecutorService {
        return ScheduledThreadPoolExecutor(
            coreSize.coerceAtLeast(1),
            NamedThreadFactory(name),
            handler
        )
    }

    /**
     * 创建自定义线程池
     *
     * 适用于需要精细控制线程池参数的场景。
     *
     * @param name          线程名前缀
     * @param corePoolSize  核心线程数
     * @param maxPoolSize   最大线程数
     * @param keepAliveSec  空闲线程存活时间 (秒)
     * @param queue         任务队列
     * @param priority      线程优先级 [Thread.MIN_PRIORITY]..[Thread.MAX_PRIORITY]
     * @param handler       拒绝策略
     * @return 配置好的 [ThreadPoolExecutor]
     */
    @JvmStatic
    @JvmOverloads
    fun newCustomPool(
        name: String,
        corePoolSize: Int,
        maxPoolSize: Int,
        keepAliveSec: Long = 60L,
        queue: BlockingQueue<Runnable> = LinkedBlockingQueue(),
        priority: Int = Thread.NORM_PRIORITY,
        handler: RejectedExecutionHandler = CallerRunsPolicy(name)
    ): ThreadPoolExecutor {
        return ThreadPoolExecutor(
            corePoolSize.coerceAtLeast(0),
            maxPoolSize.coerceAtLeast(corePoolSize),
            keepAliveSec,
            TimeUnit.SECONDS,
            queue,
            NamedThreadFactory(name, priority),
            handler
        )
    }

    // ==================== 状态查询 ====================

    /**
     * 获取指定线程池的统计信息
     *
     * @param pool 线程池
     * @return 包含活跃线程数、任务数等统计的 Map
     */
    @JvmStatic
    fun getPoolStats(pool: ThreadPoolExecutor): Map<String, Any> {
        return mapOf(
            "activeCount" to pool.activeCount,
            "poolSize" to pool.poolSize,
            "corePoolSize" to pool.corePoolSize,
            "maximumPoolSize" to pool.maximumPoolSize,
            "completedTaskCount" to pool.completedTaskCount,
            "taskCount" to pool.taskCount,
            "queueSize" to pool.queue.size,
            "queueRemainingCapacity" to pool.queue.remainingCapacity(),
            "isShutdown" to pool.isShutdown,
            "isTerminated" to pool.isTerminated
        )
    }

    /**
     * 打印所有预置线程池的统计信息（用于调试）
     */
    @JvmStatic
    fun printAllStats() {
        val pools = mapOf(
            "ioPool" to ioPool,
            "computePool" to computePool,
            "backgroundPool" to backgroundPool,
            "singlePool" to singlePool
        )
        pools.forEach { (name, pool) ->
            val s = getPoolStats(pool)
            println("[$name] active=${s["activeCount"]}, pool=${s["poolSize"]}, " +
                    "completed=${s["completedTaskCount"]}, queue=${s["queueSize"]}")
        }
    }

    // ==================== 生命周期管理 ====================

    /**
     * 优雅关闭所有预置线程池
     *
     * 等待已提交的任务执行完毕，不再接受新任务。
     * 建议在 Application.onTerminate() 或进程销毁前调用。
     */
    @JvmStatic
    fun shutdown() {
        ioPool.shutdown()
        computePool.shutdown()
        backgroundPool.shutdown()
        singlePool.shutdown()
        priorityPool.shutdown()
        scheduledPool.shutdown()
    }

    /**
     * 立即关闭所有预置线程池
     *
     * 尝试中断正在执行的任务，返回未执行的任务列表。
     * 仅用于紧急情况，常规关闭请使用 [shutdown]。
     */
    @JvmStatic
    fun shutdownNow(): List<Runnable> {
        val remaining = mutableListOf<Runnable>()
        remaining.addAll(ioPool.shutdownNow())
        remaining.addAll(computePool.shutdownNow())
        remaining.addAll(backgroundPool.shutdownNow())
        remaining.addAll(singlePool.shutdownNow())
        remaining.addAll(priorityPool.shutdownNow())
        remaining.addAll(scheduledPool.shutdownNow())
        return remaining
    }

    /**
     * 等待所有预置线程池终止
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true 表示所有线程池已终止
     */
    @JvmStatic
    @JvmOverloads
    fun awaitTermination(
        timeout: Long = 30,
        unit: TimeUnit = TimeUnit.SECONDS
    ): Boolean {
        val pools = listOf(ioPool, computePool, backgroundPool, singlePool, priorityPool, scheduledPool)
        val timeoutNanos = unit.toNanos(timeout.coerceAtLeast(0))
        val startNanos = System.nanoTime()
        return pools.all { pool ->
            try {
                val remaining = timeoutNanos - (System.nanoTime() - startNanos)
                remaining > 0L && pool.awaitTermination(remaining, TimeUnit.NANOSECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 带名称的线程工厂
     *
     * 创建的线程具有统一的命名格式: "prefix-N"，
     * 便于在 Android Studio Profiler 和 logcat 中定位。
     */
    class NamedThreadFactory(
        private val prefix: String,
        private val priority: Int = Thread.NORM_PRIORITY
    ) : ThreadFactory {
        private val poolNumber = AtomicInteger(1)
        private val threadNumber = AtomicInteger(1)
        private val group = Thread.currentThread().threadGroup

        override fun newThread(r: Runnable): Thread {
            val name = "$prefix-${threadNumber.getAndIncrement()}"
            return Thread(group, r, name, 0).apply {
                if (isDaemon) isDaemon = false
                this.priority = priority
                // 设置未捕获异常处理器
                setUncaughtExceptionHandler { t, e ->
                    android.util.Log.e(
                        "ThreadPool-$prefix",
                        "Uncaught exception in thread '${t.name}'",
                        e
                    )
                }
            }
        }
    }

    /**
     * CallerRuns 拒绝策略
     *
     * 当线程池和队列都满时，由调用线程直接执行任务，
     * 提供天然的背压 (back-pressure) 机制。
     *
     * 对比 JDK 内置策略:
     * - AbortPolicy: 抛异常（可能导致任务丢失）
     * - DiscardPolicy: 静默丢弃（难以排查）
     * - DiscardOldestPolicy: 丢弃最旧任务（可能丢失关键任务）
     * - **CallerRunsPolicy: 调用者执行（推荐，提供背压）**
     */
    class CallerRunsPolicy(
        private val poolName: String
    ) : RejectedExecutionHandler {

        override fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
            if (executor.isShutdown) {
                android.util.Log.w(
                    "ThreadPool-$poolName",
                    "Task rejected: pool is shutdown"
                )
                return
            }
            android.util.Log.w(
                "ThreadPool-$poolName",
                "Task rejected: pool full, running on caller thread. " +
                        "poolSize=${executor.poolSize}, active=${executor.activeCount}, " +
                        "queueSize=${executor.queue.size}"
            )
            // 在调用线程中直接运行（提供背压）
            if (!executor.isShutdown) {
                r.run()
            }
        }
    }
}
