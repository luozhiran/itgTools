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

## `preloadUrls` 与 `preloadUrlRules` 区别

两者都会进入同一条预热候选队列，但适合的配置复杂度不同。

| 配置项 | 适合场景 | 能配置什么 | 默认行为 |
| --- | --- | --- | --- |
| `preloadUrls` | 少量固定 URL，只有“开/关”和全局约束 | 只能配置 URL 字符串 | 源码会自动转成 `PreloadUrlRule(id = "url_$index", priority = 0, loginRequired = config.preloadLoginRequired, wifiOnly = config.preloadWifiOnly)` |
| `preloadUrlRules` | 每个 URL 需要独立优先级、冷却时间、网络或登录约束 | `id`、`url`、`enable`、`host`、`pathPrefix`、`scene`、`priority`、`ttlMs`、`loginRequired`、`wifiOnly` | 按规则字段参与筛选和排序 |

选择建议：

- 只想预热 1 到 2 个稳定页面，用 `preloadUrls`。
- 需要按业务优先级选择页面，用 `preloadUrlRules.priority`。
- 不同页面有不同冷却时间，用 `preloadUrlRules.ttlMs`。
- 某些页面只允许 WiFi 或登录后预热，用 `preloadUrlRules.wifiOnly/loginRequired`。
- 灰度或远程配置已经有规则 ID，使用 `preloadUrlRules.id`，便于日志、冷却和熔断定位。

两者同时配置时，源码会执行：

```kotlin
val simpleRules = config.preloadUrls.mapIndexed { index, url ->
    PreloadUrlRule(
        id = "url_$index",
        url = url,
        priority = 0,
        loginRequired = config.preloadLoginRequired,
        wifiOnly = config.preloadWifiOnly
    )
}
val rules = config.preloadUrlRules + simpleRules
```

然后统一经过 `enable`、熔断、`allowedHosts`、黑名单、登录态、WiFi、冷却时间、`priority` 排序和 `preloadMaxUrlCount` 截断。

## 配置选择 Demo

简单固定页面：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadUrls = listOf(
        "https://h5.example.com/home",
        "https://h5.example.com/help"
    ),
    preloadMaxUrlCount = 1,
    allowedHosts = listOf("h5.example.com")
)
```

精细规则页面：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadUrlRules = listOf(
        PreloadUrlRule(
            id = "home_high",
            url = "https://h5.example.com/home",
            scene = "home",
            priority = 100,
            ttlMs = 10 * 60 * 1000L,
            loginRequired = true,
            wifiOnly = true
        ),
        PreloadUrlRule(
            id = "help_low",
            url = "https://h5.example.com/help",
            scene = "help",
            priority = 10,
            ttlMs = 60 * 60 * 1000L,
            loginRequired = false,
            wifiOnly = false
        )
    ),
    preloadMaxUrlCount = 1,
    allowedHosts = listOf("h5.example.com")
)
```

## 关键说明

- 黑名单优先级高于 `preloadUrls` 和 `preloadUrlRules`。
- `preloadUrls` 和 `preloadUrlRules` 会合并，不会互相覆盖；同一个 URL 不建议两边重复配置。
- `preloadMaxUrlCount` 控制单次启动最多预热数量。
- `preloadMinIntervalMs` 或 rule 的 `ttlMs` 控制冷却时间。
- `preloadUrls` 自动生成的规则优先级为 0；如果要和高优先级业务页竞争，请改用 `preloadUrlRules`。
- `preloadParallelEnable` 是实验能力，会增加内存峰值。
- 支付、登录、下单、隐私授权、一次性 token 页面不应预热。

## 验证方式

- 不在 `allowedHosts` 的 URL 应被跳过。
- 黑名单 URL 即使在白名单或 rules 中也不应被预热。
- 并行预热开启前要确认高内存和 WiFi 条件。

[返回 README](../README.md)