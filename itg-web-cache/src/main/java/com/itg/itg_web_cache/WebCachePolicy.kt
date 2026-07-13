package com.itg.itg_web_cache

data class WebCachePolicy(
    val enabled: Boolean,
    val cacheMode: CacheModeOption = CacheModeOption.DEFAULT,
    val reason: String
)
