package com.itg.itg_web_cache

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.ArrayDeque

object WebCachePreloadManager {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resolver = WebCachePolicyResolver()
    private val circuitBreaker = WebCacheCircuitBreaker()
    private val lastPreloadTimes = mutableMapOf<String, Long>()
    private val activeWebViews = mutableMapOf<WebView, ActivePreload>()
    private val pendingQueue = ArrayDeque<PreloadUrlRule>()

    private var appContext: Context? = null
    private var configProvider: WebCacheConfigProvider = WebCacheConfigProvider { WebCacheConfig() }
    private var stateProvider: WebCacheStateProvider = WebCacheStateProvider { WebCacheRuntimeState() }
    private var eventListener: WebCacheEventListener = NoOpWebCacheEventListener
    private var logger: WebCacheLogger = NoOpWebCacheLogger
    private var configured = false
    private var cancelled = false
    private var scheduledStartRunnable: Runnable? = null
    private var scheduledIdleHandler: MessageQueue.IdleHandler? = null

    fun configure(
        context: Context,
        configProvider: WebCacheConfigProvider,
        stateProvider: WebCacheStateProvider = WebCacheStateProvider { WebCacheRuntimeState() },
        eventListener: WebCacheEventListener = NoOpWebCacheEventListener,
        logger: WebCacheLogger = NoOpWebCacheLogger
    ) {
        this.appContext = context.applicationContext
        this.configProvider = configProvider
        this.stateProvider = stateProvider
        this.eventListener = eventListener
        this.logger = logger
        this.configured = true
    }

