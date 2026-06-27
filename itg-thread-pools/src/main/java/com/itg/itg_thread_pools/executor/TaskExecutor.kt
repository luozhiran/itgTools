package com.itg.itg_thread_pools.executor

import android.os.Handler
import android.os.Looper
import com.itg.itg_thread_pools.manager.ThreadPoolManager
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

// ==================== 优先级定义 ====================

/**
 * 任务优先级枚举
 *
 * 数值越大优先级越高:
 * - [LOW]: 低优先级，适用于可延迟的后台任务（如预加载、日志写入）
 * - [NORMAL]: 普通优先级（默认），适用于大多数常规任务
 * - [HIGH]: 高优先级，适用于用户可见的异步操作（如加载当前页面数据）
 * - [CRITICAL]: 最高优先级，适用于必须尽快完成的紧急任务
 */
enum class Priority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    companion object {
        /**
         * 根据整数值获取 Priority
         */
        @JvmStatic
        fun fromValue(value: Int): Priority {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}

interface PrioritizedTask {
    val priority: Priority
    val sequence: Long
}

/**
 * 带优先级的 Runnable
 *
 * 包装标准 Runnable，附加优先级信息，支持按优先级排序。
 *
 * @property priority 任务优先级
 * @property sequence 任务序号（用于同优先级 FIFO 排序）
 * @property runnable 被包装的 Runnable
 */
class PriorityRunnable(
    override val priority: Priority,
    override val sequence: Long,
    private val runnable: Runnable
) : Runnable, Comparable<PriorityRunnable>, PrioritizedTask {

    override fun run() = runnable.run()

    override fun compareTo(other: PriorityRunnable): Int {
        // 优先级高的排前面
        val pc = other.priority.value.compareTo(priority.value)
        if (pc != 0) return pc
        // 同优先级按提交顺序（FIFO）
        return sequence.compareTo(other.sequence)
    }

    override fun toString(): String = "PriorityRunnable(priority=$priority, seq=$sequence)"
}

/**
 * 带优先级的 FutureTask
 *
 * 支持优先级排序的可取消异步任务。
 */
class PriorityFutureTask<T>(
    override val priority: Priority,
    override val sequence: Long,
    callable: Callable<T>
) : FutureTask<T>(callable), Comparable<PriorityFutureTask<T>>, PrioritizedTask {

    override fun compareTo(other: PriorityFutureTask<T>): Int {
        val pc = other.priority.value.compareTo(priority.value)
        if (pc != 0) return pc
        return sequence.compareTo(other.sequence)
    }

    override fun toString(): String = "PriorityFutureTask(priority=$priority, seq=$sequence)"
}


// ==================== 任务执行器 ====================

/**
 * 任务执行器
 *
 * 封装 [ThreadPoolManager]，提供便捷、语义化的任务提交 API。
 * 支持优先级任务、延迟执行、超时控制、批量等待、Future 组合等。
 *
 * 核心特性:
 * - 语义化 API: io { }, compute { }, main { } ...
 * - 优先级队列支持
 * - 延迟执行与定时任务
 * - Future 异常处理
 * - 批量等待与超时控制
 *
 * 基本使用:
 * ```kotlin
 * // 在 I/O 线程执行
 * TaskExecutor.io {
 *     val data = api.fetchData()
 *     // 切回主线程更新 UI
 *     TaskExecutor.main { textView.text = data }
 * }
 *
 * // 在计算线程执行并获取结果
 * val future = TaskExecutor.compute<Int> { (1..1000).sum() }
 * val result = TaskExecutor.await(future, timeout = 5, unit = TimeUnit.SECONDS)
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
object TaskExecutor {

    private val sequenceGenerator = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ==================== 语义化 API (fire-and-forget) ====================

    /**
     * 在 I/O 线程池执行任务
     *
     * 适用于网络请求、文件读写等 I/O 密集型操作。
     *
     * @param task 要执行的任务
     *
     * 使用示例:
     * ```kotlin
     * TaskExecutor.io {
     *     val bitmap = BitmapDecodeUtils.decodeFromUrl(url)
     *     TaskExecutor.main { imageView.setImageBitmap(bitmap) }
     * }
     * ```
     */
    /**
     * 在计算线程池执行任务
     *
     * 适用于图片处理、数据加密解密、复杂计算等 CPU 密集型操作。
     *
     * @param task 要执行的任务
     */
    /**
     * 在后台线程池执行任务
     *
     * 适用于通用后台操作。
     *
     * @param task 要执行的任务
     */
    /**
     * 在单线程串行池执行任务
     *
     * 适用于需要保证执行顺序的操作（如数据库写入序列）。
     *
     * @param task 要执行的任务
     */
    @JvmStatic
    fun single(task: () -> Unit) {
        ThreadPoolManager.singlePool.execute(wrap(task))
    }

