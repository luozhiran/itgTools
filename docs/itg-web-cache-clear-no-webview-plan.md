# itg-web-cache 无 WebView 时缓存清除方案

## 一、问题分析

### 1.1 现状

`WebCacheCleaner.clearHttpCache()` 的当前实现（`WebCacheCleaner.kt:35-54`）：

```kotlin
fun clearHttpCache(context: Context, ...) {
    runOnMain {
        val webView = WebView(context.applicationContext)  // ← 临时创建 WebView
        webView.clearCache(true)
        webView.clearFormData()
        webView.clearHistory()
        webView.destroy()                                   // ← 立即销毁
    }
}
```

### 1.2 问题根因

| 问题 | 说明 |
|------|------|
| **依赖临时 WebView** | 每次清除都要 `new WebView(context)`，这是重量级操作（~100MB 内存开销 / 渲染引擎初始化） |
| **必须在主线程** | WebView 操作强制要求主线程，没有 Looper 的线程无法执行 |
| **无延迟/重试机制** | 如果 App 冷启动时触发清除（配置版本号变化），此时 WebView 渲染引擎可能尚未初始化，临时创建也可能失败 |
| **版本号清除未接入** | `WebCacheConfig.clearCacheVersion` / `disableClearVersion` 字段已定义，但**没有代码在启动时读取并触发清除**——这是一个功能缺口 |

### 1.3 触发链路缺失

```
配置下发 clearCacheVersion=2
  → WebCacheConfig.clearCacheVersion = "2"
  → ❌ 无代码读取此字段并调用 WebCacheCleaner
  → 缓存永远不会被清除
```

即使补上触发链路，也会遇到「无 WebView 可用」的问题。

### 1.4 Android WebView 缓存存储路径

```
/data/data/<package>/app_webview/
  ├── Cookies
  ├── Cookies-journal
  ├── Local Storage/
  ├── Service Worker/
  ├── HTTP Cache/          ← WebView.clearCache(true) 清除这个
  └── Web Data             ← SQLite 数据库 (历史记录等)
```

## 二、方案设计

### 2.1 总体思路

```
┌─────────────────────────────────────────────┐
│  清除触发源                                  │
│  - 配置版本号变化 (clearCacheVersion)         │
│  - 关闭预热 (disableClearVersion)            │
│  - 用户主动调用 (clearByPolicy)              │
└──────────────────┬──────────────────────────┘
                   │
     ┌─────────────▼─────────────┐
     │ WebView 是否可用？          │
     │ (有活跃 WebView / 主线程)   │
     └──────┬────────────┬──────┘
            │            │
       可用 ✅        不可用 ❌
            │            │
     ┌──────▼──────┐  ┌─▼───────────────────┐
     │ WebView     │  │ 1. 文件系统直接清除    │
     │ .clearCache │  │ 2. CookieManager     │
     │ (临时创建)   │  │ 3. WebStorage        │
     └─────────────┘  │ 4. PendingClearFlag  │
                      │    (延迟到首个WebView) │
                      └──────────────────────┘
```

### 2.2 三级清除策略

#### Level 1: WebView 直接清除（现有方案，优先使用）

**条件**: 主线程 + WebView 可用  
**方法**: `new WebView(ctx).clearCache(true)`  
**覆盖**: HTTP 缓存 (全部)

```kotlin
// 适用场景：用户触发 / App 运行中有 WebView 活跃时
WebCacheCleaner.clearHttpCache(context)
```

#### Level 2: 文件系统直接删除（无需 WebView）**【新增】**

**条件**: 不需要 WebView  
**方法**: 直接删除 WebView 缓存目录文件  
**覆盖**: HTTP 缓存 (全部)

```kotlin
fun clearCacheByFileSystem(context: Context): Boolean {
    val cacheDir = File(context.cacheDir, "WebView")
    // 删除 .../app_webview/ 下的缓存文件
    // 不删除 Cookies / Local Storage（属于 SITE_DATA 范围）
    return deleteRecursive(cacheDir)
}
```

WebView 内部 HTTP 缓存路径（Android 各版本兼容）：

