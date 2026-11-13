package com.itg.itg_file.core

import java.io.IOException
import kotlinx.coroutines.CancellationException

/** 公开入口统一通知失败；私有实现显式接收 [Reporter]。 */
internal object FileFailureCallbacks {
    internal class Reporter private constructor(val enabled: Boolean) {

        fun <T> fail(fallback: T, step: String): T {
            if (enabled) throw ReportedFailure(IOException(step))
            return fallback
        }

        fun rethrow(error: Throwable) {
            if (error is CancellationException) throw error
            if (error is Error) throw error
            if (!enabled) return
            if (error is ReportedFailure) throw error
            throw ReportedFailure(
                error as? Exception ?: IOException("文件操作异常：${error.message}", error)
            )
        }

        fun <T> invokeUserCallback(block: () -> T): T = block()

        companion object {
            val NONE = Reporter(false)
            fun forCallback(callback: ((Exception) -> Unit)?): Reporter =
                if (callback == null) NONE else Reporter(true)
        }
    }

    private class ReportedFailure(val original: Exception) : RuntimeException(original)

    fun <T> run(
        operation: String,
        target: String,
        onFailure: ((Exception) -> Unit)?,
        failed: (T) -> Boolean,
        fallback: T,
        block: (Reporter) -> T
    ): T {
        val reporter = Reporter.forCallback(onFailure)
        return try {
            val result = block(reporter)
            if (reporter.enabled && failed(result)) {
                notify(onFailure, IllegalStateException("$operation 未报告具体失败步骤：$target"))
            }
            result
        } catch (error: ReportedFailure) {
            notify(onFailure, error.original)
            fallback
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            notify(onFailure, error)
            fallback
        }
    }

    /** 异步回调捕获异常时保留协程取消传播。 */
    fun rethrowCancellation(error: Throwable) {
        if (error is CancellationException) throw error
    }

    private fun notify(callback: ((Exception) -> Unit)?, error: Exception) {
        try {
            callback?.invoke(error)
        } catch (callbackError: Exception) {
            if (callbackError is CancellationException) throw callbackError
            callbackError.printStackTrace()
        }
    }
}
