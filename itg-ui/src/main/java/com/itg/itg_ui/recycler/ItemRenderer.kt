package com.itg.itg_ui.recycler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

/** 单一 Item 类型的 View 创建、数据绑定和差量比较规则。 */
abstract class ItemRenderer<I : Any, VB : ViewBinding, A : Any>(
    val itemClass: Class<I>,
    private val itemKey: (I) -> Any = { item -> defaultItemKey(item) },
) {
    abstract fun createBinding(inflater: LayoutInflater, parent: ViewGroup): VB

    abstract fun bind(
        binding: VB,
        item: I,
        actions: A,
        lifecycleOwner: LifecycleOwner?,
        payloads: List<Any>,
    )

    /** Item key 相同后调用；data class 默认 equals 已能覆盖大部分场景。 */
    open fun areContentsTheSame(oldItem: I, newItem: I): Boolean = oldItem == newItem

    /** 返回 null 表示执行完整绑定。 */
    open fun getChangePayload(oldItem: I, newItem: I): Any? = null

    open fun onViewRecycled(binding: VB) = Unit

    @Suppress("UNCHECKED_CAST")
    internal fun bindErased(
        binding: ViewBinding,
        item: Any,
        actions: A,
        lifecycleOwner: LifecycleOwner?,
        payloads: List<Any>,
    ) = bind(binding as VB, item as I, actions, lifecycleOwner, payloads)

    @Suppress("UNCHECKED_CAST")
    internal fun itemKeyErased(item: Any): Any = itemKey(item as I)

    @Suppress("UNCHECKED_CAST")
    internal fun contentsSameErased(oldItem: Any, newItem: Any): Boolean =
        areContentsTheSame(oldItem as I, newItem as I)

    @Suppress("UNCHECKED_CAST")
    internal fun payloadErased(oldItem: Any, newItem: Any): Any? =
        getChangePayload(oldItem as I, newItem as I)

    @Suppress("UNCHECKED_CAST")
    internal fun recycledErased(binding: ViewBinding) = onViewRecycled(binding as VB)
}

/** 兼容旧 Item；普通业务模型应由 renderer 显式提供 itemKey。 */
fun defaultItemKey(item: Any): Any =
    (item as? ItgListItem)?.stableId
        ?: error(
            "${item.javaClass.name} 未实现 ItgListItem，注册 Renderer 时必须提供 itemKey。"
        )
