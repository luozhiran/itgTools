package com.itg.itg_web_cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebCachePolicyResolverTest {
    private val resolver = WebCachePolicyResolver()

    @Test
    fun containerPolicyDisabledWhenKillSwitchEnabled() {
        val policy = resolver.resolveContainerPolicy(
            url = "https://m.example.com/job/home",
            scene = "job_home",
            config = WebCacheConfig(
                killSwitch = true,
                containerCacheEnable = true,
                containerUrlWhitelist = listOf("https://m.example.com/job/*"),
                allowedHosts = listOf("m.example.com")
            )
        )

        assertFalse(policy.enabled)
        assertEquals("kill_switch", policy.reason)
    }

    @Test
    fun containerPolicyEnabledForWhitelistedUrlAndScene() {
        val policy = resolver.resolveContainerPolicy(
            url = "https://m.example.com/job/home",
            scene = "job_home",
            config = WebCacheConfig(
                containerCacheEnable = true,
                containerCacheMode = CacheModeOption.CACHE_ELSE_NETWORK,
                containerUrlWhitelist = listOf("https://m.example.com/job/*"),
                containerSceneWhitelist = listOf("job_home"),
                allowedHosts = listOf("m.example.com")
            )
        )

        assertTrue(policy.enabled)
        assertEquals(CacheModeOption.CACHE_ELSE_NETWORK, policy.cacheMode)
    }

    @Test
    fun containerWhitelistMatchesSamePathWithQuery() {
        val policy = resolver.resolveContainerPolicy(
            url = "https://m.example.com/job/home?tab=recommend",
            scene = "job_home",
            config = WebCacheConfig(
                containerCacheEnable = true,
                containerUrlWhitelist = listOf("https://m.example.com/job/home"),
                allowedHosts = listOf("m.example.com")
            )
        )

        assertTrue(policy.enabled)
    }

    @Test
    fun emptyAllowedHostsRejectsContainerPolicy() {
        val policy = resolver.resolveContainerPolicy(
            url = "https://m.example.com/job/home",
            scene = "job_home",
            config = WebCacheConfig(
                containerCacheEnable = true,
                containerUrlWhitelist = listOf("https://m.example.com/job/*"),
                allowedHosts = emptyList()
            )
        )

        assertFalse(policy.enabled)
        assertEquals("invalid_or_disallowed_url", policy.reason)
    }

    @Test
    fun selectPreloadRulesFiltersBlacklistAndLimitsByPriority() {
        val rules = resolver.selectPreloadRules(
            config = WebCacheConfig(
                preloadEnable = true,
                preloadMaxUrlCount = 1,
                preloadUrlRules = listOf(
                    PreloadUrlRule(
                        id = "low",
                        url = "https://m.example.com/help",
                        priority = 1
                    ),
                    PreloadUrlRule(
                        id = "high",
                        url = "https://m.example.com/job/home",
                        priority = 100
                    ),
                    PreloadUrlRule(
                        id = "blocked",
                        url = "https://m.example.com/pay",
                        priority = 200
                    )
                ),
                preloadUrlBlacklist = listOf("https://m.example.com/pay"),
                allowedHosts = listOf("m.example.com")
            ),
            state = WebCacheRuntimeState(isLoggedIn = true),
            nowMs = 1_000L,
            lastPreloadTimes = emptyMap(),
            isBlocked = { false }
        )

        assertEquals(listOf("high"), rules.map { it.id })
    }

    @Test
    fun preloadBlacklistMatchesSamePathWithQuery() {
        val rules = resolver.selectPreloadRules(
            config = WebCacheConfig(
                preloadEnable = true,
                preloadMaxUrlCount = 2,
                preloadUrlRules = listOf(
                    PreloadUrlRule(
                        id = "pay",
                        url = "https://m.example.com/pay?orderId=123",
                        priority = 100
                    ),
                    PreloadUrlRule(
                        id = "home",
                        url = "https://m.example.com/home",
                        priority = 1
                    )
                ),
                preloadUrlBlacklist = listOf("https://m.example.com/pay"),
                allowedHosts = listOf("m.example.com")
            ),
            state = WebCacheRuntimeState(isLoggedIn = true),
            nowMs = 1_000L,
            lastPreloadTimes = emptyMap(),
            isBlocked = { false }
        )

        assertEquals(listOf("home"), rules.map { it.id })
    }

    @Test
    fun containerLoadedUrlKeyParticipatesInPreloadCooldown() {
        val rules = resolver.selectPreloadRules(
            config = WebCacheConfig(
                preloadEnable = true,
                preloadUrlRules = listOf(
                    PreloadUrlRule(
                        id = "home_rule",
                        url = "https://m.example.com/home?from=preload"
                    )
                ),
                preloadMinIntervalMs = 30 * 60 * 1000L,
                allowedHosts = listOf("m.example.com")
            ),
            state = WebCacheRuntimeState(isLoggedIn = true),
            nowMs = 2_000L,
            lastPreloadTimes = mapOf(
                "https://m.example.com/home" to 1_000L
            ),
            isBlocked = { false }
        )

        assertTrue(rules.isEmpty())
    }

    @Test
    fun emptyAllowedHostsRejectsPreloadRules() {
        val rules = resolver.selectPreloadRules(
            config = WebCacheConfig(
                preloadEnable = true,
                preloadUrlRules = listOf(
                    PreloadUrlRule(
                        id = "home",
                        url = "https://m.example.com/home"
                    )
                ),
                allowedHosts = emptyList()
            ),
            state = WebCacheRuntimeState(isLoggedIn = true),
            nowMs = 1_000L,
            lastPreloadTimes = emptyMap(),
            isBlocked = { false }
        )

        assertTrue(rules.isEmpty())
    }

    @Test
    fun parallelCountIsCappedAndRequiresWifi() {
        val config = WebCacheConfig(
            preloadParallelEnable = true,
            preloadParallelCount = 5,
            preloadParallelWifiOnly = true
        )

        val wifiCount = resolver.resolvePreloadParallelCount(
            config,
            WebCacheRuntimeState(networkType = NetworkType.WIFI)
        )
        val cellularCount = resolver.resolvePreloadParallelCount(
            config,
            WebCacheRuntimeState(networkType = NetworkType.CELLULAR)
        )

        assertEquals(2, wifiCount)
        assertEquals(1, cellularCount)
    }

    @Test
    fun circuitBreakerBlocksUrlAfterConsecutiveFailuresAndSuccessClearsIt() {
        val breaker = WebCacheCircuitBreaker(maxFailuresPerUrl = 3, blockDurationMs = 1_000L)
        val rule = PreloadUrlRule(id = "home", url = "https://m.example.com/job/home")

        breaker.recordFailure(rule, nowMs = 1_000L)
        breaker.recordFailure(rule, nowMs = 1_100L)
        assertFalse(breaker.isBlocked(rule, nowMs = 1_200L))

        breaker.recordFailure(rule, nowMs = 1_300L)
        assertTrue(breaker.isBlocked(rule, nowMs = 1_400L))

        breaker.recordSuccess(rule)
        assertFalse(breaker.isBlocked(rule, nowMs = 1_500L))
    }

    @Test
    fun circuitBreakerTotalFailuresCanBeResetForLaunch() {
        val breaker = WebCacheCircuitBreaker(maxFailuresPerUrl = 10, maxTotalFailuresPerLaunch = 2)
        val first = PreloadUrlRule(id = "first", url = "https://m.example.com/first")
        val second = PreloadUrlRule(id = "second", url = "https://m.example.com/second")
        val third = PreloadUrlRule(id = "third", url = "https://m.example.com/third")

        breaker.recordFailure(first)
        assertFalse(breaker.isBlocked(third))

        breaker.recordFailure(second)
        assertTrue(breaker.isBlocked(third))

        breaker.resetLaunchFailures()
        assertFalse(breaker.isBlocked(third))
    }
}
