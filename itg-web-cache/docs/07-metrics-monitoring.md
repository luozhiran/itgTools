# 07. 生产监控与命中率采集

本节说明如何用 `WebCacheEventListener` 和 `WebCacheLogger` 建立线上监控，覆盖策略命中率、预热成功率、预热覆盖率、页面耗时收益、异常原因和清理结果。

## 适用条件

- 准备把 `itg-web-cache` 发布到线上，需要灰度监控和问题回滚依据。
- 需要统计 WebCache 是否真的被业务页面使用。
- 需要判断预热是否成功、是否覆盖到用户实际打开的页面、是否带来耗时收益。
- 需要监控异常、超时、取消、低内存、配置未命中等原因。

## 先明确采集边界

`eventListener` 是业务指标采集入口，`logger` 是 SDK 内部异常日志入口。

当前 Android WebView 不会通过本模块直接告诉你每个静态资源是否从 HTTP cache 命中，所以这里不能承诺“真实资源级 HTTP 缓存命中率”。线上建议采集以下可验证指标：

| 指标 | 是否可直接采集 | 说明 |
| --- | --- | --- |
| 容器策略命中率 | 可以 | `web_cache_container_policy` 中 `reason == "enabled"` 的比例 |
| 预热启动数 | 可以 | `web_cache_preload_start` 计数 |
| 预热成功率 | 可以 | `web_cache_preload_finish / web_cache_preload_start` |
| 预热失败率 | 可以 | `web_cache_preload_error`、`web_cache_preload_timeout` 按 `reason` 聚合 |
| 预热取消率 | 可以 | `web_cache_preload_cancel` 按 `reason` 聚合 |
| 预热覆盖率 | 可以近似 | 预热成功后的 URL，在有效窗口内被正式 WebView 打开 |
| 页面首屏耗时 | 可以 | `web_container_first_paint.elapsedMs` |
| 真实资源级 HTTP cache 命中率 | 不能直接采集 | 需要 H5/网络代理/CDN 日志或 WebViewClient/Performance API 额外配合 |

## 使用场景总览

| 监控目标 | 推荐事件 | 统计口径 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| 策略是否生效 | `web_cache_container_policy` | `reason == "enabled"` / 总数 | 正式 WebView 打开时 | Runtime 每次 `applyToContainer` 都发出策略结果 |
| 页面是否使用缓存模式 | `web_cache_container_policy` | 按 `cacheMode` 聚合 | 判断 `DEFAULT/CACHE_ELSE_NETWORK/NO_CACHE` 占比 | 事件里携带最终 policy 的 `cacheMode` |
| 预热是否启动 | `web_cache_preload_start` | 按 `ruleId/url/scene` 计数 | 判断预热规则是否被选中 | 只有隐藏 WebView 开始加载 URL 时才发出 |
| 预热是否成功 | `web_cache_preload_finish` | finish / start | 判断页面是否能被隐藏 WebView 正常加载 | 完成后携带 `elapsedMs` 和 `reason=finished` |
| 预热失败原因 | `web_cache_preload_error`、`web_cache_preload_timeout` | 按 `reason` 分组 | 排查 WebView 创建失败、加载失败、渲染进程异常、超时 | PreloadManager 对失败原因有明确 reason |
| 预热为什么没跑 | `web_cache_preload_disabled` | 按 `reason` 分组 | 灰度时发现预热量低 | 当前有 `already_running`、`no_eligible_rules` |
| 预热是否被取消 | `web_cache_preload_cancel` | 按 `reason` 分组 | 观察退后台、低内存、打开正式容器冲突 | 取消路径会发出 reason |
| 用户是否打开预热页 | `web_cache_preload_finish` + `web_container_open` | 同 URL/path 在有效窗口内关联 | 计算预热覆盖率 | 预热成功事件和正式容器打开事件都带 URL |
| 页面耗时收益 | `web_container_first_paint` | 对比覆盖/未覆盖、命中/未命中策略的 P50/P90/P95 | 判断是否继续扩大灰度 | Runtime 记录 page start 到 finish 的耗时 |
| 清理是否成功 | `web_cache_clear_start/finish/error` | 按 `reason=http_cache/site_data` 计数 | 缓存污染修复、关闭预热后清理 | Cleaner 发出清理开始和结果 |
| SDK 内部异常 | `WebCacheLogger.log` | message + throwable | 兜底排查 provider、listener、WebView 操作异常 | SafeCallbacks 会捕获并通过 logger 输出 |

