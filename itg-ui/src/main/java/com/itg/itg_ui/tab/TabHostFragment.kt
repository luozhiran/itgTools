package com.itg.itg_ui.tab

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseFragment
import com.itg.itg_ui.R

/**
 * Tab 宿主 Fragment。
 *
 * 与 [TabHostActivity] 能力对称，用于"页面内嵌二级 Tab"场景。
 * 例如：一个 Activity 中有外层 Tab，某个 Tab 页面内部又嵌了二级分类 Tab。
 *
 * 布局要求：与 [TabHostActivity] 相同（id 为 `tabLayout` 的 TabLayout + id 为 `viewPager` 的 ViewPager2）。
 *
 * 注意：子 Tab 的 Fragment 的 ViewModel 与宿主 Activity 共享（Fragment 默认行为），
 * 可通过 [androidx.fragment.app.Fragment.requireActivity] 获取。
 *
 * @param VB ViewBinding 或 DataBinding 生成类
 * @param VM ViewModel 子类，须继承 [ItgModel]
 */
abstract class TabHostFragment<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseFragment<VB, VM>() {

    /**
     * Tab 绑定能力，用于编程式切换 Tab、更新角标等。
     * 业务层（子 Fragment 中）可通过 `(parentFragment as? TabHostFragment<*, *>)?.tabViewPager` 访问。
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

    protected abstract fun onCreateTabs(): List<TabItem<*>>

    // ==================== 子类可选覆写 ====================

    protected open fun onCreateTabConfig(): TabConfig = TabConfig()
    protected open val tabLayoutId: Int = R.id.tabLayout
    protected open val viewPagerId: Int = R.id.viewPager

    private var bindRunnable: Runnable? = null

    // ==================== 生命周期 ====================

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        check(activity is AppCompatActivity) {
            "TabHostFragment 要求宿主 Activity 继承自 AppCompatActivity，" +
                "当前宿主类型为 ${activity.javaClass.name}。" +
                "请将宿主 Activity 改为继承 AutoBindingBaseActivity 或 AppCompatActivity。"
        }
        tabViewPager.inject(activity)

        val tabs = onCreateTabs()
        require(tabs.isNotEmpty()) {
            "onCreateTabs() 必须返回至少一个 Tab。"
        }

        bindRunnable = Runnable {
            tabViewPager.bind(
                tabLayout = view.findViewById(tabLayoutId),
                viewPager = view.findViewById(viewPagerId),
                tabs = tabs,
                hostFragment = this,
                config = onCreateTabConfig(),
            )
        }
        view.post(bindRunnable)
    }

    override fun onDestroyView() {
        // 取消尚未执行的 bind Runnable，避免 Fragment 销毁后持有引用
        bindRunnable?.let { view?.removeCallbacks(it) }
        bindRunnable = null
        super.onDestroyView()
    }
}
