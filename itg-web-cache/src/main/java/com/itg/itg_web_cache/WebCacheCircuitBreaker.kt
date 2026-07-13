package com.itg.itg_web_cache

class WebCacheCircuitBreaker(
    private val maxFailuresPerUrl: Int = 3,
    private val blockDurationMs: Long = 30 * 60 * 1_000L,
    private val maxTotalFailuresPerLaunch: Int = 5
) {
    private val failuresByKey = mutableMapOf<String, Int>()
    private val blockedUntilByKey = mutableMapOf<String, Long>()
    private var totalFailures = 0

    fun isBlocked(rule: PreloadUrlRule, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (totalFailures >= maxTotalFailuresPerLaunch) return true
        val key = key(rule)
        val blockedUntil = blockedUntilByKey[key] ?: return false
        if (nowMs >= blockedUntil) {
            blockedUntilByKey.remove(key)
            failuresByKey.remove(key)
            return false
        }
        return true
    }

    fun recordSuccess(rule: PreloadUrlRule) {
        failuresByKey.remove(key(rule))
        blockedUntilByKey.remove(key(rule))
    }

    fun recordFailure(rule: PreloadUrlRule, nowMs: Long = System.currentTimeMillis()) {
        totalFailures++
        val key = key(rule)
        val failures = (failuresByKey[key] ?: 0) + 1
        failuresByKey[key] = failures
        if (failures >= maxFailuresPerUrl) {
            blockedUntilByKey[key] = nowMs + blockDurationMs
        }
    }

    fun resetLaunchFailures() {
        totalFailures = 0
    }

    private fun key(rule: PreloadUrlRule): String = rule.id.ifBlank { rule.url }
}
