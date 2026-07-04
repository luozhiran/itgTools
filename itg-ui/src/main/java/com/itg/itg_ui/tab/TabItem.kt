package com.itg.itg_ui.tab

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * 描述一个 Tab 页签。
 *
 * 每个 [TabItem] 对应 ViewPager2 中的一个页面。至少需要指定 [fragmentClass]，
 * 标题和图标可选。Adapter 通过反射实例化 Fragment，因此 Fragment 必须提供无参构造器。
 *
 * @param F Fragment 类型，推荐继承 [BaseTabFragment] 以获得懒加载和可见性回调支持。
 */
data class TabItem<F : Fragment>(
    /** Tab 标题文本（与 [titleRes] 二选一，直接传字符串） */
    val title: String? = null,
    /** Tab 标题资源 ID（与 [title] 二选一） */
    val titleRes: Int? = null,
    /** Tab 图标 [Drawable]（可选，与 [iconRes] 二选一） */
    val icon: Drawable? = null,
    /** Tab 图标资源 ID（可选，与 [icon] 二选一） */
    val iconRes: Int? = null,
    /**
     * Fragment 类对象，Adapter 通过 [Class.newInstance] 反射实例化。
     * 对应的 Fragment 必须有无参构造器（Android Fragment 标准要求）。
     */
    val fragmentClass: Class<F>,
    /** 传给 Fragment 的 arguments（可选），在 Fragment 创建时自动 [Fragment.setArguments] */
    val arguments: Bundle? = null,
    /** 初始角标配置（可选），运行时可通过 [TabViewPagerAbility.updateBadge] 动态更新 */
    val badge: TabBadge? = null,
    /** 唯一标识，默认使用 [fragmentClass] 的 canonicalName */
    val tag: String = fragmentClass.canonicalName ?: fragmentClass.simpleName,
)
