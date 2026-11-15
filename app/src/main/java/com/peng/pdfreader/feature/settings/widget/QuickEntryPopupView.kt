package com.peng.pdfreader.feature.settings.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.databinding.SettingsQuickEntryBinding
import com.peng.pdfreader.databinding.SettingsQuickEntryItemBinding

/**
 * The quick entry popup of the design: a small panel with an icon header and a
 * list of entries that can be reordered by dragging their handle.
 *
 * The view is self contained: [show] puts it into the root view of the anchor
 * and positions the panel right above the anchor, [dismiss] takes it away
 * again.
 */
class QuickEntryPopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** One entry of the popup. */
    data class Entry(
        val id: String,
        @StringRes val labelRes: Int,
    )

    private val panelBinding = SettingsQuickEntryBinding.inflate(LayoutInflater.from(context), this, false)
    private val scrim = View(context)

    private val entries = mutableListOf<Entry>()

    /** Called when an entry is tapped. */
    var onEntryClick: ((Entry) -> Unit)? = null

    /** Called when the popup closes. */
    var onDismiss: (() -> Unit)? = null

    private var isShowing = false

    init {
        scrim.setBackgroundColor(ContextCompat.getColor(context, R.color.scrim_45))
        scrim.setOnClickListener { dismiss() }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panelBinding.root)

        applyPanelSurfaces()
        buildHeader()
    }

    /** Replaces the entries of the popup. */
    fun setEntries(items: List<Entry>) {
        entries.clear()
        entries.addAll(items)
        rebuildItems()
    }

    /** Shows the panel above the given anchor view. */
    fun show(anchor: View) {
        if (isShowing) return
        val root = anchor.rootView as? ViewGroup ?: return
        isShowing = true
        root.addView(this)
        root.post { positionAbove(anchor, root) }
    }

    /** Removes the popup from the screen. */
    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        (parent as? ViewGroup)?.removeView(this)
        onDismiss?.invoke()
    }

    private fun positionAbove(anchor: View, root: ViewGroup) {
        val anchorLocation = IntArray(2)
        val rootLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        root.getLocationInWindow(rootLocation)

        val anchorTop = anchorLocation[1] - rootLocation[1]
        val anchorRight = anchorLocation[0] - rootLocation[0] + anchor.width

        val panel = panelBinding.root
        val params = panel.layoutParams as LayoutParams
        params.gravity = Gravity.TOP or Gravity.START
        params.topMargin = (anchorTop - panelHeight() - GAP_DP * density()).coerceAtLeast(0f).toInt()
        params.leftMargin = (anchorRight - PANEL_WIDTH_DP * density()).coerceAtLeast(0f).toInt()
        panel.layoutParams = params
    }

    private fun panelHeight(): Int = ((HEADER_HEIGHT_DP + entries.size * ITEM_HEIGHT_DP) * density()).toInt()

    private fun applyPanelSurfaces() {
        val density = density()
        panelBinding.llHeader.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(8f * density, 8f * density, 8f * density, 8f * density, 0f, 0f, 0f, 0f)
            setColor(ContextCompat.getColor(context, R.color.surface_neutral_soft))
        }
        panelBinding.llItems.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 8f * density, 8f * density, 8f * density, 8f * density)
            setColor(Color.WHITE)
        }
    }

    private fun buildHeader() {
        val header = panelBinding.llHeader
        header.removeAllViews()
        HEADER_ICONS.forEach { (iconRes, descriptionRes) ->
            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                contentDescription = context.getString(descriptionRes)
                setOnClickListener {
                    if (iconRes == R.mipmap.ic_close) dismiss()
                }
            }
            header.addView(
                icon,
                LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    marginStart = dp(10)
                },
            )
        }
    }

    private fun rebuildItems() {
        val container = panelBinding.llItems
        container.removeAllViews()
        entries.forEach { entry ->
            val row = SettingsQuickEntryItemBinding.inflate(LayoutInflater.from(context), container, false)
            row.tvLabel.text = context.getString(entry.labelRes)
            row.root.setOnClickListener { onEntryClick?.invoke(entry) }
            container.addView(row.root)
            attachDragBehaviour(row.root)
        }
    }

    /** Dragging the handle moves the row to another position of the list. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragBehaviour(row: View) {
        val handle = row.findViewById<View>(R.id.ivDragHandle)
        var downRawY = 0f

        handle.setOnTouchListener { _, event ->
            val container = panelBinding.llItems
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    row.isPressed = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - downRawY
                    row.translationY = delta
                    val target = insertionIndex(container, row, delta)
                    if (target != container.indexOfChild(row)) {
                        moveRow(container, row, target)
                        row.translationY = 0f
                        downRawY = event.rawY
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    row.translationY = 0f
                    row.isPressed = false
                    syncModelOrder(container)
                    true
                }

                else -> false
            }
        }
    }

    /** The index the dragged row would take if it was dropped now. */
    private fun insertionIndex(container: ViewGroup, row: View, delta: Float): Int {
        val center = row.y + delta + row.height / 2f
        var index = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === row) continue
            if (center > child.y + child.height / 2f) index++
        }
        return index
    }

    private fun moveRow(container: ViewGroup, row: View, index: Int) {
        container.removeView(row)
        container.addView(row, index)
    }

    /** Keeps the data model in sync with the order of the views. */
    private fun syncModelOrder(container: ViewGroup) {
        if (entries.size != container.childCount) return
        val ordered = mutableListOf<Entry>()
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val label = row.findViewById<TextView>(R.id.tvLabel)?.text
            val entry = entries.firstOrNull { context.getString(it.labelRes) == label }
            if (entry != null) ordered.add(entry)
        }
        if (ordered.size == entries.size) {
            entries.clear()
            entries.addAll(ordered)
        }
    }

    private fun dp(value: Int): Int = (value * density()).toInt()

    private fun density(): Float = resources.displayMetrics.density

    private companion object {
        const val PANEL_WIDTH_DP = 156
        const val HEADER_HEIGHT_DP = 36
        const val ITEM_HEIGHT_DP = 36
        const val GAP_DP = 8

        val HEADER_ICONS = listOf(
            R.mipmap.ic_share_red to R.string.share,
            R.mipmap.ic_feedback to R.string.feedback,
            R.mipmap.ic_more to R.string.settings_action_more,
            R.mipmap.ic_close to R.string.settings_close,
        )
    }
}
