package com.itg.itg_web_cache

fun interface WebCacheConfigProvider {
    fun getConfig(): WebCacheConfig
}

fun interface WebCacheStateProvider {
    fun getState(): WebCacheRuntimeState
}

interface WebCacheLogger {
    fun log(message: String, throwable: Throwable? = null)
}

object NoOpWebCacheLogger : WebCacheLogger {
    override fun log(message: String, throwable: Throwable?) = Unit
}

fun interface WebCacheEventListener {
    fun onEvent(event: WebCacheEvent)
}

object NoOpWebCacheEventListener : WebCacheEventListener {
    override fun onEvent(event: WebCacheEvent) = Unit
}
