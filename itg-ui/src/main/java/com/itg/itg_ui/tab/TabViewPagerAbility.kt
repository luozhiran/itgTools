package com.itg.itg_ui.tab

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.itg.itg_base.ability.LifeAbility
import com.itg.itg_ui.tab.style.TabIndicatorStyle
import com.itg.itg_ui.tab.style.TabItemStyle
import com.itg.itg_ui.tab.style.TabStyle
import com.itg.itg_ui.tab.style.TabTextStyle
import java.lang.ref.WeakReference

/**
 * TabLayout + ViewPager2 绑定能力，继承 [LifeAbility] 获得生命周期感知。
 *
 * 职责：
 * 1. 创建 [GenericTabAdapter] 并设置到 ViewPager2
 * 2. 通过 [TabLayoutMediator] 联动 TabLayout 和 ViewPager2
 * 3. 应用 [TabStyle] 样式配置（指示器、文字、Tab 项、自定义 View）
 * 4. 管理 Tab 选中/取消选中事件，分发给 [BaseTabFragment]
 * 5. 管理角标（BadgeDrawable）
 * 6. 在 onDestroy 时清理 Mediator 和回调
 *
 * 使用方式：
 * ```
 * tabViewPager.inject(this)
 * tabViewPager.bind(
 *     tabLayout = binding.tabLayout,
 *     viewPager = binding.viewPager,
 *     tabs = listOf(TabItem("首页", fragmentClass = HomeFragment::class.java)),
 *     config = TabConfig(),
 * )
 * ```
 */
open class TabViewPagerAbility : LifeAbility() {

    // ==================== 内部状态 ====================

    private var adapter: GenericTabAdapter? = null
    private var tabMediator: TabLayoutMediator? = null
    private var config: TabConfig = TabConfig()
    private var tabs: List<TabItem<*>> = emptyList()

    private var tabLayout: TabLayout? = null
    private var viewPager: ViewPager2? = null

    /** 上次选中的 position，用于分发页面切换时的取消选中事件 */
    private var lastSelectedPosition = -1

    /** 已分发选中事件的 Fragment View，避免同一 View 被重复通知。 */
    private var selectedFragmentView: WeakReference<View>? = null

    private var fragmentManager: FragmentManager? = null

    /** 防止 [bind] 被重复调用 */
    private var isBound = false

