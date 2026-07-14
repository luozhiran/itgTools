package com.itg.log.core

/**
 * 一条日志记录 — 包含步骤序号、计时信息和消息正文
 *
 * @param step     步骤序号（从 1 开始）
 * @param deltaMs  距上一步耗时（毫秒）
 * @param totalMs  距测试开始累计耗时（毫秒）
 * @param level    日志级别
 * @param tag      日志标签
 * @param message  日志消息正文
 * @param timestampMs 日志记录时的系统时间戳
 */
data class LogEntry(
    val step: Int,
    val deltaMs: Long,
    val totalMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)
