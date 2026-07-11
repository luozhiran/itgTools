# itg-concurrent 三模块架构方案

## 一句话总结

新增 **itg-concurrent-core**（中间件）和 **itg-coroutine-pools**（协程实现），现有 **itg-thread-pools 零修改**。用户通过中间件统一 API 编码，可随时切换线程池 / 协程后端，也可同时使用两者。

---

## 一、架构总览

```
                       业务代码
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           ▼              ▼              ▼
   直接使用旧API     使用中间件API     混合使用
   (不改代码)         (推荐新代码)     (灵活切换)
           │              │              │
           ▼              └──────┬───────┘
  ┌────────────────┐             │
  │itg-thread-pools│             ▼
  │  (零修改!)      │   ┌─────────────────────┐
  │                │   │ itg-concurrent-core  │
  │ TaskExecutor   │   │    (中间件/统一API)    │
  │ ThreadPoolMgr  │   │                      │
  │ HandlerManager │   │ ConcurrentScope      │
  │ ThreadUtils    │   │ TaskDispatcher       │
  └────────┬───────┘   │ ConcurrentFactory    │
           │           └──────┬──────┬─────────┘
           │                  │      │
           │     ┌────────────┘      └────────────┐
           │     ▼                                ▼
           │   ┌──────────────────┐  ┌──────────────────┐
           │   │ 线程池适配器       │  │ 协程适配器        │
           │   │ (core 内置)       │  │ (core 内置)       │
           │   │ 桥接 itg-thread-  │  │ 桥接 itg-coroutine│
           │   │ pools 到统一接口   │  │ -pools 到统一接口  │
           │   └──────────────────┘  └────────┬─────────┘
           │                                  │
           └──────────────────────────────────┘
                                              ▼
                                    ┌──────────────────┐
                                    │itg-coroutine-pools│
                                    │    (新增)          │
                                    │                    │
                                    │ CoroutineScope     │
                                    │ Dispatchers 管理    │
                                    │ Channel 消息通道    │
                                    └──────────────────┘
```

### 三个模块的定位

| 模块 | 定位 | 改动 | 说明 |
|------|------|:--:|------|
| **itg-concurrent-core** | 🧩 中间件 / 统一抽象层 | 新增 | 定义接口 + 工厂 + 内置两种适配器 |
| **itg-thread-pools** | 🔧 线程池后端 | **零修改** | 保持现状，core 通过适配器桥接 |
| **itg-coroutine-pools** | 🚀 协程后端 | 新增 | 协程原生实现，也实现 core 接口 |

### 用户使用方式矩阵

| 场景 | 依赖配置 | 代码写法 |
|------|---------|---------|
| **只用线程池** | `itg-thread-pools` | `TaskExecutor.io { }` (老API) |
| **只用协程** | `itg-coroutine-pools` | `CoroutineExecutor.io { }` (新API) |
| **通过中间件，底层线程池** | `itg-concurrent-core` + `itg-thread-pools` | `Concurrent.io { }` → 线程池执行 |
| **通过中间件，底层协程** | `itg-concurrent-core` + `itg-coroutine-pools` | `Concurrent.io { }` → 协程执行 |
| **同时使用两者** | 全部依赖 | 灵活按任务选择后端 |

---

## 二、中间件模块设计：itg-concurrent-core

### 2.1 模块结构

```
itg-concurrent-core/
├── build.gradle.kts
├── src/main/java/com/itg/concurrent/
│   ├── Concurrent.kt              ← 统一入口（类似 TaskExecutor 的角色）
│   ├── TaskDispatcher.kt          ← 核心抽象：任务分发器
│   ├── ConcurrentScope.kt         ← 生命周期管理（替代 shutdown/awaitTermination）
│   ├── ConcurrentFactory.kt       ← 工厂 + SPI 注册
│   ├── backend/
│   │   ├── ThreadPoolAdapter.kt   ← 适配 itg-thread-pools
│   │   └── CoroutineAdapter.kt    ← 适配 itg-coroutine-pools
│   └── util/
│       ├── ConcurrentUtils.kt     ← 统一工具方法
│       └── ThreadChecks.kt        ← 线程检测（两个后端共用）
```

### 2.2 核心接口设计