    fun startAfterHomeReady() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startAfterHomeReady() }
            return
        }
        if (!configured) {
            WebCacheSafeCallbacks.log(logger, "WebCachePreloadManager is not configured.")
            return
        }
        cancelScheduledStart()
        val config = readConfig()
        val startRunnable = Runnable {
            scheduledStartRunnable = null
            scheduleStartWhenMainQueueIdle()
        }
        scheduledStartRunnable = startRunnable
        mainHandler.postDelayed(startRunnable, config.preloadDelayMs.coerceAtLeast(0L))
    }

    fun startNow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startNow() }
            return
        }
        cancelScheduledStart()
        if (activeWebViews.isNotEmpty() || pendingQueue.isNotEmpty()) {
            emit(WebCacheEvent(name = "web_cache_preload_disabled", reason = "already_running"))
            return
        }
        val context = appContext ?: return
        val config = readConfig()
        val state = readState()
        val now = System.currentTimeMillis()
        val rules = resolver.selectPreloadRules(
            config = config,
            state = state,
            nowMs = now,
            lastPreloadTimes = lastPreloadTimes,
            isBlocked = { circuitBreaker.isBlocked(it, now) }
        )
        if (rules.isEmpty()) {
            emit(WebCacheEvent(name = "web_cache_preload_disabled", reason = "no_eligible_rules"))
            return
        }
        cancelled = false
        pendingQueue.clear()
        pendingQueue.addAll(rules)
        val parallelCount = resolver.resolvePreloadParallelCount(config, state)
        repeat(parallelCount) {
            startNext(context, config)
        }
    }

    fun cancel(reason: String = "cancel") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cancel(reason) }
            return
        }
        cancelScheduledStart()
        cancelled = true
        pendingQueue.clear()
        val snapshot = activeWebViews.keys.toList()
        snapshot.forEach { webView ->
            finishPreload(webView, success = false, reason = reason, countAsFailure = false)
        }
        emit(WebCacheEvent(name = "web_cache_preload_cancel", reason = reason))
    }

    fun cancelIfConflicts(url: String, reason: String = "container_open_conflict") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cancelIfConflicts(url, reason) }
            return
        }
        val targetHost = UrlRuleMatcher.hostOf(url)
        val removedPending = pendingQueue.removeAll { rule ->
            isConflictUrl(rule.url, url, targetHost)
        }
        val activeSnapshot = activeWebViews.entries
            .filter { entry -> isConflictUrl(entry.value.rule.url, url, targetHost) }
            .map { it.key }
        activeSnapshot.forEach { webView ->
            finishPreload(webView, success = false, reason = reason, countAsFailure = false)
        }
        if (removedPending || activeSnapshot.isNotEmpty()) {
            emit(
                WebCacheEvent(
                    name = "web_cache_preload_cancel",
                    url = url,
                    reason = reason
                )
            )
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            cancel("low_memory")
        }
    }

    fun markContainerLoaded(url: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { markContainerLoaded(url) }
            return
        }
        markUrlCooldown(url)
    }

    fun resetLaunchFailures() {
        circuitBreaker.resetLaunchFailures()
    }

    private fun startNext(context: Context, config: WebCacheConfig) {
        if (cancelled) return
        val state = readState()
        if (!canStartWithState(config, state)) {
            pendingQueue.clear()
            return
        }
        val rule = pollNextRunnableRule(config, state) ?: return
        val startMs = System.currentTimeMillis()
        emit(
            WebCacheEvent(
                name = "web_cache_preload_start",
                url = rule.url,
                ruleId = rule.id
            )
        )
        val webView = runCatching { WebView(context) }.getOrElse {
            circuitBreaker.recordFailure(rule)
            WebCacheSafeCallbacks.log(logger, "Failed to create hidden WebView.", it)
            emit(
                WebCacheEvent(
                    name = "web_cache_preload_error",
                    url = rule.url,
                    ruleId = rule.id,
                    reason = "create_webview_failed:${it.message}"
                )
            )
            startNext(context, config)
            return
        }
        val timeoutRunnable = Runnable {
            finishPreload(webView, success = false, reason = "timeout")
        }
        activeWebViews[webView] = ActivePreload(rule, startMs, timeoutRunnable)
        if (!applyPreloadSettings(webView)) {
            finishPreload(webView, success = false, reason = "apply_settings_failed", countAsFailure = true)
            return
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val active = activeWebViews[view] ?: return
                val finishRunnable = Runnable {
                    finishPreload(view, success = true, reason = "finished")
                }
                active.postFinishRunnable?.let { mainHandler.removeCallbacks(it) }
                active.postFinishRunnable = finishRunnable
                mainHandler.postDelayed(
                    finishRunnable,
                    config.preloadPostFinishDelayMs.coerceAtLeast(0L)
                )
            }

            @Deprecated("Deprecated by Android WebView API")
            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                finishPreload(view, success = false, reason = "error_$errorCode")
            }

            @TargetApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                val detailReason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (detail.didCrash()) "render_process_crash" else "render_process_gone"
                } else {
                    "render_process_gone"
                }
                finishPreload(view, success = false, reason = detailReason)
                return true
            }
        }
        mainHandler.postDelayed(timeoutRunnable, config.preloadTimeoutMs.coerceAtLeast(1_000L))
        runCatching {
            webView.loadUrl(rule.url)
        }.onFailure {
            finishPreload(webView, success = false, reason = "load_url_failed:${it.message}", countAsFailure = true)
        }
    }

    private fun applyPreloadSettings(webView: WebView): Boolean {
        return runCatching {
            val settings = webView.settings
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }.onFailure {
            WebCacheSafeCallbacks.log(logger, "Failed to apply preload WebView settings.", it)
        }.isSuccess
    }

    private fun finishPreload(
        webView: WebView,
        success: Boolean,
        reason: String,
        countAsFailure: Boolean = shouldRecordFailure(reason)
    ) {
        val active = activeWebViews.remove(webView) ?: return
        mainHandler.removeCallbacks(active.timeoutRunnable)
        active.postFinishRunnable?.let { mainHandler.removeCallbacks(it) }
        runCatching { webView.stopLoading() }
        runCatching {
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }.onFailure {
            WebCacheSafeCallbacks.log(logger, "Failed to destroy hidden WebView.", it)
        }
        val elapsed = System.currentTimeMillis() - active.startMs
        if (success) {
            circuitBreaker.recordSuccess(active.rule)
            val now = System.currentTimeMillis()
            lastPreloadTimes[active.rule.id.ifBlank { active.rule.url }] = now
            markUrlCooldown(active.rule.url, now)
        } else if (countAsFailure) {
            circuitBreaker.recordFailure(active.rule)
        }
        emit(
            WebCacheEvent(
                name = if (success) "web_cache_preload_finish" else eventNameForFailure(reason),
                url = active.rule.url,
                ruleId = active.rule.id,
                reason = reason,
                elapsedMs = elapsed
            )
        )
        if (!cancelled) {
            val context = appContext ?: return
            val nextConfig = readConfig()
            val nextState = readState()
            if (!canStartWithState(nextConfig, nextState)) {
                pendingQueue.clear()
                return
            }
            startNext(context, nextConfig)
        }
    }

    private fun pollNextRunnableRule(
        config: WebCacheConfig,
        state: WebCacheRuntimeState
    ): PreloadUrlRule? {
        while (true) {
            val rule = pendingQueue.pollFirst() ?: return null
            if (canRunRuleNow(rule, config, state)) return rule
        }
    }

    private fun canStartWithState(config: WebCacheConfig, state: WebCacheRuntimeState): Boolean {
        if (config.killSwitch || !config.preloadEnable) return false
        if (!state.isForeground || !state.isHomeReady || state.isLowMemory || state.isLowPowerMode) {
            return false
        }
        if (config.preloadWifiOnly && state.networkType != NetworkType.WIFI) return false
        return true
    }

    private fun canRunRuleNow(
        rule: PreloadUrlRule,
        config: WebCacheConfig,
        state: WebCacheRuntimeState
    ): Boolean {
        val now = System.currentTimeMillis()
        return rule.enable &&
            !circuitBreaker.isBlocked(rule, now) &&
            UrlRuleMatcher.isValidHttpUrl(rule.url, config.allowedHosts) &&
            !UrlRuleMatcher.matchesBlacklist(rule.url, config.preloadUrlBlacklist) &&
            (!config.preloadLoginRequired || !rule.loginRequired || state.isLoggedIn) &&
            (!rule.wifiOnly || state.networkType == NetworkType.WIFI) &&
            isOutsideCooldown(rule, config, now)
    }

    private fun isOutsideCooldown(
        rule: PreloadUrlRule,
        config: WebCacheConfig,
        nowMs: Long
    ): Boolean {
        val ruleKey = rule.id.ifBlank { rule.url }
        val urlKey = UrlRuleMatcher.cooldownKeyOf(rule.url)
        val last = listOfNotNull(
            lastPreloadTimes[ruleKey],
            urlKey?.let { lastPreloadTimes[it] }
        ).maxOrNull() ?: return true
        val ttl = rule.ttlMs ?: config.preloadMinIntervalMs
        return nowMs - last >= ttl
    }

    private fun markUrlCooldown(url: String, nowMs: Long = System.currentTimeMillis()) {
        val urlKey = UrlRuleMatcher.cooldownKeyOf(url) ?: return
        lastPreloadTimes[urlKey] = nowMs
    }

    private fun eventNameForFailure(reason: String): String {
        return if (reason == "timeout") "web_cache_preload_timeout" else "web_cache_preload_error"
    }

    private fun shouldRecordFailure(reason: String): Boolean {
        return reason == "timeout" ||
            reason == "apply_settings_failed" ||
            reason == "render_process_crash" ||
            reason == "render_process_gone" ||
            reason.startsWith("error_") ||
            reason.startsWith("load_url_failed") ||
            reason.startsWith("create_webview_failed")
    }

    private fun scheduleStartWhenMainQueueIdle() {
        if (scheduledIdleHandler != null) return
        val idleHandler = MessageQueue.IdleHandler {
            scheduledIdleHandler = null
            startNow()
            false
        }
        scheduledIdleHandler = idleHandler
        Looper.myQueue().addIdleHandler(idleHandler)
    }

    private fun cancelScheduledStart() {
        scheduledStartRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStartRunnable = null
        scheduledIdleHandler?.let { Looper.myQueue().removeIdleHandler(it) }
        scheduledIdleHandler = null
    }

    private fun isConflictUrl(preloadUrl: String, containerUrl: String, containerHost: String?): Boolean {
        if (preloadUrl.equals(containerUrl, ignoreCase = true)) return true
        val preloadHost = UrlRuleMatcher.hostOf(preloadUrl) ?: return false
        return containerHost != null && preloadHost.equals(containerHost, ignoreCase = true)
    }

    private fun readConfig(): WebCacheConfig {
        return runCatching { configProvider.getConfig() }.getOrElse {
            WebCacheSafeCallbacks.log(logger, "Failed to read Web cache config.", it)
            WebCacheConfig()
        }
    }

    private fun readState(): WebCacheRuntimeState {
        return runCatching { stateProvider.getState() }.getOrElse {
            WebCacheSafeCallbacks.log(logger, "Failed to read Web cache runtime state.", it)
            WebCacheRuntimeState(isForeground = false)
        }
    }

    private fun emit(event: WebCacheEvent) {
        WebCacheSafeCallbacks.emit(eventListener, event, logger)
    }

    private data class ActivePreload(
        val rule: PreloadUrlRule,
        val startMs: Long,
        val timeoutRunnable: Runnable,
        var postFinishRunnable: Runnable? = null
    )
}
