package com.itg.itgtools

import android.app.Application
import com.itg.itg_web_cache.NetworkType
import com.itg.itg_web_cache.PreloadUrlRule
import com.itg.itg_web_cache.WebCacheConfig
import com.itg.itg_web_cache.WebCacheConfigProvider
import com.itg.itg_web_cache.WebCacheLogger
import com.itg.itg_web_cache.WebCacheRuntimeState
import com.itg.itg_web_cache.WebCacheStateProvider
import com.itg.itg_web_cache.WebCacheEventListener
import com.itg.itg_web_cache.WebCachePreloadManager
import com.itg.itg_web_cache.WebCacheRuntime

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val configProvider = WebCacheConfigProvider {
            WebCacheConfig(
                preloadEnable = false,
                killSwitch = false,
                allowedHosts = listOf("m.example.com"),
                preloadUrlRules = listOf(
                    PreloadUrlRule(
                        id = "job_home",
                        url = "https://m.example.com/job/home",
                        host = "m.example.com",
                        pathPrefix = "/job",
                        scene = "job_home",
                        priority = 100
                    )
                ),
                containerCacheEnable = false,
                containerUrlWhitelist = listOf("https://m.example.com/job/*")
            )
        }

        val stateProvider = WebCacheStateProvider {
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

        val eventListener = WebCacheEventListener { event ->
            // 接入项目埋点系统
        }

        val logger = object : WebCacheLogger {
            override fun log(message: String, throwable: Throwable?) {
                // 接入项目日志系统
            }
        }

        WebCacheRuntime.configure(
            configProvider = configProvider,
            eventListener = eventListener,
            logger = logger
        )

        WebCachePreloadManager.configure(
            context = this,
            configProvider = configProvider,
            stateProvider = stateProvider,
            eventListener = eventListener,
            logger = logger
        )

    }

}