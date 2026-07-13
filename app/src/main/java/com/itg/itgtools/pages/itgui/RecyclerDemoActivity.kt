package com.itg.itgtools.pages.itgui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itg_ui.recycler.ItgListItem
import com.itg.itg_ui.recycler.RecyclerConfig
import com.itg.itg_ui.recycler.RecyclerViewAbility
import com.itg.itg_ui.recycler.dataBinding
import com.itg.itg_ui.recycler.itgRecyclerAdapter
import com.itg.itg_ui.recycler.viewBinding
import com.itg.itgtools.R
import com.itg.itgtools.databinding.ActivityItgUiRecyclerBinding
import com.itg.itgtools.databinding.ItemItgUiBannerBinding
import com.itg.itgtools.databinding.ItemItgUiUserBinding

data class RecyclerUserRow(
    override val stableId: Long,
    val name: String,
) : ItgListItem

data class RecyclerBannerRow(
    override val stableId: Long,
    val text: String,
) : ItgListItem

data class RecyclerDataRow(
    override val stableId: Long,
    val title: String,
) : ItgListItem

interface RecyclerDemoActions {
    fun onUserClick(id: Long)
    fun onBannerClick(id: Long)
    fun onDataClick(id: Long)
}

class RecyclerDemoModel(app: Application) : ItgModel(app) {
    private val _rows = MutableLiveData<List<ItgListItem>>(initialRows())
    val rows: LiveData<List<ItgListItem>> = _rows
    private var nextId = 100L
    private var renameCount = 1

    fun changeFirstName() {
        renameCount++
        _rows.value = _rows.value.orEmpty().map {
            if (it is RecyclerUserRow && it.stableId == 1L) it.copy(name = "用户 A · 更新 $renameCount") else it
        }
    }

    fun addItem() {
        val id = nextId++
        _rows.value = _rows.value.orEmpty() + RecyclerDataRow(id, "自动绑定新增项 #$id")
    }

    fun clearItems() {
        _rows.value = emptyList()
    }

    private fun initialRows(): List<ItgListItem> = listOf(
        RecyclerBannerRow(10, "Banner：不同 Item 使用独立 ViewBinding"),
        RecyclerUserRow(1, "用户 A"),
        RecyclerDataRow(20, "DataBinding 自动绑定 Item"),
        RecyclerUserRow(2, "用户 B"),
    )
}

class RecyclerDemoActivity :
    AutoBindingBaseActivity<ActivityItgUiRecyclerBinding, RecyclerDemoModel>(), RecyclerDemoActions {
    private val recyclerAbility = RecyclerViewAbility(
        RecyclerConfig(hasFixedSize = true, itemViewCacheSize = 8)
    )

    private val adapter by lazy {
        itgRecyclerAdapter<RecyclerDemoActions>(actions = this) {
            viewBinding<RecyclerUserRow, ItemItgUiUserBinding, RecyclerDemoActions>(
                inflate = ItemItgUiUserBinding::inflate,
                getChangePayload = { old, new -> if (old.name != new.name) NAME_PAYLOAD else null },
                bindPayload = { itemBinding, item, _, payloads ->
                    if (NAME_PAYLOAD in payloads) {
                        itemBinding.name.text = item.name
                        true
                    } else false
                },
            ) { item, actions ->
                name.text = item.name
                root.setOnClickListener { actions.onUserClick(item.stableId) }
            }
            viewBinding<RecyclerBannerRow, ItemItgUiBannerBinding, RecyclerDemoActions>(
                inflate = ItemItgUiBannerBinding::inflate,
            ) { item, actions ->
                bannerText.text = item.text
                root.setOnClickListener { actions.onBannerClick(item.stableId) }
            }
            dataBinding<RecyclerDataRow, RecyclerDemoActions>(
                layoutId = R.layout.item_itg_ui_data_binding,
                itemVariableId = RecyclerBindingVariables.ITEM,
                actionsVariableId = RecyclerBindingVariables.ACTIONS,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerAbility.bind(binding.recyclerView, this, adapter)
        recyclerAbility.observeItems(viewModel.rows)
        binding.changeName.setOnClickListener { viewModel.changeFirstName() }
        binding.addItem.setOnClickListener { viewModel.addItem() }
        binding.clearItems.setOnClickListener { viewModel.clearItems() }
    }

    override fun onUserClick(id: Long) = messages.toast("ViewBinding User 点击：$id")
    override fun onBannerClick(id: Long) = messages.toast("ViewBinding Banner 点击：$id")
    override fun onDataClick(id: Long) = messages.toast("DataBinding 自动事件：$id")

    companion object {
        private const val NAME_PAYLOAD = "name"
    }
}
