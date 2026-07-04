package com.itg.itgtools.pages.itgui

import android.content.Intent
import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.databinding.ActivityItgUiExamplesBinding

class ItgUiExamplesActivity :
    AutoBindingBaseActivity<ActivityItgUiExamplesBinding, ItgUiDemoModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.basicTabs.opens<BasicTabsActivity>()
        binding.styledTabs.opens<StyledTabsActivity>()
        binding.bottomTabs.opens<BottomNavigationTabsActivity>()
        binding.customTabs.opens<CustomTabsActivity>()
        binding.nestedTabs.opens<NestedTabsActivity>()
        binding.dynamicTabs.opens<DynamicTabsActivity>()
        binding.manualTabs.opens<ManualTabsActivity>()
        binding.recycler.opens<RecyclerDemoActivity>()
    }

    private inline fun <reified T : android.app.Activity> android.view.View.opens() {
        setOnClickListener { startActivity(Intent(this@ItgUiExamplesActivity, T::class.java)) }
    }
}
