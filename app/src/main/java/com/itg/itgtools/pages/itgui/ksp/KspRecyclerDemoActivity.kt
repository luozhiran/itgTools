package com.itg.itgtools.pages.itgui.ksp

import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.databinding.ActivityItgUiKspRecyclerBinding
import com.itg.itg_ui.recycler.RecyclerConfig
import com.itg.itg_ui.recycler.RecyclerViewAbility

class KspRecyclerDemoActivity :
    AutoBindingBaseActivity<ActivityItgUiKspRecyclerBinding, KspRecyclerDemoModel>(),
    KspDemoActions {

    private val recyclerAbility = RecyclerViewAbility(
        RecyclerConfig(hasFixedSize = true, itemViewCacheSize = 8)
    )

    private val adapter by lazy {
        createKspDemoRecyclerAdapter(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerAbility.bind(binding.recyclerView, this, adapter)
        recyclerAbility.observeItems(viewModel.rows)
        binding.changeName.setOnClickListener { viewModel.renameUser() }
        binding.addItem.setOnClickListener { viewModel.addItem() }
        binding.clearItems.setOnClickListener { viewModel.clearItems() }
    }

    override fun onUserClick(id: Long) = messages.toast("KSP ViewBinding User 点击：$id")
    override fun onBannerClick(id: Long) = messages.toast("KSP ViewBinding Banner 点击：$id")
    override fun onDataClick(id: Long) = messages.toast("KSP DataBinding 点击：$id")
}
