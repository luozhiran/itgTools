package com.itg.itg_ui.recycler

import android.content.Context
import androidx.recyclerview.widget.RecyclerView

data class RecyclerConfig(
    val layoutManagerFactory: ((Context) -> RecyclerView.LayoutManager)? = null,
    val itemAnimatorFactory: ((Context) -> RecyclerView.ItemAnimator?)? = null,
    val hasFixedSize: Boolean = false,
    val nestedScrollingEnabled: Boolean = true,
    val clipToPadding: Boolean = true,
    val itemViewCacheSize: Int? = null,
) {
    init {
        require(itemViewCacheSize == null || itemViewCacheSize >= 0) {
            "itemViewCacheSize 必须大于等于 0。"
        }
    }
}
