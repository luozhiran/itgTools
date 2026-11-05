package com.itg.itg_ui.tab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.google.android.material.color.MaterialColors

/** Draws an image with an optional count or dot badge inside its top-end corner. */
internal class ImageBadgeDrawable(
    private val image: Drawable,
    badge: TabImageBadge,
    context: Context,
) : Drawable(), Drawable.Callback {

    private val density = context.resources.displayMetrics.density
    private val showDot = badge.showAsDot
    private val label = when {
        showDot || badge.count <= 0 -> null
        badge.count > badge.maxNumber -> "${badge.maxNumber}+"
        else -> badge.count.toString()
    }
    private val backgroundColor = badge.backgroundColor ?: MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorError,
        DEFAULT_BADGE_COLOR,
    )
    private val textColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnError,
        Color.WHITE,
    )
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val badgeBounds = RectF()
    init {
        image.callback = this
    }

    override fun draw(canvas: Canvas) {
        image.draw(canvas)
        when {
            showDot -> drawDot(canvas)
            label != null -> drawLabel(canvas, label)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        image.bounds = bounds
    }

    private fun drawDot(canvas: Canvas) {
        val radius = minOf(dp(DOT_RADIUS_DP), bounds.width() * 0.16f, bounds.height() * 0.16f)
        if (radius <= 0f) return
        canvas.drawCircle(bounds.right - radius, bounds.top + radius, radius, backgroundPaint)
    }

    private fun drawLabel(canvas: Canvas, text: String) {
        val badgeHeight = minOf(dp(MAX_LABEL_HEIGHT_DP), bounds.height() * 0.5f)
        if (badgeHeight <= 0f) return

        textPaint.textSize = badgeHeight * 0.68f
        val horizontalPadding = dp(LABEL_HORIZONTAL_PADDING_DP)
        val availableTextWidth = (bounds.width() - horizontalPadding * 2f).coerceAtLeast(1f)
        val measuredTextWidth = textPaint.measureText(text)
        if (measuredTextWidth > availableTextWidth) {
            textPaint.textSize *= availableTextWidth / measuredTextWidth
        }
        val badgeWidth = maxOf(badgeHeight, textPaint.measureText(text) + horizontalPadding * 2f)
            .coerceAtMost(bounds.width().toFloat())
        badgeBounds.set(
            bounds.right - badgeWidth,
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.top + badgeHeight,
        )
        val radius = badgeHeight / 2f
        canvas.drawRoundRect(badgeBounds, radius, radius, backgroundPaint)

        val metrics = textPaint.fontMetrics
        val baseline = badgeBounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, badgeBounds.centerX(), baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        val resolvedAlpha = alpha.coerceIn(0, 255)
        image.alpha = resolvedAlpha
        backgroundPaint.alpha = Color.alpha(backgroundColor) * resolvedAlpha / 255
        textPaint.alpha = Color.alpha(textColor) * resolvedAlpha / 255
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        image.colorFilter = colorFilter
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun isStateful(): Boolean = image.isStateful

    override fun onStateChange(state: IntArray): Boolean = image.setState(state)

    override fun getIntrinsicWidth(): Int = image.intrinsicWidth

    override fun getIntrinsicHeight(): Int = image.intrinsicHeight

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val DOT_RADIUS_DP = 3f
        const val MAX_LABEL_HEIGHT_DP = 12f
        const val LABEL_HORIZONTAL_PADDING_DP = 2f
        const val DEFAULT_BADGE_COLOR = 0xFFB3261E.toInt()
    }
}
