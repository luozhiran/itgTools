package com.itg.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Future

/**
 * 任务分发器 — 中间件的核心抽象
 *
 * 所有任务提交最终都通过此接口执行。
 * 线程池和协程两种后端各自实现此接口。
 *
 * 实现类:
 * - [com.itg.concurrent.backend.ThreadPoolAdapter] — 基于 itg-thread-pools
 * - [com.itg.concurrent.backend.CoroutineAdapter] — 基于 itg-coroutine-pools
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
 * 协程原生分发器 — 扩展 [TaskDispatcher]，支持 suspend 函数
 *
 * 仅协程后端实现此接口。
 */
interface CoroutineTaskDispatcher : TaskDispatcher {
    /** 在分发器上执行 suspend 函数（非阻塞） */
    suspend fun <T> executeSuspend(task: suspend () -> T): T

    /** 获取此分发器对应的 [CoroutineDispatcher] */
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
