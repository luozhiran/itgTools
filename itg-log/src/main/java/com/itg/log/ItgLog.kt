package com.itg.log

import java.util.concurrent.atomic.AtomicBoolean

/**
 * itg-log 全局控制
 *
 * ## 生产环境关闭
 * ```kotlin
 * // Application.onCreate()
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         ItgLog.globalEnabled = BuildConfig.DEBUG
 *     }
 * }
 * ```
 *
 * 全局关闭后，所有 [TestLogger] 实例的日志输出被静默丢弃，
 * 不会产生任何日志字符串拼接、格式化或 I/O。
 */
object ItgLog {

    /**
     * 全局开关 — 设为 false 后所有日志静默
     *
     * 线程安全：使用 [AtomicBoolean] 保证多线程可见性。
     * 关闭后 [StepTimer.step] 仍返回 [LogEntry]（用于数据采集），但 [TestLogger] 不会产生输出。
     */
    @JvmStatic
    var globalEnabled: Boolean = true
}
