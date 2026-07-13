package com.itg.itg_ui.recycler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class BindingViewHolder internal constructor(
    val binding: ViewBinding,
) : RecyclerView.ViewHolder(binding.root)

class ItgRecyclerAdapter<A : Any> internal constructor(
    internal val registry: ItemRendererRegistry<A>,
    private val actions: A,
) : ListAdapter<Any, BindingViewHolder>(ItgItemDiffCallback(registry)) {

    internal var lifecycleOwner: LifecycleOwner? = null
    private val stableIds = ItemStableIdStore(registry)

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemId(position: Int): Long = stableIds.idFor(getItem(position))

    override fun submitList(list: List<Any>?) {
        list?.let { requireUniqueItemKeys(it, registry) }
        super.submitList(list)
    }

    override fun submitList(list: List<Any>?, commitCallback: Runnable?) {
        list?.let { requireUniqueItemKeys(it, registry) }
        super.submitList(list, commitCallback)
    }

    override fun getItemViewType(position: Int): Int =
        registry.rendererFor(getItem(position)).viewType

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val renderer = registry.rendererForViewType(viewType).renderer
        return BindingViewHolder(renderer.createBinding(LayoutInflater.from(parent.context), parent))
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        bind(holder, position, emptyList())
    }

    override fun onBindViewHolder(
        holder: BindingViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        bind(holder, position, payloads)
    }

    private fun bind(holder: BindingViewHolder, position: Int, payloads: List<Any>) {
        registry.rendererForViewType(holder.itemViewType).renderer.bindErased(
            binding = holder.binding,
            item = getItem(position),
            actions = actions,
            lifecycleOwner = lifecycleOwner,
            payloads = payloads,
        )
    }

    override fun onViewRecycled(holder: BindingViewHolder) {
        registry.rendererForViewType(holder.itemViewType).renderer.recycledErased(holder.binding)
        super.onViewRecycled(holder)
    }
}

internal class ItgItemDiffCallback<A : Any>(
    private val registry: ItemRendererRegistry<A>,
) : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean =
        oldItem.javaClass == newItem.javaClass &&
            registry.rendererFor(oldItem).renderer.itemKeyErased(oldItem) ==
            registry.rendererFor(newItem).renderer.itemKeyErased(newItem)

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem.javaClass != newItem.javaClass) return false
        return registry.rendererFor(oldItem).renderer.contentsSameErased(oldItem, newItem)
    }

    override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
        if (oldItem.javaClass != newItem.javaClass) return null
        return registry.rendererFor(oldItem).renderer.payloadErased(oldItem, newItem)
    }
}

private data class ItemIdentity(
    val itemClass: Class<*>,
    val key: Any,
)

internal class ItemStableIdStore<A : Any>(
    private val registry: ItemRendererRegistry<A>,
) {
    private val ids = mutableMapOf<ItemIdentity, Long>()
    private var nextId = Long.MIN_VALUE

    fun idFor(item: Any): Long {
        val renderer = registry.rendererFor(item).renderer
        val identity = ItemIdentity(item.javaClass, renderer.itemKeyErased(item))
        return ids.getOrPut(identity) { nextId++ }
    }
}
