package com.itg.itgtools.webcache

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itg.itg_web_cache.WebCacheRuntime
import com.itg.itgtools.R
import com.itg.itgtools.route.RoutePath.ITG_WEB_CONTAINER_ACTIVITY
import com.therouter.router.Route

@Route(path = ITG_WEB_CONTAINER_ACTIVITY)
class WebCacheDemoWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var policyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_cache_web)

        webView = findViewById(R.id.webCacheWebView)
        progress = findViewById(R.id.webCacheProgress)
        policyText = findViewById(R.id.webCachePolicyText)

        initWebView()
        val url = WebCacheDemoConfig.targetUrl
        val beforeMode = webView.settings.cacheMode
        val policy = WebCacheRuntime.applyToContainer(
            webView = webView,
            url = url,
            scene = WebCacheDemoConfig.DEFAULT_SCENE
        )
        val afterMode = webView.settings.cacheMode
        policyText.text = "policy=${policy.enabled}, mode=${policy.cacheMode.configValue}, reason=${policy.reason}, before=${cacheModeName(beforeMode)}, after=${cacheModeName(afterMode)}"
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        WebCacheRuntime.detachContainer(webView)
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun cacheModeName(cacheMode: Int): String {
        return when (cacheMode) {
            WebSettings.LOAD_DEFAULT -> "default"
            WebSettings.LOAD_CACHE_ELSE_NETWORK -> "cache_else_network"
            WebSettings.LOAD_NO_CACHE -> "no_cache"
            WebSettings.LOAD_CACHE_ONLY -> "cache_only"
            else -> cacheMode.toString()
        }
    }

    private fun initWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.cacheMode = if (WebCacheDemoConfig.businessNoCachePreset) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        settings.loadsImagesAutomatically = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                WebCacheRuntime.onContainerPageStarted(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                WebCacheRuntime.onContainerPageFinished(view, url)
                WebCacheDemoConfig.appendDemoEvent(
                    "container loaded success, same URL enters preload cooldown: $url"
                )
            }
        }
    }
}
