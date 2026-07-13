package com.itg.concurrent

import java.util.*

/**
 * 并发工厂 — 管理后端的注册、切换和生命周期
 *
 * 核心功能:
 * - **自动检测**: 根据 classpath 自动选择可用后端（优先协程，fallback 线程池）
 * - **全局切换**: 一行代码切换所有分发器的后端
 * - **混合模式**: 不同类型的任务使用不同的后端（如 IO→协程, COMPUTE→线程池）
 * - **手动注册**: 支持自定义分发器覆盖默认实现
 *
 * 使用示例:
 * ```kotlin
 * // Application.onCreate()
 *
 * // 方式1: 全局切换
 * ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
 *
 * // 方式2: 混合模式 — IO 高并发用协程，计算用线程池
 * ConcurrentFactory.useMixed(mapOf(
 *     DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
 *     DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL
 * ))
 *
 * // 方式3: 不配置，自动检测 classpath
 * // 有协程 → 自动用协程
 * // 没协程 → fallback 线程池
 * ```
 */
object ConcurrentFactory {

    enum class BackendType {
        /** 线程池后端 (itg-thread-pools) */
        THREAD_POOL,
        /** 协程后端 (itg-coroutine-pools) */
        COROUTINE,
        /** 自动检测 classpath */
        AUTO
    }

    @Volatile
    private var _currentBackend: BackendType = BackendType.AUTO

    /** 当前全局后端类型 */
    val currentBackend: BackendType get() = _currentBackend

    /** 分发器注册表（手动注册覆盖默认） */
    private val registries = EnumMap<DispatcherType, TaskDispatcher>(DispatcherType::class.java)

    /** 后端的可用性缓存 */
    @Volatile
    private var coroutineAvailable: Boolean? = null

    @Volatile
    private var threadPoolAvailable: Boolean? = null

    // ==================== 获取分发器 ====================

    /**
     * 获取指定类型的分发器
     *
     * 查找顺序:
     * 1. 用户手动注册的分发器（[register]）
     * 2. 根据 [currentBackend] 选择默认实现
     * 3. 根据 classpath 可用性自动选择
     */
    fun getDispatcher(type: DispatcherType): TaskDispatcher {
        // 1. 手动注册优先
        registries[type]?.let { return it }

        // 2-3. 根据后端解析
        return resolveDefault(type)
    }

    // ==================== 后端切换 ====================

    /**
     * 全局切换后端
     *
     * 清空手动注册缓存，所有分发器使用新后端。
     *
     * @param backend 目标后端类型（COROUTINE / THREAD_POOL / AUTO）
     */
    fun switchTo(backend: BackendType) {
        _currentBackend = backend
        registries.clear()
    }

    /**
     * 混合模式 — 按任务类型分配后端
     *
     * ```kotlin
     * ConcurrentFactory.useMixed(mapOf(
     *     DispatcherType.IO      to BackendType.COROUTINE,   // IO → 协程高并发
     *     DispatcherType.COMPUTE to BackendType.THREAD_POOL, // 计算 → 线程池可预测
     *     DispatcherType.MAIN    to BackendType.COROUTINE,   // 主线程无所谓
     * ))
     * // SINGLE 和 BACKGROUND 未指定，走 currentBackend 或自动检测
     * ```
     */
    fun useMixed(config: Map<DispatcherType, BackendType>) {
        config.forEach { (type, backend) ->
            registries[type] = createDispatcher(type, backend)
        }
    }

    /**
     * 手动注册自定义分发器（覆盖默认）
     */
    fun register(type: DispatcherType, dispatcher: TaskDispatcher) {
        registries[type] = dispatcher
    }

    // ==================== 可用性检测 ====================

    /**
     * 检测协程后端是否可用
     */
    fun isCoroutineAvailable(): Boolean {
        if (coroutineAvailable == null) {
            coroutineAvailable = try {
                // 检测 itg-coroutine-pools 是否在 classpath 上
                Class.forName("com.itg.itg_coroutine_pools.executor.CoroutineExecutor")
                true
            } catch (_: ClassNotFoundException) { false }
        }
        return coroutineAvailable!!
    }

    /**
     * 检测线程池后端是否可用
     */
    fun isThreadPoolAvailable(): Boolean {
        if (threadPoolAvailable == null) {
            threadPoolAvailable = try {
                Class.forName("com.itg.itg_thread_pools.manager.ThreadPoolManager")
                true
            } catch (_: ClassNotFoundException) { false }
        }
        return threadPoolAvailable!!
    }

    /**
     * 获取当前可用的后端列表（用于 UI 展示或调试）
     */
    fun getAvailableBackends(): List<BackendType> {
        val list = mutableListOf<BackendType>()
        if (isCoroutineAvailable()) list.add(BackendType.COROUTINE)
        if (isThreadPoolAvailable()) list.add(BackendType.THREAD_POOL)
        return list
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭所有已注册的分发器，释放资源
     */
    fun shutdown() {
        registries.values.forEach {
            if (it is AutoCloseable) {
                try { it.close() } catch (_: Exception) { }
            }
        }
        registries.clear()
    }

    // ==================== 内部方法 ====================

    private fun resolveDefault(type: DispatcherType): TaskDispatcher {
        return when (_currentBackend) {
            BackendType.THREAD_POOL -> createThreadPoolDispatcher(type)
            BackendType.COROUTINE   -> createCoroutineDispatcher(type)
            BackendType.AUTO        -> {
                // 自动检测: 优先协程，fallback 线程池
                if (isCoroutineAvailable()) createCoroutineDispatcher(type)
                else if (isThreadPoolAvailable()) createThreadPoolDispatcher(type)
                else throw IllegalStateException(
                    "No concurrent backend available. " +
                    "Add itg-coroutine-pools or itg-thread-pools as a dependency."
                )
            }
        }
    }

    private fun createDispatcher(type: DispatcherType, backend: BackendType): TaskDispatcher {
        return when (backend) {
            BackendType.THREAD_POOL -> createThreadPoolDispatcher(type)
            BackendType.COROUTINE   -> createCoroutineDispatcher(type)
            BackendType.AUTO        -> resolveDefault(type)
        }
    }

    private fun createThreadPoolDispatcher(type: DispatcherType): TaskDispatcher {
        return com.itg.concurrent.backend.ThreadPoolAdapter.create(type)
    }

    private fun createCoroutineDispatcher(type: DispatcherType): TaskDispatcher {
        return com.itg.concurrent.backend.CoroutineAdapter.create(type)
    }
}