    /** 初始页面回调的延迟 Runnable，用于 unbind 时取消 */
    private var initialPageRunnable: Runnable? = null

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            fragment: Fragment,
            view: View,
            savedInstanceState: Bundle?,
        ) {
            if (fm !== fragmentManager) return
            val position = adapter?.getPosition(fragment) ?: return
            if (position != lastSelectedPosition || position != viewPager?.currentItem) return

            // FragmentStateAdapter may create the selected Fragment after onPageSelected().
            // Post once so Fragment.onViewCreated() completes before business callbacks run.
            view.post {
                if (position == lastSelectedPosition && position == viewPager?.currentItem) {
                    dispatchSelectedFragment(position)
                }
            }
        }
    }

    // ==================== 绑定入口 ====================

    /**
     * 绑定 TabLayout + ViewPager2。
     *
     * @param tabLayout    Material TabLayout
     * @param viewPager    ViewPager2
     * @param tabs         Tab 声明列表
     * @param hostFragment 当宿主是 Fragment 时必须传入（用于获取 childFragmentManager）
     * @param config       可选配置（默认 [TabConfig()]）
     */
    open fun bind(
        tabLayout: TabLayout,
        viewPager: ViewPager2,
        tabs: List<TabItem<*>>,
        hostFragment: Fragment? = null,
        config: TabConfig = TabConfig(),
    ) {
        check(!isBound) {
            "TabViewPagerAbility.bind() 已调用过。如需重新绑定 Tab 列表，请先调用 unbind()，或创建新的 TabViewPagerAbility 实例。"
        }
        check(isActivityAlive()) {
            "TabViewPagerAbility 未注入生命周期或宿主 Activity 已销毁。请在 bind() 前调用 inject(activity)。"
        }
        require(tabs.isNotEmpty()) {
            "tabs 列表不能为空，至少需要一个 Tab。"
        }
        require(config.defaultPosition in tabs.indices) {
            "defaultPosition=${config.defaultPosition} 超出 Tab 范围 0..${tabs.lastIndex}。"
        }
        require(
            config.offscreenPageLimit == ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT ||
                config.offscreenPageLimit >= 1
        ) {
            "offscreenPageLimit 必须为 ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT(-1) 或大于等于 1。"
        }
        isBound = true

        this.tabLayout = tabLayout
        this.viewPager = viewPager
        this.tabs = tabs
        this.config = config

        val activity = ownerActivity

        // 1. 应用容器级样式（在 Tab 创建之前）
        config.style?.let { applyContainerStyle(tabLayout, it) }

        // 2. 创建 Adapter
        val fm = if (hostFragment != null) {
            hostFragment.childFragmentManager
        } else {
            activity.supportFragmentManager
        }
        fragmentManager = fm
        fm.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false)
        val adapterLifecycle = hostFragment?.viewLifecycleOwner?.lifecycle ?: activity.lifecycle
        adapter = GenericTabAdapter(activity, fm, adapterLifecycle, tabs)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = config.swipeable
        viewPager.offscreenPageLimit = config.offscreenPageLimit
        tabLayout.tabMode = config.tabMode
        tabLayout.tabGravity = config.tabGravity

        // 3. 注册页面切换回调（必须在 setCurrentItem 之前，确保能收到回调）
        viewPager.registerOnPageChangeCallback(pageChangeCallback)

        // 4. 设置默认页
        //    当 defaultPosition != currentItem（即 != 0）时，setCurrentItem 会触发
        //    onPageSelected 回调，从而自动完成首次选中逻辑。
        //    当 defaultPosition == 0 时，ViewPager2 已在该位置，需在步骤 7 手动触发。
        if (config.defaultPosition != viewPager.currentItem) {
            viewPager.setCurrentItem(config.defaultPosition, false)
        }

        // 5. TabLayoutMediator 联动
        val mediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // 标题
            if (config.autoTitle) {
                tab.text = adapter?.getPageTitle(position)
            }
            // 图标
            val item = tabs[position]
            configureTabIcon(tab, item)
            // 应用 TabItemStyle 到每个 tab
            config.style?.itemStyle?.let { applyItemStyle(tab, it) }
            // 自定义 Tab View（优先级最高，会覆盖 itemStyle 的大部分效果）
            val customProvider = config.style?.customTabViewProvider
            if (customProvider != null) {
                val provider = customProvider
                val customView = provider.createView(
                    LayoutInflater.from(tabLayout.context), tabLayout, position, item
                )
                provider.bindView(customView, position, item)
                tab.customView = customView
                provider.onBadgeChanged(customView, item.badge)
            } else {
                item.badge?.let { applyBadge(tab, it) }
            }
        }
        mediator.attach()
        tabMediator = mediator

        // 6. 应用指示器样式
        config.style?.indicator?.let { applyIndicatorStyle(tabLayout, it) }

        // 7. 应用文字样式（在 Mediator.attach() 之后，因为 attach 会创建 TextView）
        config.style?.textStyle?.let { applyTextStyle(tabLayout, it) }

        // 8. 确保初始 Tab（position == 0 且 defaultPosition == 0）收到生命周期回调
        //    ViewPager2 不会为已有位置触发 onPageSelected，
        //    使用 post 确保 ViewPager2 已完成 populate 且 Fragment 已创建。
        if (lastSelectedPosition < 0) {
            val initialPos = viewPager.currentItem
            if (initialPos in tabs.indices) {
                initialPageRunnable = Runnable {
                    initialPageRunnable = null
                    dispatchPageSelected(initialPos)
                }
                tabLayout.post(initialPageRunnable)
            }
        }
    }

    // ==================== 公开操作 ====================

    /** 切换到指定 Tab */
    fun selectTab(position: Int, smoothScroll: Boolean = true) {
        check(isBound) { "请先调用 bind() 再切换 Tab。" }
        require(position in tabs.indices) {
            "position=$position 超出 Tab 范围 0..${tabs.lastIndex}。"
        }
        viewPager?.setCurrentItem(position, smoothScroll)
    }

    /** 获取当前选中的 position */
    fun currentPosition(): Int = viewPager?.currentItem ?: 0

    /** 动态更新角标。传 [badge] 为 null 时清除角标 */
    fun updateBadge(position: Int, badge: TabBadge?) {
        val tab = tabLayout?.getTabAt(position) ?: return
        val customProvider = config.style?.customTabViewProvider
        if (customProvider != null) {
            tab.customView?.let { customProvider.onBadgeChanged(it, badge) }
            return
        }
        if (badge != null) {
            applyBadge(tab, badge)
        } else {
            tab.removeBadge()
        }
    }

    /** 获取指定 position 的 Fragment 实例（可能为 null，取决于是否已被 ViewPager2 创建） */
    fun getFragmentAt(position: Int): Fragment? = adapter?.getFragmentAt(position)

    // ==================== 页面切换回调 ====================

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            if (position == lastSelectedPosition) return // 防止重复回调
            dispatchPageSelected(position)
        }
    }

    /**
     * 分发页面选中事件，同时通知旧页面取消选中和新页面选中。
     * 提取为独立方法以便在 bind() 中手动触发初始回调。
     */
    private fun dispatchPageSelected(position: Int) {
        if (position !in tabs.indices) return

        // 通知旧页面：Tab 取消选中
        if (lastSelectedPosition >= 0 && lastSelectedPosition < tabs.size && lastSelectedPosition != position) {
            (adapter?.getFragmentAt(lastSelectedPosition) as? BaseTabFragment<*, *>)
                ?.onTabUnselectedInternal()
            notifyCustomViewSelection(lastSelectedPosition, selected = false)
            updateTextSizeForPosition(lastSelectedPosition, selected = false)
            selectedFragmentView = null
        }

        lastSelectedPosition = position

        // 通知新页面：Tab 选中。Fragment View 尚未创建时由 lifecycle callback 补发。
        dispatchSelectedFragment(position)
        notifyCustomViewSelection(position, selected = true)
        updateTextSizeForPosition(position, selected = true)
    }

    private fun dispatchSelectedFragment(position: Int) {
        val fragment = adapter?.getFragmentAt(position) ?: return
        val fragmentView = fragment.view ?: return
        if (selectedFragmentView?.get() === fragmentView) return

        selectedFragmentView = WeakReference(fragmentView)
        if (fragment is BaseTabFragment<*, *>) {
            fragment.onTabSelectedInternal(
                allowFirstVisible = config.lazyLoadOnFirstSelect,
            )
        }
        config.onPageSelected?.invoke(position, fragment)
    }

    // ==================== 样式应用：容器级 ====================

    private fun applyContainerStyle(tabLayout: TabLayout, style: TabStyle) {
        // 背景（支持 @ColorInt）
        style.tabBackground?.let { bg ->
            tabLayout.setBackgroundColor(bg)
        }
        // 高度
        style.tabHeightDp?.let {
            tabLayout.layoutParams = tabLayout.layoutParams?.apply {
                height = dp2pxInt(it)
            }
        }
        // 阴影
        style.tabElevationDp?.let {
            tabLayout.elevation = dp2px(it)
        }
        // 分割线：TabLayout 继承 HorizontalScrollView，不提供原生分割线 API。
        // 如需 Tab 间分割线，请使用 CustomTabViewProvider 在自定义布局中加入分割线元素。
        // TabStyle.tabDividerDrawable 和 tabDividerPaddingDp 字段保留以供未来扩展使用。
    }

    // ==================== 样式应用：指示器 ====================

    private fun applyIndicatorStyle(tabLayout: TabLayout, indicator: TabIndicatorStyle) {
        // 自定义 Drawable 优先
        if (indicator.drawable != null) {
            tabLayout.setSelectedTabIndicator(indicator.drawable)
        } else {
            val resolvedColor = indicator.color
                ?: getColorAttr(com.google.android.material.R.attr.colorPrimary)
            val cornerRadius = indicator.cornerRadiusDp ?: 0f
            if (cornerRadius > 0f) {
                val shapeDrawable = MaterialShapeDrawable(
                    ShapeAppearanceModel.builder()
                        .setAllCornerSizes(cornerRadius * tabLayout.context.resources.displayMetrics.density)
                        .build()
                ).apply {
                    fillColor = ColorStateList.valueOf(resolvedColor)
                    setTint(resolvedColor)
                }
                tabLayout.setSelectedTabIndicator(shapeDrawable)
            } else {
                tabLayout.setSelectedTabIndicatorColor(resolvedColor)
            }
        }
        // 高度
        indicator.heightDp?.let {
            tabLayout.setSelectedTabIndicatorHeight(dp2pxInt(it))
        }
        // 宽度（固定宽度模式）—— 非公开 API，通过反射设置
        indicator.widthDp?.let {
            applyIndicatorWidth(tabLayout, dp2pxInt(it))
        }
        // 水平内边距 —— 非公开 API，通过反射设置
        indicator.horizontalPaddingDp?.let {
            applyIndicatorHorizontalPadding(tabLayout, dp2pxInt(it))
        }
        // 重力
        tabLayout.setSelectedTabIndicatorGravity(indicator.gravity)
        // 与文字间距 —— 非公开 API，通过反射设置
        indicator.distanceFromTextDp?.let {
            applyIndicatorDistanceFromText(tabLayout, dp2pxInt(it))
        }
        // 动画
        if (indicator.animationEnabled) {
            @Suppress("DEPRECATION")
            tabLayout.tabIndicatorAnimationMode = TabLayout.INDICATOR_ANIMATION_MODE_LINEAR
            // 动画时长和插值器：优先通过公开 API（Material 1.4+），
            // 不存在则通过反射设置（低版本 Material 无公开 API）
            applyIndicatorAnimationDuration(tabLayout, indicator.animationDurationMs)
            indicator.animationInterpolator?.let {
                applyIndicatorInterpolator(tabLayout, it)
            }
        } else {
            applyIndicatorAnimationDuration(tabLayout, 0)
        }
    }

    // ==================== 样式应用：文字 ====================

    private fun applyTextStyle(tabLayout: TabLayout, textStyle: TabTextStyle) {
        // 颜色
        when {
            textStyle.textColorStateList != null -> {
                tabLayout.setTabTextColors(textStyle.textColorStateList)
            }
            textStyle.selectedColor != null || textStyle.unselectedColor != null -> {
                val unselected = textStyle.unselectedColor
                    ?: tabLayout.tabTextColors?.defaultColor
                    ?: Color.GRAY
                val selected = textStyle.selectedColor
                    ?: tabLayout.tabTextColors?.defaultColor
                    ?: Color.BLACK
                tabLayout.setTabTextColors(unselected, selected)
            }
        }
        // 字体、大小等在 Tab 创建后逐项设置
        for (i in 0 until tabLayout.tabCount) {
            applyTextStyleToTab(tabLayout.getTabAt(i), textStyle, selected = (i == currentPosition()))
        }
    }

    private fun applyTextStyleToTab(tab: TabLayout.Tab?, textStyle: TabTextStyle, selected: Boolean) {
        val textView = findTabTextView(tab) ?: return
        // 字号
        val size = if (selected) textStyle.selectedSizeSp else textStyle.unselectedSizeSp
        size?.let { textView.textSize = it }
        // 字体
        val tf = if (selected) {
            textStyle.selectedTypeface ?: textStyle.typeface
        } else {
            textStyle.unselectedTypeface ?: textStyle.typeface
        }
        tf?.let { textView.typeface = it }
        // 其他外观
        textStyle.letterSpacing?.let { textView.letterSpacing = it }
        textView.isAllCaps = textStyle.allCaps
        textView.maxLines = textStyle.maxLines
        textStyle.ellipsize?.let { textView.ellipsize = it }
    }

    private fun findTabTextView(tab: TabLayout.Tab?): TextView? {
        if (tab == null) return null
        // 如果有 customView，尝试从中找第一个 TextView
        if (tab.customView != null) {
            return tab.customView as? TextView
                ?: (tab.customView as? android.view.ViewGroup)?.let { vg ->
                    findTextViewInView(vg)
                }
        }
        // 默认 TabView 中查找 TextView
        val tabView = tab.view
        if (tabView.childCount > 0) {
            for (i in 0 until tabView.childCount) {
                val child = tabView.getChildAt(i)
                if (child is TextView) return child
            }
        }
        return null
    }

    private fun findTextViewInView(view: android.view.ViewGroup): TextView? {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is TextView) return child
            if (child is android.view.ViewGroup) {
                findTextViewInView(child)?.let { return it }
            }
        }
        return null
    }

    private fun updateTextSizeForPosition(position: Int, selected: Boolean) {
        val textStyle = config.style?.textStyle ?: return
        if (textStyle.selectedSizeSp == null && textStyle.unselectedSizeSp == null
            && textStyle.selectedTypeface == null && textStyle.unselectedTypeface == null
            && textStyle.typeface == null
        ) return
        val tab = tabLayout?.getTabAt(position) ?: return
        applyTextStyleToTab(tab, textStyle, selected)
    }

    // ==================== 样式应用：TabItemStyle ====================

    /**
     * 应用 [TabItemStyle] 到单个 Tab。
     * 通过 [TabLayoutMediator] 的 onConfigureTab 回调在每个 Tab 创建时调用。
     */
    private fun applyItemStyle(tab: TabLayout.Tab, itemStyle: TabItemStyle) {
        val view = tab.view

        // 最小宽度
        itemStyle.minWidthDp?.let { view.minimumWidth = dp2pxInt(it) }

        // 内边距
        itemStyle.padding?.let {
            view.setPadding(
                dp2pxInt(it.leftDp), dp2pxInt(it.topDp),
                dp2pxInt(it.rightDp), dp2pxInt(it.bottomDp)
            )
        }

        // 图标着色
        itemStyle.iconTint?.let { tint ->
            tab.icon?.setTintList(tint)
        }

        // 背景（选中/未选中 StateListDrawable，支持 @ColorInt 和 @DrawableRes）
        val selectedBg = itemStyle.selectedBackground
        val unselectedBg = itemStyle.unselectedBackground
        if (selectedBg != null || unselectedBg != null) {
            val stateList = StateListDrawable().apply {
                if (selectedBg != null) {
                    addState(
                        intArrayOf(android.R.attr.state_selected),
                        resolveBackgroundDrawable(view, selectedBg)
                    )
                }
                if (unselectedBg != null) {
                    addState(
                        intArrayOf(-android.R.attr.state_selected),
                        resolveBackgroundDrawable(view, unselectedBg)
                    )
                }
            }
            view.background = stateList
        }

        // 水波纹效果
        itemStyle.rippleColor?.let { rippleColor ->
            val mask = ColorDrawable(Color.WHITE)
            val ripple = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                view.background, // 保留现有背景
                mask
            )
            view.background = ripple
        }

        // 图标大小
        // 注意：setBounds 设置的是单个 tab.icon 的绘制区域，与 TabLayout.tabIconSize
        // 属性（通过 setTabIconSize 或 XML app:tabIconSize 设置）不同。
        // setBounds 优先于 TabLayout 全局设置，两者同时使用时以 setBounds 为准。
        itemStyle.iconSizeDp?.let { size ->
            tab.icon?.setBounds(0, 0, dp2pxInt(size), dp2pxInt(size))
        }
    }

    // ==================== 样式应用：自定义 View 状态通知 ====================

    private fun notifyCustomViewSelection(position: Int, selected: Boolean) {
        val provider = config.style?.customTabViewProvider ?: return
        tabLayout?.getTabAt(position)?.customView?.let { view ->
            provider.onSelectedChanged(view, selected)
        }
    }

    // ==================== 图标配置 ====================

    private fun configureTabIcon(tab: TabLayout.Tab, item: TabItem<*>) {
        val icon = (item.icon
            ?: item.iconRes?.let { AppCompatResources.getDrawable(tab.view.context, it) })?.mutate()
        if (icon != null) {
            tab.setIcon(icon)
        }
    }

    // ==================== 角标 ====================

    private fun applyBadge(tab: TabLayout.Tab, badge: TabBadge) {
        // Recreate the drawable so nullable style properties restore Material defaults on update.
        tab.removeBadge()
        if (!badge.showAsDot && badge.count <= 0) return
        val badgeDrawable = tab.orCreateBadge
        if (badge.showAsDot) {
            badgeDrawable.isVisible = true
            badgeDrawable.clearNumber()
        } else if (badge.count > 0) {
            // maxNumber 支持任意上限；直接保留实际 count，超限时 BadgeDrawable 才会显示 "+"。
            badgeDrawable.maxCharacterCount = BadgeDrawable.BADGE_CONTENT_NOT_TRUNCATED
            badgeDrawable.maxNumber = badge.maxNumber
            badgeDrawable.number = badge.count
            badgeDrawable.isVisible = true
        }
        badge.backgroundColor?.let { badgeDrawable.backgroundColor = it }
    }

    /**
     * 根据传入的 Int 值智能解析为 [Drawable]。
     * Android 资源 ID 格式为 0xPPTTEEEE，应用资源以 0x7F 开头。
     * @ColorInt 的 alpha 字节通常为 0xFF 或 0x00，不会与资源 ID 冲突。
     * 优先按资源 ID 解析（0x7F 开头或可通过 getResourceTypeName 验证），
     * 否则视为颜色值。
     */
    private fun resolveBackgroundDrawable(view: View, value: Int): Drawable {
        // 资源 ID 判断：应用资源以 0x7F 开头，系统资源以 0x01 开头
        val isResourceId = (value ushr 24) in setOf(0x7F, 0x01, 0x02)
        if (isResourceId) {
            return try {
                AppCompatResources.getDrawable(view.context, value) ?: ColorDrawable(value)
            } catch (_: android.content.res.Resources.NotFoundException) {
                ColorDrawable(value)
            }
        }
        return ColorDrawable(value)
    }

    /**
     * 获取 TabLayout 内部的 SlidingTabIndicator（私有内部类实例）。
     * Material 将指示器相关字段（宽度/内边距/间距）都放在该类中，TabLayout 自身不暴露。
     * 返回 null 表示未找到（可能因版本差异）。
     */
    private fun findSlidingIndicator(tabLayout: TabLayout): View? {
        for (i in 0 until tabLayout.childCount) {
            val child = tabLayout.getChildAt(i)
            if (child.javaClass.simpleName == "SlidingTabIndicator") {
                return child
            }
        }
        return null
    }

    /**
     * 设置指示器固定宽度，通过反射（Material 未提供公开 setter）。
     * 字段位于 SlidingTabIndicator 内部类，非 TabLayout 自身。
     */
    private fun applyIndicatorWidth(tabLayout: TabLayout, widthPx: Int) {
        val indicator = findSlidingIndicator(tabLayout) ?: return
        try {
            val field = indicator.javaClass.getDeclaredField("indicatorWidth")
            field.isAccessible = true
            field.setInt(indicator, widthPx)
        } catch (_: Exception) {
            // 反射失败则忽略（使用默认宽度）
        }
    }

    /**
     * 设置指示器水平内边距，通过反射（Material 未提供公开 setter）。
     * 字段位于 SlidingTabIndicator 内部类。
     */
    private fun applyIndicatorHorizontalPadding(tabLayout: TabLayout, paddingPx: Int) {
        val indicator = findSlidingIndicator(tabLayout) ?: return
        try {
            val field = indicator.javaClass.getDeclaredField("indicatorPadding")
            field.isAccessible = true
            field.setInt(indicator, paddingPx)
        } catch (_: Exception) {
            // 反射失败则忽略
        }
    }

    /**
     * 设置指示器与文字间距，通过反射（Material 未提供公开 setter）。
     * 字段位于 SlidingTabIndicator 内部类。
     */
    private fun applyIndicatorDistanceFromText(tabLayout: TabLayout, distancePx: Int) {
        val indicator = findSlidingIndicator(tabLayout) ?: return
        try {
            val field = indicator.javaClass.getDeclaredField("indicatorDistanceFromText")
            field.isAccessible = true
            field.setInt(indicator, distancePx)
        } catch (_: Exception) {
            // 反射失败则忽略（使用默认间距）
        }
    }

    // ==================== 工具方法（动画） ====================

    /**
     * 设置指示器动画时长，优先使用公开 API，不存在则反射（兼容低版本 Material）。
     */
    private fun applyIndicatorAnimationDuration(tabLayout: TabLayout, durationMs: Int) {
        // 尝试公开 API：TabLayout.setTabIndicatorAnimationDuration()（Material 1.4+ 存在）
        try {
            val setter = TabLayout::class.java.getMethod(
                "setTabIndicatorAnimationDuration", Int::class.java
            )
            setter.invoke(tabLayout, durationMs)
            return
        } catch (_: NoSuchMethodException) {
            // 公开 API 不存在，回退到反射私有字段
        }
        // 反射私有字段（Material < 1.4）
        try {
            val field = TabLayout::class.java.getDeclaredField("tabIndicatorAnimationDuration")
            field.isAccessible = true
            field.setInt(tabLayout, durationMs)
        } catch (_: Exception) {
            // 反射失败，使用默认时长（不影响功能）
        }
    }

    /**
     * 设置指示器动画插值器，优先使用公开 API，不存在则反射。
     */
    private fun applyIndicatorInterpolator(tabLayout: TabLayout, interpolator: android.view.animation.Interpolator) {
        try {
            val setter = TabLayout::class.java.getMethod(
                "setTabIndicatorInterpolator", android.view.animation.Interpolator::class.java
            )
            setter.invoke(tabLayout, interpolator)
            return
        } catch (_: NoSuchMethodException) {
            // 公开 API 不存在，回退到反射
        }
        try {
            val field = TabLayout::class.java.getDeclaredField("tabIndicatorInterpolator")
            field.isAccessible = true
            field.set(tabLayout, interpolator)
        } catch (_: Exception) {
            // 反射失败，使用默认插值器
        }
    }

    // ==================== 工具方法 ====================

    @ColorInt
    private fun getColorAttr(attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        val resolved = ownerActivity.theme.resolveAttribute(attrRes, typedValue, true)
        return if (resolved) {
            typedValue.data
        } else {
            Color.BLUE
        }
    }

    private fun dp2px(dp: Float): Float =
        dp * ownerActivity.resources.displayMetrics.density

    private fun dp2pxInt(dp: Int): Int =
        (dp * ownerActivity.resources.displayMetrics.density).toInt()

    private fun dp2pxInt(dp: Float): Int =
        (dp * ownerActivity.resources.displayMetrics.density).toInt()

    // ==================== 生命周期 ====================

    /**
     * 解绑 TabLayout + ViewPager2，清理所有内部资源。
     * 调用后可重新调用 [bind] 进行新的绑定。
     */
    open fun unbind() {
        val boundViewPager = viewPager
        boundViewPager?.let { vp ->
            try {
                vp.unregisterOnPageChangeCallback(pageChangeCallback)
            } catch (_: Exception) {
                // 回调可能已被移除
            }
        }
        // 取消尚未执行的初始页面回调 Runnable
        initialPageRunnable?.let { tabLayout?.removeCallbacks(it) }
        initialPageRunnable = null
        tabMediator?.detach()
        tabMediator = null
        fragmentManager?.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
        fragmentManager = null
        // ViewPager2 otherwise keeps the adapter (and its FragmentManager/tabs) after Fragment View teardown.
        boundViewPager?.adapter = null
        adapter = null
        tabLayout = null
        viewPager = null
        // 释放 config（可能持有 customTabViewProvider → 匿名类 → Activity 引用）
        config = TabConfig()
        tabs = emptyList()
        lastSelectedPosition = -1
        selectedFragmentView = null
        isBound = false
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        unbind()
    }
}
