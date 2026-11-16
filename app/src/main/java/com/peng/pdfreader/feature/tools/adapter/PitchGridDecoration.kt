package com.peng.pdfreader.feature.tools.adapter

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Places the cells of a grid on a fixed pitch.
 *
 * The grids of the design are not centred inside their container: the split
 * pages and the image tiles sit at x = 12 / 128 / 244, so the gap between two
 * cells is 12 and the outer gap is 12 as well. A plain "centre the slack"
 * decoration would put the third column at 252 instead of 244.
 */
class PitchGridDecoration(
    private val cellWidthPx: Int,
    private val pitchPx: Int,
    private val startMarginPx: Int,
    private val rowSpacingPx: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val spanCount = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: 1
        val slot = if (parent.width > 0) parent.width / spanCount else cellWidthPx
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % spanCount
        outRect.left = startMarginPx + column * (pitchPx - slot)
        outRect.right = slot - cellWidthPx - outRect.left
        outRect.bottom = rowSpacingPx
    }
}

/**
 * A plain gap around and below the cells of a list.
 */
class EdgeGapDecoration(
    private val sidePx: Int,
    private val bottomPx: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.left = sidePx
        outRect.right = sidePx
        outRect.bottom = bottomPx
    }
}