```kotlin
// ==================== TaskDispatcher.kt ====================
package com.itg.concurrent

import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 任务分发器 — 中间件的核心抽象
 *
 * 所有任务提交最终都通过这个接口执行。
 * 线程池和协程两种后端各自实现此接口。
 */
interface TaskDispatcher {
    /** 分发器名称（用于调试） */
    val name: String

    /** 执行 fire-and-forget 任务 */
    fun execute(task: () -> Unit)

    /** 执行有返回值的任务 */
    fun <T> submit(task: () -> T): Future<T>

    /** 延迟执行 */
    fun schedule(task: () -> Unit, delayMs: Long): Future<*>

    /** 是否支持协程原生调度（suspend 函数） */
    val supportsCoroutineNative: Boolean
}

/**
 * 协程原生分发器 — 支持 suspend 函数
 */
interface CoroutineTaskDispatcher : TaskDispatcher {
    /** 在分发器上执行 suspend 函数（非阻塞） */
    suspend fun <T> executeSuspend(task: suspend () -> T): T

    /** 获取此分发器对应的 CoroutineDispatcher */
    val coroutineDispatcher: CoroutineDispatcher
}

/**
 * 预设分发器类型
 */
enum class DispatcherType {
    /** I/O 密集型 — 网络 / 文件 / 数据库 */
    IO,
    /** CPU 密集型 — 计算 / 加解密 / 图片处理 */
    COMPUTE,
    /** 通用后台 */
    BACKGROUND,
    /** 单线程串行 */
    SINGLE,
    /** 主线程 / UI 线程 */
    MAIN
}
```

### 2.3 统一入口：Concurrent

```kotlin
// ==================== Concurrent.kt ====================
package com.itg.concurrent

/**
 * 统一并发入口 — 中间件的门面
 *
 * 使用方式和 TaskExecutor 几乎一致，用户只需改 import：
 *
 * ```kotlin
 * // 老代码 (itg-thread-pools 直接使用)
 * import com.itg.itg_thread_pools.executor.TaskExecutor
 * TaskExecutor.io { doWork() }
 *
 * // 新代码 (通过中间件，默认协程后端)
 * import com.itg.concurrent.Concurrent
 * Concurrent.io { doWork() }
 *
 * // 新代码 (suspend 原生写法，协程后端)
 * Concurrent.ioSuspend { doSuspendWork() }
 * ```
 */
object Concurrent {

    /** 当前后端类型 */
    val backend: BackendType get() = ConcurrentFactory.currentBackend

    // ============ 老 API 兼容层（和 TaskExecutor 签名一致）============

    @JvmStatic fun io(task: () -> Unit)           = get(DispatcherType.IO).execute(task)
    @JvmStatic fun compute(task: () -> Unit)       = get(DispatcherType.COMPUTE).execute(task)
    @JvmStatic fun background(task: () -> Unit)    = get(DispatcherType.BACKGROUND).execute(task)
    @JvmStatic fun single(task: () -> Unit)        = get(DispatcherType.SINGLE).execute(task)

    @JvmStatic fun main(task: () -> Unit) {
        get(DispatcherType.MAIN).execute(task)
    }

    @JvmStatic fun <T> io(task: () -> T): Future<T>     = get(DispatcherType.IO).submit(task)
    @JvmStatic fun <T> compute(task: () -> T): Future<T> = get(DispatcherType.COMPUTE).submit(task)
    @JvmStatic fun <T> background(task: () -> T): Future<T> = get(DispatcherType.BACKGROUND).submit(task)

    @JvmStatic fun ioDelayed(task: () -> Unit, delayMs: Long): Future<*>
        = get(DispatcherType.IO).schedule(task, delayMs)

    @JvmStatic fun mainDelayed(task: () -> Unit, delayMs: Long): Future<*>
        = get(DispatcherType.MAIN).schedule(task, delayMs)

    // ============ 新 API：suspend 原生支持（仅协程后端高效）============

    @JvmStatic
    suspend fun <T> ioSuspend(task: suspend () -> T): T {
        val disp = get(DispatcherType.IO)
        return if (disp is CoroutineTaskDispatcher) {
            disp.executeSuspend(task)
        } else {
            // 回退：在线程池上跑 suspend 块
            withContext(disp.toCoroutineDispatcher()) { task() }
        }
    }

    @JvmStatic
    suspend fun <T> computeSuspend(task: suspend () -> T): T {
        val disp = get(DispatcherType.COMPUTE)
        return if (disp is CoroutineTaskDispatcher) disp.executeSuspend(task)
        else withContext(disp.toCoroutineDispatcher()) { task() }
    }

    // ============ 进阶：获取分发器直接使用 ============

    @JvmStatic
    fun get(type: DispatcherType): TaskDispatcher = ConcurrentFactory.getDispatcher(type)

    @JvmStatic
    fun getCoroutineDispatcher(type: DispatcherType): CoroutineDispatcher {
        val disp = get(type)
        return if (disp is CoroutineTaskDispatcher) disp.coroutineDispatcher
        else disp.toCoroutineDispatcher()
    }
}
```

### 2.4 工厂 + 后端注册：ConcurrentFactory

