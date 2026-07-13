package com.itg.itg_web_cache

import android.webkit.WebSettings

/**
 * Cache modes exposed by this library. The enum keeps dynamic config values
 * stable and maps them to Android WebView cache constants only at the edge.
 */
enum class CacheModeOption(val configValue: String) {
    DEFAULT("default"),
    CACHE_ELSE_NETWORK("cache_else_network"),
    NO_CACHE("no_cache");

    fun toWebSettingsCacheMode(): Int {
        return when (this) {
            DEFAULT -> WebSettings.LOAD_DEFAULT
            CACHE_ELSE_NETWORK -> WebSettings.LOAD_CACHE_ELSE_NETWORK
            NO_CACHE -> WebSettings.LOAD_NO_CACHE
        }
    }

    companion object {
        fun fromConfig(value: String?): CacheModeOption {
            return when (value?.trim()?.lowercase()) {
                CACHE_ELSE_NETWORK.configValue -> CACHE_ELSE_NETWORK
                NO_CACHE.configValue -> NO_CACHE
                else -> DEFAULT
            }
        }
    }
}
