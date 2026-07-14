package com.itg.itg_web_cache

/**
 * A single URL preload rule.
 *
 * Rules are selected by [WebCachePolicyResolver] according to enable state, URL validity,
 * blacklist, login/network requirements, cooldown, and priority.
 */
data class PreloadUrlRule(
    /**
     * Stable rule identifier used for logs, cooldown records, and circuit breaker keys.
     * Keep it stable across app launches and remote config updates.
     */
    val id: String,

    /**
     * Absolute HTTP/HTTPS URL loaded by the hidden WebView during preload.
     */
    val url: String,

    /**
     * Whether this rule is enabled. Disabled rules are ignored before preload starts.
     */
    val enable: Boolean = true,

    /**
     * Expected host for documentation and business readability.
     * URL validation uses [url] together with [WebCacheConfig.allowedHosts].
     */
    val host: String? = null,

    /**
     * Optional path prefix describing the page group covered by this rule.
     * This field is metadata for business configuration; the current selector preloads [url].
     */
    val pathPrefix: String? = null,

    /**
     * Optional business scene associated with this preload rule, used in logs and configuration mapping.
     */
    val scene: String? = null,

    /**
     * Selection priority. Higher values are selected first when candidates exceed
     * [WebCacheConfig.preloadMaxUrlCount].
     */
    val priority: Int = 0,

    /**
     * Minimum interval before this rule can be preloaded again in the current process.
     * When null, [WebCacheConfig.preloadMinIntervalMs] is used.
     */
    val ttlMs: Long? = null,

    /**
     * Whether this rule requires the user to be logged in.
     * It only takes effect when [WebCacheConfig.preloadLoginRequired] is true.
     */
    val loginRequired: Boolean = true,

    /**
     * Whether this rule is allowed only on Wi-Fi.
     * When true, cellular and other network types skip this rule.
     */
    val wifiOnly: Boolean = false
)