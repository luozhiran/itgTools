# 04. 清理与 API

本节说明缓存清理、版本号和 API 速查。

## 适用条件

- H5 缓存污染，需要一次性清理。
- 关闭预热后需要按策略处理历史缓存。
- 需要查看 WebCache 的核心类型。

## 推荐做法

```kotlin
WebCacheConfig(clearCacheVersion = "2026-07-15-001")
```

## 可复制 Demo

```kotlin
import com.itg.itg_web_cache.WebCacheClearPolicy
import com.itg.itg_web_cache.WebCacheConfig

val config = WebCacheConfig(
    preloadEnable = false,
    clearCacheVersion = "h5-cache-fix-001",
    disableClearPolicy = WebCacheClearPolicy.NONE,
    disableClearVersion = null
)
```

## 关键说明

- WebView HTTP 缓存是应用级，不是某个 URL 独占。
- `clearCacheVersion` 适合缓存污染修复，版本变化时执行一次。
- 关闭预热默认不清历史缓存，避免影响正常浏览收益。
- `disableClearPolicy` 专门描述关闭预热后的历史缓存处理。
- 缓存清理不要频繁触发，需配合灰度和监控。

## API 速查

| 类型 | 说明 |
| --- | --- |
| `WebCacheRuntime` | 正式容器策略入口和事件入口 |
| `WebCacheRuntimeApi` | `applyToContainer`、`onContainerPageStarted`、`onContainerPageFinished`、`detachContainer` |
| `WebCacheConfig` | 预热、清理、正式容器缓存策略总配置 |
| `PreloadUrlRule` | 单条预热 URL 规则 |
| `WebCachePolicyResolver` | 策略解析和规则选择 |
| `WebCachePreloadManager` | 隐藏 WebView 预热管理 |
| `WebCacheCleaner` | WebView 缓存和站点数据清理 |
| `WebCacheEvent` | 事件上报数据结构 |
| `CacheModeOption` | WebSettings cacheMode 映射 |
| `NetworkType` | 网络状态枚举 |

## 验证方式

- 清理版本号变化后应只执行一次清理。
- 清理后正式 WebView 能重新从网络加载资源。
- 事件中应包含 reason，方便排查策略命中情况。

[返回 README](../README.md)