## 事件字典

| 事件名 | 来源 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| `web_cache_container_policy` | `WebCacheRuntime.applyToContainer` | `url`、`scene`、`reason`、`cacheMode` | 正式容器缓存策略结果 |
| `web_container_open` | `onContainerPageStarted` | `url`、`reason`、`cacheMode` | 正式 WebView 开始加载 |
| `web_container_first_paint` | `onContainerPageFinished` | `url`、`reason`、`cacheMode`、`elapsedMs` | 页面加载完成耗时，近似首屏指标 |
| `web_cache_container_detach` | `detachContainer` | 无 | 容器状态释放 |
| `web_cache_preload_disabled` | `startNow` | `reason` | 本轮预热未启动 |
| `web_cache_preload_start` | `startNext` | `url`、`ruleId` | 隐藏 WebView 开始预热 |
| `web_cache_preload_finish` | `finishPreload` | `url`、`ruleId`、`reason`、`elapsedMs` | 预热成功完成 |
| `web_cache_preload_error` | `finishPreload` | `url`、`ruleId`、`reason`、`elapsedMs` | 预热失败 |
| `web_cache_preload_timeout` | `finishPreload` | `url`、`ruleId`、`elapsedMs` | 预热超时 |
| `web_cache_preload_cancel` | `cancel/cancelIfConflicts` | `url`、`reason` | 预热取消 |
| `web_cache_clear_start` | `WebCacheCleaner` | `reason` | 清理开始，`reason=http_cache/site_data` |
| `web_cache_clear_finish` | `WebCacheCleaner` | `reason` | 清理成功 |
| `web_cache_clear_error` | `WebCacheCleaner` | `reason` | 清理失败 |

常见 `reason`：

| reason | 含义 | 建议处理 |
| --- | --- | --- |
| `enabled` | 容器策略命中 | 计入策略命中 |
| `kill_switch` | 熔断关闭 | 确认远程配置是否主动关闭 |
| `container_disabled` | 容器策略关闭 | 检查 `containerCacheEnable` |
| `invalid_or_disallowed_url` | URL 非 HTTPS 或 host 不在 `allowedHosts` | 检查 URL 和域名白名单 |
| `url_not_in_container_whitelist` | URL 未命中容器白名单 | 检查容器 URL 白名单 |
| `scene_blacklisted` | scene 命中黑名单 | 正常保护，按业务场景观察 |
| `scene_not_in_whitelist` | scene 未命中白名单 | 检查 scene 传值和白名单配置 |
| `business_cache_mode_exists` | 业务已设置非默认 cacheMode | 检查是否允许 `containerForceOverride` |
| `not_main_thread` | 非主线程调用容器 API | 接入错误，应修调用线程 |
| `already_running` | 已有预热正在执行 | 可能触发频繁，需看调用时机 |
| `no_eligible_rules` | 没有符合条件的预热规则 | 检查开关、运行态、URL、黑名单、登录、WiFi、冷却 |
| `timeout` | 单个 URL 预热超时 | 检查页面性能或降低预热范围 |
| `app_background`、`low_memory`、`low_power`、`logout` | 宿主主动取消原因 | 通常是保护性取消 |
| `container_open_conflict` | 正式容器打开了相同 URL/host | 正常保护，避免隐藏 WebView 与正式页面争抢 |

## 推荐指标口径

