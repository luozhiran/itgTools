package com.itg.rnbussiness.nativecode

import android.widget.TextView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event

/** A small native Android View rendered by Fabric. */
class ItgDemoNativeView(context: ReactContext) : TextView(context) {
    init {
        text = "Fabric Native View"
        textSize = 18f
        setPadding(24, 20, 24, 20)
        setBackgroundColor(0xffdbeafe.toInt())
        setOnClickListener { emitPress() }
    }

    private fun emitPress() {
        val reactContext = context as ReactContext
        val payload = Arguments.createMap().apply { putString("text", text.toString()) }
        val event = PressEvent(UIManagerHelper.getSurfaceId(reactContext), id, payload)
        UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(event)
    }

    private class PressEvent(
        surfaceId: Int,
        viewId: Int,
        private val payload: WritableMap,
    ) : Event<PressEvent>(surfaceId, viewId) {
        override fun getEventName() = "onPress"
        override fun getEventData() = payload
    }
}
