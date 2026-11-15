package com.peng.pdfreader.feature.tools.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/**
 * The tool screens need a couple of surfaces that only exist in the design
 * (the yellow tool panel, the blue info banner, the round page badges).
 *
 * They are built in code on purpose: the tool screens own their drawing, so
 * no shared drawable resource has to grow for a single screen.
 */
object ToolShapes {

    /** Colour of the yellow panel behind the tool grid. */
    const val PANEL_YELLOW = 0xFFFFE36E.toInt()

    /** Colour of the info banner of the split screen. */
    const val BANNER_BLUE = 0xFF6D99F9.toInt()

    /** Colour of a selected page / image badge. */
    const val BADGE_SELECTED = 0xFF28BF1D.toInt()

    /** Colour of an unselected page / image badge. */
    const val BADGE_UNSELECTED = 0x99151515.toInt()

    /** Colour of the page count badge of the result card. */
    const val BADGE_PAGE_COUNT = 0x73000000.toInt()

    /** Colour of the "before" chip of the compress result. */
    const val SIZE_BEFORE = 0xFFF59633.toInt()

    /** Colour of the "after" chip of the compress result. */
    const val SIZE_AFTER = 0xFF6D99F9.toInt()

    /** A rounded rectangle of the given colour. */
    fun roundRect(context: Context, @ColorInt color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radiusDp)
        }

    /** A rounded rectangle whose bottom corners are rounded. */
    fun bottomRounded(context: Context, @ColorInt color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            val radius = dp(context, radiusDp)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }

    /** A rounded rectangle whose top corners are rounded. */
    fun topRounded(context: Context, @ColorInt color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            val radius = dp(context, radiusDp)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }

    /**
     * A copy of a gradient button drawable with a different corner radius.
     *
     * The shared `gradient_red` is a constant drawable, so it has to be
     * mutated before its radius is changed.
     */
    fun gradientPill(context: Context, @DrawableRes resId: Int, radiusDp: Float): GradientDrawable {
        val source = ContextCompat.getDrawable(context, resId) as? GradientDrawable
            ?: GradientDrawable()
        return (source.mutate() as GradientDrawable).apply {
            cornerRadius = dp(context, radiusDp)
        }
    }

    /** The badge of a page or an image tile. */
    fun badge(context: Context, selected: Boolean): GradientDrawable = roundRect(
        context,
        if (selected) BADGE_SELECTED else BADGE_UNSELECTED,
        4f,
    )

    /** Translucent black background of the page count badge. */
    fun pageCountBadge(context: Context): GradientDrawable =
        roundRect(context, BADGE_PAGE_COUNT, 4f)

    /** The white circle of the info banner. */
    fun infoDot(context: Context): GradientDrawable = roundRect(context, Color.WHITE, 7f)

    /** Converts dp into pixels. */
    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