    /**
     * 在主线程（UI 线程）执行任务
     *
     * 如果当前已在主线程，则直接执行；否则 post 到主线程消息队列。
     *
     * @param task 要执行的任务
     */
    @JvmStatic
    fun main(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            mainHandler.post(wrap(task))
        }
    }

    // ==================== 返回 Future 的 API ====================

    /**
     * 在 I/O 线程池执行有返回值的任务
     *
     * @param task 有返回值的任务
     * @return [Future] 对象，可通过 [await] 获取结果
     *
     * 使用示例:
     * ```kotlin
     * val future = TaskExecutor.io<Int> { calculateSomething() }
     * // ... 做其他事情 ...
     * val result = TaskExecutor.await(future)
     * ```
     */
    @JvmStatic
    fun <T> io(task: () -> T): Future<T> {
        val future = FutureTask(Callable { task() })
        ThreadPoolManager.ioPool.execute(future)
        return future
    }

    /**
     * 在计算线程池执行有返回值的任务
     *
     * @param task 有返回值的任务
     * @return [Future] 对象
     */
    @JvmStatic
    fun <T> compute(task: () -> T): Future<T> {
        val future = FutureTask(Callable { task() })
        ThreadPoolManager.computePool.execute(future)
        return future
    }

    /**
     * 在后台线程池执行有返回值的任务
     *
     * @param task 有返回值的任务
     * @return [Future] 对象
     */
    @JvmStatic
    fun <T> background(task: () -> T): Future<T> {
        val future = FutureTask(Callable { task() })
        ThreadPoolManager.backgroundPool.execute(future)
        return future
    }

    // ==================== 优先级执行 ====================

    /**
     * 按优先级提交任务到指定线程池
     *
     * @param pool     目标线程池
     * @param priority 任务优先级
     * @param task     要执行的任务
     *
     * 注意: 目标线程池需要使用 [PriorityBlockingQueue] 才能生效，
     * 预置的 I/O 池使用 SynchronousQueue，不支持优先级排序。
     * 可通过 [ThreadPoolManager.newFixedPool] 配合 PriorityBlockingQueue 使用。
     */
    @JvmStatic
    fun execute(pool: ThreadPoolExecutor, priority: Priority, task: () -> Unit) {
        val seq = sequenceGenerator.getAndIncrement()
        val priorityTask = PriorityRunnable(priority, seq, wrap(task))
        pool.execute(priorityTask)
    }

    /**
     * 按优先级提交任务到后台线程池
     *
     * @param priority 任务优先级
     * @param task     要执行的任务
     */
    @JvmStatic
    fun background(priority: Priority, task: () -> Unit) {
        execute(ThreadPoolManager.priorityPool, priority, task)
    }

    /**
     * 创建带优先级的 Future 任务
     *
     * @param priority 任务优先级
     * @param task     有返回值的任务
     * @return PriorityFutureTask
     */
    @JvmStatic
    fun <T> submitWithPriority(pool: ThreadPoolExecutor, priority: Priority, task: () -> T): PriorityFutureTask<T> {
        val seq = sequenceGenerator.getAndIncrement()
        val future = PriorityFutureTask(priority, seq, Callable { task() })
        pool.execute(future)
        return future
    }

    // ==================== 延迟执行 ====================

    /**
     * 延迟在主线程执行任务
     *
     * @param task    要执行的任务
     * @param delayMs 延迟毫秒数
     * @return 可用于取消的 [Runnable]
     *
     * 使用示例:
     * ```kotlin
     * val runnable = TaskExecutor.mainDelayed(1000) {
     *     Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
     * }
     * // 如需取消:
     * TaskExecutor.cancel(runnable)
     * ```
     */
    @JvmStatic
    fun mainDelayed(task: () -> Unit, delayMs: Long): Runnable {
        val runnable = wrap(task)
        mainHandler.postDelayed(runnable, delayMs)
        return runnable
    }

    /**
     * 延迟在 I/O 线程池执行任务
     *
     * @param task    要执行的任务
     * @param delayMs 延迟毫秒数
     * @return Future 可用于取消或等待
     */
    @JvmStatic
    fun ioDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        return ThreadPoolManager.scheduledPool.schedule(
            wrap(task),
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * 延迟在后台线程池执行任务
     *
     * @param task    要执行的任务
     * @param delayMs 延迟毫秒数
     * @return Future 可用于取消或等待
     */
    @JvmStatic
    fun backgroundDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        return ThreadPoolManager.scheduledPool.schedule(
            wrap(task),
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    // ==================== 定时执行 ====================

    /**
     * 以固定频率执行任务（scheduleAtFixedRate）
     *
     * 以上一次任务**开始**时间为基准计算下次执行时间。
     * 如果单次执行时间超过周期，则任务会连续执行，不会重叠。
     *
     * @param task       要执行的任务
     * @param initialDelayMs 首次延迟毫秒
     * @param periodMs   执行周期毫秒
     * @return 可用于取消的 [Future]
     *
     * 使用示例:
     * ```kotlin
     * val future = TaskExecutor.scheduleAtFixedRate(
     *     initialDelayMs = 0,
     *     periodMs = 3000
     * ) {
     *     Log.d("Heartbeat", "ping")
     * }
     * ```
     */
    @JvmStatic
    fun scheduleAtFixedRate(
        task: () -> Unit,
        initialDelayMs: Long = 0,
        periodMs: Long
    ): Future<*> {
        return ThreadPoolManager.scheduledPool.scheduleAtFixedRate(
            wrap(task),
            initialDelayMs,
            periodMs,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * 以固定延迟执行任务（scheduleWithFixedDelay）
     *
     * 以上一次任务**结束**时间为基准计算延迟。
     * 确保两次执行之间有固定间隔。
     *
     * @param task       要执行的任务
     * @param initialDelayMs 首次延迟毫秒
     * @param delayMs    任务间隔毫秒
     * @return 可用于取消的 [Future]
     */
    @JvmStatic
    fun scheduleWithFixedDelay(
        task: () -> Unit,
        initialDelayMs: Long = 0,
        delayMs: Long
    ): Future<*> {
        return ThreadPoolManager.scheduledPool.scheduleWithFixedDelay(
            wrap(task),
            initialDelayMs,
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    // ==================== 取消 ====================

    /**
     * 取消 Future 任务
     *
     * @param future     要取消的 Future
     * @param mayInterrupt 是否中断正在执行的线程，默认 true
     * @return true 表示成功取消
     */
    @JvmStatic
    @JvmOverloads
    fun cancel(future: Future<*>, mayInterrupt: Boolean = true): Boolean {
        return future.cancel(mayInterrupt)
    }

    /**
     * 从主线程消息队列中移除指定任务
     *
     * @param runnable 之前通过 [mainDelayed] 提交的 Runnable
     */
    @JvmStatic
    fun cancelMain(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }

    /**
     * 移除主线程消息队列中所有匹配的回调
     *
     * @param token 用于匹配的 token 对象
     */
    @JvmStatic
    fun cancelMainAll(token: Any?) {
        mainHandler.removeCallbacksAndMessages(token)
    }

    // ==================== 等待 ====================

    /**
     * 阻塞等待 Future 完成并返回结果
     *
     * 注意: 此方法会阻塞当前线程，**不可在主线程调用**。
     *
     * @param future  Future 对象
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 任务执行结果
     * @throws TimeoutException 超时
     * @throws ExecutionException 任务执行异常
     * @throws CancellationException 任务被取消
     *
     * 使用示例:
     * ```kotlin
     * TaskExecutor.io {
     *     val future = TaskExecutor.compute<Int> { heavyComputation() }
     *     try {
     *         val result = TaskExecutor.await(future)
     *         TaskExecutor.main { displayResult(result) }
     *     } catch (e: TimeoutException) {
     *         TaskExecutor.main { showTimeoutError() }
     *     }
     * }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    @Throws(TimeoutException::class, ExecutionException::class, CancellationException::class)
    fun <T> await(
        future: Future<T>,
        timeout: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ): T? {
        return try {
            if (timeout <= 0) {
                future.get()
            } else {
                future.get(timeout, unit)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ExecutionException("Interrupted while waiting for future", e)
        }
    }

    /**
     * 阻塞等待多个 Future 全部完成
     *
     * @param futures Future 列表
     * @param timeout 总超时时间
     * @param unit    时间单位
     * @throws TimeoutException 超时（某些任务未完成）
     */
    @JvmStatic
    @JvmOverloads
    @Throws(TimeoutException::class)
    fun awaitAll(
        futures: List<Future<*>>,
        timeout: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ) {
        val timeoutNanos = if (timeout > 0) unit.toNanos(timeout) else Long.MAX_VALUE
        val startNanos = System.nanoTime()
        futures.forEach { future ->
            val elapsed = System.nanoTime() - startNanos
            val remaining = if (timeoutNanos == Long.MAX_VALUE) Long.MAX_VALUE
            else timeoutNanos - elapsed
            if (remaining <= 0) throw TimeoutException("awaitAll timed out")
            try {
                if (remaining != Long.MAX_VALUE) {
                    future.get(remaining, TimeUnit.NANOSECONDS)
                } else {
                    future.get()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ExecutionException("Interrupted while awaiting futures", e)
            } catch (e: CancellationException) {
                // 已取消的任务视为完成，继续等待其他
            }
        }
    }

    /**
     * 阻塞等待任意一个 Future 完成
     *
     * @param futures Future 列表
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 第一个完成的 Future 在列表中的索引
     */
    @JvmStatic
    @Throws(TimeoutException::class)
    fun awaitAny(
        futures: List<Future<*>>,
        timeout: Long,
        unit: TimeUnit
    ): Int {
        require(timeout >= 0) { "timeout must be non-negative" }
        val timeoutNanos = unit.toNanos(timeout)
        val startNanos = System.nanoTime()
        while (System.nanoTime() - startNanos < timeoutNanos) {
            futures.forEachIndexed { index, future ->
                if (future.isDone) return index
            }
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ExecutionException("Interrupted while awaiting futures", e)
            }
        }
        throw TimeoutException("awaitAny timed out after ${unit.toMillis(timeout)}ms")
    }

    // ==================== 内部方法 ====================

    private fun wrap(task: () -> Unit): Runnable = Runnable { task() }
}
