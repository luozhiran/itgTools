package com.itg.itg_web_cache

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object WebCacheCleaner {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun clearByPolicy(
        context: Context,
        policy: WebCacheClearPolicy,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        when (policy) {
            WebCacheClearPolicy.NONE -> WebCacheSafeCallbacks.complete(onComplete, true, logger)
            WebCacheClearPolicy.HTTP_CACHE -> clearHttpCache(context, eventListener, logger, onComplete)
            WebCacheClearPolicy.SITE_DATA -> clearSiteData(context, eventListener, logger, onComplete)
        }
    }

    fun clearHttpCache(
        context: Context,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runOnMain {
            emit(eventListener, logger, WebCacheEvent(name = "web_cache_clear_start", reason = "http_cache"))
            val success = runCatching {
                val webView = WebView(context.applicationContext)
                webView.clearCache(true)
                webView.clearFormData()
                webView.clearHistory()
                webView.destroy()
            }.onFailure {
                WebCacheSafeCallbacks.log(logger, "Failed to clear WebView HTTP cache.", it)
            }.isSuccess
            emit(
                eventListener,
                logger,
                WebCacheEvent(
                    name = if (success) "web_cache_clear_finish" else "web_cache_clear_error",
                    reason = "http_cache"
                )
            )
            WebCacheSafeCallbacks.complete(onComplete, success, logger)
        }
    }

    fun clearSiteData(
        context: Context,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runOnMain {
            emit(eventListener, logger, WebCacheEvent(name = "web_cache_clear_start", reason = "site_data"))
            var success = true
            runCatching {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies {
                    runCatching { CookieManager.getInstance().flush() }
                }
            }.onFailure {
                success = false
                WebCacheSafeCallbacks.log(logger, "Failed to clear WebView site data.", it)
            }
            clearHttpCache(context, eventListener, logger) { httpSuccess ->
                val finalSuccess = success && httpSuccess
                emit(
                    eventListener,
                    logger,
                    WebCacheEvent(
                        name = if (finalSuccess) "web_cache_clear_finish" else "web_cache_clear_error",
                        reason = "site_data"
                    )
                )
                WebCacheSafeCallbacks.complete(onComplete, finalSuccess, logger)
            }
        }
    }

    private fun emit(
        eventListener: WebCacheEventListener,
        logger: WebCacheLogger,
        event: WebCacheEvent
    ) {
        WebCacheSafeCallbacks.emit(eventListener, event, logger)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}