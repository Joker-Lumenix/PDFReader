package com.peng.pdfreader.core.office.pptx

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.OoxmlNode
import com.peng.pdfreader.core.office.OoxmlPackage

/**
 * A chart of a slide.
 *
 * The charts live in their own part, the slide only names the part and the
 * place of the chart on the slide.
 */
data class PptxChart(
    /**
     * The kind of the chart.
     *
     * It is "bar" for the horizontal bars, "column" for the vertical ones,
     * then "line", "pie", "doughnut", "area", "scatter" and "radar".
     */
    val kind: String,
    /** The title of the chart. */
    val title: String?,
    /** The names of the groups of the chart. */
    val categories: List<String>,
    /** The rows of the chart. */
    val series: List<PptxChartSeries>,
    /** Whether the chart shows a legend. */
    val legend: Boolean,
    /** Whether the chart shows the axis of the values. */
    val showValueAxis: Boolean,
    /** Whether the chart shows the axis of the groups. */
    val showCategoryAxis: Boolean,
    /** The highest value of the chart, when the file names it. */
    val maxValue: Float?,
    /** The lowest value of the chart, when the file names it. */
    val minValue: Float?,
    /** How the rows of the chart stand on each other. */
    val grouping: String,
)

/**
 * One row of a chart.
 */
data class PptxChartSeries(
    /** The name of the row. */
    val name: String,
    /** The values of the row, a missing value is null. */
    val values: List<Float?>,
    /** The colour of the row. */
    val color: OfficeColor?,
)

/**
 * Reads the charts of a presentation.
 *
 * The charts of the office software live in `ppt/charts/chart1.xml`, the
 * slide only names the part of the chart.
 */
object PptxChartParser {

    private const val TAG = "PptxChartParser"

    /** The colours that a chart uses when the file does not name them. */
    private val FALLBACK_COLORS = listOf(
        OfficeColor(0.26f, 0.47f, 0.79f, 1f),
        OfficeColor(0.89f, 0.39f, 0.38f, 1f),
        OfficeColor(0.55f, 0.68f, 0.29f, 1f),
        OfficeColor(0.95f, 0.71f, 0.25f, 1f),
        OfficeColor(0.44f, 0.36f, 0.68f, 1f),
        OfficeColor(0.32f, 0.71f, 0.75f, 1f),
    )

    /**
     * Reads the chart of a part.
     *
     * @return null when the part does not hold a chart that the reader can
     * show
     */
    fun parse(pkg: OoxmlPackage, part: String, theme: Map<String, OfficeColor>): PptxChart? {
        val root = pkg.node(part) ?: return null
        val chart = root.child("chart") ?: return null
        val plot = chart.child("plotArea") ?: return null
        val kind = kindOf(plot) ?: return null
        val series = readSeries(plot, kind, theme)
        if (series.isEmpty()) return null
        return PptxChart(
            kind = kind.first,
            title = titleOf(chart),
            categories = categoriesOf(plot, kind.second),
            series = series,
            legend = chart.child("legend") != null,
            showValueAxis = axisShown(plot, "valAx"),
            showCategoryAxis = axisShown(plot, "catAx"),
            maxValue = axisValue(plot, "valAx", "max"),
            minValue = axisValue(plot, "valAx", "min"),
            grouping = groupingOf(plot),
        )
    }

    /**
     * The kind of the chart and the name of the element that holds the groups.
     *
     * The groups of a chart live in a different element for every kind of
     * chart.
     */
    private fun kindOf(plot: OoxmlNode): Pair<String, String>? {
        val types = listOf(
            "barChart" to ("bar" to "cat"),
            "lineChart" to ("line" to "cat"),
            "pieChart" to ("pie" to "cat"),
            "doughnutChart" to ("doughnut" to "cat"),
            "areaChart" to ("area" to "cat"),
            "radarChart" to ("radar" to "cat"),
            "scatterChart" to ("scatter" to "xVal"),
            "bubbleChart" to ("scatter" to "xVal"),
        )
        types.forEach { (element, kind) ->
            val node = plot.child(element) ?: return@forEach
            // The bars of a bar chart stand next to each other or on top of
            // each other, the direction tells which way they point.
            val direction = node.child("barDir")?.attr("val")
            val name = if (kind.first == "bar") {
                if (direction == "bar") "bar" else "column"
            } else {
                kind.first
            }
            return kind.copy(first = name)
        }
        return null
    }