| 指标名 | 公式 | 维度 | 说明 |
| --- | --- | --- | --- |
| `container_policy_hit_rate` | `container_policy_enabled / container_policy_total` | `scene`、`cacheMode`、`urlKey`、`version` | 正式容器策略命中率 |
| `preload_success_rate` | `preload_finish / preload_start` | `ruleId`、`urlKey`、`version` | 预热成功率 |
| `preload_error_rate` | `(preload_error + preload_timeout) / preload_start` | `reason`、`ruleId`、`urlKey` | 预热失败率 |
| `preload_cancel_rate` | `preload_cancel / preload_start` | `reason` | 观察低内存、退后台、冲突取消 |
| `preload_coverage_rate` | `covered_container_open / preload_finish` | `scene`、`urlKey`、`ruleId` | 预热成功后是否被用户打开 |
| `first_paint_p50/p90/p95` | `web_container_first_paint.elapsedMs` 分位数 | `scene`、`cacheMode`、`preloadCovered` | 页面耗时收益 |
| `clear_success_rate` | `clear_finish / clear_start` | `reason` | 缓存清理成功率 |
| `sdk_error_count` | `logger.log` 计数 | `message`、`throwableClass` | SDK 内部异常 |

建议生产维度：

- `appVersion`：App 版本。
- `webCacheVersion`：WebCache 配置版本或灰度版本。
- `scene`：业务场景，例如 `home`、`help`、`job_home`。
- `ruleId`：预热规则 ID。
- `urlKey`：脱敏后的 URL key，只保留 `scheme://host/path`。
- `cacheMode`：`default/cache_else_network/no_cache`。
- `reason`：策略未命中、失败或取消原因。
- `networkType`、`isLoggedIn`、`isForeground`：如果业务侧可拿到，可以作为附加维度。

不要上报完整 URL query。query 中可能包含 token、订单号、手机号、搜索词或其他隐私数据。

## 可复制 Demo：接入 eventListener 和 logger

```kotlin
import com.itg.itg_web_cache.WebCacheEvent
import com.itg.itg_web_cache.WebCacheEventListener
import com.itg.itg_web_cache.WebCacheLogger
import com.itg.itg_web_cache.WebCachePreloadManager
import com.itg.itg_web_cache.WebCacheRuntime

val eventListener = WebCacheEventListener { event ->
    WebCacheMetricsCollector.onEvent(event)
}

val logger = object : WebCacheLogger {
    override fun log(message: String, throwable: Throwable?) {
        WebCacheMetricsCollector.onSdkLog(message, throwable)
    }
}
```

接入到 Runtime 和 PreloadManager：

```kotlin
WebCacheRuntime.configure(
    configProvider = configProvider,
    eventListener = eventListener,
    logger = logger
)

WebCachePreloadManager.configure(
    context = context.applicationContext,
    configProvider = configProvider,
    stateProvider = stateProvider,
    eventListener = eventListener,
    logger = logger
)
```

## 可复制 Demo：生产采集器

下面示例只依赖 `WebCacheEvent`，可以直接放到业务层后替换 `reportCounter/reportTimer/reportLog` 为你的埋点 SDK。

