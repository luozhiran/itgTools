package com.itg.log.format

import com.itg.log.core.LogEntry
import com.itg.log.core.LogLevel

/**
 * 日志格式化器接口 — 将 [LogEntry] 转为可读字符串
 *
 * 实现此接口可自定义日志输出格式。
 */
fun interface LogFormatter {
    /**
     * 格式化单条日志
     *
     * @param entry 日志记录
     * @return 格式化后的字符串
     */
    fun format(entry: LogEntry): String
}

/**
 * 默认格式化器 — 简洁紧凑格式
 *
 * 输出示例:
 * ```
 * [#01 D+   0ms T    0ms] 操作完成
 * [#02 D+  52ms T   52ms] 结果: 42 ✅
 * ```
 */
class CompactFormatter(
    /** 步骤序号最小宽度 */
    val stepWidth: Int = 2,
    /** 增量耗时最小宽度 */
    val deltaWidth: Int = 4,
    /** 累计耗时最小宽度 */
    val totalWidth: Int = 5,
    /** 分隔符 */
    val separator: String = " "
) : LogFormatter {

    override fun format(entry: LogEntry): String {
        val sn = entry.step.toString().padStart(stepWidth, '0')
        val d = entry.deltaMs.toString().padStart(deltaWidth, ' ')
        val t = entry.totalMs.toString().padStart(totalWidth, ' ')
        return "[#$sn D+${d}ms T${t}ms]$separator${entry.message}"
    }
}

/**
 * 带时间戳的格式化器
 *
 * 输出示例:
 * ```
 * 12:30:45.123 [#01 D+   0ms T    0ms] 操作完成
 * ```
 */
class TimestampFormatter(
    private val delegate: LogFormatter = CompactFormatter(),
    private val timeFormat: (Long) -> String = { ts ->
        val s = ts / 1000 % 60
        val m = ts / 60000 % 60
        val h = ts / 3600000 % 24
        val ms = ts % 1000
        "%02d:%02d:%02d.%03d".format(h, m, s, ms)
    }
) : LogFormatter {
    override fun format(entry: LogEntry): String {
        return "${timeFormat(entry.timestampMs)} ${delegate.format(entry)}"
    }
}

/**
 * 按日志级别着色的格式化器 — 返回带 ANSI 颜色代码的字符串
 *
 * 适用于支持 ANSI 的终端输出。
 */
class ColoredFormatter(
    private val delegate: LogFormatter = CompactFormatter()
) : LogFormatter {
    override fun format(entry: LogEntry): String {
        val color = when (entry.level) {
            LogLevel.VERBOSE -> "[37m"
            LogLevel.DEBUG   -> "[36m"
            LogLevel.INFO    -> "[32m"
            LogLevel.WARN    -> "[33m"
            LogLevel.ERROR   -> "[31m"
        }
        return "$color${delegate.format(entry)}[0m"
    }
}
