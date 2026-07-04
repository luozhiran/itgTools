package com.itg.itg_ui.tab.style

import android.content.res.ColorStateList
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * 单个 Tab 项的视觉配置。
 *
 * 注意：这是对 TabLayout 中每一个 Tab 的样式控制，不是对 Tab 内容 Fragment 的样式控制。
 *
 * Material Components 图标位置常量（TabLayout 无公开常量，使用整数值）：
 * - `0` = ICON_GRAVITY_START（图标在文字左侧，水平排列）
 * - `1` = ICON_GRAVITY_TOP（图标在文字上方，默认）
 * - `2` = ICON_GRAVITY_END（图标在文字右侧，水平排列）
 * - `3` = ICON_GRAVITY_BOTTOM（图标在文字下方）
 */
data class TabItemStyle(
    // ========== 尺寸 ==========

    /** Tab 最小宽度（dp），null = Material 默认（24dp + 文字宽度） */
    val minWidthDp: Int? = null,
    /** Tab 内边距（dp），null = Material 默认 */
    val padding: TabPadding? = null,

    // ========== 背景 ==========

    /** 选中态背景色（@ColorInt）或背景资源（@DrawableRes），null = 透明 */
    @ColorInt val selectedBackground: Int? = null,
    /** 未选中态背景色（@ColorInt）或背景资源（@DrawableRes），null = 透明 */
    @ColorInt val unselectedBackground: Int? = null,

    // ========== 点击反馈 ==========

    /** 水波纹/触摸反馈颜色（@ColorInt），null = Material 默认 */
    @ColorInt val rippleColor: Int? = null,

    // ========== 图标 ==========

    /**
     * 图标相对于文字的位置，取值 0-3：
     * - `1` = 图标在文字上方（默认）
     * - `0` = 图标在文字左侧
     * - `2` = 图标在文字右侧
     * - `3` = 图标在文字下方
     */
    val iconGravity: Int = 1, // ICON_GRAVITY_TOP
    /** 图标着色（支持选中/未选中双色），null = 不自动着色，使用图标原始颜色 */
    val iconTint: ColorStateList? = null,
    /** 图标大小（dp），null = Material 默认 24dp */
    val iconSizeDp: Int? = null,
    /** 图标与文字之间的间距（dp），null = Material 默认 8dp */
    val iconTextGapDp: Int? = null,
)