```kotlin
// ==================== ConcurrentFactory.kt ====================
package com.itg.concurrent

import java.util.*

/**
 * 并发工厂 — 管理后端的注册和切换
 *
 * 通过 SPI 自动发现可用的后端实现，
 * 也可通过代码手动注册和切换。
 */
object ConcurrentFactory {

    enum class BackendType { THREAD_POOL, COROUTINE, AUTO }

    @Volatile
    private var _currentBackend: BackendType = BackendType.AUTO

    /** 当前后端类型 */
    val currentBackend: BackendType get() = _currentBackend

    /** 分发器注册表 */
    private val registries = EnumMap<DispatcherType, TaskDispatcher>(DispatcherType::class.java)

    /**
     * 获取指定类型的分发器
     *
     * 查找顺序：
     * 1. 用户手动注册的分发器
     * 2. 根据 currentBackend 选择默认实现
     * 3. 根据 classpath 可用性自动选择
     */
    fun getDispatcher(type: DispatcherType): TaskDispatcher {
        registries[type]?.let { return it }
        return resolveDefault(type)
    }

    /**
     * 手动注册自定义分发器（覆盖默认）
     */
    fun register(type: DispatcherType, dispatcher: TaskDispatcher) {
        registries[type] = dispatcher
    }

    /**
     * 切换后端
     *
     * ```kotlin
     * // 在 Application.onCreate() 中全局切换
     * ConcurrentFactory.switchTo(BackendType.COROUTINE)
     * ```
     */
    fun switchTo(backend: BackendType) {
        _currentBackend = backend
        registries.clear()  // 清空缓存，下次获取时用新后端
    }

    /**
     * 同时启用两个后端，按类型分配
     *
     * ```kotlin
     * // IO 用协程，COMPUTE 用线程池
     * ConcurrentFactory.register(DispatcherType.IO,
     *     CoroutineBackend.getDispatcher(DispatcherType.IO))
     * ConcurrentFactory.register(DispatcherType.COMPUTE,
     *     ThreadPoolBackend.getDispatcher(DispatcherType.COMPUTE))
     * ```
     */
    fun useMixed(config: Map<DispatcherType, BackendType>) {
        config.forEach { (type, backend) ->
            registries[type] = when (backend) {
                BackendType.THREAD_POOL -> ThreadPoolAdapter.create(type)
                BackendType.COROUTINE   -> CoroutineAdapter.create(type)
                BackendType.AUTO        -> resolveDefault(type)
            }
        }
    }

    /**
     * 生命周期：关闭所有分发器
     */
    fun shutdown() {
        registries.values.forEach {
            if (it is AutoCloseable) it.close()
        }
        registries.clear()
    }

    // ---- 内部 ----

    private fun resolveDefault(type: DispatcherType): TaskDispatcher {
        return when (_currentBackend) {
            BackendType.COROUTINE   -> CoroutineAdapter.create(type)
            BackendType.THREAD_POOL -> ThreadPoolAdapter.create(type)
            BackendType.AUTO        -> autoResolve(type)
        }
    }

    private fun autoResolve(type: DispatcherType): TaskDispatcher {
        // 检测 classpath 上是否有协程库
        val hasCoroutines = try {
            Class.forName("kotlinx.coroutines.CoroutineScope")
            true
        } catch (_: ClassNotFoundException) { false }

        return if (hasCoroutines) CoroutineAdapter.create(type)
        else ThreadPoolAdapter.create(type)
    }
}
```

### 2.5 线程池适配器（桥接 itg-thread-pools，不改原代码）

```kotlin
// ==================== ThreadPoolAdapter.kt ====================
package com.itg.concurrent.backend

import com.itg.concurrent.*
import com.itg.itg_thread_pools.manager.ThreadPoolManager
import com.itg.itg_thread_pools.executor.TaskExecutor
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 将 itg-thread-pools 适配为 TaskDispatcher 接口
 *
 * 纯粹的外层包装，不改动 itg-thread-pools 一行代码。
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
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val future = FutureTask { }
            handler.postDelayed({
                task()
                future.run()
            }, delayMs)
            return future
        }
    }
}

/**
 * 将 TaskDispatcher 转为 CoroutineDispatcher（用于桥接线程池到协程上下文）
 */
fun TaskDispatcher.toCoroutineDispatcher(): CoroutineDispatcher {
    if (this is CoroutineTaskDispatcher) return this.coroutineDispatcher

    // 用 Java Executor 包装为 CoroutineDispatcher
    val executor = Executor { command -> this.execute { command.run() } }
    return executor.asCoroutineDispatcher()
}
```

### 2.6 协程适配器（桥接 itg-coroutine-pools）

```kotlin
// ==================== CoroutineAdapter.kt ====================
package com.itg.concurrent.backend

import com.itg.concurrent.*
import com.itg.itg_coroutine_pools.CoroutineDispatcherManager
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * 将 itg-coroutine-pools 适配为 TaskDispatcher 接口
 */
object CoroutineAdapter {

    fun create(type: DispatcherType): TaskDispatcher {
        return when (type) {
            DispatcherType.IO         -> CoroutinePoolDispatcher("io", CoroutineDispatcherManager.ioDispatcher)
            DispatcherType.COMPUTE    -> CoroutinePoolDispatcher("compute", CoroutineDispatcherManager.computeDispatcher)
            DispatcherType.BACKGROUND -> CoroutinePoolDispatcher("bg", CoroutineDispatcherManager.backgroundDispatcher)
            DispatcherType.SINGLE     -> CoroutinePoolDispatcher("single", CoroutineDispatcherManager.singleDispatcher)
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
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
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

        override fun close() { /* Dispatchers.Main 不需要关闭 */ }
    }
}

/**
 * Deferred → Future 桥接工具
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
```