| Android 版本 | 缓存路径 |
|-------------|---------|
| < 5.0 | `context.cacheDir` 下的 `webviewCacheChromium` |
| ≥ 5.0 | `context.dataDir/app_webview/` 下的缓存子目录 |

**风险**：不同厂商 ROM 可能定制路径；但 `context.cacheDir` 是标准 API，可靠。

#### Level 3: 延迟清除标记（兜底）**【新增】**

**条件**: 上述两种方式均失败  
**方法**: 写入 SharedPreferences 标记，在下一个 WebView 创建时执行清除

```kotlin
// 写入标记
fun markPendingClear(type: CacheClearType) {
    prefs.edit().putBoolean("pending_clear_$type", true).apply()
}

// 在第一个 WebView 创建时检查并执行
fun executePendingClears(webView: WebView) {
    if (prefs.getBoolean("pending_clear_http", false)) {
        webView.clearCache(true)
        prefs.edit().remove("pending_clear_http").apply()
    }
}
```

### 2.3 推荐架构

新增内部类 `WebCacheClearScheduler`：

```
WebCacheClearScheduler
├── clearByPolicy(config, context)      ← 调度入口
├── tryClearWithWebView(context)        ← Level 1: 临时 WebView
├── tryClearByFileSystem(context)       ← Level 2: 文件系统删除
├── markPending(type, version)          ← Level 3: 延迟标记
└── executePendingClears(webView)       ← WebView 可用时执行延迟清除
```

### 2.4 调用时机

| 时机 | 清除类型 | 条件 |
|------|---------|------|
| App 冷启动 + 配置版本变化 | `clearCacheVersion` 变化 | 尝试 Level 2 → 失败则 Level 3 |
| 预热功能关闭 | `disableClearVersion` 变化 | 尝试 Level 2 → 失败则 Level 3 |
| 用户主动触发 | 任意 | Level 1 (用户触发时 WebView 通常可用) |
| 首个 WebView 创建时 | 检查延迟标记 | 执行 Level 3 队列中的清除 |

### 2.5 实现要点

**WebCacheCleaner 改造**：

```kotlin
object WebCacheCleaner {
    // 新增：调度清除（自动选择策略）
    fun clearByPolicy(context: Context, policy: WebCacheClearPolicy, ...) {
        when (policy) {
            HTTP_CACHE -> clearHttpCacheOptimized(context, ...)
            SITE_DATA  -> clearSiteDataOptimized(context, ...)
        }
    }

    // 新增：优化的 HTTP 缓存清除
    private fun clearHttpCacheOptimized(context: Context, ...) {
        // 尝试创建临时 WebView（主线程必须）
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val success = tryClearWithWebView(context)
            if (success) return
        }
        // Fallback: 文件系统删除
        val success = tryClearByFileSystem(context)
        if (success) return
        // 兜底: 延迟标记
        markPendingClear(PendingClearType.HTTP_CACHE)
    }

    // 新增：文件系统直接清除
    fun tryClearByFileSystem(context: Context): Boolean { ... }

    // 新增：延迟标记
    fun markPendingClear(type: PendingClearType) { ... }

    // 新增：在 WebView 可用时执行延迟清除（由使用者调用）
    fun executePendingClears(webView: WebView) { ... }
}
```

**WebCacheRuntime 改造（补上版本号触发链路）**：

```kotlin
// 在 WebCacheRuntime.configure() 或新增 init() 中检查版本号
fun checkAndExecuteVersionClears(context: Context) {
    val config = configProvider.getConfig()
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 检查通用清除版本
    if (config.clearCacheVersion != null) {
        val lastVersion = prefs.getString(LAST_CLEAR_VERSION_KEY, null)
        if (config.clearCacheVersion != lastVersion) {
            WebCacheCleaner.clearByPolicy(context, WebCacheClearPolicy.HTTP_CACHE)
            prefs.edit().putString(LAST_CLEAR_VERSION_KEY, config.clearCacheVersion).apply()
        }
    }

    // 检查关闭预热清除版本
    if (!config.preloadEnable && config.disableClearVersion != null) {
        val lastDisableVersion = prefs.getString(LAST_DISABLE_CLEAR_VERSION_KEY, null)
        if (config.disableClearVersion != lastDisableVersion) {
            val policy = config.disableClearPolicy
            if (policy != WebCacheClearPolicy.NONE) {
                WebCacheCleaner.clearByPolicy(context, policy)
            }
            prefs.edit().putString(LAST_DISABLE_CLEAR_VERSION_KEY, config.disableClearVersion).apply()
        }
    }
}
```

