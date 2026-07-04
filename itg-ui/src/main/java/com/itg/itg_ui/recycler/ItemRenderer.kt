package com.itg.itg_ui.recycler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

/** 单一 Item 类型的 View 创建、数据绑定和差量比较规则。 */
abstract class ItemRenderer<I : ItgListItem, VB : ViewBinding, A : Any>(
    val itemClass: Class<I>,
) {
    abstract fun createBinding(inflater: LayoutInflater, parent: ViewGroup): VB

    abstract fun bind(
        binding: VB,
        item: I,
        actions: A,
        lifecycleOwner: LifecycleOwner?,
        payloads: List<Any>,
    )

    /** stableId 相同后调用；data class 默认 equals 已能覆盖大部分场景。 */
    open fun areContentsTheSame(oldItem: I, newItem: I): Boolean = oldItem == newItem

    /** 返回 null 表示执行完整绑定。 */
    open fun getChangePayload(oldItem: I, newItem: I): Any? = null

    open fun onViewRecycled(binding: VB) = Unit

    @Suppress("UNCHECKED_CAST")
    internal fun bindErased(
        binding: ViewBinding,
        item: ItgListItem,
        actions: A,
        lifecycleOwner: LifecycleOwner?,
        payloads: List<Any>,
    ) = bind(binding as VB, item as I, actions, lifecycleOwner, payloads)

    @Suppress("UNCHECKED_CAST")
    internal fun contentsSameErased(oldItem: ItgListItem, newItem: ItgListItem): Boolean =
        areContentsTheSame(oldItem as I, newItem as I)

    @Suppress("UNCHECKED_CAST")
    internal fun payloadErased(oldItem: ItgListItem, newItem: ItgListItem): Any? =
        getChangePayload(oldItem as I, newItem as I)

    @Suppress("UNCHECKED_CAST")
    internal fun recycledErased(binding: ViewBinding) = onViewRecycled(binding as VB)
}
