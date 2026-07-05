package com.itg.itgtools.pages.itgui.ksp

import com.itg.itg_ksp.annotations.ItgBind
import com.itg.itg_ksp.annotations.ItgAutoTextField
import com.itg.itg_ksp.annotations.ItgContentsSame
import com.itg.itg_ksp.annotations.ItgDataBindingItem
import com.itg.itg_ksp.annotations.ItgPayload
import com.itg.itg_ksp.annotations.ItgViewBindingItem
import com.itg.itg_ui.recycler.ItgListItem
import com.itg.itgtools.databinding.ItemItgUiBannerBinding
import com.itg.itgtools.databinding.ItemItgUiUserBinding

@ItgViewBindingItem(
    bindingClassName = USER_BINDING_CLASS,
    actionsClassName = KSP_DEMO_ACTIONS_CLASS,
)
data class KspUserRow(
    override val stableId: Long,
    val name: String,
) : ItgListItem {
    @ItgBind
    fun bind(binding: ItemItgUiUserBinding, actions: KspDemoActions, payloads: List<Any>) {
        if (payloads.contains(NAME_PAYLOAD)) {
            binding.name.text = name
            return
        }
        binding.name.text = name
        binding.description.text = "Generated ViewBinding binding"
        binding.root.setOnClickListener { actions.onUserClick(stableId) }
    }

    @ItgPayload
    fun payload(oldItem: KspUserRow): Any? = if (oldItem.name != name) NAME_PAYLOAD else null

    @ItgContentsSame
    fun contentsSame(oldItem: KspUserRow): Boolean = oldItem.name == name
}

@ItgViewBindingItem(
    bindingClassName = BANNER_BINDING_CLASS,
    actionsClassName = KSP_DEMO_ACTIONS_CLASS,
)
data class KspBannerRow(
    override val stableId: Long,
    val text: String,
) : ItgListItem {
    @ItgBind
    fun bind(binding: ItemItgUiBannerBinding, actions: KspDemoActions) {
        binding.bannerText.text = text
        binding.root.setOnClickListener { actions.onBannerClick(stableId) }
    }
}

@ItgViewBindingItem(
    bindingClassName = AUTO_BINDING_CLASS,
    actionsClassName = KSP_DEMO_ACTIONS_CLASS,
)
data class KspAutoRow(
    override val stableId: Long,
    @ItgAutoTextField
    val headline: String,
    @ItgAutoTextField(viewName = "detail")
    val detail: String,
) : ItgListItem

@ItgDataBindingItem(
    layoutExpression = "com.itg.itgtools.R.layout.item_itg_ui_data_binding",
    itemVariableExpression = "com.itg.itgtools.pages.itgui.RecyclerBindingVariables.ITEM",
    actionsVariableExpression = "com.itg.itgtools.pages.itgui.RecyclerBindingVariables.ACTIONS",
    actionsClassName = KSP_DEMO_ACTIONS_CLASS,
)
data class KspDataRow(
    override val stableId: Long,
    val title: String,
) : ItgListItem {
    @ItgPayload
    fun payload(oldItem: KspDataRow): Any? = if (oldItem.title != title) TITLE_PAYLOAD else null

    @ItgContentsSame
    fun contentsSame(oldItem: KspDataRow): Boolean = oldItem.title == title
}

private const val NAME_PAYLOAD = "name"
private const val TITLE_PAYLOAD = "title"
private const val USER_BINDING_CLASS = "com.itg.itgtools.databinding.ItemItgUiUserBinding"
private const val BANNER_BINDING_CLASS = "com.itg.itgtools.databinding.ItemItgUiBannerBinding"
private const val AUTO_BINDING_CLASS = "com.itg.itgtools.databinding.ItemItgUiAutoBindingBinding"
private const val KSP_DEMO_ACTIONS_CLASS = "com.itg.itgtools.pages.itgui.ksp.KspDemoActions"
