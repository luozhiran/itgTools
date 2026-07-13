package com.itg.itgtools.pages.itgui

import android.graphics.Color
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.TabBadge
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem
import com.itg.itg_ui.tab.style.TabIndicatorStyle
import com.itg.itg_ui.tab.style.TabItemStyle
import com.itg.itg_ui.tab.style.TabPadding
import com.itg.itg_ui.tab.style.TabStyle
import com.itg.itg_ui.tab.style.TabTextStyle
import com.itg.itgtools.databinding.ActivityItgUiBottomTabsBinding

class BottomNavigationTabsActivity :
    TabHostActivity<ActivityItgUiBottomTabsBinding, ItgUiDemoModel>() {

    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        page("首页", android.R.drawable.ic_menu_view, 0xFFE8F5E9.toInt()),
        page("发现", android.R.drawable.ic_menu_search, 0xFFE3F2FD.toInt()),
        page("消息", android.R.drawable.ic_dialog_email, 0xFFFFF3E0.toInt(), TabBadge(showAsDot = true)),
        page("我的", android.R.drawable.ic_menu_myplaces, 0xFFF3E5F5.toInt()),
    )

    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_FIXED,
        swipeable = false,
        style = TabStyle(
            indicator = TabIndicatorStyle(color = Color.TRANSPARENT, heightDp = 0),
            textStyle = TabTextStyle(
                selectedColor = 0xFF1976D2.toInt(),
                unselectedColor = 0xFF757575.toInt(),
                selectedSizeSp = 11f,
                unselectedSizeSp = 11f,
                allCaps = false,
            ),
            itemStyle = TabItemStyle(
                padding = TabPadding.vertical(8f),
                iconGravity = 1,
                iconSizeDp = 24,
            ),
        ),
        onPageSelected = { position, _ -> binding.status.text = "底部导航选中：${onCreateTabs()[position].title}" },
    )

    private fun page(title: String, icon: Int, color: Int, badge: TabBadge? = null) = TabItem(
        title = title,
        iconRes = icon,
        fragmentClass = DemoPageFragment::class.java,
        arguments = DemoPageFragment.arguments(title, "底部导航模式：禁止左右滑动", color),
        badge = badge,
        tag = "bottom-$title",
    )
}
