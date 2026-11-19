package com.peng.pdfreader.core.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.peng.pdfreader.R
import kotlin.math.roundToInt

/**
 * A frame whose height follows its width.
 *
 * The grids of the design put three tiles side by side and fill the whole
 * width of the screen. The width of a tile therefore depends on the screen,
 * the height of the design has to follow it - a fixed height would stretch
 * the tile on a wide screen and squash it on a narrow one.
 */
class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The height of the view for a given width. */
    var ratio: Float = DEFAULT_RATIO

    init {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.RatioFrameLayout)
        ratio = typed.getFloat(R.styleable.RatioFrameLayout_ratio, DEFAULT_RATIO)
        typed.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * ratio).roundToInt().coerceAtLeast(0)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    private companion object {
        /** A square is the default, the design uses it for the image tiles. */
        const val DEFAULT_RATIO = 1f
    }
}