    /** How the rows of the chart stand on each other. */
    private fun groupingOf(plot: OoxmlNode): String {
        val types = listOf("barChart", "lineChart", "areaChart")
        types.forEach { element ->
            val grouping = plot.child(element)?.child("grouping")?.attr("val")
            if (grouping != null) return grouping
        }
        return "standard"
    }

    /** The title of the chart. */
    private fun titleOf(chart: OoxmlNode): String? {
        val title = chart.child("title") ?: return null
        val text = title.descendants("t").joinToString("") { it.text }.trim()
        return text.ifEmpty { null }
    }

    /** Whether an axis is shown. */
    private fun axisShown(plot: OoxmlNode, name: String): Boolean {
        val axis = plot.child(name) ?: return false
        val deleted = axis.child("delete")?.attrBool("val")
        return deleted != true
    }

    /** The range of the values of the chart. */
    private fun axisValue(plot: OoxmlNode, axis: String, name: String): Float? {
        val scaling = plot.child(axis)?.child("scaling") ?: return null
        return scaling.child(name)?.attrFloat("val")
    }

    /** The names of the groups of the chart. */
    private fun categoriesOf(plot: OoxmlNode, holder: String): List<String> {
        val first = chartHolderOf(plot)?.children("ser")?.firstOrNull() ?: return emptyList()
        val cat = first.child("cat") ?: first.child("xVal") ?: return emptyList()
        val strings = cat.descendants("strCache").firstOrNull()
        if (strings != null) return readPoints(strings) { it.text }.filterNotNull()
        val numbers = cat.descendants("numCache").firstOrNull()
        if (numbers != null) return readPoints(numbers) { it.text }.filterNotNull()
        val literal = cat.child("strLit") ?: cat.child("numLit") ?: return emptyList()
        return readPoints(literal) { it.text }.filterNotNull()
    }

    /** The rows of the chart. */
    private fun readSeries(
        plot: OoxmlNode,
        kind: Pair<String, String>,
        theme: Map<String, OfficeColor>,
    ): List<PptxChartSeries> {
        val holder = chartHolderOf(plot) ?: return emptyList()
        val rows = holder.children("ser")
        val result = ArrayList<PptxChartSeries>()
        rows.forEachIndexed { index, row ->
            val name = row.child("tx")?.let { tx ->
                tx.descendants("strCache").firstOrNull()?.let { cache ->
                    readPoints(cache) { it.text }.firstOrNull()
                } ?: tx.descendants("v").firstOrNull()?.text
            } ?: ""
            val values = readValues(row, kind.second)
            if (values.isEmpty()) return@forEachIndexed
            val color = row.child("spPr")?.child("solidFill")
                ?.let { OfficeColors.colorOf(it, theme) }
                ?: themeColor(theme, index)
            result += PptxChartSeries(
                name = name,
                values = values,
                color = color,
            )
        }
        return result
    }

    /** The element of the plot area that holds the rows of the chart. */
    private fun chartHolderOf(plot: OoxmlNode): OoxmlNode? =
        plot.nodes().firstOrNull { it.localName.endsWith("Chart") }

    /** The values of a row of the chart. */
    private fun readValues(row: OoxmlNode, holder: String): List<Float?> {
        val container = row.child("val") ?: row.child("yVal") ?: return emptyList()
        val cache = container.descendants("numCache").firstOrNull()
            ?: container.descendants("numLit").firstOrNull()
            ?: return emptyList()
        return readPoints(cache) { it.text.trim().toFloatOrNull() }
    }

    /**
     * The points of a cache.
     *
     * A point that the file does not carry is a missing value, the places of
     * the points are kept.
     */
    private fun <T> readPoints(cache: OoxmlNode, value: (OoxmlNode) -> T): List<T?> {
        val points = cache.children("pt")
        if (points.isEmpty()) return emptyList()
        val count = points.mapNotNull { it.attrInt("idx") }.maxOrNull()?.plus(1) ?: points.size
        val result = ArrayList<T?>(count)
        for (index in 0 until count) result += null
        points.forEach { point ->
            val index = point.attrInt("idx") ?: return@forEach
            if (index < 0 || index >= count) return@forEach
            result[index] = value(point.child("v") ?: point)
        }
        return result
    }

    /** The colour of a row, taken from the theme. */
    private fun themeColor(theme: Map<String, OfficeColor>, index: Int): OfficeColor {
        val accents = listOf("accent1", "accent2", "accent3", "accent4", "accent5", "accent6")
        accents.forEach { slot ->
            theme[slot]?.let { return it }
        }
        return FALLBACK_COLORS[index % FALLBACK_COLORS.size]
    }
}