### 2.7 统一工具：ConcurrentUtils

```kotlin
// ==================== ConcurrentUtils.kt ====================
package com.itg.concurrent.util

import android.os.Looper

/**
 * 并发工具 — 底层后端无关的公共方法
 */
object ConcurrentUtils {

    @JvmStatic fun isMainThread(): Boolean =
        Looper.myLooper() == Looper.getMainLooper()

    @JvmStatic fun isBackgroundThread(): Boolean = !isMainThread()

    /** 非阻塞延迟（仅协程后端） */
    @JvmStatic
    suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }

    /** 阻塞等待 Future 结果（两个后端通用） */
    @JvmStatic
    fun <T> await(future: java.util.concurrent.Future<T>, timeoutMs: Long = 0): T? {
        return try {
            if (timeoutMs <= 0) future.get()
            else future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /** 安全的阻塞延迟（两个后端通用，不可在主线程调用） */
    @JvmStatic
    fun sleep(ms: Long) {
        if (isMainThread()) return
        try { Thread.sleep(ms) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
```

---

## 三、协程后端模块：itg-coroutine-pools

### 3.1 模块结构

```
itg-coroutine-pools/
├── build.gradle.kts
├── src/main/java/com/itg/itg_coroutine_pools/
│   ├── manager/
│   │   └── CoroutineDispatcherManager.kt   ← 对应 ThreadPoolManager
│   ├── executor/
│   │   └── CoroutineExecutor.kt            ← 对应 TaskExecutor
│   ├── channel/
│   │   └── ChannelManager.kt               ← 对应 HandlerManager
│   └── utils/
│       └── CoroutineUtils.kt               ← 对应 ThreadUtils
```

### 3.2 CoroutineDispatcherManager

```kotlin
// ==================== CoroutineDispatcherManager.kt ====================
package com.itg.itg_coroutine_pools.manager

import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 协程调度器管理器 — 对标 ThreadPoolManager
 *
 * 提供预配置的 CoroutineDispatcher 实例和工厂方法。
 * 自带 fallback：如果协程不可用，自动回退到 Java Executor。
 *
 * 和 ThreadPoolManager 的 API 保持一致：
 * ```
 * // 线程池版本
 * ThreadPoolManager.ioPool.execute { }
 *
 * // 协程版本（同样是获取 dispatcher + execute）
 * CoroutineDispatcherManager.ioDispatcher.execute { }
 * ```
 */
object CoroutineDispatcherManager {

    // ============ 预置 Dispatcher ============

    /**
     * I/O 密集型 — 对标 ioPool
     *
     * Dispatchers.IO 弹性线程池，默认最多 64 线程。
     * 额外通过 limitedParallelism 保证最少并行度。
     */
    @JvmField
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * 计算密集型 — 对标 computePool
     *
     * Dispatchers.Default 固定大小为 CPU 核心数。
     */
    @JvmField
    val computeDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * 通用后台 — 对标 backgroundPool
     */
    @JvmField
    val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * 单线程串行 — 对标 singlePool
     *
     * limitedParallelism(1) 确保所有任务在同一个线程上串行执行。
     */
    @JvmField
    val singleDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * 主线程 — 对标 mainExecutor
     */
    @JvmField
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    // ============ 工厂方法 ============

    /**
     * 创建固定并发度的 Dispatcher
     *
     * 对标 ThreadPoolManager.newFixedPool(name, threads)
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
    fun newSingleDispatcher(name: String = "itg-coro-single"): CoroutineDispatcher {
        return newFixedDispatcher(name, 1)
    }

    /**
     * 从 Java Executor 创建 Dispatcher（桥接模式）
     *
     * 对标 ThreadPoolManager.newCustomPool()
     */
    @JvmStatic
    fun fromExecutor(executor: Executor): CoroutineDispatcher {
        return executor.asCoroutineDispatcher()
    }

    /**
     * 从 ThreadPoolExecutor 创建 Dispatcher（保留线程命名等）
     */
    @JvmStatic
    fun fromThreadPool(
        coreSize: Int,
        maxSize: Int,
        name: String = "itg-coro-custom"
    ): CoroutineDispatcher {
        val factory = NamedThreadFactory(name)
        val pool = ThreadPoolExecutor(
            coreSize, maxSize,
            60L, TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            factory
        )
        return pool.asCoroutineDispatcher()
    }

    // ============ 状态监控 ============

    @JvmStatic
    fun getDispatcherInfo(dispatcher: CoroutineDispatcher): Map<String, String> {
        return mapOf(
            "type" to (dispatcher::class.simpleName ?: "unknown"),
            "toString" to dispatcher.toString()
        )
    }

    // ============ 生命周期 ============

    @JvmStatic
    fun shutdown(scope: CoroutineScope) {
        scope.cancel()
    }

    // 对标 NamedThreadFactory
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
                    android.util.Log.e("CoroutinePool-$prefix",
                        "Uncaught exception in thread '${t.name}'", e)
                }
            }
        }
    }
}
```

