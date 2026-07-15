# 02. 正式容器策略

本节说明如何给正式 WebView 应用缓存策略。

## 适用条件

- 页面 URL 在 `containerUrlWhitelist` 内。
- scene 不在黑名单，且满足 scene 白名单规则。
- 业务允许 Runtime 修改 `webView.settings.cacheMode`。

## 推荐做法

```kotlin
val policy = WebCacheRuntime.applyToContainer(webView, url, scene = "home")
```

## 可复制 Demo

```kotlin
import android.webkit.WebViewClient
import com.itg.itg_web_cache.WebCacheRuntime

webView.webViewClient = object : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        WebCacheRuntime.onContainerPageStarted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        WebCacheRuntime.onContainerPageFinished(view, url)
    }
}

val policy = WebCacheRuntime.applyToContainer(
    webView = webView,
    url = "https://h5.example.com/home",
    scene = "home"
)
webView.loadUrl("https://h5.example.com/home")

// Activity/Fragment 销毁时
WebCacheRuntime.detachContainer(webView)
```

## 关键说明

- `containerCacheEnable = false` 时不会修改 WebView cacheMode。
- `containerForceOverride = false` 时，如果业务已设置非默认 cacheMode，Runtime 不覆盖。
- `containerSceneBlacklist` 优先于 scene 白名单。
- `applyToContainer` 会取消与当前正式 URL 冲突的预热任务。
- `detachContainer` 用于移除 Runtime 内部弱引用状态。

## 验证方式

- policy.reason 可解释是否命中策略或被跳过。
- 命中策略后 `webView.settings.cacheMode` 应与 `containerCacheMode` 对应。

[返回 README](../README.md)