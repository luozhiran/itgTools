package com.itg.itg_ui.tab.style

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.itg.itg_ui.tab.TabBadge
import com.itg.itg_ui.tab.TabItem

/**
 * 完全自定义 Tab View 的结构化接口。
 *
 * 与旧方案（Lambda `(Inflater, TabLayout, Int, TabItem) -> View`）相比，优势：
 *  1. 支持选中/未选中的状态更新（不用每次重建 View）
 *  2. 支持角标更新回调
 *  3. 语义更清晰，生命周期明确
 *
 * 使用示例——带未读数的自定义 Tab：
 * ```
 * val provider = object : CustomTabViewProvider {
 *     override fun createView(
 *         inflater: LayoutInflater, parent: TabLayout,
 *         position: Int, item: TabItem<*>
 *     ): View {
 *         return inflater.inflate(R.layout.custom_tab_badge, parent, false)
 *     }
 *
 *     override fun bindView(view: View, position: Int, item: TabItem<*>) {
 *         view.findViewById<TextView>(R.id.tabTitle).text = item.title
 *     }
 *
 *     override fun onSelectedChanged(view: View, selected: Boolean) {
 *         view.findViewById<TextView>(R.id.tabTitle).apply {
 *             setTextColor(if (selected) Color.RED else Color.GRAY)
 *         }
 *     }
 * }
 * ```
 */
interface CustomTabViewProvider {

    /**
     * 创建 Tab View（每个 Tab 调用一次，在 Tab 首次创建时）。
     *
     * @param inflater  [LayoutInflater]，已设置 parent context 的 theme
     * @param parent    TabLayout 容器
     * @param position  Tab 在列表中的位置
     * @param item      当前 Tab 的声明信息
     */
    fun createView(
        inflater: LayoutInflater,
        parent: TabLayout,
        position: Int,
        item: TabItem<*>,
    ): View

    /**
     * 绑定数据到 View（在 [createView] 之后立即调用）。
     * 可用于后续数据刷新，但通常一次绑定即够。
     */
    fun bindView(view: View, position: Int, item: TabItem<*>) {}

    /**
     * 选中状态变化回调（每次 Tab 切换时触发）。
     *
     * @param view     之前 [createView] 返回的 View 实例
     * @param selected true = 该 Tab 被选中；false = 该 Tab 被取消选中
     */
    fun onSelectedChanged(view: View, selected: Boolean) {}

    /**
     * 角标更新回调（当业务层调用 [com.itg.itg_ui.tab.TabViewPagerAbility.updateBadge] 时触发）。
     * 默认不实现——如果自定义 View 中需要显示角标，覆写此方法即可。
     *
     * @param view  之前 [createView] 返回的 View 实例
     * @param badge 新的角标配置，null 表示清除角标
     */
    fun onBadgeChanged(view: View, badge: TabBadge?) {}
}