```kotlin
import android.net.Uri
import com.itg.itg_web_cache.WebCacheEvent
import java.util.LinkedHashMap

object WebCacheMetricsCollector {
    private const val PRELOAD_COVER_WINDOW_MS = 30 * 60 * 1000L
    private const val MAX_PRELOAD_RECORDS = 200

    private val preloadedUrls = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_PRELOAD_RECORDS
        }
    }

    @Synchronized
    fun onEvent(event: WebCacheEvent) {
        val urlKey = event.url?.let { normalizeUrlKey(it) }
        val baseTags = mutableMapOf<String, String>()
        urlKey?.let { baseTags["urlKey"] = it }
        event.scene?.let { baseTags["scene"] = it }
        event.ruleId?.let { baseTags["ruleId"] = it }
        event.reason?.let { baseTags["reason"] = it }
        event.cacheMode?.configValue?.let { baseTags["cacheMode"] = it }

        when (event.name) {
            "web_cache_container_policy" -> {
                reportCounter("web_cache.container.policy.total", baseTags)
                if (event.reason == "enabled") {
                    reportCounter("web_cache.container.policy.enabled", baseTags)
                } else {
                    reportCounter("web_cache.container.policy.disabled", baseTags)
                }
            }

            "web_container_open" -> {
                val preloadCovered = urlKey?.let { isPreloadCovered(it, event.timestampMs) } ?: false
                reportCounter(
                    "web_cache.container.open",
                    baseTags + ("preloadCovered" to preloadCovered.toString())
                )
            }

            "web_container_first_paint" -> {
                val preloadCovered = urlKey?.let { isPreloadCovered(it, event.timestampMs) } ?: false
                event.elapsedMs?.let { elapsed ->
                    reportTimer(
                        "web_cache.container.first_paint_ms",
                        elapsed,
                        baseTags + ("preloadCovered" to preloadCovered.toString())
                    )
                }
            }

            "web_cache_preload_start" -> {
                reportCounter("web_cache.preload.start", baseTags)
            }

            "web_cache_preload_finish" -> {
                if (urlKey != null) {
                    preloadedUrls[urlKey] = event.timestampMs
                }
                reportCounter("web_cache.preload.finish", baseTags)
                event.elapsedMs?.let { elapsed ->
                    reportTimer("web_cache.preload.elapsed_ms", elapsed, baseTags)
                }
            }

            "web_cache_preload_error",
            "web_cache_preload_timeout" -> {
                reportCounter("web_cache.preload.error", baseTags + ("event" to event.name))
                event.elapsedMs?.let { elapsed ->
                    reportTimer("web_cache.preload.error_elapsed_ms", elapsed, baseTags)
                }
            }

            "web_cache_preload_cancel" -> {
                reportCounter("web_cache.preload.cancel", baseTags)
            }

            "web_cache_preload_disabled" -> {
                reportCounter("web_cache.preload.disabled", baseTags)
            }

            "web_cache_clear_start" -> {
                reportCounter("web_cache.clear.start", baseTags)
            }

            "web_cache_clear_finish" -> {
                reportCounter("web_cache.clear.finish", baseTags)
            }

            "web_cache_clear_error" -> {
                reportCounter("web_cache.clear.error", baseTags)
            }
        }
    }

    fun onSdkLog(message: String, throwable: Throwable?) {
        reportLog(
            "web_cache.sdk.log",
            mapOf(
                "message" to message,
                "throwableClass" to throwable?.javaClass?.name.orEmpty(),
                "throwableMessage" to throwable?.message.orEmpty()
            )
        )
    }

    private fun isPreloadCovered(urlKey: String, openTimeMs: Long): Boolean {
        val preloadTime = preloadedUrls[urlKey] ?: return false
        return openTimeMs >= preloadTime && openTimeMs - preloadTime <= PRELOAD_COVER_WINDOW_MS
    }

    private fun normalizeUrlKey(url: String): String {
        val uri = Uri.parse(url)
        val scheme = uri.scheme.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        return "$scheme://$host$path"
    }

    private fun reportCounter(name: String, tags: Map<String, String>) {
        // TODO 接入你的埋点系统，例如 Analytics.trackCounter(name, tags)
    }

    private fun reportTimer(name: String, valueMs: Long, tags: Map<String, String>) {
        // TODO 接入你的埋点系统，例如 Analytics.trackTimer(name, valueMs, tags)
    }

    private fun reportLog(name: String, fields: Map<String, String>) {
        // TODO 接入你的日志系统，例如 LogReporter.report(name, fields)
    }
}
```

## 可复制 Demo：按 session 计算预热覆盖

如果你的埋点系统支持服务端计算，客户端只需要上报事件。服务端可以按 `deviceId/sessionId/urlKey` 做关联：

```text
1. 收到 web_cache_preload_finish(urlKey=A, t1)
2. 在 t1 后 30 分钟内收到 web_container_open(urlKey=A, t2)
3. 认为本次正式打开被预热覆盖：preloadCovered=true
```

