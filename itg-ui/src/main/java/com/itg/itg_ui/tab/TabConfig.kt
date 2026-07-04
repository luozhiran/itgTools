package com.itg.itg_ui.tab

import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.style.TabStyle

/**
 * 全局 Tab 行为配置。
 *
 * 采用与 itg-base Ability 一致的 Config 模式：
 * - 每个 field 都有合理默认值
 * - 业务层按需覆盖，零行代码跑默认行为
 *
 * 使用方式：
 * ```
 * override fun onCreateTabConfig() = TabConfig(
 *     tabMode = TabLayout.MODE_SCROLLABLE,
 *     swipeable = false,
 *     style = TabStyle(indicator = TabIndicatorStyle(color = 0xFFFF0000.toInt())),
 * )
 * ```
 */
data class TabConfig(
    /** Tab 模式：[TabLayout.MODE_FIXED]（默认）或 [TabLayout.MODE_SCROLLABLE] */
    val tabMode: Int = TabLayout.MODE_FIXED,
    /** Tab 对齐方式：[TabLayout.GRAVITY_FILL]（默认）或 [TabLayout.GRAVITY_CENTER] */
    val tabGravity: Int = TabLayout.GRAVITY_FILL,
    /** 默认选中位置（0-based），默认 0 */
    val defaultPosition: Int = 0,
    /** 是否允许用户滑动切换（默认 true）。设为 false 时只能通过点击 Tab 切换 */
    val swipeable: Boolean = true,
    /**
     * 离屏保留的页面数（默认 1，即保留当前页左右各 1 页）。
     * ViewPager2 默认行为也是 1。增大此值会预创建更多 Fragment，消耗更多内存，
     * 但可减少切换时的重建开销。
     */
    val offscreenPageLimit: Int = 1,
    /**
     * TabLayout 与 ViewPager2 联动时，是否自动设置 Tab 标题（默认 true）。
     * 设为 false 时需自行通过 [customTabViewProvider] 或手动设置标题。
     */
    val autoTitle: Boolean = true,
    /**
     * 全局视觉样式（null = 使用 TabLayout 自身的 XML 属性 / Material 默认样式）。
     * 非 null 时，[TabViewPagerAbility] 会逐项应用样式配置。
     */
    val style: TabStyle? = null,
    /**
     * 用户滑动/点击到某一页时的附加回调（null = 无额外操作）。
     * 页面切换已自动处理 [BaseTabFragment] 的可见性回调，此回调用于额外逻辑。
     */
    val onPageSelected: ((Int, Fragment) -> Unit)? = null,
    /**
     * 首次选中某 Tab 时是否触发懒加载回调（默认 true）。
     * 仅在 Fragment 为 [BaseTabFragment] 子类时生效。
     */
    val lazyLoadOnFirstSelect: Boolean = true,
)
