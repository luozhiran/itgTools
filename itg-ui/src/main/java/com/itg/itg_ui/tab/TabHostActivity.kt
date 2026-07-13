package com.itg.itg_ui.tab

import android.os.Bundle
import androidx.viewbinding.ViewBinding
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itg_ui.R

/**
 * Tab 宿主 Activity。
 *
 * 业务层继承此类，在 [onCreateTabs] 中声明 Tab 列表即可——**无需**手动：
 * - 找 View
 * - 创建 Adapter
 * - 创建 TabLayoutMediator
 * - 管理生命周期
 *
 * 布局要求：
 * - 必须有 id 为 `tabLayout` 的 [com.google.android.material.tabs.TabLayout]
 * - 必须有 id 为 `viewPager` 的 [androidx.viewpager2.widget.ViewPager2]
 * - 其余内容（Toolbar、FAB 等）自由布局
 *
 * 如需自定义 id，覆写 [tabLayoutId] / [viewPagerId]。
 *
 * 示例：
 * ```
 * @Route(path = RoutePath.MAIN_HOME_ACTIVITY)
 * class MainActivity : TabHostActivity<ActivityMainBinding, MainModel>() {
 *
 *     override fun onCreateTabs(): List<TabItem<*>> = listOf(
 *         TabItem("首页", fragmentClass = HomeFragment::class.java),
 *         TabItem("我的", fragmentClass = ProfileFragment::class.java),
 *     )
 *
 *     override fun onCreateTabConfig() = TabConfig(
 *         tabMode = TabLayout.MODE_FIXED,
 *         swipeable = false,
 *     )
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         // Tab 无关的初始化放这里（Toolbar 等）
 *     }
 * }
 * ```
 *
 * @param VB ViewBinding 或 DataBinding 生成类
 * @param VM ViewModel 子类，须继承 [ItgModel]
 */
abstract class TabHostActivity<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseActivity<VB, VM>() {

    /**
     * Tab 绑定能力，用于编程式切换 Tab、更新角标等。
     * 业务层（Fragment 中）可通过 `(requireActivity() as? TabHostActivity<*, *>)?.tabViewPager` 访问。
     *
     * 子类可覆写以使用自定义 [TabViewPagerAbility] 子类：
     * ```
     * override val tabViewPager by lazy { MyCustomTabAbility() }
     * ```
     * 或覆写 [createTabViewPager] 工厂方法。
     */
    open val tabViewPager: TabViewPagerAbility by lazy { createTabViewPager() }

    /** 工厂方法：创建 [TabViewPagerAbility] 实例。子类覆写以返回自定义子类。 */
    protected open fun createTabViewPager(): TabViewPagerAbility = TabViewPagerAbility()

    // ==================== 子类必须实现的抽象方法 ====================

    /**
     * 声明 Tab 列表。
     * 返回的列表不能为空。
     */
    protected abstract fun onCreateTabs(): List<TabItem<*>>

    // ==================== 子类可选覆写 ====================

    /**
     * 自定义 Tab 全局配置。
     * 默认返回 [TabConfig()]（全部默认值）。
     */
    protected open fun onCreateTabConfig(): TabConfig = TabConfig()

    /** 布局中 TabLayout 的 id。默认 R.id.tabLayout */
    protected open val tabLayoutId: Int = R.id.tabLayout

    /** 布局中 ViewPager2 的 id。默认 R.id.viewPager */
    protected open val viewPagerId: Int = R.id.viewPager

    private var bindRunnable: Runnable? = null

    // ==================== 生命周期 ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tabViewPager.inject(this)

        val tabs = onCreateTabs()
        require(tabs.isNotEmpty()) {
            "onCreateTabs() 必须返回至少一个 Tab。"
        }

        // post 到下一帧：确保 DataBinding 布局已完成，避免 findViewById 返回 null
        bindRunnable = Runnable {
            tabViewPager.bind(
                tabLayout = requireNotNull(findViewById(tabLayoutId)) {
                    "布局中未找到 tabLayoutId=$tabLayoutId 对应的 TabLayout。"
                },
                viewPager = requireNotNull(findViewById(viewPagerId)) {
                    "布局中未找到 viewPagerId=$viewPagerId 对应的 ViewPager2。"
                },
                tabs = tabs,
                config = onCreateTabConfig(),
            )
        }
        binding.root.post(bindRunnable)
    }

    override fun onDestroy() {
        // 取消尚未执行的 bind Runnable，避免 Activity 销毁后持有引用
        bindRunnable?.let { binding.root.removeCallbacks(it) }
        bindRunnable = null
        super.onDestroy()
    }
}
