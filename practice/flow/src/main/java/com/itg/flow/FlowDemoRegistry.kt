package com.itg.flow

import android.app.Activity
import androidx.annotation.StringRes
import kotlin.jvm.java

data class FlowDemoEntry(
    @field:StringRes val titleRes: Int,
    @field:StringRes val descriptionRes: Int,
    val activityClass: Class<out Activity>,
)

internal object FlowDemoRegistry {
    val entries = listOf(
        FlowDemoEntry(
            R.string.flow_d,
            descriptionRes = R.string.flow_des,
            activityClass = SimpleFlowActivity::class.java
        )
    )
}