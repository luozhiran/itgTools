package com.itg.itg_ui.recycler

import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference

class RecyclerController<A : Any> internal constructor(
    val adapter: ItgRecyclerAdapter<A>,
    recyclerView: RecyclerView,
) {
    private val recyclerViewRef = WeakReference(recyclerView)

    val currentItems: List<ItgListItem> get() = adapter.currentList

    fun submitList(items: List<ItgListItem>, commitCallback: (() -> Unit)? = null) {
        val immutableCopy = items.toList()
        if (commitCallback == null) {
            adapter.submitList(immutableCopy)
        } else {
            adapter.submitList(immutableCopy, Runnable(commitCallback))
        }
    }

    fun clear(commitCallback: (() -> Unit)? = null) = submitList(emptyList(), commitCallback)

    fun scrollToPosition(position: Int, smooth: Boolean = false) {
        require(position >= 0) { "position 必须大于等于 0。" }
        recyclerViewRef.get()?.let { recyclerView ->
            if (smooth) recyclerView.smoothScrollToPosition(position)
            else recyclerView.scrollToPosition(position)
        }
    }

}

internal fun requireUniqueStableIds(items: List<ItgListItem>) {
    val duplicateId = items.groupingBy { it.stableId }.eachCount()
        .entries.firstOrNull { it.value > 1 }?.key
    require(duplicateId == null) {
        "stableId=$duplicateId 在列表中重复；stableId 必须全局唯一。"
    }
}
