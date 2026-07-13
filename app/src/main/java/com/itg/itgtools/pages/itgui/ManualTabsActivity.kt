package com.itg.itgtools.pages.itgui

import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabItem
import com.itg.itg_ui.tab.TabViewPagerAbility
import com.itg.itgtools.databinding.ActivityItgUiManualTabsBinding

class ManualTabsActivity :
    AutoBindingBaseActivity<ActivityItgUiManualTabsBinding, ItgUiDemoModel>() {
    private val tabAbility = DemoTabViewPagerAbility()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabAbility.inject(this)
        tabAbility.bind(
            tabLayout = binding.demoTabLayout,
            viewPager = binding.demoViewPager,
            tabs = listOf(
                TabItem(
                    title = "BaseTabFragment",
                    fragmentClass = DemoPageFragment::class.java,
                    arguments = DemoPageFragment.arguments("手动页面", "Activity 不继承 TabHostActivity", 0xFFE3F2FD.toInt()),
                    tag = "manual-base",
                ),
                TabItem(title = "普通 Fragment", fragmentClass = PlainDemoFragment::class.java, tag = "manual-plain"),
            ),
            config = TabConfig(
                lazyLoadOnFirstSelect = false,
                onPageSelected = { position, fragment ->
                    binding.status.text = "自定义 id + XML 样式 + 禁用懒加载：position=$position，${fragment.javaClass.simpleName}"
                }
            ),
        )
        binding.selectSecond.setOnClickListener { tabAbility.selectTab(1) }
        binding.demoViewPager.setPageTransformer { page, position ->
            page.rotationY = position * -12f
            page.alpha = (1f - kotlin.math.abs(position) * 0.35f).coerceAtLeast(0.65f)
        }
    }

    override fun onDestroy() {
        tabAbility.unbind()
        super.onDestroy()
    }
}

/** 对应 README 的 L3 整体替换入口，可继续覆写 bind/unbind 扩展行为。 */
class DemoTabViewPagerAbility : TabViewPagerAbility()
