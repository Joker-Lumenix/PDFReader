package com.peng.pdfreader.appcore.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * The gap between the rows of a document list.
 *
 * The row itself is a single template ([FileListItemView]); the spacing
 * between the rows belongs to the list, not to the row, so it lives here
 * instead of being baked into a per feature wrapper layout.
 */
class FileListItemDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.bottom = gapPx
    }
}