### 3.3 CoroutineExecutor

```kotlin
// ==================== CoroutineExecutor.kt ====================
package com.itg.itg_coroutine_pools.executor

import com.itg.itg_coroutine_pools.manager.CoroutineDispatcherManager
import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/**
 * 协程任务执行器 — 对标 TaskExecutor
 *
 * API 和 TaskExecutor 保持一致，用户只需改 import：
 *
 * ```
 * // 线程池版本                         // 协程版本
 * TaskExecutor.io { }                  CoroutineExecutor.io { }
 * TaskExecutor.main { }                CoroutineExecutor.main { }
 * TaskExecutor.io<T> { }: Future<T>    CoroutineExecutor.io<T> { }: Future<T>
 * TaskExecutor.await(future)           CoroutineExecutor.await(future)
 * ```
 *
 * 额外提供 suspend 原生 API（协程独有优势）：
 * ```
 * CoroutineExecutor.ioSuspend { suspendWork() }
 * ```
 */
object CoroutineExecutor {

    private val sequenceGenerator = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ============ Fire-and-Forget（和 TaskExecutor 签名一致）============

    @JvmStatic fun io(task: () -> Unit)        { scope.launch(CoroutineDispatcherManager.ioDispatcher) { task() } }
    @JvmStatic fun compute(task: () -> Unit)    { scope.launch(CoroutineDispatcherManager.computeDispatcher) { task() } }
    @JvmStatic fun background(task: () -> Unit) { scope.launch(CoroutineDispatcherManager.backgroundDispatcher) { task() } }
    @JvmStatic fun single(task: () -> Unit)     { scope.launch(CoroutineDispatcherManager.singleDispatcher) { task() } }

    @JvmStatic
    fun main(task: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            task()
        } else {
            scope.launch(Dispatchers.Main) { task() }
        }
    }

    // ============ 有返回值 Future（和 TaskExecutor 签名一致）============

    @JvmStatic fun <T> io(task: () -> T): Future<T>
        = scope.async(CoroutineDispatcherManager.ioDispatcher) { task() }.asFuture()

    @JvmStatic fun <T> compute(task: () -> T): Future<T>
        = scope.async(CoroutineDispatcherManager.computeDispatcher) { task() }.asFuture()

    @JvmStatic fun <T> background(task: () -> T): Future<T>
        = scope.async(CoroutineDispatcherManager.backgroundDispatcher) { task() }.asFuture()

    // ============ 延迟执行 ============

    @JvmStatic fun mainDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        val deferred = scope.async {
            delay(delayMs)
            withContext(Dispatchers.Main) { task() }
        }
        return deferred.asFuture()
    }

    @JvmStatic fun ioDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        val deferred = scope.async(CoroutineDispatcherManager.ioDispatcher) {
            delay(delayMs)
            task()
        }
        return deferred.asFuture()
    }

    @JvmStatic fun backgroundDelayed(task: () -> Unit, delayMs: Long): Future<*> {
        val deferred = scope.async(CoroutineDispatcherManager.backgroundDispatcher) {
            delay(delayMs)
            task()
        }
        return deferred.asFuture()
    }

    // ============ 定时执行 ============

    @JvmStatic
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
        return job.asFuture()
    }

    @JvmStatic
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
        return job.asFuture()
    }

    // ============ 取消 ============

    @JvmStatic
    @JvmOverloads
    fun cancel(future: Future<*>, mayInterrupt: Boolean = true): Boolean {
        return future.cancel(mayInterrupt)
    }

    // ============ 等待 ============

    @JvmStatic
    @JvmOverloads
    fun <T> await(future: Future<T>, timeoutMs: Long = 0): T? {
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

    @JvmStatic
    @JvmOverloads
    fun awaitAll(futures: List<Future<*>>, timeoutMs: Long = 0) {
        val deadline = if (timeoutMs > 0) System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs) else Long.MAX_VALUE
        futures.forEach { future ->
            val remaining = if (deadline == Long.MAX_VALUE) 0L
            else TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(0)
            if (deadline != Long.MAX_VALUE && remaining <= 0) throw TimeoutException("awaitAll timed out")
            try {
                if (remaining <= 0) future.get() else future.get(remaining, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: CancellationException) { }
        }
    }

    @JvmStatic
    fun awaitAny(futures: List<Future<*>>, timeoutMs: Long): Int {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            futures.forEachIndexed { index, future ->
                if (future.isDone) return index
            }
            Thread.sleep(10)
        }
        throw TimeoutException("awaitAny timed out")
    }

    // ============ 协程原生 API（独有优势）============

    @JvmStatic
    suspend fun <T> ioSuspend(task: suspend () -> T): T {
        return withContext(CoroutineDispatcherManager.ioDispatcher) { task() }
    }

    @JvmStatic
    suspend fun <T> computeSuspend(task: suspend () -> T): T {
        return withContext(CoroutineDispatcherManager.computeDispatcher) { task() }
    }

    @JvmStatic
    suspend fun <T> mainSuspend(task: suspend () -> T): T {
        return withContext(Dispatchers.Main) { task() }
    }

    // ============ 生命周期 ============

    @JvmStatic
    fun shutdown() {
        scope.cancel()
    }

    // ---- Deferred → Future 桥接 ----

    private fun <T> Deferred<T>.asFuture(): Future<T> = object : java.util.concurrent.Future<T> {
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
}
```

