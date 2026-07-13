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
}