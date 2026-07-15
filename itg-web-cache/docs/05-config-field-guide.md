# 05. 复杂配置字段学习

本节按真实使用场景解释 `WebCacheConfig` 中容易混淆的配置字段，帮助你判断字段该不该配、什么时候生效、和其他字段如何组合。

## 适用条件

- 需要把远程配置、本地兜底配置或默认值映射到 `WebCacheConfig`。
- 需要配置预热、正式容器缓存、缓存清理或安全域名。
- 不确定某个字段与其他字段的优先级关系。

## Demo 公共 import

本页 Kotlin 示例默认使用以下 import，复制到业务代码时按实际用到的类型保留即可：

```kotlin
import com.itg.itg_web_cache.CacheModeOption
import com.itg.itg_web_cache.PreloadUrlRule
import com.itg.itg_web_cache.WebCacheClearPolicy
import com.itg.itg_web_cache.WebCacheConfig
```

## 字段场景总览

| 配置目标 | 相关字段 | 生效范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| 紧急关闭所有能力 | `killSwitch` | 预热和正式容器策略 | 线上出现崩溃、内存异常、缓存策略异常 | Resolver 首先检查 `killSwitch`，命中后预热返回空队列，容器策略返回 disabled |
| 开启后台预热 | `preloadEnable` | 隐藏 WebView 预热 | 灰度验证低风险 H5 页面预热收益 | `selectPreloadRules()` 只有在开关开启且运行态满足时才选择 URL |
| 选择预热 URL | `preloadUrls`、`preloadUrlRules`、`preloadUrlBlacklist`、`allowedHosts` | 预热候选队列 | 需要配置固定 URL、复杂规则或排除高风险页面 | 源码会合并 URL 与规则，再经过域名、黑名单、登录态、网络和优先级筛选 |
| 控制本轮预热数量 | `preloadMaxUrlCount` | 单次预热触发 | 候选 URL 多，但希望控制资源消耗 | 筛选后按 `priority` 降序执行 `.take(preloadMaxUrlCount)` |
| 控制预热时序 | `preloadDelayMs`、`preloadTimeoutMs`、`preloadPostFinishDelayMs`、`preloadMinIntervalMs` | 单个预热任务和冷却 | 避免抢首页资源、避免隐藏 WebView 长时间存活、避免重复预热 | 预热管理器按延迟、主线程空闲、超时、完成后等待和冷却时间调度任务 |
| 限制网络和登录态 | `preloadWifiOnly`、`preloadLoginRequired`、规则级 `wifiOnly/loginRequired` | 预热筛选 | 页面依赖登录态或不希望消耗移动流量 | Resolver 在入队前过滤不满足网络和登录条件的规则 |
| 实验性并行预热 | `preloadParallelEnable`、`preloadParallelCount`、`preloadParallelWifiOnly`、`preloadParallelHighMemoryOnly` | 预热执行方式 | 多个页面都低风险且希望缩短预热总耗时 | `resolvePreloadParallelCount()` 在运行态允许时返回并行数，当前源码限制在 `1..2` |
| 一次性清理缓存 | `clearCacheVersion`、`disableClearPolicy`、`disableClearVersion` | 应用级 WebView 缓存 | 缓存污染、灰度回滚、关闭预热后需要处理历史缓存 | 版本号用于保证一次性执行，策略区分通用清理和关闭预热后的清理 |
| 正式容器缓存策略 | `containerCacheEnable`、`containerCacheMode`、`containerUrlWhitelist`、`containerSceneWhitelist`、`containerSceneBlacklist`、`containerForceOverride` | 正式 WebView | 页面加载时希望使用默认缓存、缓存优先或禁用缓存 | Runtime 调用 resolver 判断 URL/scene，再改写 `webView.settings.cacheMode` |
| 安全域名控制 | `allowedHosts` | 预热和正式容器策略 | 远程配置可能被误配或污染，需要限制业务域名 | URL 校验要求 host 命中 `allowedHosts`；为空时拒绝所有 URL，生产必须配置 |

## 配置来源与生效时机

`WebCacheConfig` 通常来自远程动态配置、本地 Assets 兜底配置和字段默认值。源码注释中的优先级是：

```text
远程动态配置 > 本地 Assets 配置 > WebCacheConfig 默认值
```

不同字段的生效时机不同：

