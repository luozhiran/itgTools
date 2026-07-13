package com.itg.itgtools.webcache

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itg.itg_web_cache.WebCacheClearPolicy
import com.itg.itg_web_cache.WebCacheCleaner
import com.itg.itg_web_cache.WebCachePreloadManager
import com.itg.itgtools.R

class WebCacheDemoActivity : AppCompatActivity() {
    private lateinit var urlEdit: EditText
    private lateinit var preloadSwitch: Switch
    private lateinit var containerSwitch: Switch
    private lateinit var parallelSwitch: Switch
    private lateinit var killSwitch: Switch
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView

    private val logSink: (String) -> Unit = { text ->
        runOnUiThread {
            logView.text = text.ifBlank { "No events yet." }
            logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_cache_demo)

        WebCacheDemoConfig.configure(applicationContext)
        bindViews()
        bindState()
        bindActions()
        WebCacheDemoConfig.addSink(logSink)
    }

    override fun onDestroy() {
        WebCacheDemoConfig.removeSink(logSink)
        super.onDestroy()
    }

    private fun bindViews() {
        urlEdit = findViewById(R.id.webCacheUrlEdit)
        preloadSwitch = findViewById(R.id.webCachePreloadSwitch)
        containerSwitch = findViewById(R.id.webCacheContainerSwitch)
        parallelSwitch = findViewById(R.id.webCacheParallelSwitch)
        killSwitch = findViewById(R.id.webCacheKillSwitch)
        logView = findViewById(R.id.webCacheLogText)
        logScroll = findViewById(R.id.webCacheLogScroll)
    }

    private fun bindState() {
        urlEdit.setText(WebCacheDemoConfig.targetUrl)
        preloadSwitch.isChecked = WebCacheDemoConfig.preloadEnabled
        containerSwitch.isChecked = WebCacheDemoConfig.containerCacheEnabled
        parallelSwitch.isChecked = WebCacheDemoConfig.parallelEnabled
        killSwitch.isChecked = WebCacheDemoConfig.killSwitch
    }

    private fun bindActions() {
        preloadSwitch.setOnCheckedChangeListener(updateSwitch { WebCacheDemoConfig.preloadEnabled = it })
        containerSwitch.setOnCheckedChangeListener(updateSwitch { WebCacheDemoConfig.containerCacheEnabled = it })
        parallelSwitch.setOnCheckedChangeListener(updateSwitch { WebCacheDemoConfig.parallelEnabled = it })
        killSwitch.setOnCheckedChangeListener(updateSwitch { WebCacheDemoConfig.killSwitch = it })

        findViewById<Button>(R.id.webCacheStartPreloadButton).setOnClickListener {
            syncUrl()
            WebCacheDemoConfig.configure(applicationContext)
            WebCachePreloadManager.startAfterHomeReady()
        }
        findViewById<Button>(R.id.webCacheOpenButton).setOnClickListener {
            syncUrl()
            WebCacheDemoConfig.configure(applicationContext)
            startActivity(Intent(this, WebCacheDemoWebActivity::class.java))
        }
        findViewById<Button>(R.id.webCacheCancelButton).setOnClickListener {
            WebCachePreloadManager.cancel("demo_cancel")
        }
        findViewById<Button>(R.id.webCacheClearHttpButton).setOnClickListener {
            WebCacheCleaner.clearByPolicy(
                context = this,
                policy = WebCacheClearPolicy.HTTP_CACHE
            )
        }
        findViewById<Button>(R.id.webCacheClearLogButton).setOnClickListener {
            WebCacheDemoConfig.clearEvents()
        }
    }

    private fun updateSwitch(update: (Boolean) -> Unit): CompoundButton.OnCheckedChangeListener {
        return CompoundButton.OnCheckedChangeListener { _, isChecked ->
            update(isChecked)
            WebCacheDemoConfig.configure(applicationContext)
        }
    }

    private fun syncUrl() {
        WebCacheDemoConfig.updateUrl(urlEdit.text?.toString().orEmpty())
    }
}
