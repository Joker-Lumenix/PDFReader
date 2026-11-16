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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
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
 * again. The order of the entries is remembered between the openings.
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
        @DrawableRes val iconRes: Int,
    )

    private val panelBinding = SettingsQuickEntryBinding.inflate(LayoutInflater.from(context), this, false)
    private val scrim = View(context)

    private val entries = mutableListOf<Entry>()

    /** The actions of the icon header of the design. */
    enum class HeaderAction { ALERT, DELETE, SHARE, EDIT }

    /** Called when an entry is tapped. */
    var onEntryClick: ((Entry) -> Unit)? = null

    /** Called when one of the header icons is tapped. */
    var onHeaderAction: ((HeaderAction) -> Unit)? = null

    /** Called when the popup closes. */
    var onDismiss: (() -> Unit)? = null

    private var isShowing = false

    init {
        scrim.setBackgroundColor(ContextCompat.getColor(context, R.color.scrim_45_deep))
        scrim.setOnClickListener { dismiss() }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panelBinding.root)

        applyPanelSurfaces()
        buildHeader()
        setEntries(defaultEntries())
    }

    /** Replaces the entries of the popup. */
    fun setEntries(items: List<Entry>) {
        val remembered = QuickEntryOrder.load(context, items.map { it.id })
        entries.clear()
        // The remembered entries come first, the new ones keep their own order.
        remembered.forEach { id -> items.firstOrNull { it.id == id }?.let { entries.add(it) } }
        items.forEach { item -> if (entries.none { it.id == item.id }) entries.add(item) }
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
        params.leftMargin = (
            anchorRight - (PANEL_WIDTH_DP + PANEL_INSET_DP) * density()
            ).coerceAtLeast(0f).toInt()
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

    /** The four icon buttons of the header of the design. */
    private fun buildHeader() {
        panelBinding.ivHeaderAlert.setImageResource(R.mipmap.ic_add_circle)
        panelBinding.ivHeaderDelete.setImageResource(R.mipmap.ic_delete_black)
        panelBinding.ivHeaderShare.setImageResource(R.mipmap.ic_share_red)
        panelBinding.ivHeaderEdit.setImageResource(R.mipmap.ic_edit_black)

        panelBinding.ivHeaderAlert.setOnClickListener {
            onHeaderAction?.invoke(HeaderAction.ALERT)
        }
        panelBinding.ivHeaderDelete.setOnClickListener {
            onHeaderAction?.invoke(HeaderAction.DELETE)
        }
        panelBinding.ivHeaderShare.setOnClickListener {
            onHeaderAction?.invoke(HeaderAction.SHARE)
        }
        panelBinding.ivHeaderEdit.setOnClickListener {
            onHeaderAction?.invoke(HeaderAction.EDIT)
        }
    }

    private fun rebuildItems() {
        val container = panelBinding.llItems
        container.removeAllViews()
        entries.forEach { entry ->
            val row = SettingsQuickEntryItemBinding.inflate(LayoutInflater.from(context), container, false)
            row.tvLabel.text = context.getString(entry.labelRes)
            row.ivItemIcon.setImageResource(entry.iconRes)
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
            QuickEntryOrder.save(context, ordered.map { it.id })
        }
    }

    /** The three entries of the design. */
    private fun defaultEntries(): List<Entry> = listOf(
        Entry("scan", R.string.quick_entry_scan, R.mipmap.ic_google_scan),
        Entry("merge", R.string.quick_entry_merge, R.mipmap.ic_merge_pdf),
        Entry("print", R.string.quick_entry_print, R.mipmap.ic_print),
    )

    private fun density(): Float = resources.displayMetrics.density

    private companion object {
        const val PANEL_WIDTH_DP = 156
        const val PANEL_INSET_DP = 8
        const val HEADER_HEIGHT_DP = 36
        const val ITEM_HEIGHT_DP = 36
        const val GAP_DP = 12
    }
}

/**
 * Remembers the order of the quick entries between the openings.
 *
 * The order lives in its own preference file, the shared preferences of the
 * app belong to the data layer.
 */
private object QuickEntryOrder {

    private const val PREFS_NAME = "quick_entry"
    private const val KEY_ORDER = "order"

    /** The ids of the remembered order, unknown ids are dropped. */
    fun load(context: Context, ids: List<String>): List<String> {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: return emptyList()
        return stored.filter { ids.contains(it) }
    }

    fun save(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, ids.joinToString(","))
            .apply()
    }
}
