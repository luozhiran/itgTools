package com.itg.rncontainer

import android.os.Bundle

/** One RN surface's immutable launch parameters. Values in [params] must be RN Bundle types. */
data class ItgRnPage(
    val componentName: String,
    val pageId: String,
    val params: Bundle = Bundle(),
    val instanceId: String = java.util.UUID.randomUUID().toString(),
) {
    init {
        require(componentName.isNotBlank()) { "componentName is required" }
        require(pageId.isNotBlank()) { "pageId is required" }
        require(instanceId.isNotBlank()) { "instanceId is required" }
    }

    internal fun launchOptions() = Bundle().apply {
        putString("instanceId", instanceId)
        putString("pageId", pageId)
        putBundle("params", Bundle(params))
    }
}
