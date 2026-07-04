package com.itg.itg_ui.tab.style

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import androidx.annotation.ColorInt

/**
 * Tab 标签文字视觉配置。
 *
 * 核心：通过 [selectedColor] + [unselectedColor] 自动创建 ColorStateList 设置到 TabLayout。
 * 如需更复杂的 ColorStateList（如 disabled 态），可使用 [textColorStateList] 整体替换。
 */
data class TabTextStyle(
    // ========== 颜色 ==========

    /** 选中态文字颜色（@ColorInt），与 [unselectedColor] 配对使用 */
    @ColorInt val selectedColor: Int? = null,
    /** 未选中态文字颜色（@ColorInt），与 [selectedColor] 配对使用 */
    @ColorInt val unselectedColor: Int? = null,
    /**
     * 完整 [ColorStateList]（优先级高于 [selectedColor]/[unselectedColor]）。
     * 用于需要 disabled 态等复杂颜色场景。
     */
    val textColorStateList: ColorStateList? = null,

    // ========== 字号 ==========

    /** 选中态文字大小（sp），null = Material 默认（14sp） */
    val selectedSizeSp: Float? = null,
    /** 未选中态文字大小（sp），null = Material 默认（14sp） */
    val unselectedSizeSp: Float? = null,

    // ========== 字体 ==========

    /** 全局字体（选中/未选中统一），null = 系统默认 */
    val typeface: Typeface? = null,
    /** 选中态专用字体（如加粗），null = 使用 [typeface] */
    val selectedTypeface: Typeface? = null,
    /** 未选中态专用字体，null = 使用 [typeface] */
    val unselectedTypeface: Typeface? = null,

    // ========== 外观 ==========

    /** 字母间距（em），null = Material 默认 */
    val letterSpacing: Float? = null,
    /** 全部大写（Material 默认 true） */
    val allCaps: Boolean = true,
    /** 最大行数 */
    val maxLines: Int = 1,
    /** 文字省略方式（null = END，即末尾省略号） */
    val ellipsize: TextUtils.TruncateAt? = null,
)
