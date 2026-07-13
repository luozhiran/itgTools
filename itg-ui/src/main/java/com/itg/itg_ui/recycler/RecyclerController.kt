package com.itg.itg_ui.recycler

import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference

class RecyclerController<A : Any> internal constructor(
    val adapter: ItgRecyclerAdapter<A>,
    recyclerView: RecyclerView,
) {
    private val recyclerViewRef = WeakReference(recyclerView)

    val currentItems: List<Any> get() = adapter.currentList

    fun submitList(items: List<Any>, commitCallback: (() -> Unit)? = null) {
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

internal fun <A : Any> requireUniqueItemKeys(
    items: List<Any>,
    registry: ItemRendererRegistry<A>,
) {
    val identities = items.map { item ->
        val key = registry.rendererFor(item).renderer.itemKeyErased(item)
        require(key !is Unit) { "${item.javaClass.name} 的 itemKey 不能是 Unit。" }
        ItemKey(item.javaClass, key)
    }
    val duplicate = identities.groupingBy { it }.eachCount()
        .entries.firstOrNull { it.value > 1 }?.key
    require(duplicate == null) {
        "Item 类型 ${duplicate?.itemClass?.name} 的 itemKey=${duplicate?.key} 重复；同类型 Item 的 key 必须唯一。"
    }

    val duplicateLegacyId = items.filterIsInstance<ItgListItem>()
        .groupingBy { it.stableId }.eachCount()
        .entries.firstOrNull { it.value > 1 }?.key
    require(duplicateLegacyId == null) {
        "stableId=$duplicateLegacyId 在列表中重复；旧 ItgListItem 的 stableId 必须全局唯一。"
    }
}

private data class ItemKey(val itemClass: Class<*>, val key: Any)
