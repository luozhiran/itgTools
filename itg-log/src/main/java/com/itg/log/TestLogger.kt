package com.itg.log

import com.itg.log.config.LogConfig
import com.itg.log.config.configureLogger
import com.itg.log.core.LogLevel
import com.itg.log.core.StepTimer

/**
 * 测试日志记录器 — 带步骤序号和计时的日志门面
 *
 * ## 基本使用
 * ```kotlin
 * val logger = TestLogger("MyTest") {
 *     outputToLogcat()
 *     outputToCallback { textView.append(it + "\n") }
 * }
 *
 * logger.beginTest("文件读写测试")
 * logger.i("开始读取文件")
 * logger.d("读取完成, size=1024")
 * logger.ok("测试通过")
 * logger.endTest()   // 输出总耗时
 * ```
 *
 * ## 输出示例
 * ```
 * ===== 文件读写测试 =====
 * [#01 D+   0ms T    0ms] 开始读取文件
 * [#02 D+  15ms T   15ms] 读取完成, size=1024 ✅
 * ----- 文件读写测试 结束, 总耗时: 18ms -----
 * ```
 *
 * ## 架构
 *
 * ```
 * TestLogger (门面)
 *   ├── StepTimer     (计时引擎)
 *   ├── LogFormatter  (格式化策略, 可替换)
 *   └── List<LogOutput> (输出目标, 可多个)
 *         ├── LogcatOutput
 *         ├── CallbackOutput
 *         └── (自定义实现)
 * ```
 */
class TestLogger private constructor(
    private val config: LogConfig,
    private val timer: StepTimer
) {
    /** 当前测试标题（beginTest 时设置） */
    private var currentTitle: String = timer.testName

    // ==================== 构造器 ====================

    /**
     * @param name   测试名称（显示在标题中）
     * @param config 日志配置
     */
    constructor(name: String, config: LogConfig = LogConfig.builder().build())
        : this(config, StepTimer(name))

    /**
     * @param name   测试名称
     * @param block  配置 DSL
     */
    constructor(name: String, block: LogConfig.Builder.() -> Unit)
        : this(name, configureLogger(block))

    // ==================== 生命周期 ====================

    /** 开始一项测试，输出标题并重置计时器。线程安全 */
    @Synchronized
    fun beginTest(name: String? = null) {
        timer.begin()
        currentTitle = name ?: timer.testName
        val title = config.headerFormatter(currentTitle)
        rawOutput(title, LogLevel.INFO)
    }

    /** 结束测试，输出总耗时。线程安全 */
    @Synchronized
    fun endTest(): Long {
        val elapsed = timer.elapsedMs
        rawOutput(
            "----- $currentTitle 结束, 总耗时: ${elapsed}ms -----",
            LogLevel.INFO
        )
        return elapsed
    }

    /** 当前测试标题 */
    val title: String get() = currentTitle

    // ==================== 按级别输出 ====================

    fun v(msg: String) = log(msg, LogLevel.VERBOSE)
    fun d(msg: String) = log(msg, LogLevel.DEBUG)
    fun i(msg: String) = log(msg, LogLevel.INFO)
    fun w(msg: String) = log(msg, LogLevel.WARN)
    fun e(msg: String) = log(msg, LogLevel.ERROR)

    /** 成功标记 */
    fun ok(msg: String) = log("$msg ✅", LogLevel.INFO)

    /** 失败标记 */
    fun fail(msg: String) = log("$msg ❌", LogLevel.ERROR)

    // ==================== 步骤记录 ====================

    /**
     * 记录一个步骤，返回 [StepRecord] 可用于后续分析
     */
    fun step(msg: String, level: LogLevel = LogLevel.DEBUG): StepRecord {
        val entry = timer.step(msg, level, config.tag)
        logEntry(entry)
        return StepRecord(entry)
    }

    /** 标记当前时间点，不增加步骤计数，返回 ms */
    fun mark(): Long = timer.mark()

    // ==================== 查询 ====================

    val currentStep: Int get() = timer.currentStep
    val elapsedMs: Long get() = timer.elapsedMs
    val isStarted: Boolean get() = timer.isStarted

    // ==================== 内部方法 ====================

    private fun log(msg: String, level: LogLevel) {
        if (level.ordinal < config.minLevel.ordinal) return
        val entry = timer.step(msg, level, config.tag)
        logEntry(entry)
    }

    private fun logEntry(entry: com.itg.log.core.LogEntry) {
        val formatted = config.formatter.format(entry)
        config.outputs.forEach { it.write(entry, formatted) }
    }

    private fun rawOutput(msg: String, level: LogLevel) {
        val entry = com.itg.log.core.LogEntry(
            step = 0,
            deltaMs = 0,
            totalMs = timer.elapsedMs,
            level = level,
            tag = config.tag,
            message = msg
        )
        config.outputs.forEach { it.write(entry, msg) }
    }
}

/**
 * 步骤记录 — 包含完整的 [LogEntry] 信息，支持后续分析
 */
data class StepRecord(
    val step: Int,
    val deltaMs: Long,
    val totalMs: Long,
    val message: String
) {
    constructor(entry: com.itg.log.core.LogEntry) : this(
        step = entry.step,
        deltaMs = entry.deltaMs,
        totalMs = entry.totalMs,
        message = entry.message
    )
}
