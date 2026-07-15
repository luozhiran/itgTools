package com.itg.itg_web_cache

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object WebCacheCleaner {
    private const val DEFAULT_SAFE_CLEAR_MAX_WAIT_MS = 30_000L
    private const val SAFE_CLEAR_RETRY_DELAY_MS = 500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isIdleClearRunning = false

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

    fun clearByPolicyWhenIdle(
        context: Context,
        policy: WebCacheClearPolicy,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null,
        maxWaitMs: Long = DEFAULT_SAFE_CLEAR_MAX_WAIT_MS
    ) {
        when (policy) {
            WebCacheClearPolicy.NONE -> WebCacheSafeCallbacks.complete(onComplete, true, logger)
            WebCacheClearPolicy.HTTP_CACHE -> clearHttpCacheWhenIdle(
                context,
                eventListener,
                logger,
                onComplete,
                maxWaitMs
            )
            WebCacheClearPolicy.SITE_DATA -> clearSiteDataWhenIdle(
                context,
                eventListener,
                logger,
                onComplete,
                maxWaitMs
            )
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

    fun clearHttpCacheWhenIdle(
        context: Context,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null,
        maxWaitMs: Long = DEFAULT_SAFE_CLEAR_MAX_WAIT_MS
    ) {
        val appContext = context.applicationContext
        runWhenSafeToClear(
            reason = "http_cache",
            eventListener = eventListener,
            logger = logger,
            onComplete = onComplete,
            maxWaitMs = maxWaitMs
        ) { complete ->
            clearHttpCache(appContext, eventListener, logger, complete)
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

    fun clearSiteDataWhenIdle(
        context: Context,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger,
        onComplete: ((Boolean) -> Unit)? = null,
        maxWaitMs: Long = DEFAULT_SAFE_CLEAR_MAX_WAIT_MS
    ) {
        val appContext = context.applicationContext
        runWhenSafeToClear(
            reason = "site_data",
            eventListener = eventListener,
            logger = logger,
            onComplete = onComplete,
            maxWaitMs = maxWaitMs
        ) { complete ->
            clearSiteData(appContext, eventListener, logger, complete)
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

    private fun runWhenSafeToClear(
        reason: String,
        eventListener: WebCacheEventListener,
        logger: WebCacheLogger,
        onComplete: ((Boolean) -> Unit)?,
        maxWaitMs: Long,
        clearAction: (((Boolean) -> Unit)?) -> Unit
    ) {
        val startMs = System.currentTimeMillis()
        runOnMain {
            scheduleSafeClearAttempt(
                reason = reason,
                eventListener = eventListener,
                logger = logger,
                onComplete = onComplete,
                maxWaitMs = maxWaitMs.coerceAtLeast(0L),
                startMs = startMs,
                waitEventEmitted = false,
                clearAction = clearAction
            )
        }
    }

    private fun scheduleSafeClearAttempt(
        reason: String,
        eventListener: WebCacheEventListener,
        logger: WebCacheLogger,
        onComplete: ((Boolean) -> Unit)?,
        maxWaitMs: Long,
        startMs: Long,
        waitEventEmitted: Boolean,
        clearAction: (((Boolean) -> Unit)?) -> Unit
    ) {
        runOnMainQueueIdle {
            val waitReason = currentUnsafeClearReason()
            val canWaitMore = System.currentTimeMillis() - startMs < maxWaitMs
            if (waitReason != null) {
                if (canWaitMore) {
                    if (!waitEventEmitted) {
                        emit(
                            eventListener,
                            logger,
                            WebCacheEvent(name = "web_cache_clear_wait", reason = waitReason)
                        )
                    }
                    mainHandler.postDelayed(
                        {
                            scheduleSafeClearAttempt(
                                reason = reason,
                                eventListener = eventListener,
                                logger = logger,
                                onComplete = onComplete,
                                maxWaitMs = maxWaitMs,
                                startMs = startMs,
                                waitEventEmitted = true,
                                clearAction = clearAction
                            )
                        },
                        SAFE_CLEAR_RETRY_DELAY_MS
                    )
                } else {
                    emit(
                        eventListener,
                        logger,
                        WebCacheEvent(name = "web_cache_clear_skip", reason = waitReason)
                    )
                    WebCacheSafeCallbacks.complete(onComplete, false, logger)
                }
                return@runOnMainQueueIdle
            }
            startSafeClear(reason, logger, onComplete, clearAction)
        }
    }

    private fun startSafeClear(
        reason: String,
        logger: WebCacheLogger,
        onComplete: ((Boolean) -> Unit)?,
        clearAction: (((Boolean) -> Unit)?) -> Unit
    ) {
        isIdleClearRunning = true
        val complete: (Boolean) -> Unit = { success ->
            isIdleClearRunning = false
            onComplete?.invoke(success)
        }
        runCatching {
            clearAction(complete)
        }.onFailure {
            isIdleClearRunning = false
            WebCacheSafeCallbacks.log(logger, "Failed to start safe WebView cache clear: $reason", it)
            WebCacheSafeCallbacks.complete(onComplete, false, logger)
        }
    }

    private fun currentUnsafeClearReason(): String? {
        return when {
            isIdleClearRunning -> "clear_running"
            WebCacheRuntime.hasActiveContainerLoads() -> "container_loading"
            else -> null
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

    private fun runOnMainQueueIdle(action: () -> Unit) {
        runOnMain {
            val idleHandler = MessageQueue.IdleHandler {
                action()
                false
            }
            Looper.myQueue().addIdleHandler(idleHandler)
        }
    }
}
