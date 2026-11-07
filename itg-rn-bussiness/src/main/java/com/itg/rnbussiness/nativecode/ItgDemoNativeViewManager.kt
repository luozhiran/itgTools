package com.itg.rnbussiness.nativecode

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.ItgDemoNativeViewManagerDelegate
import com.facebook.react.viewmanagers.ItgDemoNativeViewManagerInterface

/** Generated delegate maps JS props to [setText]; the event maps native taps to JS. */
@ReactModule(name = ItgDemoNativeViewManager.NAME)
class ItgDemoNativeViewManager : SimpleViewManager<ItgDemoNativeView>(),
    ItgDemoNativeViewManagerInterface<ItgDemoNativeView> {
    private val generatedDelegate = ItgDemoNativeViewManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<ItgDemoNativeView> = generatedDelegate
    override fun getName() = NAME
    override fun createViewInstance(context: ThemedReactContext) = ItgDemoNativeView(context)

    @ReactProp(name = "text")
    override fun setText(view: ItgDemoNativeView, value: String?) {
        view.text = value ?: ""
    }

    override fun getExportedCustomBubblingEventTypeConstants(): Map<String, Any> = mapOf(
        "onPress" to mapOf(
            "phasedRegistrationNames" to mapOf(
                "bubbled" to "onPress",
                "captured" to "onPressCapture",
            ),
        ),
    )

    companion object { const val NAME = "ItgDemoNativeView" }
}
