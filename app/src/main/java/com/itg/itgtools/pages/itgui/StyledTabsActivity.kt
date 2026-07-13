package com.itg.itgtools.pages.itgui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem
import com.itg.itg_ui.tab.style.TabIndicatorStyle
import com.itg.itg_ui.tab.style.TabItemStyle
import com.itg.itg_ui.tab.style.TabPadding
import com.itg.itg_ui.tab.style.TabStyle
import com.itg.itg_ui.tab.style.TabTextStyle
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding

class StyledTabsActivity : TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {
    private val categories = listOf("推荐", "热点", "科技", "财经", "体育", "文化", "本地")

    override fun onCreateTabs(): List<TabItem<*>> = categories.mapIndexed { index, title ->
        TabItem(
            title = title,
            fragmentClass = DemoPageFragment::class.java,
            arguments = DemoPageFragment.arguments(title, "服务端分类可映射为动态 Tab", pageColors[index % pageColors.size]),
            tag = "styled-$title",
        )
    }

    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_SCROLLABLE,
        tabGravity = TabLayout.GRAVITY_CENTER,
        style = TabStyle(
            tabBackground = Color.rgb(183, 28, 28),
            tabElevationDp = 4f,
            indicator = TabIndicatorStyle(
                color = Color.WHITE,
                heightDp = 3,
                widthDp = 28,
                cornerRadiusDp = 1.5f,
                horizontalPaddingDp = 4f,
                distanceFromTextDp = 6,
                animationDurationMs = 180,
            ),
            textStyle = TabTextStyle(
                selectedColor = Color.WHITE,
                unselectedColor = 0xB3FFFFFF.toInt(),
                selectedSizeSp = 16f,
                unselectedSizeSp = 14f,
                selectedTypeface = Typeface.DEFAULT_BOLD,
                allCaps = false,
            ),
            itemStyle = TabItemStyle(
                minWidthDp = 72,
                padding = TabPadding.horizontal(14f),
                rippleColor = 0x33FFFFFF,
            ),
        ),
        onPageSelected = { position, _ -> binding.status.text = "滚动样式 Tab：${categories[position]}" },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.title.text = "新闻样式：滚动模式、固定宽度圆角指示器、文字状态"
        binding.actionPrimary.text = "第一项"
        binding.actionSecondary.text = "最后一项"
        binding.actionTertiary.visibility = android.view.View.GONE
        binding.root.post {
            binding.actionPrimary.setOnClickListener { tabViewPager.selectTab(0) }
            binding.actionSecondary.setOnClickListener { tabViewPager.selectTab(categories.lastIndex) }
        }
    }

    companion object {
        private val pageColors = intArrayOf(
            0xFFFFEBEE.toInt(), 0xFFFFF3E0.toInt(), 0xFFE3F2FD.toInt(), 0xFFE8F5E9.toInt()
        )
    }
}
