# 06. 运行态状态接入

本节说明 `WebCacheRuntimeState` 的真实消费链路，以及如何用 App 消息或生命周期事件更新状态快照。

## 适用条件

- 需要让预热只在 App 前台、首页稳定、网络合适、用户状态正确时执行。
- 需要把登录、网络、低内存、低电量等事件接入 WebCache 预热判断。
- 已经给 `WebCacheRuntimeState` 字段设置了值，但不确定这些值在哪里生效。

## 先明确一件事

`WebCacheRuntimeState` 不是全局状态中心，也没有自动监听能力。当前源码中，它只通过 `WebCacheStateProvider.getState()` 被 `WebCachePreloadManager.startNow()` 读取：

```text
WebCachePreloadManager.startNow()
  -> readState()
  -> stateProvider.getState()
  -> WebCachePolicyResolver.selectPreloadRules(config, state, ...)
  -> WebCachePolicyResolver.resolvePreloadParallelCount(config, state)
```

因此，更新状态字段本身不会立刻触发预热、取消预热或重新筛选。你需要：

- 用宿主 App 自己维护一份最新 `WebCacheRuntimeState` 快照。
- 在 `WebCachePreloadManager.configure(stateProvider = ...)` 中返回这份快照。
- 首页准备好后调用 `startAfterHomeReady()` 或 `startNow()`，此时才会读取最新状态。
- 进入后台、低内存等需要立即停止的场景，主动调用 `WebCachePreloadManager.cancel(...)` 或 `onTrimMemory(...)`。

## 字段使用总览

| 状态字段 | 推荐消息来源 | 被源码怎么使用 | 不满足时结果 |
| --- | --- | --- | --- |
| `isForeground` | App 前后台生命周期 | `selectPreloadRules()` 前置条件；`resolvePreloadParallelCount()` 并行条件 | 本轮不预热；并行降级为串行 |
| `isHomeReady` | 首页首屏完成、核心接口完成 | `selectPreloadRules()` 前置条件 | 首页未就绪时本轮不预热 |
| `isLoggedIn` | 登录成功、退出登录、Token 失效 | 登录依赖规则筛选 | 登录依赖页面被跳过 |
| `networkType` | 网络连接变化 | WiFi 约束和并行 WiFi 约束 | 非 WiFi 时跳过 WiFi-only 规则或并行降级 |
| `isLowMemory` | `onTrimMemory`、系统低内存回调 | `selectPreloadRules()` 前置条件；并行条件 | 本轮不预热；并行降级 |
| `isLowPowerMode` | 省电模式广播、PowerManager | `selectPreloadRules()` 前置条件；并行条件 | 本轮不预热；并行降级 |
| `isHighMemoryDevice` | 启动时设备内存判断 | 并行预热条件 | 非高内存设备并行降级为串行 |

## 推荐做法

用一个轻量的状态持有器接收 App 消息，`stateProvider` 只负责返回最新快照：

```kotlin
WebCachePreloadManager.configure(
    context = applicationContext,
    configProvider = configProvider,
    stateProvider = WebCacheStateProvider { WebCacheRuntimeStateStore.current() },
    eventListener = eventListener,
    logger = logger
)
```

## 可复制 Demo：消息驱动状态快照

下面示例用一个简单的 `sealed class` 表示 App 内部消息。你也可以把这些消息替换成 LiveData、Flow、EventBus、RxJava、广播接收器或自己的消息总线。

```kotlin
import android.app.Application
import android.content.ComponentCallbacks2
import com.itg.itg_web_cache.NetworkType
import com.itg.itg_web_cache.WebCacheConfigProvider
import com.itg.itg_web_cache.WebCacheEventListener
import com.itg.itg_web_cache.WebCacheLogger
import com.itg.itg_web_cache.WebCachePreloadManager
import com.itg.itg_web_cache.WebCacheRuntimeState
import com.itg.itg_web_cache.WebCacheStateProvider

sealed class WebCacheStateMessage {
    data class ForegroundChanged(val foreground: Boolean) : WebCacheStateMessage()
    data class HomeReadyChanged(val ready: Boolean) : WebCacheStateMessage()
    data class LoginChanged(val loggedIn: Boolean) : WebCacheStateMessage()
    data class NetworkChanged(val type: NetworkType) : WebCacheStateMessage()
    data class LowMemoryChanged(val lowMemory: Boolean) : WebCacheStateMessage()
    data class LowPowerChanged(val lowPower: Boolean) : WebCacheStateMessage()
    data class HighMemoryDeviceResolved(val highMemory: Boolean) : WebCacheStateMessage()
}

object WebCacheRuntimeStateStore {
    @Volatile
    private var state = WebCacheRuntimeState(
        isForeground = false,
        isHomeReady = false,
        isLoggedIn = false,
        networkType = NetworkType.NONE,
        isLowMemory = false,
        isLowPowerMode = false,
        isHighMemoryDevice = false
    )

    fun current(): WebCacheRuntimeState = state

    @Synchronized
    fun dispatch(message: WebCacheStateMessage) {
        val old = state
        state = when (message) {
            is WebCacheStateMessage.ForegroundChanged -> {
                old.copy(isForeground = message.foreground)
            }
            is WebCacheStateMessage.HomeReadyChanged -> {
                old.copy(isHomeReady = message.ready)
            }
            is WebCacheStateMessage.LoginChanged -> {
                old.copy(isLoggedIn = message.loggedIn)
            }
            is WebCacheStateMessage.NetworkChanged -> {
                old.copy(networkType = message.type)
            }
            is WebCacheStateMessage.LowMemoryChanged -> {
                old.copy(isLowMemory = message.lowMemory)
            }
            is WebCacheStateMessage.LowPowerChanged -> {
                old.copy(isLowPowerMode = message.lowPower)
            }
            is WebCacheStateMessage.HighMemoryDeviceResolved -> {
                old.copy(isHighMemoryDevice = message.highMemory)
            }
        }
    }
}

fun setupWebCachePreload(
    application: Application,
    configProvider: WebCacheConfigProvider,
    eventListener: WebCacheEventListener,
    logger: WebCacheLogger
) {
    WebCachePreloadManager.configure(
        context = application,
        configProvider = configProvider,
        stateProvider = WebCacheStateProvider { WebCacheRuntimeStateStore.current() },
        eventListener = eventListener,
        logger = logger
    )
}
```