| 字段类型 | 生效时机 | 说明 |
| --- | --- | --- |
| 预热开关和 URL 规则 | 下次冷启动或下次触发预热流程 | 取决于业务何时重新读取配置并调用预热 |
| 正式容器策略 | 下次调用 `WebCacheRuntime.applyToContainer()` | `configProvider` 会在应用策略时读取当前配置 |
| 清理版本号 | 下次冷启动检测时执行一次 | 需要业务侧保存“已处理版本”，避免重复清理 |

## 优先级与组合规则

源码的核心判断顺序可以简化为：

```text
1. killSwitch 最高优先级
2. 预热：preloadEnable + 前后台/首页就绪/内存/低电量/网络状态
3. 预热：preloadUrls + preloadUrlRules 合并
4. 预热：enable、熔断、allowedHosts、黑名单、登录态、WiFi、冷却时间
5. 预热：priority 降序，take(preloadMaxUrlCount)
6. 预热执行：preloadParallelEnable 决定是否尝试并行
7. 容器：containerCacheEnable + allowedHosts + URL 白名单 + scene 黑白名单
8. 容器：containerForceOverride 决定是否覆盖业务已有 cacheMode
```

需要特别注意：

- `killSwitch = true` 会同时关闭预热和正式容器缓存策略。
- `allowedHosts` 同时影响预热 URL 和正式容器 URL；为空时拒绝所有 URL，生产环境必须配置。
- `preloadUrlBlacklist` 的优先级高于 `preloadUrls` 和 `preloadUrlRules`。
- `containerSceneBlacklist` 优先级高于 `containerSceneWhitelist`。
- `preloadMaxUrlCount` 只决定本轮选多少个 URL，不决定并发数。
- `preloadParallelEnable` 只决定执行方式，运行态不满足时会退回串行。
- `containerForceOverride = false` 时，如果业务已经设置非默认 `cacheMode`，Runtime 不会覆盖。

## 字段分组说明

### 预热总开关

| 字段 | 默认值 | 远程配置 key | 说明 |
| --- | --- | --- | --- |
| `preloadEnable` | `false` | `web_cache_preload_enable` | 预热功能总开关，默认关闭，建议灰度后开启 |
| `killSwitch` | `false` | `web_cache_preload_kill_switch` | 紧急熔断开关，优先级最高 |

最小开启配置：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    killSwitch = false,
    preloadUrls = listOf("https://h5.example.com/home"),
    allowedHosts = listOf("h5.example.com")
)
```

### 预热 URL 与规则

| 字段 | 默认值 | 远程配置 key | 说明 |
| --- | --- | --- | --- |
| `preloadUrls` | `emptyList()` | `web_cache_preload_urls` | 简单固定 URL 列表，源码会自动转成优先级为 0 的规则 |
| `preloadUrlRules` | `emptyList()` | `web_cache_preload_url_rules` | 精细规则列表，可配置 `id`、`priority`、`ttlMs`、登录态、WiFi 等 |
| `preloadUrlBlacklist` | `emptyList()` | `web_cache_preload_url_blacklist` | 预热黑名单，命中后直接跳过 |
| `preloadMaxUrlCount` | `1` | `web_cache_preload_max_url_count` | 本轮最多选入队列的 URL 数量 |
| `allowedHosts` | `emptyList()` | `web_cache_allowed_hosts` | 允许预热和应用容器策略的 host 白名单；为空时拒绝所有 URL |

精细规则配置：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadUrlRules = listOf(
        PreloadUrlRule(
            id = "home_v1",
            url = "https://h5.example.com/home",
            scene = "home",
            priority = 100,
            ttlMs = 30 * 60 * 1000L,
            loginRequired = true,
            wifiOnly = true
        ),
        PreloadUrlRule(
            id = "help_v1",
            url = "https://h5.example.com/help",
            scene = "help",
            priority = 10,
            loginRequired = false,
            wifiOnly = false
        )
    ),
    preloadUrlBlacklist = listOf(
        "https://h5.example.com/pay",
        "https://h5.example.com/order/create"
    ),
    preloadMaxUrlCount = 2,
    allowedHosts = listOf("h5.example.com")
)
```

### 预热时序与冷却

| 字段 | 默认值 | 远程配置 key | 说明 |
| --- | --- | --- | --- |
| `preloadDelayMs` | `2000L` | `web_cache_preload_delay_ms` | 首页进入后延迟多久允许预热；延迟结束后还会等待主线程空闲再启动 |
| `preloadTimeoutMs` | `15000L` | `web_cache_preload_timeout_ms` | 单个 URL 预热加载超时时间 |
| `preloadPostFinishDelayMs` | `3000L` | `web_cache_preload_post_finish_delay_ms` | `onPageFinished` 后继续等待异步资源的时间 |
| `preloadMinIntervalMs` | `30 * 60 * 1000L` | `web_cache_preload_min_interval_ms` | 全局冷却时间，规则级 `ttlMs` 为空时使用；预热成功和正式容器加载成功都会参与冷却 |

