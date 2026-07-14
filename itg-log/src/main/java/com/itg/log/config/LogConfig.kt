package com.itg.log.config

import com.itg.log.core.LogLevel
import com.itg.log.format.CompactFormatter
import com.itg.log.format.LogFormatter
import com.itg.log.output.*

/**
 * TestLogger 配置 — Builder 模式
 *
 * 使用示例:
 * ```kotlin
 * val logger = TestLogger.configure {
 *     tag = "MyTest"
 *     minLevel = LogLevel.DEBUG
 *     addOutput(LogcatOutput())
 *     addOutput(CallbackOutput { textView.append(it) })
 *     headerFormat { "═══ $it ═══" }
 * }
 * ```
 */
class LogConfig private constructor(
    val tag: String,
    val minLevel: LogLevel,
    val enabled: Boolean,
    val formatter: LogFormatter,
    val outputs: List<LogOutput>,
    val headerFormatter: (String) -> String
) {
    /** Builder */
    class Builder {
        var tag: String = "TestLog"
        var minLevel: LogLevel = LogLevel.DEBUG
        var enabled: Boolean = true
        var formatter: LogFormatter = CompactFormatter()
        private val outputs = mutableListOf<LogOutput>()
        var headerFormatter: (String) -> String = { "===== $it =====" }

        /** 添加输出目标 */
        fun addOutput(output: LogOutput): Builder {
            outputs.add(output)
            return this
        }

        /** 输出到 logcat */
        fun outputToLogcat(tag: String = "ItgLog"): Builder {
            outputs.add(LogcatOutput(tag))
            return this
        }

        /** 输出到回调（UI 显示等） */
        fun outputToCallback(callback: (String) -> Unit): Builder {
            outputs.add(CallbackOutput(callback))
            return this
        }

        fun build(): LogConfig {
            val finalOutputs = if (outputs.isEmpty()) {
                listOf(LogcatOutput(tag))
            } else {
                outputs.toList()
            }
            return LogConfig(tag, minLevel, enabled, formatter, finalOutputs, headerFormatter)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/** DSL 入口 */
fun configureLogger(block: LogConfig.Builder.() -> Unit): LogConfig {
    return LogConfig.builder().apply(block).build()
}
