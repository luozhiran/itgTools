package com.itg.itg_ui.tab.style

import android.graphics.drawable.Drawable
import android.view.animation.Interpolator
import androidx.annotation.ColorInt
import com.google.android.material.tabs.TabLayout

/**
 * TabLayout 指示器（Indicator）视觉配置。
 *
 * Material 默认行为：底部横线，颜色 = colorPrimary，高度 2dp，自动跟随 Tab 宽度。
 * 以下所有字段为 null 时保持 Material 默认行为。
 */
data class TabIndicatorStyle(
    // ========== 颜色 ==========

    /** 指示器颜色（null = Material 默认 colorPrimary）。支持 @ColorInt */
    @ColorInt val color: Int? = null,

    // ========== 尺寸 ==========

    /** 指示器高度（dp），null = Material 默认 2dp */
    val heightDp: Int? = null,
    /** 指示器固定宽度（dp），null = 自动跟随 Tab 文字宽度 */
    val widthDp: Int? = null,

    // ========== 形状 ==========

    /** 指示器圆角半径（dp），null = 直角。与 [drawable] 互斥，[drawable] 优先级更高 */
    val cornerRadiusDp: Float? = null,
    /** 完全自定义指示器 [Drawable]。设置后 [color]/[heightDp]/[cornerRadiusDp] 被忽略 */
    val drawable: Drawable? = null,
    /** 指示器水平内边距（左右），dp。用于缩小指示器相对于 Tab 的宽度 */
    val horizontalPaddingDp: Float? = null,

    // ========== 位置 ==========

    /**
     * 指示器位置：[TabLayout.INDICATOR_GRAVITY_BOTTOM]（默认）、
     * [TabLayout.INDICATOR_GRAVITY_TOP]、[TabLayout.INDICATOR_GRAVITY_CENTER]、
     * [TabLayout.INDICATOR_GRAVITY_STRETCH]
     */
    val gravity: Int = TabLayout.INDICATOR_GRAVITY_BOTTOM,

    // ========== 动画 ==========

    /** 指示器切换动画时长（ms），默认 300ms */
    val animationDurationMs: Int = 300,
    /** 动画插值器（null = Material 默认 LinearOutSlowInInterpolator） */
    val animationInterpolator: Interpolator? = null,
    /** 是否启用指示器切换动画（默认 true） */
    val animationEnabled: Boolean = true,

    // ========== 高级 ==========

    /** 指示器与文字之间的间距（dp），null = Material 默认 */
    val distanceFromTextDp: Int? = null,
)
