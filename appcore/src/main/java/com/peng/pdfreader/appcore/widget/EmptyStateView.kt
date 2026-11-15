package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.peng.pdfreader.appcore.R

/**
 * Empty state block: illustration, title, hint and an optional action button.
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val illustration: ImageView
    private val titleView: TextView
    private val hintView: TextView
    private val actionView: TextView

    /** Raised when the action button is tapped. */
    var onActionClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_empty_state, this, true)

        illustration = findViewById(R.id.ivIllustration)
        titleView = findViewById(R.id.tvTitle)
        hintView = findViewById(R.id.tvHint)
        actionView = findViewById(R.id.tvAction)

        actionView.setOnClickListener { onActionClick?.invoke() }
    }

    var title: CharSequence
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    var hint: CharSequence?
        get() = hintView.text
        set(value) {
            hintView.text = value
            hintView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

    var actionText: CharSequence?
        get() = actionView.text
        set(value) {
            actionView.text = value
            actionView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

    var illustrationDrawable: Drawable?
        get() = illustration.drawable
        set(value) {
            illustration.setImageDrawable(value)
            illustration.visibility = if (value == null) GONE else VISIBLE
        }
}