**在 WebView 创建时检查延迟清除**：

```kotlin
// 在 WebCacheRuntime.applyToContainer() 或业务 WebView 初始化时调用
fun onWebViewCreated(webView: WebView) {
    WebCacheCleaner.executePendingClears(webView)
}
```

### 2.6 文件系统清除实现参考

```kotlin
fun tryClearByFileSystem(context: Context): Boolean {
    return try {
        val appWebViewDir = File(context.dataDir, "app_webview")
        if (!appWebViewDir.exists()) return false
        var deleted = 0L
        // 遍历 app_webview 子目录，删除 HTTP 缓存相关文件
        appWebViewDir.listFiles()?.forEach { child ->
            if (child.name.contains("Cache", ignoreCase = true) ||
                child.name.contains("Code Cache", ignoreCase = true) ||
                child.name.startsWith("GPUCache")
            ) {
                deleted += deleteDir(child)
            }
        }
        deleted > 0
    } catch (e: Exception) {
        false
    }
}

private fun deleteDir(dir: File): Long {
    var size = 0L
    dir.listFiles()?.forEach { child ->
        if (child.isDirectory) size += deleteDir(child)
        size += child.length()
        child.delete()
    }
    dir.delete()
    return size
}
```

## 三、API 变更

### 新增 API

| API | 说明 |
|-----|------|
| `WebCacheCleaner.clearByPolicy(context, policy, ...)` 增强 | 内部自动选择 WebView / 文件系统 / 延迟标记策略 |
| `WebCacheCleaner.tryClearByFileSystem(context): Boolean` | 公开的文件系统清除 |
| `WebCacheCleaner.executePendingClears(webView)` | WebView 可用时执行延迟清除 |
| `WebCacheRuntime.init(context)` 或增强 `configure()` | 启动时检查配置版本号并触发清除 |

### 不变 API

| API | 说明 |
|-----|------|
| `WebCacheCleaner.clearHttpCache(context, ...)` | 保持现有签名，内部升级 |
| `WebCacheCleaner.clearSiteData(context, ...)` | 保持现有签名 |
| `WebCacheClearPolicy` | 枚举不变 |
| `WebCacheConfig.clearCacheVersion` / `disableClearVersion` | 配置字段不变，新增触发逻辑 |

### 调用方（App）接入

```kotlin
// Application.onCreate()
WebCacheRuntime.configure(configProvider)

// 新增：检查版本号并执行清除
WebCacheRuntime.checkAndExecuteVersionClears(applicationContext)

// 在业务 WebView 初始化时检查延迟清除
class MyWebView(context: Context) : WebView(context) {
    init {
        WebCacheCleaner.executePendingClears(this)
    }
}
```

## 四、风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 文件系统路径厂商定制 | 中 | 多路径 fallback 尝试；Android 标准路径覆盖 95% 设备 |
| 删除正在被其他 WebView 使用的缓存文件 | 低 | 当前无 WebView 时清除；删除操作不影响已打开的文件描述符（Linux 语义） |
| 延迟清除在下次启动前缓存仍存在 | 低 | 延迟清除在下一次 WebView 创建时立即执行，间隔极短 |
| CookieManager.removeAllCookies 异步回调 | 低 | 已有 `onComplete` 回调机制 |

## 五、实施优先级

| 优先级 | 内容 |
|--------|------|
| **P0** | `WebCacheRuntime` 接入版本号触发链路（`checkAndExecuteVersionClears`） |
| **P0** | `WebCacheCleaner` 增加文件系统 fallback（`tryClearByFileSystem`） |
| **P1** | 延迟清除标记 + `executePendingClears` |
| **P2** | 文件系统路径多版本兼容矩阵测试 |
