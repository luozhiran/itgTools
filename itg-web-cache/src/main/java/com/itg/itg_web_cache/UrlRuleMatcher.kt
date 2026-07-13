package com.itg.itg_web_cache

import java.net.URI

internal object UrlRuleMatcher {
    fun isValidHttpUrl(url: String, allowedHosts: List<String>): Boolean {
        val uri = parse(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host.isBlank()) return false
        return allowedHosts.isEmpty() || allowedHosts.any { it.equals(host, ignoreCase = true) }
    }

    fun matchesWhitelist(url: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { matchesPattern(url, it) }
    }

    fun matchesBlacklist(url: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { matchesPattern(url, it) }
    }

    fun matchesRule(url: String, rule: PreloadUrlRule): Boolean {
        val uri = parse(url) ?: return false
        val hostMatches = rule.host?.let { it.equals(uri.host, ignoreCase = true) } ?: true
        val path = uri.path.orEmpty()
        val pathMatches = rule.pathPrefix?.let { path.startsWith(it) } ?: true
        return hostMatches && pathMatches
    }

    fun hostOf(url: String): String? = parse(url)?.host

    private fun matchesPattern(url: String, pattern: String): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return false
        if (cleanPattern.endsWith("*")) {
            return url.startsWith(cleanPattern.dropLast(1), ignoreCase = true)
        }
        return url.equals(cleanPattern, ignoreCase = true)
    }

    private fun parse(url: String): URI? {
        return runCatching { URI(url.trim()) }.getOrNull()
    }
}
