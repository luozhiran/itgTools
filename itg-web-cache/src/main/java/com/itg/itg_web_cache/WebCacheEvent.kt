package com.itg.itg_web_cache

data class WebCacheEvent(
    val name: String,
    val url: String? = null,
    val ruleId: String? = null,
    val scene: String? = null,
    val reason: String? = null,
    val cacheMode: CacheModeOption? = null,
    val elapsedMs: Long? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