保守资源配置：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadDelayMs = 3000L,
    preloadTimeoutMs = 10000L,
    preloadPostFinishDelayMs = 2000L,
    preloadMinIntervalMs = 60 * 60 * 1000L,
    preloadMaxUrlCount = 1,
    allowedHosts = listOf("h5.example.com")
)
```

### 网络、登录态与并行预热

| 字段 | 默认值 | 远程配置 key | 说明 |
| --- | --- | --- | --- |
| `preloadWifiOnly` | `false` | `web_cache_preload_wifi_only` | 全局限制预热只在 WiFi 下执行 |
| `preloadLoginRequired` | `true` | `web_cache_preload_login_required` | 全局登录态门禁；规则级 `loginRequired` 只有在它为 true 时才生效 |
| `preloadParallelEnable` | `false` | `web_cache_preload_parallel_enable` | 是否尝试并行预热 |
| `preloadParallelCount` | `1` | `web_cache_preload_parallel_count` | 并行数量，当前源码会限制到 `1..2` |
| `preloadParallelWifiOnly` | `true` | `web_cache_preload_parallel_wifi_only` | 并行预热是否只允许 WiFi |
| `preloadParallelHighMemoryOnly` | `true` | `web_cache_preload_parallel_high_memory_only` | 并行预热是否只允许高内存设备 |

生产默认建议使用串行：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadWifiOnly = true,
    preloadLoginRequired = true,
    preloadParallelEnable = false,
    preloadMaxUrlCount = 1,
    allowedHosts = listOf("h5.example.com")
)
```

实验性并行灰度：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadWifiOnly = true,
    preloadMaxUrlCount = 3,
    preloadParallelEnable = true,
    preloadParallelCount = 2,
    preloadParallelWifiOnly = true,
    preloadParallelHighMemoryOnly = true,
    allowedHosts = listOf("h5.example.com")
)
```

### 缓存清理

| 字段 | 默认值 | 远程配置 key | 说明 |
| --- | --- | --- | --- |
| `clearCacheVersion` | `null` | `web_cache_preload_clear_cache_version` | 通用 WebView HTTP 缓存清理版本号 |
| `disableClearPolicy` | `WebCacheClearPolicy.NONE` | `web_cache_preload_disable_clear_policy` | 关闭预热后的历史缓存处理策略 |
| `disableClearVersion` | `null` | `web_cache_preload_disable_clear_version` | 关闭预热后触发一次性清理的独立版本号 |

缓存污染修复配置：

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    clearCacheVersion = "h5-cache-fix-20260715-001",
    disableClearPolicy = WebCacheClearPolicy.NONE,
    disableClearVersion = null
)
```

关闭预热但保留历史缓存：

```kotlin
val config = WebCacheConfig(
    preloadEnable = false,
    disableClearPolicy = WebCacheClearPolicy.NONE,
    disableClearVersion = null
)
```

### 正式容器缓存策略

| 字段 | 默认值 | 远程配置 key | 说明 |
| --- | --- | --- | --- |
| `containerCacheEnable` | `false` | `web_cache_container_cache_enable` | 正式 WebView 容器缓存策略总开关 |
| `containerCacheMode` | `CacheModeOption.DEFAULT` | `web_cache_container_cache_mode` | Runtime 应用到 `WebSettings.cacheMode` 的缓存模式 |
| `containerUrlWhitelist` | `emptyList()` | `web_cache_container_url_whitelist` | 允许应用缓存策略的 URL 白名单 |
| `containerSceneWhitelist` | `emptyList()` | `web_cache_container_scene_whitelist` | 允许应用缓存策略的 scene 白名单，非空时必须命中 |
| `containerSceneBlacklist` | `emptyList()` | `web_cache_container_scene_blacklist` | 禁止应用缓存策略的 scene 黑名单 |
| `containerForceOverride` | `false` | `web_cache_container_force_override` | 是否覆盖业务已显式设置的非默认 `cacheMode` |

低风险页面使用缓存优先：

```kotlin
val config = WebCacheConfig(
    containerCacheEnable = true,
    containerCacheMode = CacheModeOption.CACHE_ELSE_NETWORK,
    containerUrlWhitelist = listOf("https://h5.example.com/home"),
    containerSceneWhitelist = listOf("home", "help"),
    containerSceneBlacklist = listOf("pay", "order_create"),
    containerForceOverride = false,
    allowedHosts = listOf("h5.example.com")
)
```

