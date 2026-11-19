package com.peng.pdfreader.ui.adapter.decoration

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Places the cells of a grid so that they fill the whole width of the screen.
 *
 * The grids of the design put three tiles side by side, the gap between two
 * tiles is 12 and the outer gap is 12 as well. The tiles share the width of
 * the screen, so their width changes with the screen - a fixed cell width
 * would leave a gap on the right of a wide screen.
 *
 * @param gapPx the gap between two tiles and the gap at the border
 */
class UniformGridDecoration(
    private val gapPx: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val spanCount = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: 1
        val width = parent.width
        if (width <= 0) return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val slot = width / spanCount
        val cell = (width - gapPx * (spanCount + 1)) / spanCount
        val column = position % spanCount
        // The tile of a column starts at gap + column * (cell + gap).
        outRect.left = gapPx + column * (cell + gapPx) - column * slot
        outRect.right = slot - cell - outRect.left
        outRect.bottom = gapPx
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
