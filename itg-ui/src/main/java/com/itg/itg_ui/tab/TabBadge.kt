package com.itg.itg_ui.tab

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/** 角标相对 Tab 图标（无图标时相对标题）的位置。 */
enum class TabBadgePosition { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/**
 * Tab 角标配置。
 *
 * 用于描述 Tab 上的数字角标或纯红点；[TabImageBadge] 可显示图片角标。
 * 通过 [TabItem.badge] 声明初始角标，通过 [TabViewPagerAbility.updateBadge] 动态更新。
 */
open class TabBadge(
    /** 角标数字（≤0 时配合 [showAsDot] 展示纯红点） */
    val count: Int = 0,
    /** 纯红点模式（忽略 [count]，仅展示红色圆点） */
    val showAsDot: Boolean = false,
    /** 角标背景色（null = Material 默认 Badge 红色） */
    @ColorInt val backgroundColor: Int? = null,
    /** 最大显示数字，超过显示如 "99+"（默认 99） */
    val maxNumber: Int = 99,
    /** 角标位置，默认图标的右上角；没有图标时相对标题。 */
    val position: TabBadgePosition = TabBadgePosition.TOP_END,
    /** 水平向 Tab 中心移动的距离（dp）；null 保留 Material 默认值。 */
    val horizontalOffsetDp: Int? = null,
    /** 垂直向 Tab 中心移动的距离（dp）；null 保留 Material 默认值。 */
    val verticalOffsetDp: Int? = null,
) {
    init {
        require(maxNumber > 0) { "maxNumber 必须大于 0。" }
    }
}

/**
 * 图片角标。图片始终显示；[count] 或 [showAsDot] 设置后，还会在图片上显示数字或圆点。
 * [image] 和 [imageRes] 至少提供一个；同时提供时 [image] 优先。
 */
class TabImageBadge(
    val image: Drawable? = null,
    @DrawableRes val imageRes: Int? = null,
    /** 图片的宽高（dp）。 */
    val imageSizeDp: Int = 24,
    count: Int = 0,
    showAsDot: Boolean = false,
    @ColorInt backgroundColor: Int? = null,
    maxNumber: Int = 99,
    position: TabBadgePosition = TabBadgePosition.TOP_END,
    horizontalOffsetDp: Int? = null,
    verticalOffsetDp: Int? = null,
) : TabBadge(
    count, showAsDot, backgroundColor, maxNumber, position, horizontalOffsetDp, verticalOffsetDp,
) {
    init {
        require(image != null || imageRes != null) { "图片角标必须提供 image 或 imageRes。" }
        require(imageSizeDp > 0) { "imageSizeDp 必须大于 0。" }
    }
}
