package com.itg.rncontainer.bridge

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import com.itg.rncontainer.ItgRnContainer
import com.itg.rncontainer.ItgRnContainerActivity
import com.itg.rncontainer.ItgRnContainerFragment

/** Register this package in the host's ReactHost package list. */
class ItgRnBridgePackage : ReactPackage {
    @Suppress("DEPRECATION")
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(ItgRnBridgeModule(reactContext))

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

private class ItgRnBridgeModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    override fun getName() = "ItgRnBridge"

    @ReactMethod
    fun closeContainer(instanceId: String, resultJson: String?, promise: Promise) {
        val activity = reactApplicationContext.currentActivity
        if (activity !is FragmentActivity) {
            promise.reject("NO_HOST", "Current Activity is not a FragmentActivity")
            return
        }
        activity.runOnUiThread {
            val fragment = findInstance(activity.supportFragmentManager, instanceId)
            if (fragment == null) {
                promise.reject("INSTANCE_NOT_FOUND", "RN instance is no longer attached: $instanceId")
                return@runOnUiThread
            }
            if (activity is ItgRnContainerActivity) {
                activity.complete(instanceId, resultJson)
            } else {
                val manager = fragment.parentFragmentManager
                manager.setFragmentResult(ItgRnContainer.resultKey(instanceId), Bundle().apply {
                    putString(ItgRnContainer.EXTRA_RESULT_JSON, resultJson)
                })
                manager.beginTransaction().remove(fragment).commit()
            }
            promise.resolve(null)
        }
    }

    private fun findInstance(manager: FragmentManager, id: String): ItgRnContainerFragment? {
        for (fragment: Fragment in manager.fragments) {
            if (fragment is ItgRnContainerFragment && fragment.instanceId == id) return fragment
            findInstance(fragment.childFragmentManager, id)?.let { return it }
        }
        return null
    }
}
