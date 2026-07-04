package com.itg.itg_ui.tab

import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseFragment

/**
 * Tab 内容 Fragment 基类。
 *
 * 继承 [AutoBindingBaseFragment]，在现有 MVVM 能力之上增加 Tab 可见性感知：
 * - [onTabFirstVisible] — 懒加载（首次可见时触发，**只触发一次**）
 * - [onTabSelected] — 每次切回该 Tab 时触发
 * - [onTabUnselected] — 每次切走时触发
 * - [isTabVisible] — 当前是否可见
 *
 * 典型用法：
 * ```
 * class HomeFragment : BaseTabFragment<FragmentHomeBinding, HomeModel>() {
 *     override fun onTabFirstVisible() {
 *         viewModel.loadData()  // 仅首次可见时加载数据
 *     }
 * }
 * ```
 *
 * @param VB ViewBinding 或 DataBinding 生成类
 * @param VM ViewModel 子类，须继承 [ItgModel]
 */
abstract class BaseTabFragment<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseFragment<VB, VM>() {

    /** 当前 Tab 是否处于可见状态 */
    var isTabVisible: Boolean = false
        private set

    /** 是否已经完成首次可见加载 */
    private var firstVisibleHandled = false

    // ==================== 供 TabViewPagerAbility 调用的回调 ====================

    /**
     * Tab 被选中时调用（由 [TabViewPagerAbility] 分发）。
     *
     * @param firstTime 是否是该 Fragment 实例第一次被选中
     */
    internal fun onTabSelectedInternal(firstTime: Boolean) {
        isTabVisible = true
        onTabSelected()

        if (firstTime || !firstVisibleHandled) {
            if (!firstVisibleHandled) {
                firstVisibleHandled = true
                onTabFirstVisible()
            }
        }
    }

    /**
     * Tab 取消选中时调用（由 [TabViewPagerAbility] 分发）。
     */
    internal fun onTabUnselectedInternal() {
        isTabVisible = false
        onTabUnselected()
    }

    // ==================== 子类可覆写的钩子 ====================

    /**
     * Tab 变为可见时回调。
     * 子类可覆写以实现可见性感知（如暂停/恢复动画、埋点曝光、刷新过期数据）。
     */
    protected open fun onTabSelected() {}

    /**
     * Tab 变为不可见时回调。
     * 子类可覆写以暂停操作（如停止滚动、取消网络请求）。
     */
    protected open fun onTabUnselected() {}

    /**
     * Tab 首次变为可见时回调——**只触发一次**。
     * 用于懒加载数据，避免一次性加载所有 Tab 数据。
     *
     * 默认行为：调用 ViewModel 的 loadData() 方法（如果存在）。
     * 子类应覆写此方法实现自己的懒加载逻辑。
     */
    protected open fun onTabFirstVisible() {
        try {
            viewModel::class.java.getMethod("loadData").invoke(viewModel)
        } catch (_: NoSuchMethodException) {
            // ViewModel 没有 loadData 方法，什么都不做
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firstVisibleHandled = false // Fragment 视图可能重建，允许再次触发首次可见
    }
}
