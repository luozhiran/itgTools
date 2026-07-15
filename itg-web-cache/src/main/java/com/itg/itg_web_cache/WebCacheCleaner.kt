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
            clearHttpCacheInternal(eventListener, logger, onComplete) {
                val webView = WebView(context.applicationContext)
                try {
                    clearHttpCacheOnWebView(webView)
                    webView.clearFormData()
                    webView.clearHistory()
                } finally {
                    webView.destroy()
                }
            }
        }
    }

    /**
     * Clears WebView HTTP cache by reusing an existing WebView instance.
     *
     * The caller owns the passed [webView]. This method does not stop loading, clear history, or destroy it.
     */
    fun clearHttpCache(
        webView: WebView,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runOnMain {
            clearHttpCacheInternal(eventListener, logger, onComplete) {
                clearHttpCacheOnWebView(webView)
            }
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
            var siteDataSuccess = true
            runCatching {
                WebStorage.getInstance().deleteAllData()
            }.onFailure {
                siteDataSuccess = false
                WebCacheSafeCallbacks.log(logger, "Failed to clear WebView storage data.", it)
            }

            runCatching {
                CookieManager.getInstance().removeAllCookies {
                    runCatching { CookieManager.getInstance().flush() }.onFailure {
                        siteDataSuccess = false
                        WebCacheSafeCallbacks.log(logger, "Failed to flush WebView cookies.", it)
                    }
                    clearHttpCache(context, eventListener, logger) { httpSuccess ->
                        completeSiteDataClear(siteDataSuccess && httpSuccess, eventListener, logger, onComplete)
                    }
                }
            }.onFailure {
                siteDataSuccess = false
                WebCacheSafeCallbacks.log(logger, "Failed to clear WebView cookies.", it)
                clearHttpCache(context, eventListener, logger) { httpSuccess ->
                    completeSiteDataClear(siteDataSuccess && httpSuccess, eventListener, logger, onComplete)
                }
            }
        }
    }

    private fun completeSiteDataClear(
        success: Boolean,
        eventListener: WebCacheEventListener,
        logger: WebCacheLogger,
        onComplete: ((Boolean) -> Unit)?
    ) {
        emit(
            eventListener,
            logger,
            WebCacheEvent(
                name = if (success) "web_cache_clear_finish" else "web_cache_clear_error",
                reason = "site_data"
            )
        )
        WebCacheSafeCallbacks.complete(onComplete, success, logger)
    }

    private fun clearHttpCacheInternal(
        eventListener: WebCacheEventListener,
        logger: WebCacheLogger,
        onComplete: ((Boolean) -> Unit)?,
        clearAction: () -> Unit
    ) {
        emit(eventListener, logger, WebCacheEvent(name = "web_cache_clear_start", reason = "http_cache"))
        val success = runCatching {
            clearAction()
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

    private fun clearHttpCacheOnWebView(webView: WebView) {
        webView.clearCache(true)
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
