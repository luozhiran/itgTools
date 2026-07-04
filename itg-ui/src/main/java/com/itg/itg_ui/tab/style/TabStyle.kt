package com.itg.itg_ui.tab.style

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * TabLayout 全局视觉样式聚合。
 *
 * 使用方式（在 [com.itg.itg_ui.tab.TabConfig] 中）：
 * ```
 * TabConfig(
 *     style = TabStyle(
 *         indicator = TabIndicatorStyle(color = 0xFFFF0000.toInt(), heightDp = 4),
 *         textStyle = TabTextStyle(
 *             selectedColor = 0xFF000000.toInt(),
 *             unselectedColor = 0xFF9E9E9E.toInt(),
 *             selectedSizeSp = 16f,
 *             selectedTypeface = Typeface.DEFAULT_BOLD,
 *         ),
 *     ),
 * )
 * ```
 *
 * 四个维度各自独立，未指定的部分（null）保持 Material 默认行为。
 */
data class TabStyle(
    /** 指示器样式（null = Material 默认） */
    val indicator: TabIndicatorStyle? = null,

    /** 文字样式（null = Material 默认） */
    val textStyle: TabTextStyle? = null,

    /** Tab 项样式（null = Material 默认） */
    val itemStyle: TabItemStyle? = null,

    // ========== TabLayout 容器级 ==========

    /** TabLayout 整体背景颜色（@ColorInt），null = 默认透明 */
    @ColorInt val tabBackground: Int? = null,
    /** TabLayout 高度（dp），null = wrap_content */
    val tabHeightDp: Int? = null,
    /** TabLayout 阴影高度（dp），null = Material 默认（无阴影） */
    val tabElevationDp: Float? = null,
    /** Tab 之间的分割线 [Drawable]，null = 无分割线 */
    val tabDividerDrawable: Drawable? = null,
    /** Tab 分割线垂直内边距（上下缩进，dp），null = 0 */
    val tabDividerPaddingDp: Int? = null,

    // ========== 自定义 Tab View（完全控制） ==========

    /**
     * 完全自定义 Tab View 的入口。
     *
     * null = 使用系统默认 TabView（TextView + 可选 icon）。
     * 非 null = 每个 Tab 调用此 Provider 创建自定义 View。
     *
     * 更简单的场景推荐先尝试 [TabTextStyle] + [TabItemStyle] + [com.itg.itg_ui.tab.TabItem.icon]，
     * 只有在需要特殊布局（如：自定义角标位置、两级文字、图标叠加等）时才使用此项。
     */
    val customTabViewProvider: CustomTabViewProvider? = null,
)
