package com.itg.itgtools.pages.itgui

import android.graphics.Color
import android.os.Bundle
import com.itg.itg_ui.tab.TabBadge
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding

class BasicTabsActivity : TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {
    private var badgeCount = 3

    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        page("首页", "默认选中，首次可见时触发懒加载", Color.rgb(232, 245, 233), android.R.drawable.ic_menu_view),
        page("发现", "滑动或点击切换，观察生命周期日志", Color.rgb(227, 242, 253), android.R.drawable.ic_menu_search),
        page("消息", "初始数字角标和运行时角标更新", Color.rgb(255, 243, 224), android.R.drawable.ic_dialog_email, TabBadge(count = badgeCount)),
        page("我的", "支持 selectTab/currentPosition/getFragmentAt", Color.rgb(243, 229, 245), android.R.drawable.ic_menu_myplaces),
    )

    override fun onCreateTabConfig() = TabConfig(
        offscreenPageLimit = 1,
        onPageSelected = { position, fragment ->
            binding.status.text = "position=$position，fragment=${fragment.javaClass.simpleName}"
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.title.text = "基础 Tab：标题、图标、懒加载、角标、公开 API"
        binding.actionPrimary.text = "下一页"
        binding.actionSecondary.text = "消息角标 +1"
        binding.actionTertiary.text = "获取 Fragment"
        binding.actionPrimary.isEnabled = false
        binding.root.post { binding.actionPrimary.isEnabled = true }

        binding.actionPrimary.setOnClickListener {
            val next = (tabViewPager.currentPosition() + 1) % onCreateTabs().size
            tabViewPager.selectTab(next)
        }
        binding.actionSecondary.setOnClickListener {
            badgeCount++
            tabViewPager.updateBadge(2, TabBadge(count = badgeCount))
        }
        binding.actionTertiary.setOnClickListener {
            val position = tabViewPager.currentPosition()
            binding.status.text = "当前 Fragment：${tabViewPager.getFragmentAt(position)?.javaClass?.simpleName ?: "尚未创建"}"
        }
        binding.viewPager.setPageTransformer { page, position ->
            page.alpha = 0.5f + (1f - kotlin.math.abs(position)).coerceIn(0f, 1f) * 0.5f
            page.translationX = -position * page.width * 0.08f
        }
    }

    private fun page(
        title: String,
        description: String,
        color: Int,
        icon: Int,
        badge: TabBadge? = null,
    ) = TabItem(
        title = title,
        iconRes = icon,
        fragmentClass = DemoPageFragment::class.java,
        arguments = DemoPageFragment.arguments(title, description, color),
        badge = badge,
        tag = "basic-$title",
    )
}
