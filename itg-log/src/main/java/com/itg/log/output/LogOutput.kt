package com.itg.log.output

import com.itg.log.core.LogEntry

/**
 * 日志输出目标接口
 *
 * 实现此接口可将日志输出到任意目标（logcat / 文件 / 网络 / UI 回调等）。
 */
fun interface LogOutput {
    /** 输出一条日志 */
    fun write(entry: LogEntry, formatted: String)
}

/**
 * 将日志写入 Android Logcat
 *
 * @param defaultTag 默认日志标签
 */
class LogcatOutput(
    private val defaultTag: String = "ItgLog"
) : LogOutput {
    override fun write(entry: LogEntry, formatted: String) {
        val tag = entry.tag.ifBlank { defaultTag }
        when (entry.level) {
            com.itg.log.core.LogLevel.VERBOSE -> android.util.Log.v(tag, formatted)
            com.itg.log.core.LogLevel.DEBUG   -> android.util.Log.d(tag, formatted)
            com.itg.log.core.LogLevel.INFO    -> android.util.Log.i(tag, formatted)
            com.itg.log.core.LogLevel.WARN    -> android.util.Log.w(tag, formatted)
            com.itg.log.core.LogLevel.ERROR   -> android.util.Log.e(tag, formatted)
        }
    }
}

/**
 * 通过回调输出日志 — 适用于 UI 实时显示
 *
 * @param callback 接收格式化后的日志字符串
 */
class CallbackOutput(
    private val callback: (String) -> Unit
) : LogOutput {
    override fun write(entry: LogEntry, formatted: String) {
        callback(formatted)
    }
}

/**
 * 组合输出 — 同时输出到多个目标
 *
 * 使用示例:
 * ```kotlin
 * val output = CompositeOutput(
 *     LogcatOutput(),
 *     CallbackOutput { uiTextView.append(it) }
 * )
 * ```
 */
class CompositeOutput(
    private val outputs: List<LogOutput>
) : LogOutput {
    constructor(vararg outputs: LogOutput) : this(outputs.toList())

    override fun write(entry: LogEntry, formatted: String) {
        outputs.forEach { it.write(entry, formatted) }
    }
}
