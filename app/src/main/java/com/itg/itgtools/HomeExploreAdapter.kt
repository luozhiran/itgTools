package com.itg.itgtools

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itg.itgtools.databinding.ItemHomeExploreBinding

data class ExploreItem(
    @field:StringRes val titleRes: Int,
    @field:StringRes val descriptionRes: Int,
    val destination: Class<out Activity>,
)

class HomeHeaderAdapter : RecyclerView.Adapter<HomeHeaderAdapter.HeaderViewHolder>() {
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_header, parent, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) = Unit

    override fun getItemCount(): Int = 1
}

class HomeExploreAdapter(
    private val onItemClick: (ExploreItem) -> Unit,
) : ListAdapter<ExploreItem, HomeExploreAdapter.ExploreViewHolder>(DIFF_CALLBACK) {

    class ExploreViewHolder(val binding: ItemHomeExploreBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExploreViewHolder {
        val binding = ItemHomeExploreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ExploreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExploreViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            number.text = (position + 1).toString().padStart(2, '0')
            title.setText(item.titleRes)
            description.setText(item.descriptionRes)
            root.setOnClickListener { onItemClick(item) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ExploreItem>() {
            override fun areItemsTheSame(oldItem: ExploreItem, newItem: ExploreItem): Boolean =
                oldItem.destination == newItem.destination

            override fun areContentsTheSame(oldItem: ExploreItem, newItem: ExploreItem): Boolean =
                oldItem == newItem
        }
    }
}
