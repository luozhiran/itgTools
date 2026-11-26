package com.itg.componse

import android.app.Activity
import androidx.annotation.StringRes
import com.itg.componse.activitys.ComposeListDemoActivity
import com.itg.componse.activitys.ComposeRememberActivity
import com.itg.componse.activitys.ComposeRememberActivity2

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
        ComposeDemoEntry(
            titleRes = R.string.compose_list_remember_title,
            descriptionRes = R.string.compose_list_remember_description,
            activityClass = ComposeRememberActivity::class.java,
        ),
        ComposeDemoEntry(
            titleRes = R.string.compose_list_remember_title2,
            descriptionRes = R.string.compose_list_remember_description2,
            activityClass = ComposeRememberActivity2::class.java,
        ),
    )
}
