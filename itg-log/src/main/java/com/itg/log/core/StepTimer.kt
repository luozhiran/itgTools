package com.itg.log.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * 步骤计时引擎 — 线程安全的核心计时逻辑
 *
 * ## 线程安全保证
 *
 * 所有写操作 ([begin], [step], [mark], [reset]) 通过内置锁同步，
 * 确保多线程并发调用时步骤序号严格递增、计时数据一致。
 *
 * 典型并发场景:
 * ```
 * Thread-1 (IO线程):    step("A") ← 获得锁, 步骤#1, 计时正确
 * Thread-2 (Compute):   step("B") ← 等待锁, 步骤#2, delta 基于 #1 的 lastStepTime
 * ```
 *
 * ## 使用方式
 * ```kotlin
 * val timer = StepTimer("my-test")
 * timer.begin()
 * timer.step("第一步完成")   // → LogEntry(step=1)
 * timer.step("第二步完成")   // → LogEntry(step=2)
 * ```
 */
class StepTimer(
    /** 测试名称 */
    val testName: String
) {
    // stepCounter 使用 AtomicInteger，读取时无需锁
    private val stepCounter = AtomicInteger(0)

    // 这三个字段由锁保护，不使用 @Volatile（锁已保证可见性）
    private var beginTime = 0L
    private var lastStepTime = 0L
    private var started = false

    /** 测试开始时间（无锁读取 — 允许微弱不一致） */
    val startTimeMs: Long get() = beginTime

    /** 测试已运行时长（无锁读取） */
    val elapsedMs: Long
        get() = if (!started) 0L else System.currentTimeMillis() - beginTime

    /** 当前步骤号（AtomicInteger 保证原子性） */
    val currentStep: Int get() = stepCounter.get()

    /** 是否已启动 */
    val isStarted: Boolean get() = started

    // ==================== 生命周期（synchronized） ====================

    /**
     * 开始计时 — 重置步骤计数器和所有时间戳
     *
     * 线程安全：内部加锁，多线程同时调用时只有第一个生效。
     */
    @Synchronized
    fun begin() {
        val now = System.currentTimeMillis()
        beginTime = now
        lastStepTime = now
        stepCounter.set(0)
        started = true
    }

    /**
     * 记录一个步骤，返回包含计时信息的 [LogEntry]
     *
     * 线程安全：步骤计数器递增 + 计时采样在同一个锁内完成，
     * 确保步骤序号与时间戳严格对应，不会出现乱序或计时跳跃。
     *
     * @param message  步骤描述
     * @param level    日志级别，默认 DEBUG
     * @param tag      日志标签，默认使用测试名称
     */
    @Synchronized
    fun step(
        message: String,
        level: LogLevel = LogLevel.DEBUG,
        tag: String = testName
    ): LogEntry {
        val now = System.currentTimeMillis()
        val step = stepCounter.incrementAndGet()
        val delta = now - lastStepTime
        val total = now - beginTime
        lastStepTime = now
        return LogEntry(
            step = step,
            deltaMs = delta,
            totalMs = total,
            level = level,
            tag = tag,
            message = message,
            timestampMs = now
        )
    }

    /**
     * 标记当前时间点（不增加步骤计数），返回距开始时间的毫秒数
     *
     * 线程安全：更新 lastStepTime 在锁内完成。
     */
    @Synchronized
    fun mark(): Long {
        val now = System.currentTimeMillis()
        lastStepTime = now
        return now - beginTime
    }

    /**
     * 重置计时器
     *
     * 线程安全：与 [begin] / [step] 互斥。
     */
    @Synchronized
    fun reset() {
        started = false
        stepCounter.set(0)
    }
}
