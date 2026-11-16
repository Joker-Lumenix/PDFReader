package com.peng.pdfreader.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.peng.pdfreader.R

/**
 * The three actions of the app bar of the document viewer.
 *
 * The design puts the rename tile at x=174, the share at x=276 and the
 * overflow at x=316; the gaps between them come from the margins of the row.
 * The three views live in one row because the action slot of the title bar
 * stacks its children on top of each other.
 */
object PreviewTitleActions {

    /** The gap between the rename tile and the share icon. */
    private const val RENAME_GAP = 78

    /** The gap between the share icon and the overflow. */
    private const val SHARE_GAP = 16

    /**
     * Builds the row of the app bar.
     *
     * @param onRename raised by the rename tile
     * @param onShare raised by the share icon
     * @param onMore raised by the overflow icon
     */
    fun build(
        context: Context,
        onRename: () -> Unit,
        onShare: () -> Unit,
        onMore: () -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(renameTile(context, onRename), params(context, RENAME_GAP))
        row.addView(shareIcon(context, onShare), params(context, SHARE_GAP))
        row.addView(moreIcon(context, onMore), params(context, 0))
        return row
    }

    /** The rename tile: a light red square with the edit glyph of the design. */
    private fun renameTile(context: Context, onClick: () -> Unit): View {
        val tile = FrameLayout(context)
        tile.background = GradientDrawable().apply {
            setColor(context.getColor(R.color.brand_primary_container))
            cornerRadius = df(context, 6.4f)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.mipmap.ic_edit_black)
            contentDescription = context.getString(R.string.preview_cd_rename)
        }
        tile.addView(
            icon,
            FrameLayout.LayoutParams(dp(context, 12), dp(context, 14)).apply {
                gravity = Gravity.CENTER
            },
        )
        tile.contentDescription = context.getString(R.string.preview_cd_rename)
        tile.setOnClickListener { onClick() }
        return tile
    }

    private fun shareIcon(context: Context, onClick: () -> Unit): View = ImageView(context).apply {
        setImageResource(R.mipmap.ic_share_square_black)
        contentDescription = context.getString(R.string.preview_cd_share)
        setOnClickListener { onClick() }
    }

    private fun moreIcon(context: Context, onClick: () -> Unit): View = ImageView(context).apply {
        setImageResource(R.mipmap.ic_more)
        contentDescription = context.getString(R.string.preview_cd_more)
        setOnClickListener { onClick() }
    }

    private fun params(context: Context, marginEnd: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)).apply {
            this.marginEnd = dp(context, marginEnd)
        }

    /** The size in whole pixels. */
    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** The size in fractional pixels. */
    private fun df(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
