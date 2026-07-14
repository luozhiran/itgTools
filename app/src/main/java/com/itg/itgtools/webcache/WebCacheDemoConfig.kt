package com.itg.itgtools.webcache

import android.content.Context
import com.itg.itg_web_cache.CacheModeOption
import com.itg.itg_web_cache.NetworkType
import com.itg.itg_web_cache.PreloadUrlRule
import com.itg.itg_web_cache.WebCacheConfig
import com.itg.itg_web_cache.WebCacheConfigProvider
import com.itg.itg_web_cache.WebCacheEvent
import com.itg.itg_web_cache.WebCacheEventListener
import com.itg.itg_web_cache.WebCacheLogger
import com.itg.itg_web_cache.WebCachePreloadManager
import com.itg.itg_web_cache.WebCacheRuntime
import com.itg.itg_web_cache.WebCacheRuntimeState
import com.itg.itg_web_cache.WebCacheStateProvider
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WebCacheDemoConfig {
    const val DEFAULT_URL = "https://www.baidu.com/"
    const val DEFAULT_SCENE = "demo_home"

    var targetUrl: String = DEFAULT_URL
    var preloadEnabled: Boolean = true
    var containerCacheEnabled: Boolean = true
    var parallelEnabled: Boolean = false
    var killSwitch: Boolean = false
    var containerForceOverride: Boolean = false
    var businessNoCachePreset: Boolean = false

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val events = mutableListOf<String>()
    private val sinks = mutableSetOf<(String) -> Unit>()

    private val configProvider = WebCacheConfigProvider { buildConfig() }
    private val stateProvider = WebCacheStateProvider {
        WebCacheRuntimeState(
            isForeground = true,
            isHomeReady = true,
            isLoggedIn = true,
            networkType = NetworkType.WIFI,
            isLowMemory = false,
            isLowPowerMode = false,
            isHighMemoryDevice = true
        )
    }
    private val eventListener = WebCacheEventListener { event ->
        appendEvent(event.toLogLine())
    }
    private val logger = object : WebCacheLogger {
        override fun log(message: String, throwable: Throwable?) {
            appendEvent("log: $message${throwable?.message?.let { " ($it)" }.orEmpty()}")
        }
    }

    fun configure(context: Context) {
        WebCacheRuntime.configure(
            configProvider = configProvider,
            eventListener = eventListener,
            logger = logger
        )
        WebCachePreloadManager.configure(
            context = context.applicationContext,
            configProvider = configProvider,
            stateProvider = stateProvider,
            eventListener = eventListener,
            logger = logger
        )
    }

    fun updateUrl(url: String) {
        targetUrl = url.trim().ifBlank { DEFAULT_URL }
    }

    fun addSink(sink: (String) -> Unit) {
        sinks.add(sink)
        sink(events.joinToString(separator = "\n"))
    }

    fun removeSink(sink: (String) -> Unit) {
        sinks.remove(sink)
    }

    fun clearEvents() {
        events.clear()
        notifySinks()
    }

    fun currentLog(): String = events.joinToString(separator = "\n")

    private fun buildConfig(): WebCacheConfig {
        val host = runCatching { URI(targetUrl).host }.getOrNull().orEmpty()
        return WebCacheConfig(
            preloadEnable = preloadEnabled,
            killSwitch = killSwitch,
            preloadMaxUrlCount = 1,
            preloadDelayMs = 500L,
            preloadTimeoutMs = 15_000L,
            preloadPostFinishDelayMs = 2_000L,
            preloadParallelEnable = parallelEnabled,
            preloadParallelCount = if (parallelEnabled) 2 else 1,
            preloadParallelWifiOnly = true,
            preloadParallelHighMemoryOnly = true,
            preloadUrlRules = listOf(
                PreloadUrlRule(
                    id = "demo_preload",
                    url = targetUrl,
                    host = host.ifBlank { null },
                    scene = DEFAULT_SCENE,
                    priority = 100,
                    loginRequired = false,
                    wifiOnly = false
                )
            ),
            preloadUrlBlacklist = emptyList(),
            containerCacheEnable = containerCacheEnabled,
            containerCacheMode = CacheModeOption.CACHE_ELSE_NETWORK,
            containerForceOverride = containerForceOverride,
            containerUrlWhitelist = listOf(targetUrl),
            containerSceneWhitelist = listOf(DEFAULT_SCENE),
            allowedHosts = host.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
        )
    }

    private fun WebCacheEvent.toLogLine(): String {
        val time = dateFormat.format(Date(timestampMs))
        val pieces = listOfNotNull(
            "[$time]",
            name,
            ruleId?.let { "rule=$it" },
            scene?.let { "scene=$it" },
            cacheMode?.let { "mode=${it.configValue}" },
            elapsedMs?.let { "elapsed=${it}ms" },
            reason?.let { "reason=$it" },
            url?.let { "url=$it" }
        )
        return pieces.joinToString(separator = " ")
    }

    private fun appendEvent(line: String) {
        events.add(line)
        if (events.size > 120) {
            events.removeAt(0)
        }
        notifySinks()
    }

    private fun notifySinks() {
        val text = currentLog()
        sinks.toList().forEach { sink ->
            runCatching { sink(text) }
        }
    }
}
