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

    /** Start colour of the brand gradient. */
    const val BRAND_LIGHT = 0xFFFA4F78.toInt()

    /** End colour of the brand gradient. */
    const val BRAND_PRIMARY = 0xFFDA232F.toInt()

    /** Colour behind a picture that has no own background. */
    const val BACKDROP = 0xFFF2F2F2.toInt()

    /** Colour of the camera tile of the design. */
    const val CAMERA_TILE = 0xFFEC7878.toInt()

    /** Colour of the handle of the progress track of the design. */
    const val PROGRESS_KNOB = 0xFFFEC43C.toInt()

    /** Colour of the surface of the bottom sheets of the design. */
    const val SURFACE_SHEET = 0xFFFFFDFD.toInt()

    /** Colour of the input field of the rename sheet of the design. */
    const val INPUT_FIELD = 0xFFF4F4F4.toInt()

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

    /**
     * The mark that removes a picture of the strip.
     *
     * The design draws it as a dark circle with a cross in it.
     */
    fun closeMark(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x99000000.toInt())
    }

    /**
     * The handle that rides on the progress track of the design.
     *
     * The design paints it as a yellow disc (#fec43c), so the handle is a
     * filled circle instead of a bitmap.
     */
    fun progressKnob(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(PROGRESS_KNOB)
    }

    /** Converts dp into pixels. */
    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
