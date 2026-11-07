package com.itg.rncontainer

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler

/** Ready-to-use full-screen host; the Application still initializes RN 0.84. */
class ItgRnContainerActivity : AppCompatActivity(), DefaultHardwareBackBtnHandler {
    private var instanceId: String? = null
    private val slotId = R.id.itg_rn_activity_slot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_itg_rn_container)
        instanceId = intent.getStringExtra(ItgRnContainer.EXTRA_INSTANCE)
        val component = intent.getStringExtra(ItgRnContainer.EXTRA_COMPONENT)
        val pageId = intent.getStringExtra(ItgRnContainer.EXTRA_PAGE)
        if (component.isNullOrBlank() || pageId.isNullOrBlank() || instanceId.isNullOrBlank()) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            ItgRnContainer.attach(supportFragmentManager, slotId, ItgRnPage(
                component, pageId, intent.getBundleExtra(ItgRnContainer.EXTRA_PARAMS) ?: Bundle(), instanceId!!
            ))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentByTag(ItgRnContainer.tag(instanceId!!)) as? ItgRnContainerFragment
                if (fragment?.onBackPressed() != true) finish()
            }
        })
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val fragment = instanceId?.let { supportFragmentManager.findFragmentByTag(ItgRnContainer.tag(it)) } as? ItgRnContainerFragment
        return fragment?.onKeyUp(keyCode, event) == true || super.onKeyUp(keyCode, event)
    }

    override fun invokeDefaultOnBackPressed() { finish() }

    internal fun complete(instance: String, resultJson: String?) {
        if (instance != instanceId) return
        setResult(RESULT_OK, Intent().putExtra(ItgRnContainer.EXTRA_RESULT_JSON, resultJson))
        finish()
    }
}