实时页面保持默认协议缓存：

```kotlin
val config = WebCacheConfig(
    containerCacheEnable = true,
    containerCacheMode = CacheModeOption.DEFAULT,
    containerUrlWhitelist = listOf("https://h5.example.com/quote"),
    containerSceneBlacklist = listOf("pay", "order_create"),
    containerForceOverride = false,
    allowedHosts = listOf("h5.example.com")
)
```

## 推荐配置模板

### 灰度初期

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    killSwitch = false,
    preloadUrlRules = listOf(
        PreloadUrlRule(
            id = "home_gray_v1",
            url = "https://h5.example.com/home",
            scene = "home",
            priority = 100,
            ttlMs = 60 * 60 * 1000L,
            loginRequired = true,
            wifiOnly = true
        )
    ),
    preloadMaxUrlCount = 1,
    preloadDelayMs = 3000L,
    preloadTimeoutMs = 10000L,
    preloadPostFinishDelayMs = 2000L,
    preloadWifiOnly = true,
    preloadParallelEnable = false,
    containerCacheEnable = true,
    containerCacheMode = CacheModeOption.DEFAULT,
    containerUrlWhitelist = listOf("https://h5.example.com/home"),
    containerSceneBlacklist = listOf("pay", "order_create", "login"),
    allowedHosts = listOf("h5.example.com")
)
```

### 稳定后扩大预热范围

```kotlin
val config = WebCacheConfig(
    preloadEnable = true,
    preloadUrlRules = listOf(
        PreloadUrlRule(
            id = "home_v2",
            url = "https://h5.example.com/home",
            scene = "home",
            priority = 100,
            ttlMs = 30 * 60 * 1000L,
            wifiOnly = true
        ),
        PreloadUrlRule(
            id = "help_v2",
            url = "https://h5.example.com/help",
            scene = "help",
            priority = 50,
            ttlMs = 60 * 60 * 1000L,
            loginRequired = false,
            wifiOnly = true
        )
    ),
    preloadMaxUrlCount = 2,
    preloadParallelEnable = false,
    containerCacheEnable = true,
    containerCacheMode = CacheModeOption.CACHE_ELSE_NETWORK,
    containerUrlWhitelist = listOf(
        "https://h5.example.com/home",
        "https://h5.example.com/help"
    ),
    allowedHosts = listOf("h5.example.com")
)
```

### 线上异常紧急熔断

```kotlin
val config = WebCacheConfig(
    killSwitch = true,
    preloadEnable = false,
    containerCacheEnable = false
)
```

## 常见误区

- `preloadEnable = false` 只关闭预热，不代表正式容器不能继续使用历史缓存；容器策略由 `containerCacheEnable` 单独控制。
- `killSwitch = true` 不是普通预热开关，它会同时让正式容器策略不生效。
- `allowedHosts = emptyList()` 表示拒绝所有 URL，预热和正式容器策略都不会生效；生产环境必须配置业务 host。
- `preloadMaxUrlCount = 3` 不表示同时预热 3 个，只表示本轮最多选 3 个进入队列。
- `preloadParallelCount = 3` 当前不会真的并发 3 个，源码会把并行数限制在 `1..2`。
- `preloadLoginRequired = false` 会让规则级 `loginRequired` 不再拦截未登录状态，需确认页面不会缓存错误页。
- `containerUrlWhitelist` 和 `containerSceneWhitelist` 是 AND 关系；scene 白名单非空时，URL 命中但 scene 为空也不会应用策略。
- `containerForceOverride = true` 会覆盖业务已设置的 `cacheMode`，只建议在确认业务没有特殊缓存意图时使用。

## 验证方式

- 通过 `WebCacheEvent.reason` 确认策略是否命中，例如 `kill_switch`、`container_disabled`、`url_not_in_container_whitelist`、`scene_blacklisted`。
- 配置 `allowedHosts` 后，用非白名单 host 验证预热和容器策略都不会生效。
- 配置 `preloadMaxUrlCount = 1` 后，确认本轮只选优先级最高的规则。`n- 正式容器打开并加载成功后，再触发同 URL 预热，应因为 URL 冷却被跳过。
- 配置 `containerForceOverride = false` 后，先由业务设置非默认 `cacheMode`，确认 Runtime 返回 `business_cache_mode_exists`。
- 缓存清理版本号变化后，应只执行一次清理，并记录已处理版本。

[返回 README](../README.md)