客户端本地计算适合快速接入；服务端计算更适合生产，因为可以跨页面、跨进程、跨埋点批次聚合。

## 看板建议

上线灰度时建议至少建 5 个看板：

| 看板 | 指标 | 目的 |
| --- | --- | --- |
| 策略命中 | `container_policy_hit_rate`、disabled reason 分布 | 确认配置是否真正作用到正式容器 |
| 预热质量 | `preload_success_rate`、`preload_error_rate`、timeout P95 | 判断隐藏 WebView 预热是否稳定 |
| 预热覆盖 | `preload_coverage_rate`、覆盖/未覆盖打开量 | 判断预热是否覆盖用户真实路径 |
| 页面收益 | `first_paint_ms` P50/P90/P95，按 `preloadCovered/cacheMode` 对比 | 判断是否有性能收益 |
| 资源和风险 | cancel reason、low_memory、render_process_gone、sdk log | 判断是否影响稳定性 |

## 告警建议

具体阈值要按业务基线调整。灰度初期可以先用这些保守规则：

| 告警 | 建议阈值 | 处理动作 |
| --- | --- | --- |
| `web_cache.preload.error` 突增 | 连续 10 分钟错误率 > 20% | 降低 `preloadMaxUrlCount` 或关闭预热 |
| timeout 突增 | P95 接近 `preloadTimeoutMs` 或 timeout 率 > 10% | 下线慢页面规则，检查 H5 性能 |
| `render_process_gone/crash` 出现 | 任意明显高于基线 | 立即缩小灰度，必要时打开 `killSwitch` |
| `low_memory` 取消明显增加 | 高于对照版本 | 关闭并行，降低预热数量和 post finish delay |
| `not_main_thread` 出现 | 任意出现都应处理 | 修正业务调用线程 |
| `invalid_or_disallowed_url` 大量出现 | 超过策略事件 10% | 检查 `allowedHosts` 和远程配置 URL |
| 首屏 P95 变差 | 灰度组比对照组差 > 10% | 回滚容器 cacheMode 或关闭预热 |

## 生产接入注意事项

- `eventListener.onEvent` 不要做磁盘 IO、网络 IO 或复杂计算；建议只入队，后台批量上报。
- 不要上报完整 URL query，统一用 `scheme://host/path` 作为 `urlKey`。
- `ruleId` 要保持稳定，否则预热成功率和失败率会被拆散。
- `scene` 应由业务显式传入，避免全部为空导致看板无法定位页面。
- 预热覆盖率是近似指标，不等于真实 HTTP cache 命中率。
- 页面耗时要和对照组比较，不要只看灰度组自身波动。
- 监控要带配置版本，方便判断某次远程配置变更是否导致异常。
- `logger` 采集的是 SDK 内部异常，不应替代业务指标。

## 验证方式

灰度前可以用以下步骤验证采集链路：

1. `containerCacheEnable = true` 且 URL 命中白名单，打开正式 WebView，应看到 `web_cache_container_policy reason=enabled`。
2. 调用 `WebCachePreloadManager.startAfterHomeReady()`，应看到 `web_cache_preload_start`，成功后看到 `web_cache_preload_finish`。
3. 把 URL 移出 `allowedHosts`，应看到 `web_cache_container_policy reason=invalid_or_disallowed_url` 或 `web_cache_preload_disabled reason=no_eligible_rules`。
4. 把 `preloadTimeoutMs` 临时调小，应看到 `web_cache_preload_timeout`。
5. 打开与预热 URL 相同的正式页面，应看到 `web_container_open` 和 `web_container_first_paint`，并能关联出 `preloadCovered=true`。
6. 调用 `WebCacheCleaner.clearByPolicy(..., HTTP_CACHE)`，应看到 `web_cache_clear_start` 和 `web_cache_clear_finish`。
7. 后台埋点看板中确认 counter、timer、log 三类数据都能按 `scene/ruleId/reason/urlKey` 查询。

[返回 README](../README.md)
