package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.peng.pdfreader.appcore.R

/**
 * Page title bar.
 *
 * Pure presentation: it renders a title, an optional subtitle, an optional back
 * arrow and an optional action slot. Every behaviour is driven by the host page,
 * the bar never decides what a click means.
 *
 * Layout follows the design: 63dp below the status bar, #fafafa surface, 1px
 * hairline at the bottom. Root pages render the title at x=20, sub pages (with
 * a back arrow) at x=52.
 */
class PageTitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val subtitleView: TextView
    private val backView: ImageView
    private val actionsSlot: FrameLayout

    /** Invoked when the back arrow is tapped. */
    var onBackClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_page_title, this, true)

        titleView = findViewById(R.id.tvTitle)
        subtitleView = findViewById(R.id.tvSubtitle)
        backView = findViewById(R.id.ivBack)
        actionsSlot = findViewById(R.id.flActions)

        backView.setOnClickListener { onBackClick?.invoke() }

        readAttributes(attrs)
        applySystemBarInset()
    }

    /**
     * Keeps the bar below the status bar.
     *
     * The app draws edge to edge, so the window starts behind the status bar.
     * The design does the same: its app bar is 87px tall, 24px of which are
     * the status bar, and the title sits centred in the 63px underneath it.
     * The bar therefore takes the status bar inset as padding and grows by it.
     */
    private fun applySystemBarInset() {
        setOnApplyWindowInsetsListener { view, insets ->
            val top = insets.getInsets(WindowInsets.Type.statusBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }
        requestApplyInsets()
    }

    /**
     * Reads the XML attributes.
     *
     * TypedArray is only AutoCloseable from API 31 on, so it is recycled by hand.
     */
    @Suppress("DEPRECATION")
    private fun readAttributes(attrs: AttributeSet?) {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.PageTitleBar)
        try {
            title = typed.getString(R.styleable.PageTitleBar_title).orEmpty()
            subtitle = typed.getString(R.styleable.PageTitleBar_subtitle)
            showBack = typed.getBoolean(R.styleable.PageTitleBar_showBack, false)
        } finally {
            typed.recycle()
        }
    }

    var title: CharSequence
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    var subtitle: CharSequence?
        get() = subtitleView.text
        set(value) {
            subtitleView.text = value
            subtitleView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

    var showBack: Boolean
        get() = backView.visibility == VISIBLE
        set(value) {
            backView.visibility = if (value) VISIBLE else GONE
        }

    /** Adds a view into the right hand action slot. */
    fun addAction(view: View) {
        actionsSlot.addView(view)
    }

    /**
     * Overrides the typography of the title.
     *
     * The document preview shows the name of the file with 15sp / medium (the
     * title of the design of the viewer), every other page keeps the 20sp /
     * bold page title of the design system.
     */
    fun setTitleTypography(sizeSp: Float, mediumWeight: Boolean) {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        titleView.setTypeface(
            if (mediumWeight) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            },
        )
    }
}
