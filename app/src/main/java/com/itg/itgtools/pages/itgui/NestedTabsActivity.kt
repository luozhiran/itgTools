package com.itg.itgtools.pages.itgui

import android.graphics.Color
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabHostFragment
import com.itg.itg_ui.tab.TabItem
import com.itg.itg_ui.tab.style.TabIndicatorStyle
import com.itg.itg_ui.tab.style.TabStyle
import com.itg.itg_ui.tab.style.TabTextStyle
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding
import com.itg.itgtools.databinding.FragmentItgUiNestedTabsBinding

class NestedTabsActivity : TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {
    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        TabItem(
            title = "概览",
            fragmentClass = DemoPageFragment::class.java,
            arguments = DemoPageFragment.arguments("外层概览", "切换到“嵌套分类”查看 childFragmentManager", 0xFFE8F5E9.toInt()),
            tag = "nested-overview",
        ),
        TabItem(title = "嵌套分类", fragmentClass = NestedTabsFragment::class.java, tag = "nested-host"),
    )

    override fun onCreateTabConfig() = TabConfig(
        onPageSelected = { position, _ -> binding.status.text = "外层 Tab position=$position" }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.title.text = "嵌套 Tab：外层 TabHostActivity + 内层 TabHostFragment"
        binding.actionPrimary.text = "进入嵌套页"
        binding.actionSecondary.visibility = android.view.View.GONE
        binding.actionTertiary.visibility = android.view.View.GONE
        binding.root.post { binding.actionPrimary.setOnClickListener { tabViewPager.selectTab(1) } }
    }
}

class NestedTabsFragment : TabHostFragment<FragmentItgUiNestedTabsBinding, ItgUiDemoModel>() {
    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        child("Android", 0xFFE3F2FD.toInt()),
        child("Kotlin", 0xFFF3E5F5.toInt()),
        child("Material", 0xFFFFF3E0.toInt()),
    )

    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_SCROLLABLE,
        style = TabStyle(
            indicator = TabIndicatorStyle(color = Color.MAGENTA, heightDp = 3, cornerRadiusDp = 1.5f),
            textStyle = TabTextStyle(selectedColor = Color.MAGENTA, allCaps = false),
        ),
    )

    private fun child(title: String, color: Int) = TabItem(
        title = title,
        fragmentClass = DemoPageFragment::class.java,
        arguments = DemoPageFragment.arguments(title, "由 NestedTabsFragment.childFragmentManager 管理", color),
        tag = "nested-child-$title",
    )
}