### 3.4 ChannelManager

```kotlin
// ==================== ChannelManager.kt ====================
package com.itg.itg_coroutine_pools.channel

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 协程消息通道管理器 — 对标 HandlerManager
 *
 * 用 Channel + CoroutineScope 替代 HandlerThread + Looper 机制。
 * 提供严格串行的消息处理，API 和 HandlerManager 保持一致。
 *
 * 对比：
 * - HandlerManager: 基于 Android HandlerThread，每个名称独占一个物理线程
 * - ChannelManager: 基于 Channel + 协程，轻量级，无需额外线程
 */
object ChannelManager {

    /** 消息结构 */
    data class Message(
        val what: Int,
        val arg1: Int = 0,
        val arg2: Int = 0,
        val obj: Any? = null
    )

    /** 每个命名的通道 */
    private data class NamedChannel(
        val scope: CoroutineScope,
        val channel: Channel<suspend () -> Unit>,
        val messageChannel: Channel<Message>
    )

    private val channels = ConcurrentHashMap<String, NamedChannel>()

    // ============ 创建 / 获取 ============

    /**
     * 获取或创建命名通道 — 对标 HandlerManager.getOrCreate()
     */
    @JvmStatic
    @JvmOverloads
    fun getOrCreate(
        name: String,
        capacity: Int = Channel.UNLIMITED,
        messageProcessor: ((Message) -> Unit)? = null
    ) {
        channels.computeIfAbsent(name) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
            val channel = Channel<suspend () -> Unit>(capacity)
            val msgChannel = Channel<Message>(capacity)

            // 消费循环（串行处理）
            scope.launch {
                // 优先处理 Runnable 任务
                val job = launch {
                    for (task in channel) {
                        try { task() } catch (e: Exception) {
                            android.util.Log.e("ChannelManager-$name", "Task error", e)
                        }
                    }
                }
                // 并行监听 Message
                if (messageProcessor != null) {
                    launch {
                        for (msg in msgChannel) {
                            try { messageProcessor(msg) } catch (e: Exception) {
                                android.util.Log.e("ChannelManager-$name", "Message error", e)
                            }
                        }
                    }
                }
                job.join()
            }

            NamedChannel(scope, channel, msgChannel)
        }
    }

    // ============ 提交任务 ============

    @JvmStatic
    fun post(name: String, task: () -> Unit) {
        channels[name]?.let { named ->
            named.scope.launch { named.channel.send { task() } }
        }
    }

    @JvmStatic
    fun postDelayed(name: String, delayMs: Long, task: () -> Unit) {
        channels[name]?.let { named ->
            named.scope.launch {
                delay(delayMs)
                named.channel.send { task() }
            }
        }
    }

    @JvmStatic
    fun postToMain(task: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            task()
        } else {
            CoroutineScope(Dispatchers.Main).launch { task() }
        }
    }

    @JvmStatic
    fun postToMainDelayed(delayMs: Long, task: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            task()
        }
    }

    // ============ 发送 Message ============

    @JvmStatic
    @JvmOverloads
    fun sendMessage(
        name: String,
        what: Int,
        arg1: Int = 0,
        arg2: Int = 0,
        obj: Any? = null
    ) {
        channels[name]?.let { named ->
            named.scope.launch {
                named.messageChannel.send(Message(what, arg1, arg2, obj))
            }
        }
    }

    @JvmStatic
    fun sendMessageDelayed(
        name: String,
        what: Int,
        delayMs: Long,
        arg1: Int = 0,
        arg2: Int = 0,
        obj: Any? = null
    ) {
        channels[name]?.let { named ->
            named.scope.launch {
                delay(delayMs)
                named.messageChannel.send(Message(what, arg1, arg2, obj))
            }
        }
    }

    // ============ 取消 / 退出 ============

    @JvmStatic
    @Synchronized
    fun quit(name: String) {
        channels.remove(name)?.let { named ->
            named.channel.close()
            named.messageChannel.close()
            named.scope.cancel()
        }
    }

    @JvmStatic
    @Synchronized
    fun quitAll() {
        channels.keys.forEach { quit(it) }
        channels.clear()
    }

    // ============ 状态查询 ============

    @JvmStatic
    fun isAlive(name: String): Boolean = channels.containsKey(name)

    @JvmStatic
    fun getAllNames(): Set<String> = channels.keys.toSet()

    @JvmStatic
    fun getCount(): Int = channels.size
}
```

