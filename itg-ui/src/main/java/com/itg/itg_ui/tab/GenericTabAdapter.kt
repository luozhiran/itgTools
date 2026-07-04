package com.itg.itg_ui.tab

import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.lang.ref.WeakReference

/**
 * 通用 FragmentStateAdapter，根据 [TabItem] 列表自动创建 Fragment。
 *
 * 与 [com.google.android.material.tabs.TabLayout] 配合时，
 * 通过 [com.google.android.material.tabs.TabLayoutMediator] 自动同步标题。
 * 每条 [TabItem] 通过 [Class.newInstance] 反射创建 Fragment 实例。
 *
 * 内存安全：
 * - 继承 FragmentStateAdapter，不可见页面会被正确保存/恢复状态
 * - Fragment 实例由 Adapter 内部管理，业务层不直接持有引用
 * - [fragmentCache] 仅用于提供外部查询，不影响 Adapter 内部管理
 */
class GenericTabAdapter(
    private val hostActivity: FragmentActivity,
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<TabItem<*>>,
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    /** 已创建的 Fragment 缓存（position → WeakReference<Fragment>），用于懒加载回调等场景。
     * 使用 [WeakReference] 避免阻止 FragmentManager 回收已移除的 Fragment。 */
    private val fragmentCache = SparseArray<WeakReference<Fragment>>(tabs.size)

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        val item = tabs[position]
        val fragment = item.fragmentClass
            .getDeclaredConstructor()
            .newInstance()
        item.arguments?.let { fragment.arguments = it }
        fragmentCache.put(position, WeakReference(fragment))
        return fragment
    }

    /**
     * 获取已创建的 Fragment（可能为 null，取决于该页面是否已被实例化）。
     * ViewPager2 的 offscreenPageLimit 决定哪些 position 已创建。
     * 使用 [WeakReference] 确保不会阻止 FragmentManager 回收已移除的 Fragment。
     */
    fun getFragmentAt(position: Int): Fragment? = fragmentCache.get(position)?.get()

    /**
     * 提供 TabLayout 标题（当 [TabConfig.autoTitle] = true 时由 Mediator 调用）。
     */
    fun getPageTitle(position: Int): CharSequence? {
        val item = tabs[position]
        return item.title
            ?: item.titleRes?.let { hostActivity.getString(it) }
    }
}
