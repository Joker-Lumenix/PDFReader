package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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
 * Layout follows the design: 87dp tall, #fafafa surface, 1px hairline at the
 * bottom. Root pages render the title at x=20, sub pages (with a back arrow)
 * at x=52.
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

        context.obtainStyledAttributes(attrs, R.styleable.PageTitleBar).use { typed ->
            title = typed.getString(R.styleable.PageTitleBar_title).orEmpty()
            subtitle = typed.getString(R.styleable.PageTitleBar_subtitle)
            showBack = typed.getBoolean(R.styleable.PageTitleBar_showBack, false)
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
}
