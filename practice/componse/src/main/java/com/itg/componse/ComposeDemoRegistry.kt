package com.itg.componse

import android.app.Activity
import androidx.annotation.StringRes

internal data class ComposeDemoEntry(
    @field:StringRes val titleRes: Int,
    @field:StringRes val descriptionRes: Int,
    val activityClass: Class<out Activity>,
)

internal object ComposeDemoRegistry {
    val entries = listOf(
        ComposeDemoEntry(
            titleRes = R.string.compose_list_demo_title,
            descriptionRes = R.string.compose_list_demo_description,
            activityClass = ComposeListDemoActivity::class.java,
        ),
    )
}