触发预热时，先更新状态，再启动：

```kotlin
fun onHomeFirstScreenReady() {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.HomeReadyChanged(ready = true)
    )
    WebCachePreloadManager.startAfterHomeReady()
}
```

进入后台或退出首页时，除了更新状态，还建议取消正在执行的预热：

```kotlin
fun onAppForegroundChanged(foreground: Boolean) {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.ForegroundChanged(foreground)
    )
    if (!foreground) {
        WebCachePreloadManager.cancel("app_background")
    }
}

fun onLeaveHomePage() {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.HomeReadyChanged(ready = false)
    )
    WebCachePreloadManager.cancel("leave_home")
}
```

低内存消息建议直接走管理器已有 API：

```kotlin
fun onTrimMemory(level: Int) {
    val lowMemory = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.LowMemoryChanged(lowMemory)
    )
    WebCachePreloadManager.onTrimMemory(level)
}
```

## 可复制 Demo：接入常见 App 消息

下面是伪代码级接入示例，重点是说明消息与字段的对应关系：

```kotlin
// App 前后台
fun onProcessStart() {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.ForegroundChanged(true)
    )
}

fun onProcessStop() {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.ForegroundChanged(false)
    )
    WebCachePreloadManager.cancel("process_stop")
}

// 登录态
fun onLoginSuccess() {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.LoginChanged(true)
    )
}

fun onLogout() {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.LoginChanged(false)
    )
    WebCachePreloadManager.cancel("logout")
}

// 网络状态
fun onNetworkChanged(type: NetworkType) {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.NetworkChanged(type)
    )
}

// 低电量模式
fun onPowerSaveModeChanged(enabled: Boolean) {
    WebCacheRuntimeStateStore.dispatch(
        WebCacheStateMessage.LowPowerChanged(enabled)
    )
    if (enabled) {
        WebCachePreloadManager.cancel("low_power")
    }
}
```

## 与配置字段的关系

`WebCacheRuntimeState` 是运行态条件，`WebCacheConfig` 是策略配置。两者同时满足才会预热。

| 配置字段 | 依赖的状态字段 | 说明 |
| --- | --- | --- |
| `preloadEnable` | 无 | 开关关闭时不会预热 |
| `preloadWifiOnly` | `networkType` | true 时必须是 `NetworkType.WIFI` |
| `preloadLoginRequired` | `isLoggedIn` | true 时，登录依赖规则需要已登录 |
| `PreloadUrlRule.wifiOnly` | `networkType` | 规则级 WiFi 约束 |
| `PreloadUrlRule.loginRequired` | `isLoggedIn` | 只有 `preloadLoginRequired = true` 时才参与拦截 |
| `preloadParallelEnable` | `networkType`、`isHighMemoryDevice`、`isLowMemory`、`isLowPowerMode`、`isForeground` | 不满足时并行数退回 1 |

## 常见误区

- 只修改 `WebCacheRuntimeState` 不会自动触发预热；需要调用 `startAfterHomeReady()` 或 `startNow()`。
- 只把 `isForeground` 改成 false 不会自动取消已经运行的隐藏 WebView；需要主动调用 `cancel("app_background")`。
- `isHomeReady = true` 不代表立即执行预热；它只是下一次筛选时允许通过首页就绪条件。
- `networkType = WIFI` 只表示网络条件满足，还要同时满足开关、域名、黑名单、登录态、内存和冷却时间。
- `isHighMemoryDevice = true` 只影响并行预热，不会让预热自动开启。
- `stateProvider.getState()` 抛异常时，源码会回退为 `WebCacheRuntimeState(isForeground = false)`，本轮不会预热。

## 验证方式

- 将 `isHomeReady = false` 后调用 `startNow()`，应收到 `web_cache_preload_disabled` 且 `reason = no_eligible_rules`。
- 将 `networkType = CELLULAR` 且配置 `preloadWifiOnly = true`，本轮不应预热。
- 将 `isLoggedIn = false` 且规则 `loginRequired = true`，登录依赖规则应被跳过。
- 将 `preloadParallelEnable = true` 但 `isHighMemoryDevice = false`，预热应退回串行。
- 进入后台时主动调用 `cancel("app_background")`，应收到 `web_cache_preload_cancel` 事件。

[返回 README](../README.md)