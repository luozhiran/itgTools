package com.itg.itg_ui.tab

import androidx.annotation.ColorInt

/**
 * Tab 角标配置。
 *
 * 用于描述 Tab 上的角标样式——数字角标或纯红点。
 * 通过 [TabItem.badge] 声明初始角标，通过 [TabViewPagerAbility.updateBadge] 动态更新。
 */
data class TabBadge(
    /** 角标数字（≤0 时配合 [showAsDot] 展示纯红点） */
    val count: Int = 0,
    /** 纯红点模式（忽略 [count]，仅展示红色圆点） */
    val showAsDot: Boolean = false,
    /** 角标背景色（null = Material 默认 Badge 红色） */
    @ColorInt val backgroundColor: Int? = null,
    /** 最大显示数字，超过显示如 "99+"（默认 99） */
    val maxNumber: Int = 99,
)