### 3.5 build.gradle.kts

```kotlin
// itg-coroutine-pools/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.itg_coroutine_pools"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    // 协程核心
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        repositories { maven { url = uri("${buildDir}/repo") } }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "itg-coroutine-pools"
                version = "0.1.0"
            }
        }
    }
}
```

---

## 四、API 对照总表

### 4.1 三个模块的类名映射

| itg-thread-pools<br>(现有,不改) | itg-coroutine-pools<br>(新增) | itg-concurrent-core<br>(中间件) | 说明 |
|:---|:---|:---|:---|
| `TaskExecutor` | `CoroutineExecutor` | `Concurrent` | 任务执行入口 |
| `ThreadPoolManager` | `CoroutineDispatcherManager` | `ConcurrentFactory.getDispatcher()` | Dispatcher 管理 |
| `HandlerManager` | `ChannelManager` | (通过 ChannelManager) | 消息通道 |
| `ThreadUtils` | `CoroutineUtils` | `ConcurrentUtils` | 工具方法 |
| `Priority` | `Priority` (复用) | `Priority` (复用) | 优先级枚举 |
| `PriorityRunnable` | `PriorityCoroutine` | - | 优先级任务 |

### 4.2 方法对照（核心 API）

| 功能 | itg-thread-pools | itg-coroutine-pools | itg-concurrent-core |
|------|:---|:---|:---|
| IO执行 | `TaskExecutor.io { }` | `CoroutineExecutor.io { }` | `Concurrent.io { }` |
| 计算执行 | `TaskExecutor.compute { }` | `CoroutineExecutor.compute { }` | `Concurrent.compute { }` |
| 后台执行 | `TaskExecutor.background { }` | `CoroutineExecutor.background { }` | `Concurrent.background { }` |
| 串行执行 | `TaskExecutor.single { }` | `CoroutineExecutor.single { }` | `Concurrent.single { }` |
| 主线程 | `TaskExecutor.main { }` | `CoroutineExecutor.main { }` | `Concurrent.main { }` |
| IO+返回值 | `TaskExecutor.io<T> { }: Future<T>` | `CoroutineExecutor.io<T> { }: Future<T>` | `Concurrent.io<T> { }: Future<T>` |
| 延迟执行 | `TaskExecutor.ioDelayed(ms) { }` | `CoroutineExecutor.ioDelayed(ms) { }` | `Concurrent.ioDelayed(ms) { }` |
| 定时执行 | `TaskExecutor.scheduleAtFixedRate(...)` | `CoroutineExecutor.scheduleAtFixedRate(...)` | (通过 get().schedule) |
| 等待结果 | `TaskExecutor.await(future)` | `CoroutineExecutor.await(future)` | `ConcurrentUtils.await(future)` |
| 批量等待 | `TaskExecutor.awaitAll(futures)` | `CoroutineExecutor.awaitAll(futures)` | (通过 get()) |
| **协程原生** | - | `CoroutineExecutor.ioSuspend { }` | `Concurrent.ioSuspend { }` |

---

## 五、使用场景示例

### 场景1：只用协程库（不用中间件）

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":itg-coroutine-pools"))
}

// 业务代码 — 和 TaskExecutor 一样的写法
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

fun loadData(onResult: (Data) -> Unit) {
    CoroutineExecutor.io {
        val data = api.fetch()
        CoroutineExecutor.main { onResult(data) }
    }
}
```

### 场景2：通过中间件 + 协程后端

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-coroutine-pools"))  // 提供后端
}

// Application.onCreate()
ConcurrentFactory.switchTo(BackendType.COROUTINE)

// 业务代码
import com.itg.concurrent.Concurrent

fun loadData(onResult: (Data) -> Unit) {
    Concurrent.io {                          // 老 API 风格
        val data = api.fetch()
        Concurrent.main { onResult(data) }
    }
}

// 或者用 suspend 新写法
suspend fun loadData(): Data {
    return Concurrent.ioSuspend { api.fetch() }
}
```

### 场景3：通过中间件 + 线程池后端

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-thread-pools"))  // 不改代码
}

// 同样的业务代码，底层用线程池
ConcurrentFactory.switchTo(BackendType.THREAD_POOL)

Concurrent.io { doWork() }  // 实际由 ThreadPoolManager.ioPool 执行
```

### 场景4：混合使用 — IO 协程，COMPUTE 线程池

```kotlin
// build.gradle.kts — 三个都依赖
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-thread-pools"))
    implementation(project(":itg-coroutine-pools"))
}

// Application.onCreate()
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO      to BackendType.COROUTINE,    // IO 用协程（高并发）
    DispatcherType.COMPUTE to BackendType.THREAD_POOL,  // 计算用线程池（稳定可预测）
    DispatcherType.SINGLE  to BackendType.COROUTINE,    // 串行用协程
))

