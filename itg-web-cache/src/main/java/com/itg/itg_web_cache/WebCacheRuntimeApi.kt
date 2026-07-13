package com.itg.itg_web_cache

import android.webkit.WebView

interface WebCacheRuntimeApi {
    fun applyToContainer(webView: WebView, url: String, scene: String? = null): WebCachePolicy
    fun onContainerPageStarted(webView: WebView, url: String)
    fun onContainerPageFinished(webView: WebView, url: String)
    fun detachContainer(webView: WebView)
}
