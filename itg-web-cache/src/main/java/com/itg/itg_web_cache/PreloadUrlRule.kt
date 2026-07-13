package com.itg.itg_web_cache

data class PreloadUrlRule(
    val id: String,
    val url: String,
    val enable: Boolean = true,
    val host: String? = null,
    val pathPrefix: String? = null,
    val scene: String? = null,
    val priority: Int = 0,
    val ttlMs: Long? = null,
    val loginRequired: Boolean = true,
    val wifiOnly: Boolean = false
)
