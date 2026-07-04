package com.itg.itgtools.pages.itgui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem
import com.itg.itg_ui.tab.style.TabIndicatorStyle
import com.itg.itg_ui.tab.style.TabItemStyle
import com.itg.itg_ui.tab.style.TabPadding
import com.itg.itg_ui.tab.style.TabStyle
import com.itg.itg_ui.tab.style.TabTextStyle
import com.itg.itgtools.R
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding

class AdvancedTabStylesActivity :
    TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {

    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        TabItem(
            titleRes = R.string.itg_ui_resource_tab,
            icon = AppCompatResources.getDrawable(this, android.R.drawable.ic_menu_compass),
            fragmentClass = DemoPageFragment::class.java,
            arguments = DemoPageFragment.arguments("资源标题", "titleRes + Drawable 图标", 0xFFFFF8E1.toInt()),
            tag = "advanced-resource",
        ),
        page("顶部指示器", android.R.drawable.ic_menu_upload, 0xFFE8EAF6.toInt()),
        page("状态背景", android.R.drawable.ic_menu_manage, 0xFFE0F2F1.toInt()),
    )

    override fun onCreateTabConfig(): TabConfig {
        val indicatorDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            color = ColorStateList.valueOf(0xFFFF6F00.toInt())
            cornerRadius = 12f
        }
        val iconTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(0xFFFF6F00.toInt(), 0xFF757575.toInt()),
        )
        val textColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(0xFFFF6F00.toInt(), 0xFF616161.toInt()),
        )
        return TabConfig(
            tabMode = TabLayout.MODE_FIXED,
            style = TabStyle(
                tabHeightDp = 64,
                indicator = TabIndicatorStyle(
                    drawable = indicatorDrawable,
                    heightDp = 6,
                    widthDp = 48,
                    gravity = TabLayout.INDICATOR_GRAVITY_TOP,
                    animationEnabled = false,
                ),
                textStyle = TabTextStyle(
                    textColorStateList = textColors,
                    typeface = Typeface.MONOSPACE,
                    selectedTypeface = Typeface.DEFAULT_BOLD,
                    selectedSizeSp = 15f,
                    unselectedSizeSp = 13f,
                    letterSpacing = 0.03f,
                    allCaps = false,
                ),
                itemStyle = TabItemStyle(
                    padding = TabPadding.all(8f),
                    selectedBackground = 0xFFFFF3E0.toInt(),
                    unselectedBackground = Color.TRANSPARENT,
                    rippleColor = 0x33FF6F00,
                    iconTint = iconTint,
                    iconSizeDp = 22,
                    iconTextGapDp = 6,
                ),
            ),
            onPageSelected = { position, _ -> binding.status.text = "高级样式 position=$position" },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.title.text = "高级样式：Drawable、顶部指示器、无动画、ColorStateList、状态背景"
        binding.actionPrimary.text = "切换下一项"
        binding.actionSecondary.visibility = android.view.View.GONE
        binding.actionTertiary.visibility = android.view.View.GONE
        binding.root.post {
            binding.actionPrimary.setOnClickListener {
                tabViewPager.selectTab((tabViewPager.currentPosition() + 1) % 3, smoothScroll = false)
            }
        }
    }

    private fun page(title: String, icon: Int, color: Int) = TabItem(
        title = title,
        iconRes = icon,
        fragmentClass = DemoPageFragment::class.java,
        arguments = DemoPageFragment.arguments(title, "完整 TabIndicatorStyle / TabTextStyle / TabItemStyle", color),
        tag = "advanced-$title",
    )
}
