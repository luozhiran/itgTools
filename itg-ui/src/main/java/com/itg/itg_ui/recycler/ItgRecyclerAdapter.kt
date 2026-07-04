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
) : ListAdapter<ItgListItem, BindingViewHolder>(ItgItemDiffCallback(registry)) {

    internal var lifecycleOwner: LifecycleOwner? = null

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun submitList(list: List<ItgListItem>?) {
        list?.let(::requireUniqueStableIds)
        super.submitList(list)
    }

    override fun submitList(list: List<ItgListItem>?, commitCallback: Runnable?) {
        list?.let(::requireUniqueStableIds)
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
) : DiffUtil.ItemCallback<ItgListItem>() {
    override fun areItemsTheSame(oldItem: ItgListItem, newItem: ItgListItem): Boolean =
        oldItem.javaClass == newItem.javaClass && oldItem.stableId == newItem.stableId

    override fun areContentsTheSame(oldItem: ItgListItem, newItem: ItgListItem): Boolean {
        if (oldItem.javaClass != newItem.javaClass) return false
        return registry.rendererFor(oldItem).renderer.contentsSameErased(oldItem, newItem)
    }

    override fun getChangePayload(oldItem: ItgListItem, newItem: ItgListItem): Any? {
        if (oldItem.javaClass != newItem.javaClass) return null
        return registry.rendererFor(oldItem).renderer.payloadErased(oldItem, newItem)
    }
}
