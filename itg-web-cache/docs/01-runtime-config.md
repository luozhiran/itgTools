# 01. Runtime 初始化

本节说明 `WebCacheRuntime.configure` 的接入方式。

## 适用条件

- App 有统一的远程配置或本地兜底配置。
- 需要收集 WebCache 事件和错误日志。
- WebView 容器会在加载前调用 Runtime API。

## 推荐做法

```kotlin
WebCacheRuntime.configure(
    configProvider = WebCacheConfigProvider { currentConfig },
    eventListener = WebCacheEventListener { event -> report(event) },
    logger = object : WebCacheLogger { ... }
)
```

## 可复制 Demo

```kotlin
import com.itg.itg_web_cache.CacheModeOption
import com.itg.itg_web_cache.WebCacheConfig
import com.itg.itg_web_cache.WebCacheConfigProvider
import com.itg.itg_web_cache.WebCacheEventListener
import com.itg.itg_web_cache.WebCacheLogger
import com.itg.itg_web_cache.WebCacheRuntime

WebCacheRuntime.configure(
    configProvider = WebCacheConfigProvider {
        WebCacheConfig(
            containerCacheEnable = true,
            containerCacheMode = CacheModeOption.DEFAULT,
            containerUrlWhitelist = listOf("https://h5.example.com/"),
            allowedHosts = listOf("h5.example.com")
        )
    },
    eventListener = WebCacheEventListener { event ->
        println("web-cache event=${event.name}, reason=${event.reason}")
    },
    logger = object : WebCacheLogger {
        override fun log(message: String, throwable: Throwable?) {
            android.util.Log.w("WebCache", message, throwable)
        }
    }
)
```

## 关键说明

- `configProvider` 每次应用策略时会被读取，适合接入远程配置快照。
- provider 抛异常时 Runtime 会退回默认 `WebCacheConfig()`。
- eventListener/logger 内部异常会被安全兜底。
- `killSwitch = true` 优先级最高。

## 验证方式

- 初始化后调用 `applyToContainer` 应产生 `web_cache_container_policy` 事件。
- 配置 provider 故意抛异常时 App 不应崩溃。

[返回 README](../README.md)