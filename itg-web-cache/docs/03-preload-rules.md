# 03. 预热规则

本节说明预热 URL、规则筛选和并行预热限制。

## 适用条件

- 只预热低风险、无副作用、可缓存的 H5 页面。
- URL host 在 `allowedHosts` 内。
- 用户网络、登录态、前后台和内存状态满足要求。

## 推荐做法

```kotlin
WebCacheConfig(
    preloadEnable = true,
    preloadUrlRules = listOf(PreloadUrlRule(...)),
    allowedHosts = listOf("h5.example.com")
)
```

## 可复制 Demo

```kotlin
import com.itg.itg_web_cache.PreloadUrlRule
import com.itg.itg_web_cache.WebCacheConfig

val config = WebCacheConfig(
    preloadEnable = true,
    preloadUrls = listOf("https://h5.example.com/home"),
    preloadUrlRules = listOf(
        PreloadUrlRule(
            id = "home_v1",
            url = "https://h5.example.com/home",
            host = "h5.example.com",
            scene = "home",
            priority = 100,
            ttlMs = 30 * 60 * 1000L,
            loginRequired = true,
            wifiOnly = true
        )
    ),
    preloadUrlBlacklist = listOf("https://h5.example.com/pay"),
    preloadMaxUrlCount = 1,
    allowedHosts = listOf("h5.example.com")
)
```

## 关键说明

- 黑名单优先级高于 `preloadUrls` 和 `preloadUrlRules`。
- `preloadMaxUrlCount` 控制单次启动最多预热数量。
- `preloadMinIntervalMs` 或 rule 的 `ttlMs` 控制冷却时间。
- `preloadParallelEnable` 是实验能力，会增加内存峰值。
- 支付、登录、下单、隐私授权、一次性 token 页面不应预热。

## 验证方式

- 不在 `allowedHosts` 的 URL 应被跳过。
- 黑名单 URL 即使在白名单或 rules 中也不应被预热。
- 并行预热开启前要确认高内存和 WiFi 条件。

[返回 README](../README.md)