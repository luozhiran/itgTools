package com.itg.itgtools.pages.itgui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.TabBadge
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem
import com.itg.itg_ui.tab.style.CustomTabViewProvider
import com.itg.itg_ui.tab.style.TabIndicatorStyle
import com.itg.itg_ui.tab.style.TabStyle
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding

class CustomTabsActivity : TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {
    private var unread = 8

    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        page("关注", 0xFFE8F5E9.toInt()),
        page("推荐", 0xFFE3F2FD.toInt(), TabBadge(showAsDot = true)),
        page("私信", 0xFFFFF3E0.toInt(), TabBadge(count = unread)),
    )

    override fun onCreateTabConfig() = TabConfig(
        autoTitle = false,
        style = TabStyle(
            indicator = TabIndicatorStyle(color = Color.TRANSPARENT, heightDp = 0),
            customTabViewProvider = DemoCustomTabProvider,
        ),
        onPageSelected = { position, _ -> binding.status.text = "自定义 Tab 选中 position=$position" },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.title.text = "CustomTabViewProvider：创建、绑定、选中状态、角标回调"
        binding.actionPrimary.text = "私信 +1"
        binding.actionSecondary.text = "清除推荐红点"
        binding.actionTertiary.text = "选中私信"
        binding.actionPrimary.setOnClickListener {
            unread++
            tabViewPager.updateBadge(2, TabBadge(count = unread))
        }
        binding.actionSecondary.setOnClickListener { tabViewPager.updateBadge(1, null) }
        binding.root.post { binding.actionTertiary.setOnClickListener { tabViewPager.selectTab(2) } }
    }

    private fun page(title: String, color: Int, badge: TabBadge? = null) = TabItem(
        title = title,
        fragmentClass = DemoPageFragment::class.java,
        arguments = DemoPageFragment.arguments(title, "完全自定义 Tab View", color),
        badge = badge,
        tag = "custom-$title",
    )

    private object DemoCustomTabProvider : CustomTabViewProvider {
        override fun createView(
            inflater: LayoutInflater,
            parent: TabLayout,
            position: Int,
            item: TabItem<*>,
        ): View = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(22.dp(parent), 12.dp(parent), 22.dp(parent), 12.dp(parent))
            addView(TextView(context).apply { tag = TITLE_TAG; textSize = 15f })
            addView(TextView(context).apply {
                tag = BADGE_TAG
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFFE53935.toInt())
                setPadding(6.dp(parent), 1.dp(parent), 6.dp(parent), 1.dp(parent))
                visibility = View.GONE
            })
        }

        override fun bindView(view: View, position: Int, item: TabItem<*>) {
            view.findViewWithTag<TextView>(TITLE_TAG).text = item.title
        }

        override fun onSelectedChanged(view: View, selected: Boolean) {
            view.setBackgroundColor(if (selected) 0xFFE3F2FD.toInt() else Color.TRANSPARENT)
            view.findViewWithTag<TextView>(TITLE_TAG)
                .setTextColor(if (selected) 0xFF1565C0.toInt() else 0xFF616161.toInt())
        }

        override fun onBadgeChanged(view: View, badge: TabBadge?) {
            view.findViewWithTag<TextView>(BADGE_TAG).apply {
                visibility = if (badge == null || (!badge.showAsDot && badge.count <= 0)) View.GONE else View.VISIBLE
                text = when {
                    badge == null -> ""
                    badge.showAsDot -> "•"
                    badge.count > badge.maxNumber -> "${badge.maxNumber}+"
                    else -> badge.count.toString()
                }
            }
        }

        private fun Int.dp(parent: TabLayout): Int =
            (this * parent.resources.displayMetrics.density).toInt()

        private const val TITLE_TAG = "demo-title"
        private const val BADGE_TAG = "demo-badge"
    }
}
