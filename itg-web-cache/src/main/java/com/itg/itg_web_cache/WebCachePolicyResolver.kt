package com.itg.itg_web_cache

class WebCachePolicyResolver {
    fun resolveContainerPolicy(
        url: String,
        scene: String?,
        config: WebCacheConfig
    ): WebCachePolicy {
        if (config.killSwitch) {
            return WebCachePolicy(false, reason = "kill_switch")
        }
        if (!config.containerCacheEnable) {
            return WebCachePolicy(false, reason = "container_disabled")
        }
        if (!UrlRuleMatcher.isValidHttpUrl(url, config.allowedHosts)) {
            return WebCachePolicy(false, reason = "invalid_or_disallowed_url")
        }
        if (config.containerUrlWhitelist.isNotEmpty() &&
            !UrlRuleMatcher.matchesWhitelist(url, config.containerUrlWhitelist)
        ) {
            return WebCachePolicy(false, reason = "url_not_in_container_whitelist")
        }
        if (scene != null && config.containerSceneBlacklist.contains(scene)) {
            return WebCachePolicy(false, reason = "scene_blacklisted")
        }
        if (config.containerSceneWhitelist.isNotEmpty() &&
            (scene == null || !config.containerSceneWhitelist.contains(scene))
        ) {
            return WebCachePolicy(false, reason = "scene_not_in_whitelist")
        }
        return WebCachePolicy(true, config.containerCacheMode, "enabled")
    }

    fun selectPreloadRules(
        config: WebCacheConfig,
        state: WebCacheRuntimeState,
        nowMs: Long,
        lastPreloadTimes: Map<String, Long>,
        isBlocked: (PreloadUrlRule) -> Boolean
    ): List<PreloadUrlRule> {
        if (config.killSwitch || !config.preloadEnable) return emptyList()
        if (!state.isForeground || !state.isHomeReady || state.isLowMemory || state.isLowPowerMode) {
            return emptyList()
        }
        if ((config.preloadWifiOnly || config.preloadParallelWifiOnly) &&
            config.preloadWifiOnly &&
            state.networkType != NetworkType.WIFI
        ) {
            return emptyList()
        }
        val rules = buildRules(config)
        if (rules.isEmpty()) return emptyList()
        val maxCount = config.preloadMaxUrlCount.coerceAtLeast(0)
        if (maxCount == 0) return emptyList()

        return rules.asSequence()
            .filter { it.enable }
            .filter { !isBlocked(it) }
            .filter { UrlRuleMatcher.isValidHttpUrl(it.url, config.allowedHosts) }
            .filter { !UrlRuleMatcher.matchesBlacklist(it.url, config.preloadUrlBlacklist) }
            .filter { !config.preloadLoginRequired || !it.loginRequired || state.isLoggedIn }
            .filter { !it.wifiOnly || state.networkType == NetworkType.WIFI }
            .filter { !config.preloadWifiOnly || state.networkType == NetworkType.WIFI }
            .filter { isOutsideCooldown(it, config, nowMs, lastPreloadTimes) }
            .sortedByDescending { it.priority }
            .take(maxCount)
            .toList()
    }

    fun resolvePreloadParallelCount(config: WebCacheConfig, state: WebCacheRuntimeState): Int {
        if (!config.preloadParallelEnable) return 1
        if (config.preloadParallelWifiOnly && state.networkType != NetworkType.WIFI) return 1
        if (config.preloadParallelHighMemoryOnly && !state.isHighMemoryDevice) return 1
        if (state.isLowMemory || state.isLowPowerMode || !state.isForeground) return 1
        return config.preloadParallelCount.coerceIn(1, 2)
    }

    private fun buildRules(config: WebCacheConfig): List<PreloadUrlRule> {
        val simpleRules = config.preloadUrls.mapIndexed { index, url ->
            PreloadUrlRule(
                id = "url_$index",
                url = url,
                priority = 0,
                loginRequired = config.preloadLoginRequired,
                wifiOnly = config.preloadWifiOnly
            )
        }
        return config.preloadUrlRules + simpleRules
    }

    private fun isOutsideCooldown(
        rule: PreloadUrlRule,
        config: WebCacheConfig,
        nowMs: Long,
        lastPreloadTimes: Map<String, Long>
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
}