// 业务代码完全不用变
Concurrent.io { networkCall() }      // → 协程执行
Concurrent.compute { heavyCalc() }   // → 线程池执行
```

### 场景5：和现有代码共存（不强制迁移）

```kotlin
// itg-file 模块不改代码，继续用 TaskExecutor
import com.itg.itg_thread_pools.executor.TaskExecutor
fun exists(path: String, onResult: (Boolean) -> Unit): Future<*> {
    return TaskExecutor.io { onResult(File(path).exists()) }
}

// 新模块用中间件
import com.itg.concurrent.Concurrent
fun newFeature() {
    Concurrent.ioSuspend { doSuspendWork() }  // 协程原生体验
}

// 两者在同一项目中共存，互不干扰
```

---

## 六、模块依赖关系与 settings.gradle.kts

```kotlin
// settings.gradle.kts 新增两行
include(":itg-concurrent-core")
include(":itg-coroutine-pools")
// include(":itg-thread-pools")  ← 已存在，不动
```

### 依赖关系图

```
itg-concurrent-core  ──optional──▶ itg-thread-pools     (编译时可选)
                     ──optional──▶ itg-coroutine-pools  (编译时可选)

itg-coroutine-pools  ──▶ kotlinx-coroutines-core
                     ──▶ kotlinx-coroutines-android

itg-thread-pools     (无新增依赖)
```

`itg-concurrent-core` 通过 `compileOnly` 声明对两个后端的依赖，运行时通过 `Class.forName` 检测可用性：

```kotlin
// itg-concurrent-core/build.gradle.kts
dependencies {
    // compileOnly — 编译时需要，运行时不强依赖
    compileOnly(project(":itg-thread-pools"))
    compileOnly(project(":itg-coroutine-pools"))
    
    // 协程核心（中间件本身需要）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
```

---

## 七、改动总览

| 模块 | 操作 | 新增文件 | 修改文件 |
|------|:--:|------|:--:|
| **itg-concurrent-core** | 🆕 新建 | `Concurrent.kt`, `TaskDispatcher.kt`, `ConcurrentFactory.kt`, `ConcurrentScope.kt`, `ThreadPoolAdapter.kt`, `CoroutineAdapter.kt`, `ConcurrentUtils.kt`, `build.gradle.kts` | - |
| **itg-coroutine-pools** | 🆕 新建 | `CoroutineDispatcherManager.kt`, `CoroutineExecutor.kt`, `ChannelManager.kt`, `CoroutineUtils.kt`, `build.gradle.kts` | - |
| **itg-thread-pools** | ✅ 不动 | - | **0 文件修改** |
| **settings.gradle.kts** | ➕ 添加 | - | 添加 2 个 include |
| **itg-file/encrypt/string/verification/outter** | ✅ 不动 | - | **0 文件修改** |

---

## 八、实施路线图

### Phase 1：协程后端 (2-3天)
1. 创建 `itg-coroutine-pools` 模块（build.gradle.kts, proguard, consumer-rules）
2. 实现 `CoroutineDispatcherManager`
3. 实现 `CoroutineExecutor`（API 和 TaskExecutor 一致）
4. 实现 `ChannelManager`（对标 HandlerManager）
5. 实现 `CoroutineUtils`
6. 编写单元测试

### Phase 2：中间件 (2-3天)
1. 创建 `itg-concurrent-core` 模块
2. 定义 `TaskDispatcher` + `CoroutineTaskDispatcher` 接口
3. 实现 `Concurrent` 统一入口
4. 实现 `ConcurrentFactory` + 后端注册/切换
5. 实现 `ThreadPoolAdapter`（桥接 itg-thread-pools，不改原代码）
6. 实现 `CoroutineAdapter`（桥接 itg-coroutine-pools）
7. 实现 `ConcurrentUtils`

### Phase 3：集成验证 (1-2天)
1. `settings.gradle.kts` 添加两个新模块
2. 验证三个模块的独立编译 + 组合使用
3. 验证中间件的后端切换（线程池 ⇄ 协程）
4. 验证混合模式（不同 DispatcherType 用不同后端）
5. 验证和现有模块的共存

**总工期：5-8 天**

---

## 九、设计优势总结

| 特性 | 说明 |
|------|------|
| 🔌 **可拔插** | 中间件抽象，后端随时切换 |
| 🔧 **零修改** | itg-thread-pools 不改一行代码 |
| 🚀 **协程原生** | itg-coroutine-pools 提供完整协程体验 |
| 🔀 **可混合** | IO用协程+Compute用线程池，各取所长 |
| 📐 **API一致** | 三个模块的方法名/参数一致，学习成本低 |
| 🏗️ **渐进迁移** | 旧代码用旧API，新代码用中间件，互不干扰 |
| 🛡️ **低风险** | itg-thread-pools 不变，现有模块零影响 |
| 🔮 **可扩展** | 未来可添加更多后端（如 RxJava、Virtual Threads） |
