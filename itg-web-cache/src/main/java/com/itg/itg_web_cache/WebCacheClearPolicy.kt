package com.itg.itg_web_cache

enum class WebCacheClearPolicy(val configValue: String) {
    NONE("none"),
    HTTP_CACHE("http_cache"),
    SITE_DATA("site_data");

    companion object {
        fun fromConfig(value: String?): WebCacheClearPolicy {
            return when (value?.trim()?.lowercase()) {
                HTTP_CACHE.configValue -> HTTP_CACHE
                SITE_DATA.configValue -> SITE_DATA
                else -> NONE
            }
        }
    }
}
