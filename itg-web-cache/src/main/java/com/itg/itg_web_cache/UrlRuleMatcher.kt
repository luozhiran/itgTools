package com.itg.itg_web_cache

import java.net.URI

internal object UrlRuleMatcher {
    fun isValidHttpUrl(url: String, allowedHosts: List<String>): Boolean {
        val uri = parse(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host.isBlank()) return false
        if (allowedHosts.isEmpty()) return false
        return allowedHosts.any { it.equals(host, ignoreCase = true) }
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

    fun cooldownKeyOf(url: String): String? {
        val uri = parse(url) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "https" || host.isBlank()) return null
        return "$scheme://$host${uri.path.orEmpty()}"
    }

    private fun matchesPattern(url: String, pattern: String): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return false

        val wildcard = cleanPattern.endsWith("*")
        val normalizedPattern = if (wildcard) cleanPattern.dropLast(1) else cleanPattern
        val urlUri = parse(url) ?: return false
        val patternUri = parse(normalizedPattern)

        if (patternUri != null && patternUri.scheme != null && patternUri.host != null) {
            return matchesUriPattern(urlUri, patternUri, wildcard)
        }

        return if (wildcard) {
            url.startsWith(normalizedPattern, ignoreCase = true)
        } else {
            url.equals(normalizedPattern, ignoreCase = true)
        }
    }

    private fun matchesUriPattern(urlUri: URI, patternUri: URI, wildcard: Boolean): Boolean {
        val patternScheme = patternUri.scheme?.lowercase() ?: return false
        val urlScheme = urlUri.scheme?.lowercase() ?: return false
        if (patternScheme != urlScheme) return false
        if (!patternUri.host.equals(urlUri.host, ignoreCase = true)) return false

        val patternPath = patternUri.path.orEmpty()
        val urlPath = urlUri.path.orEmpty()
        return if (wildcard) {
            urlPath.startsWith(patternPath)
        } else {
            urlPath == patternPath
        }
    }

    private fun parse(url: String): URI? {
        return runCatching { URI(url.trim()) }.getOrNull()
    }
}
