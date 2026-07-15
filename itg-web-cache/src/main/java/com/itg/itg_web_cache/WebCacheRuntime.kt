package com.itg.itg_web_cache

import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import java.util.WeakHashMap

object WebCacheRuntime : WebCacheRuntimeApi {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resolver = WebCachePolicyResolver()
    private var configProvider: WebCacheConfigProvider = WebCacheConfigProvider { WebCacheConfig() }
    private var eventListener: WebCacheEventListener = NoOpWebCacheEventListener
    private var logger: WebCacheLogger = NoOpWebCacheLogger
    private val appliedPolicies = WeakHashMap<WebView, WebCachePolicy>()
    private val pageStartTimes = WeakHashMap<WebView, Long>()

    fun configure(
        configProvider: WebCacheConfigProvider,
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger
    ) {
        this.configProvider = configProvider
        this.eventListener = eventListener
        this.logger = logger
    }

    override fun applyToContainer(webView: WebView, url: String, scene: String?): WebCachePolicy {
        if (!isMainThread()) {
            val policy = WebCachePolicy(false, reason = "not_main_thread")
            WebCacheSafeCallbacks.log(
                logger,
                "WebCacheRuntime.applyToContainer must be called on the main thread."
            )
            emitContainerPolicy(url, scene, policy)
            return policy
        }
        WebCachePreloadManager.cancelIfConflicts(url)
        val config = runCatching { configProvider.getConfig() }.getOrElse {
            WebCacheSafeCallbacks.log(logger, "Failed to read Web cache config.", it)
            WebCacheConfig()
        }
        val resolvedPolicy = resolver.resolveContainerPolicy(url, scene, config)
        val policy = if (resolvedPolicy.enabled) {
            applyContainerPolicy(webView, url, scene, config, resolvedPolicy)
        } else {
            resolvedPolicy
        }
        appliedPolicies[webView] = policy
        emitContainerPolicy(url, scene, policy)
        return policy
    }

    private fun applyContainerPolicy(
        webView: WebView,
        url: String,
        scene: String?,
        config: WebCacheConfig,
        policy: WebCachePolicy
    ): WebCachePolicy {
        val settings = runCatching { webView.settings }.getOrElse {
            emitApplyError(url, scene, it)
            return WebCachePolicy(false, policy.cacheMode, "apply_error")
        }
        if (!config.containerForceOverride && settings.cacheMode != WebSettings.LOAD_DEFAULT) {
            return WebCachePolicy(false, policy.cacheMode, "business_cache_mode_exists")
        }
        return runCatching {
            settings.cacheMode = policy.cacheMode.toWebSettingsCacheMode()
            policy
        }.getOrElse {
            emitApplyError(url, scene, it)
            WebCachePolicy(false, policy.cacheMode, "apply_error")
        }
    }

    private fun emitApplyError(url: String, scene: String?, throwable: Throwable) {
        WebCacheSafeCallbacks.log(logger, "Failed to apply WebView cache mode.", throwable)
        WebCacheSafeCallbacks.emit(
            eventListener,
            WebCacheEvent(
                name = "web_cache_container_apply_error",
                url = url,
                scene = scene,
                reason = throwable.message
            ),
            logger
        )
    }

    private fun emitContainerPolicy(url: String, scene: String?, policy: WebCachePolicy) {
        WebCacheSafeCallbacks.emit(
            eventListener,
            WebCacheEvent(
                name = "web_cache_container_policy",
                url = url,
                scene = scene,
                reason = policy.reason,
                cacheMode = policy.cacheMode
            ),
            logger
        )
    }

    override fun onContainerPageStarted(webView: WebView, url: String) {
        if (!isMainThread()) {
            mainHandler.post { onContainerPageStarted(webView, url) }
            return
        }
        pageStartTimes[webView] = System.currentTimeMillis()
        WebCacheSafeCallbacks.emit(
            eventListener,
            WebCacheEvent(
                name = "web_container_open",
                url = url,
                reason = appliedPolicies[webView]?.reason,
                cacheMode = appliedPolicies[webView]?.cacheMode
            ),
            logger
        )
    }

    override fun onContainerPageFinished(webView: WebView, url: String) {
        if (!isMainThread()) {
            mainHandler.post { onContainerPageFinished(webView, url) }
            return
        }
        val start = pageStartTimes.remove(webView)
        WebCacheSafeCallbacks.emit(
            eventListener,
            WebCacheEvent(
                name = "web_container_first_paint",
                url = url,
                reason = appliedPolicies[webView]?.reason,
                cacheMode = appliedPolicies[webView]?.cacheMode,
                elapsedMs = start?.let { System.currentTimeMillis() - it }
            ),
            logger
        )
    }

    override fun detachContainer(webView: WebView) {
        if (!isMainThread()) {
            mainHandler.post { detachContainer(webView) }
            return
        }
        appliedPolicies.remove(webView)
        pageStartTimes.remove(webView)
        WebCacheSafeCallbacks.emit(
            eventListener,
            WebCacheEvent(name = "web_cache_container_detach"),
            logger
        )
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
}
