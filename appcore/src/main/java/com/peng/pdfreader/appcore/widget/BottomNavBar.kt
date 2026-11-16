package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.peng.pdfreader.appcore.R
import com.peng.pdfreader.appcore.util.SystemBarInsets

/**
 * Bottom navigation bar.
 *
 * Four tabs, 80dp tall, #fafafa surface with a hairline on top. The host page
 * supplies the icons so the library stays free of feature specific artwork.
 */
class BottomNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tabsHolder: LinearLayout

    private val items = mutableListOf<BottomNavItem>()
    private var selectedId: Int = 0
    private var onSelected: (Int) -> Unit = {}

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_bottom_nav, this, true)
        tabsHolder = findViewById(R.id.llTabs)
        applySystemBarInset()
    }

    /**
     * Keeps the bar above the system navigation.
     *
     * The app draws edge to edge, so the window reaches behind the navigation
     * bar; the bar takes its inset as padding and grows by it.
     */
    private fun applySystemBarInset() {
        setOnApplyWindowInsetsListener { view, insets ->
            val bottom = SystemBarInsets.bottom(insets)
            view.setPadding(0, 0, 0, bottom)
            insets
        }
        requestApplyInsets()
    }

    /**
     * Installs the tabs.
     *
     * @param items tab descriptors
     * @param selectedId id of the tab that starts out selected
     * @param onSelected invoked with the id of the newly selected tab
     */
    fun setItems(
        items: List<BottomNavItem>,
        selectedId: Int,
        onSelected: (Int) -> Unit,
    ) {
        this.items.clear()
        this.items.addAll(items)
        this.selectedId = selectedId
        this.onSelected = onSelected
        rebuild()
    }

    /** Switches the selected tab without notifying the listener. */
    fun selectTab(id: Int) {
        if (selectedId == id) return
        selectedId = id
        rebuild()
    }

    /** Currently selected tab id. */
    val currentTabId: Int get() = selectedId

    private fun rebuild() {
        tabsHolder.removeAllViews()
        items.forEach { item ->
            val tab = LayoutInflater.from(context)
                .inflate(R.layout.widget_bottom_nav_item, tabsHolder, false)
            val icon = tab.findViewById<ImageView>(R.id.ivIcon)
            val label = tab.findViewById<TextView>(R.id.tvLabel)
            val selected = item.id == selectedId
            icon.setImageDrawable(if (selected) item.iconSelected else item.iconUnselected)
            label.text = item.label
            label.setTextColor(
                context.getColor(
                    if (selected) R.color.text_error else R.color.text_accent_neutral,
                ),
            )
            tab.setOnClickListener {
                if (selectedId != item.id) {
                    selectTab(item.id)
                    onSelected(item.id)
                }
            }
            tabsHolder.addView(tab)
        }
    }
}

/**
 * A single bottom navigation tab.
 *
 * @param id stable identifier used for selection
 * @param iconSelected artwork of the selected state
 * @param iconUnselected artwork of the unselected state
 * @param label caption below the icon
 */
data class BottomNavItem(
    val id: Int,
    val iconSelected: Drawable?,
    val iconUnselected: Drawable?,
    val label: String,
)
