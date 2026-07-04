package com.itg.itgtools.pages.itgui

import android.os.Bundle
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding

class DynamicTabsActivity : TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {
    private var loggedIn = false
    private var showDiscover = true

    override fun onCreateTabs(): List<TabItem<*>> = buildTabs()

    override fun onCreateTabConfig() = TabConfig(
        onPageSelected = { position, _ -> binding.status.text = "动态列表选中 position=$position" }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.title.text = "动态 Tab：登录态变化、过滤禁用、unbind 后重新 bind"
        binding.actionPrimary.text = "切换登录态"
        binding.actionSecondary.text = "显示/隐藏发现"
        binding.actionTertiary.text = "当前状态"
        binding.actionPrimary.setOnClickListener { loggedIn = !loggedIn; rebuild() }
        binding.actionSecondary.setOnClickListener { showDiscover = !showDiscover; rebuild() }
        binding.actionTertiary.setOnClickListener { updateStatus() }
        updateStatus()
    }

    private fun buildTabs(): List<TabItem<*>> = buildList {
        add(page("首页", 0xFFE8F5E9.toInt()))
        if (showDiscover) add(page("发现", 0xFFE3F2FD.toInt()))
        if (loggedIn) add(page("消息", 0xFFFFF3E0.toInt()))
        add(page(if (loggedIn) "我的" else "登录", 0xFFF3E5F5.toInt()))
    }

    private fun rebuild() {
        tabViewPager.unbind()
        binding.root.post {
            tabViewPager.bind(binding.tabLayout, binding.viewPager, buildTabs(), config = onCreateTabConfig())
            updateStatus()
        }
    }

    private fun updateStatus() {
        binding.status.text = "loggedIn=$loggedIn，showDiscover=$showDiscover，tabs=${buildTabs().size}"
    }

    private fun page(title: String, color: Int) = TabItem(
        title = title,
        fragmentClass = DemoPageFragment::class.java,
        arguments = DemoPageFragment.arguments(title, "该页面来自运行时 buildTabs()", color),
        tag = "dynamic-$title",
    )
}
