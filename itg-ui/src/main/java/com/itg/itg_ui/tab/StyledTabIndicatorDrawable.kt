package com.itg.itg_ui.tab

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import java.lang.ref.WeakReference

/**
 * Applies indicator sizing and positioning without depending on TabLayout private fields.
 * TabLayout owns the animated outer bounds; this drawable only adjusts the delegated drawing bounds.
 */
internal class StyledTabIndicatorDrawable(
    private val delegate: Drawable,
    private val tabLayout: TabLayout,
    private val widthPx: Int?,
    private val horizontalPaddingPx: Int,
    private val heightPx: Int,
    private val distanceFromTextPx: Int?,
) : Drawable(), Drawable.Callback {

    private val verticalBounds = Rect()
    private val textBounds = Rect()
    private var cachedTabPosition = TabLayout.Tab.INVALID_POSITION
    private var cachedTextView: WeakReference<TextView>? = null

    init {
        delegate.callback = this
    }

    override fun draw(canvas: Canvas) {
        val outer = bounds
        val availableLeft = outer.left + horizontalPaddingPx
        val availableRight = (outer.right - horizontalPaddingPx).coerceAtLeast(availableLeft)
        val availableWidth = availableRight - availableLeft
        val drawWidth = widthPx?.coerceAtMost(availableWidth) ?: availableWidth
        val left = availableLeft + (availableWidth - drawWidth) / 2
        val right = left + drawWidth

        resolveVerticalBounds(outer, verticalBounds)
        delegate.setBounds(left, verticalBounds.top, right, verticalBounds.bottom)
        delegate.draw(canvas)
    }

    private fun resolveVerticalBounds(outer: Rect, output: Rect) {
        if (tabLayout.tabIndicatorGravity == TabLayout.INDICATOR_GRAVITY_STRETCH) {
            output.set(outer)
            return
        }

        val distance = distanceFromTextPx ?: run {
            output.set(outer)
            return
        }
        val slidingIndicator = tabLayout.getChildAt(0) as? ViewGroup
            ?: run {
                output.set(outer)
                return
            }
        val textView = selectedTextView() ?: run {
            output.set(outer)
            return
        }
        textView.getDrawingRect(textBounds)
        slidingIndicator.offsetDescendantRectToMyCoords(textView, textBounds)

        when (tabLayout.tabIndicatorGravity) {
            TabLayout.INDICATOR_GRAVITY_BOTTOM -> {
                val top = (textBounds.bottom + distance)
                    .coerceAtMost(slidingIndicator.height - heightPx)
                    .coerceAtLeast(0)
                output.set(outer.left, top, outer.right, top + heightPx)
            }
            TabLayout.INDICATOR_GRAVITY_TOP -> {
                val bottom = (textBounds.top - distance)
                    .coerceAtLeast(heightPx)
                    .coerceAtMost(slidingIndicator.height)
                output.set(outer.left, bottom - heightPx, outer.right, bottom)
            }
            else -> output.set(outer)
        }
    }

    private fun selectedTextView(): TextView? {
        val position = tabLayout.selectedTabPosition
        if (position == cachedTabPosition) {
            cachedTextView?.get()?.let { return it }
        }
        val selectedTab = tabLayout.getTabAt(position) ?: return null
        return findTextView(selectedTab.customView ?: selectedTab.view)?.also {
            cachedTabPosition = position
            cachedTextView = WeakReference(it)
        }
    }

    private fun findTextView(view: View): TextView? {
        if (view is TextView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findTextView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    override fun getIntrinsicWidth(): Int = widthPx ?: delegate.intrinsicWidth

    override fun getIntrinsicHeight(): Int = heightPx

    override fun setAlpha(alpha: Int) {
        delegate.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        delegate.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = delegate.opacity.takeUnless { it == PixelFormat.UNKNOWN }
        ?: PixelFormat.TRANSLUCENT

    override fun isStateful(): Boolean = delegate.isStateful

    override fun onStateChange(state: IntArray): Boolean = delegate.setState(state)

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
