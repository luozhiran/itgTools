package com.itg.log.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * 步骤计时引擎 — 核心计时逻辑
 *
 * 线程安全，支持并发场景下的步骤计数和计时。
 *
 * 使用方式:
 * ```kotlin
 * val timer = StepTimer("my-test")
 * timer.begin()
 * timer.step("第一步完成")   // → LogEntry(step=1, deltaMs=..., totalMs=...)
 * timer.step("第二步完成")   // → LogEntry(step=2, deltaMs=..., totalMs=...)
 * val entry = timer.mark()  // 标记时间点但不输出
 * ```
 */
class StepTimer(
    /** 测试名称（会作为标题输出） */
    val testName: String
) {
    private val stepCounter = AtomicInteger(0)

    @Volatile private var beginTime = 0L
    @Volatile private var lastStepTime = 0L
    @Volatile private var started = false

    /** 测试开始时间 */
    val startTimeMs: Long get() = beginTime

    /** 测试已运行时长 */
    val elapsedMs: Long
        get() = if (!started) 0L else System.currentTimeMillis() - beginTime

    /** 当前步骤号 */
    val currentStep: Int get() = stepCounter.get()

    /** 是否已启动 */
    val isStarted: Boolean get() = started

    // ==================== 生命周期 ====================

    /**
     * 开始计时 — 重置步骤计数器和所有时间戳
     */
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
     * @param message  步骤描述
     * @param level    日志级别，默认 DEBUG
     * @param tag      日志标签，默认使用测试名称
     */
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
     */
    fun mark(): Long {
        val now = System.currentTimeMillis()
        lastStepTime = now
        return now - beginTime
    }

    /**
     * 重置计时器
     */
    fun reset() {
        started = false
        stepCounter.set(0)
    }
}
