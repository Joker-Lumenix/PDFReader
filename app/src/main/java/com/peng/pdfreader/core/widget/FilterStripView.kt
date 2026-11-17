package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R

/**
 * 文件类型筛选条。
 *
 * 设计稿中的筛选条由若干个等宽的条目组成：文字在上，指示器（一条圆角横线）在下，
 * 选中项的文字使用品牌色并且显示指示器，其余条目只显示灰色的文字。
 *
 * 该控件自己绘制条目，所以条目的几何属性都可以在布局中配置，也可以通过代码设置：
 *
 * ```xml
 * <com.peng.pdfreader.core.widget.FilterStripView
 *     android:id="@+id/filterStrip"
 *     android:layout_width="match_parent"
 *     android:layout_height="44dp"
 *     app:filterSelectedColor="@color/brand_primary"
 *     app:filterUnselectedColor="@color/text_disabled"
 *     app:filterLabelTextSize="14sp"
 *     app:filterIndicatorWidth="44dp"
 *     app:filterIndicatorHeight="2dp"
 *     app:filterIndicatorRadius="1dp"
 *     app:filterIndicatorGap="12dp"
 *     app:filterItemHeight="44dp" />
 * ```
 *
 * 条目的文字通过 [items] 传入，点击之后由 [onItemSelected] 回调通知调用方，
 * 选中的条目由 [selectedId] 决定。
 */
class FilterStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 筛选条上的一个条目。 */
    data class Item(
        /** 条目的标识，点击之后原样回调给 [onItemSelected]。 */
        val id: String,
        /** 条目的文字。 */
        val label: String,
    )

    /** 选中时文本的颜色。 */
    var selectedColor: Int = Color.RED
        set(value) {
            field = value
            refreshLabels()
        }

    /** 未选中时文本的颜色。 */
    var unselectedColor: Int = Color.GRAY
        set(value) {
            field = value
            refreshLabels()
        }

    /** 指示器的颜色。 */
    var indicatorColor: Int = Color.RED
        set(value) {
            field = value
            refreshIndicators()
        }

    /** 文本的字号，单位sp。 */
    var labelTextSizeSp: Float = 14f
        set(value) {
            field = value
            refreshLabels()
        }

    /** 指示器的宽度，单位dp。 */
    var indicatorWidthDp: Float = 44f
        set(value) {
            field = value
            refreshIndicators()
        }

    /** 指示器的高度，单位dp。 */
    var indicatorHeightDp: Float = 2f
        set(value) {
            field = value
            refreshIndicators()
        }

    /** 指示器的圆角，单位dp。 */
    var indicatorRadiusDp: Float = 1f
        set(value) {
            field = value
            refreshIndicators()
        }

    /** 指示器与文本之间的距离，单位dp。 */
    var indicatorGapDp: Float = 12f
        set(value) {
            field = value
            refreshIndicators()
        }

    /** 每一个条目的宽度，单位dp，0表示按权重平分。 */
    var itemWidthDp: Float = 0f
        set(value) {
            field = value
            rebuild()
        }

    /** 每一个条目的高度，单位dp。 */
    var itemHeightDp: Float = 44f
        set(value) {
            field = value
            rebuild()
        }

    /** 筛选条上的条目。 */
    var items: List<Item> = emptyList()
        set(value) {
            field = value
            rebuild()
        }

    /** 当前选中的条目。 */
    var selectedId: String = ""
        set(value) {
            field = value
            refreshSelection()
        }

    /** 点击条目之后回调，参数是条目的标识。 */
    var onItemSelected: ((String) -> Unit)? = null

    /**
     * 读取布局中的属性。
     *
     * 属性的默认值来自设计稿：选中 #FF3B5C，未选中 #C4C4C4，文字 14sp，
     * 指示器 44x2dp、圆角 1dp、与文字的距离 12dp，条目 44dp 高。
     */
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val typed = context.obtainStyledAttributes(attrs, R.styleable.FilterStripView, defStyleAttr, 0)
        try {
            selectedColor = typed.getColor(
                R.styleable.FilterStripView_filterSelectedColor,
                ContextCompat.getColor(context, R.color.brand_primary),
            )
            unselectedColor = typed.getColor(
                R.styleable.FilterStripView_filterUnselectedColor,
                ContextCompat.getColor(context, R.color.text_disabled),
            )
            indicatorColor = typed.getColor(
                R.styleable.FilterStripView_filterIndicatorColor,
                ContextCompat.getColor(context, R.color.brand_primary),
            )
            labelTextSizeSp = typed.getDimension(
                R.styleable.FilterStripView_filterLabelTextSize,
                sp(14f),
            )
            indicatorWidthDp = dp(
                typed.getDimension(R.styleable.FilterStripView_filterIndicatorWidth, dp(44f)),
            )
            indicatorHeightDp = dp(
                typed.getDimension(R.styleable.FilterStripView_filterIndicatorHeight, dp(2f)),
            )
            indicatorRadiusDp = dp(
                typed.getDimension(R.styleable.FilterStripView_filterIndicatorRadius, dp(1f)),
            )
            indicatorGapDp = dp(
                typed.getDimension(R.styleable.FilterStripView_filterIndicatorGap, dp(12f)),
            )
            itemWidthDp = dp(
                typed.getDimension(R.styleable.FilterStripView_filterItemWidth, 0f),
            )
            itemHeightDp = dp(
                typed.getDimension(R.styleable.FilterStripView_filterItemHeight, dp(44f)),
            )
        } finally {
            typed.recycle()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onItemSelected = null
    }

    /** 按照当前的条目重新构建视图。 */
    private fun rebuild() {
        removeAllViews()
        items.forEach { item ->
            addView(createItemView(item))
        }
        refreshSelection()
    }

    private fun createItemView(item: Item): View {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val params = LayoutParams(
                if (itemWidthDp > 0f) itemWidthDp.toInt() else 0,
                itemHeightDp.toInt(),
            )
            if (itemWidthDp <= 0f) params.weight = 1f
            layoutParams = params
            isClickable = true
            isFocusable = true
            setOnClickListener { onItemSelected?.invoke(item.id) }
        }

        val label = TextView(context).apply {
            text = item.label
            setTextColor(unselectedColor)
            textSize = labelTextSizeSp
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
        column.addView(
            label,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )

        val indicator = View(context).apply {
            background = indicatorDrawable()
        }
        val indicatorParams = LayoutParams(
            indicatorWidthDp.toInt(),
            indicatorHeightDp.toInt(),
        )
        indicatorParams.topMargin = indicatorGapDp.toInt()
        column.addView(indicator, indicatorParams)
        return column
    }

    /** 刷新选中状态：文字颜色与指示器的可见性。 */
    private fun refreshSelection() {
        for (index in 0 until childCount) {
            val column = getChildAt(index) as? LinearLayout ?: continue
            val label = column.getChildAt(0) as? TextView ?: continue
            val indicator = column.getChildAt(1) ?: continue
            val selected = index < items.size && items[index].id == selectedId
            label.setTextColor(if (selected) selectedColor else unselectedColor)
            indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun refreshLabels() {
        for (index in 0 until childCount) {
            val column = getChildAt(index) as? LinearLayout ?: continue
            val label = column.getChildAt(0) as? TextView ?: continue
            label.textSize = labelTextSizeSp
        }
        refreshSelection()
    }

    private fun refreshIndicators() {
        for (index in 0 until childCount) {
            val column = getChildAt(index) as? LinearLayout ?: continue
            val indicator = column.getChildAt(1) ?: continue
            indicator.background = indicatorDrawable()
            val params = indicator.layoutParams as? LayoutParams ?: continue
            params.width = indicatorWidthDp.toInt()
            params.height = indicatorHeightDp.toInt()
            params.topMargin = indicatorGapDp.toInt()
            indicator.layoutParams = params
        }
    }

    private fun indicatorDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = indicatorRadiusDp
        setColor(indicatorColor)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
