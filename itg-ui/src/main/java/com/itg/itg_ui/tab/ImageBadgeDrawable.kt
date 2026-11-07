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

/** 在图片上绘制可选的数字或圆点，供 Tab 的 ViewOverlay 使用。 */
internal class ImageBadgeDrawable(
    private val image: Drawable,
    private val badge: TabImageBadge,
    context: Context,
) : Drawable(), Drawable.Callback {
    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity
    private val bubble = RectF()
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private var drawableAlpha = 255

    init {
        image.callback = this
    }

    override fun onBoundsChange(bounds: Rect) {
        image.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        image.draw(canvas)
        val bounds = bounds
        if (bounds.isEmpty) return

        bubblePaint.color = badge.backgroundColor ?: Color.RED
        bubblePaint.alpha = drawableAlpha
        if (badge.showAsDot) {
            val radius = minOf(4f * density, bounds.width() * 0.2f)
            canvas.drawCircle(bounds.right - radius, bounds.top + radius, radius, bubblePaint)
        } else if (badge.count > 0) {
            val label = if (badge.count > badge.maxNumber) "${badge.maxNumber}+" else badge.count.toString()
            val height = minOf(14f * density, bounds.height() * 0.6f)
            textPaint.textSize = minOf(10f * scaledDensity, height * 0.72f)
            val width = (textPaint.measureText(label) + 5f * density)
                .coerceAtLeast(height)
                .coerceAtMost(bounds.width().toFloat())
            bubble.set(bounds.right - width, bounds.bottom - height, bounds.right.toFloat(), bounds.bottom.toFloat())
            canvas.drawRoundRect(bubble, height / 2f, height / 2f, bubblePaint)
            textPaint.alpha = drawableAlpha
            val textY = bubble.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, bubble.centerX(), textY, textPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        image.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        image.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun isStateful(): Boolean = image.isStateful

    override fun onStateChange(state: IntArray): Boolean = image.setState(state)

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
