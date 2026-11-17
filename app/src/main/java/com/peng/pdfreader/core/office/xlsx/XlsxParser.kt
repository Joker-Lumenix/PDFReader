package com.peng.pdfreader.core.office.xlsx

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.OoxmlNode
import com.peng.pdfreader.core.office.OoxmlPackage
import com.peng.pdfreader.core.office.OfficeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeSet

/** A parsed workbook: the sheets in file order plus the active sheet index. */
data class XlsxWorkbook(val sheets: List<XlsxSheet>, val activeSheetIndex: Int)

/** A single worksheet with its rows, column metrics and view settings. */
data class XlsxSheet(
    val name: String,
    val rows: List<XlsxRow>,
    val columnWidthsDp: List<Float>,
    val defaultColumnWidthDp: Float,
    val frozenRows: Int,
    val frozenColumns: Int,
    val tabColor: OfficeColor?,
)

/** A worksheet row: [index] is the 1 based Excel row number. */
data class XlsxRow(val index: Int, val heightDp: Float, val cells: List<XlsxCell>)

/** A single cell: [column] is 0 based, [row] is 1 based. */
data class XlsxCell(
    val column: Int,
    val row: Int,
    val value: String,
    val rawValue: String,
    val formula: String?,
    val style: XlsxCellStyle,
    val columnSpan: Int,
    val rowSpan: Int,
    val isCovered: Boolean,
)

/** Visual attributes of a cell, resolved from the workbook styles part. */
data class XlsxCellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontColor: OfficeColor? = null,
    val background: OfficeColor? = null,
    val horizontal: String? = null,
    val vertical: String? = null,
    val wrapText: Boolean = false,
    val numberFormat: String? = null,
    val borders: Set<String> = setOf(),
)

/**
 * Offline SpreadsheetML reader.
 *
 * Everything is resolved from the OOXML package on device: the workbook part
 * provides the sheet list, the shared string table provides the text values
 * and the styles part provides fonts, fills, borders and number formats.
 * The parser is namespace agnostic, only local element names are matched.
 */
object XlsxParser {

    private const val WORKBOOK_PART = "xl/workbook.xml"
    private const val SHARED_STRINGS_PART = "xl/sharedStrings.xml"
    private const val STYLES_PART = "xl/styles.xml"
    private const val THEME_PART = "xl/theme/theme1.xml"

    /** Excel character width -> dp (the classic 7 px per character + 5 px padding). */
    private fun widthToDp(width: Float): Float = Math.round(width * 7f + 5f).toFloat()

    /** Excel points -> dp on the same scale as the column heuristic. */
    private fun heightToDp(points: Float): Float = points * 4f / 3f

    /** Theme slot order used by the @theme attribute of the styles part. */
    private val THEME_SLOTS = listOf(
        "lt1", "dk1", "lt2", "dk2",
        "accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
    )

    /**
     * The number of rows that are parsed per sheet.
     *
     * A big workbook would not fit into the memory of the phone, the preview
     * shows the first rows of it.
     */
    private const val MAX_ROWS = 2000

    /** Number format ids that are defined by the specification itself. */
    private val BUILTIN_FORMATS: Map<Int, String> = mapOf(
        0 to "General",
        1 to "0",
        2 to "0.00",
        3 to "#,##0",
        4 to "#,##0.00",
        9 to "0%",
        10 to "0.00%",
        14 to "m/d/yy",
        49 to "@",
    )

    fun parse(pkg: OoxmlPackage): XlsxWorkbook? = Reader(pkg).readAll()

    /**
     * Reads a workbook sheet by sheet.
     *
     * The shared strings and the styles of the workbook are read once, the
     * sheets are read one after the other, so the reader can show the first
     * sheet while the rest of the workbook is still read. The package has to
     * stay open until every sheet is read.
     */
    class Reader(private val pkg: OoxmlPackage) {

        private val theme = readTheme(pkg)
        private val sharedStrings = readSharedStrings(pkg)
        private val styles = readStyles(pkg, theme)
        private val workbook = pkg.node(WORKBOOK_PART)
        private val sheetNodes = workbook?.child("sheets")?.children("sheet").orEmpty()
        private val index: List<SheetIndex>

        init {
            val list = ArrayList<SheetIndex>()
            for (position in sheetNodes.indices) {
                val sheetNode = sheetNodes[position]
                val name = sheetNode.attr("name") ?: "Sheet${position + 1}"
                val part = resolveSheetPart(pkg, sheetNode, position) ?: continue
                list += SheetIndex(name, part)
            }
            if (list.isEmpty()) {
                val parts = pkg.partNames()
                    .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
                    .sorted()
                parts.forEachIndexed { position, part ->
                    list += SheetIndex("Sheet${position + 1}", part)
                }
            }
            index = list
        }

        /** The number of the sheets of the workbook. */
        val sheetCount: Int get() = index.size

        /** The name of the sheet with the given index. */
        fun sheetName(position: Int): String = index.getOrNull(position)?.name.orEmpty()

        /** The index of the sheet that is shown first. */
        val activeSheetIndex: Int
            get() {
                val active = workbook?.child("bookViews")?.child("workbookView")?.attrInt("activeTab") ?: 0
                return active.coerceIn(0, maxOf(0, sheetCount - 1))
            }

        /** Reads one sheet of the workbook. */
        fun readSheet(position: Int): XlsxSheet? {
            val entry = index.getOrNull(position) ?: return null
            return parseWorksheet(pkg, entry.part, entry.name, sharedStrings, styles, theme)
        }

        /** Reads the whole workbook. */
        fun readAll(): XlsxWorkbook? {
            val sheets = ArrayList<XlsxSheet>()
            for (position in 0 until index.size) {
                readSheet(position)?.let { sheets += it }
            }
            if (sheets.isEmpty()) return null
            return XlsxWorkbook(sheets, activeSheetIndex.coerceIn(0, sheets.size - 1))
        }
    }

    /** The name and the part of a sheet of the workbook. */
    data class SheetIndex(val name: String, val part: String)

    // ------------------------------------------------------------------ parts

    private fun resolveSheetPart(pkg: OoxmlPackage, sheetNode: OoxmlNode, index: Int): String? {
        val relationshipId = sheetNode.attr("id")
        if (relationshipId != null) {
            val resolved = pkg.resolveRelationship(WORKBOOK_PART, relationshipId)
            if (resolved != null && pkg.hasPart(resolved)) return resolved
        }
        val sheetId = sheetNode.attrInt("sheetId")
        if (sheetId != null) {
            val guess = "xl/worksheets/sheet$sheetId.xml"
            if (pkg.hasPart(guess)) return guess
        }
        val guess = "xl/worksheets/sheet${index + 1}.xml"
        return if (pkg.hasPart(guess)) guess else null
    }

    /** Reads the theme colour scheme; falls back to the Excel theme part. */
    private fun readTheme(pkg: OoxmlPackage): Map<String, OfficeColor> {
        val shared = OfficeColors.readTheme(pkg)
        if (shared.isNotEmpty()) return shared
        val root = pkg.node(THEME_PART) ?: return emptyMap()
        val scheme = root.descendants("clrScheme").firstOrNull() ?: return emptyMap()
        val result = HashMap<String, OfficeColor>()
        scheme.nodes().forEach { slot ->
            OfficeColors.colorOf(slot, emptyMap())?.let { result[slot.localName] = it }
        }
        return result
    }

    private fun readSharedStrings(pkg: OoxmlPackage): List<String> {
        val root = pkg.node(SHARED_STRINGS_PART) ?: return emptyList()
        return root.children("si").map { item -> richText(item) }
    }

    /** Concatenates the plain or rich (r/t) text of a shared string item. */
    private fun richText(node: OoxmlNode): String {
        val runs = node.children("r")
        if (runs.isNotEmpty()) {
            return runs.joinToString("") { run -> run.children("t").joinToString("") { it.text } }
        }
        return node.children("t").joinToString("") { it.text }
    }

    // ----------------------------------------------------------------- styles

    private fun readStyles(pkg: OoxmlPackage, theme: Map<String, OfficeColor>): WorkbookStyles {
        val root = pkg.node(STYLES_PART)
        if (root == null) return WorkbookStyles.empty()

        val numberFormats = HashMap<Int, String>()
        root.child("numFmts")?.children("numFmt")?.forEach { node ->
            val id = node.attrInt("numFmtId") ?: return@forEach
            val code = node.attr("formatCode") ?: return@forEach
            numberFormats[id] = code
        }

        val fonts = root.child("fonts")?.children("font")?.map { font ->
            FontDef(
                bold = isOn(font.child("b")),
                italic = isOn(font.child("i")),
                underline = isOn(font.child("u")),
                color = font.child("color")?.let { resolveColor(it, theme) },
            )
        } ?: emptyList()

        val fills = root.child("fills")?.children("fill")?.map { fill ->
            val pattern = fill.child("patternFill")
            if (pattern != null && pattern.attr("patternType") == "solid") {
                pattern.child("fgColor")?.let { resolveColor(it, theme) }
            } else {
                null
            }
        } ?: emptyList()

        val borders: List<Set<String>> = root.child("borders")?.children("border")?.map { border ->
            val sides = mutableSetOf<String>()
            for (side in listOf("left", "right", "top", "bottom")) {
                val node = border.child(side) ?: continue
                val style = node.attr("style")
                if (style != null && style != "none") sides.add(side)
            }
            sides
        } ?: emptyList()

        val xfs = root.child("cellXfs")?.children("xf")?.map { xf ->
            val alignment = xf.child("alignment")
            XfDef(
                numFmtId = xf.attrInt("numFmtId") ?: 0,
                fontId = xf.attrInt("fontId") ?: 0,
                fillId = xf.attrInt("fillId") ?: 0,
                borderId = xf.attrInt("borderId") ?: 0,
                horizontal = normalizeHorizontal(alignment?.attr("horizontal")),
                vertical = normalizeVertical(alignment?.attr("vertical")),
                wrapText = alignment?.flag("wrapText") == true,
            )
        } ?: emptyList()

        return WorkbookStyles(numberFormats, fonts, fills, borders, xfs)
    }

    /** A style flag element is on unless it explicitly carries a false value. */
    private fun isOn(node: OoxmlNode?): Boolean {
        if (node == null) return false
        val value = node.attr("val") ?: return true
        return value.trim().lowercase() !in setOf("0", "false", "off")
    }

    private fun normalizeHorizontal(value: String?): String? = when (value?.trim()?.lowercase()) {
        "left" -> "left"
        "center", "centercontinuous" -> "center"
        "right" -> "right"
        else -> null
    }

    private fun normalizeVertical(value: String?): String? = when (value?.trim()?.lowercase()) {
        "top" -> "top"
        "center" -> "center"
        "bottom" -> "bottom"
        else -> null
    }

    /** Resolves a colour element (@rgb / @theme + tint) against the theme. */
    private fun resolveColor(node: OoxmlNode, theme: Map<String, OfficeColor>): OfficeColor? {
        val rgb = node.attr("rgb")?.let { OfficeColors.parseHex(it) }
        if (rgb != null) return applyTint(node, rgb)
        val themeIndex = node.attrInt("theme")
        if (themeIndex != null) {
            val slot = THEME_SLOTS.getOrNull(themeIndex) ?: return null
            val base = theme[slot] ?: return null
            return applyTint(node, base)
        }
        if (node.attrInt("auto") == 1) return OfficeColor.Black
        return null
    }

    /** Applies the DrawingML tint transform of a colour element. */
    private fun applyTint(node: OoxmlNode, color: OfficeColor): OfficeColor {
        val tint = node.child("tint")?.attr("val")?.trim()?.toDoubleOrNull() ?: return color
        fun channel(value: Float): Float = when {
            tint < 0 -> value * (1f + tint.toFloat())
            tint > 0 -> value * (1f - tint.toFloat()) + tint.toFloat()
            else -> value
        }
        return OfficeColor(
            channel(color.red).coerceIn(0f, 1f),
            channel(color.green).coerceIn(0f, 1f),
            channel(color.blue).coerceIn(0f, 1f),
            color.alpha,
        )
    }

    /** Resolves the tab colour of a sheet, which may be a direct attribute. */
    private fun resolveTabColor(node: OoxmlNode, theme: Map<String, OfficeColor>): OfficeColor? {
        OfficeColors.colorOf(node, theme)?.let { return it }
        return resolveColor(node, theme)
    }

    // -------------------------------------------------------------- worksheet

    private fun parseWorksheet(
        pkg: OoxmlPackage,
        part: String,
        name: String,
        sharedStrings: List<String>,
        styles: WorkbookStyles,
        theme: Map<String, OfficeColor>,
    ): XlsxSheet? {
        val root = pkg.node(part) ?: return null

        val tabColor = root.child("sheetPr")?.child("tabColor")?.let { resolveTabColor(it, theme) }

        val pane = root.child("sheetViews")?.child("sheetView")?.child("pane")
        val frozenColumns = (pane?.attrInt("xSplit") ?: 0).coerceAtLeast(0)
        val frozenRows = (pane?.attrInt("ySplit") ?: 0).coerceAtLeast(0)

        val widthByColumn = readColumnWidths(root)
        val defaultColumnWidthDp = readDefaultColumnWidth(root)
        val defaultRowHeightDp = readDefaultRowHeight(root)

        val rowNodes = root.child("sheetData")?.children("row")
            ?.take(MAX_ROWS)
            .orEmpty()
        val sharedFormulas = readSharedFormulas(rowNodes)
        val drafts = readCells(rowNodes, sharedStrings, sharedFormulas)
        val rowHeights = readRowHeights(rowNodes)
        val merges = readMerges(root)

        val maxColumn = maxOf(
            drafts.values.maxOfOrNull { row -> row.keys.maxOrNull() ?: -1 } ?: -1,
            widthByColumn.keys.maxOrNull() ?: -1,
            merges.maxOfOrNull { it.lastColumn } ?: -1,
        )

        val rows = buildRows(drafts, merges, styles, rowHeights, defaultRowHeightDp)

        val columnWidthsDp = ArrayList<Float>(maxColumn + 1)
        for (column in 0..maxColumn) {
            columnWidthsDp.add(widthByColumn[column] ?: defaultColumnWidthDp)
        }

        return XlsxSheet(
            name = name,
            rows = rows,
            columnWidthsDp = columnWidthsDp,
            defaultColumnWidthDp = defaultColumnWidthDp,
            frozenRows = frozenRows,
            frozenColumns = frozenColumns,
            tabColor = tabColor,
        )
    }

    private fun readColumnWidths(root: OoxmlNode): Map<Int, Float> {
        val result = HashMap<Int, Float>()
        val cols = root.child("cols") ?: return result
        for (col in cols.children("col")) {
            val width = col.attrFloat("width") ?: continue
            val min = col.attrInt("min") ?: continue
            val max = col.attrInt("max") ?: min
            val dp = widthToDp(width)
            for (index in min..max) {
                if (index >= 1) result[index - 1] = dp
            }
        }
        return result
    }

    private fun readDefaultColumnWidth(root: OoxmlNode): Float {
        val format = root.child("sheetFormatPr")
        val defaultWidth = format?.attrFloat("defaultColWidth")
        if (defaultWidth != null && defaultWidth > 0f) return widthToDp(defaultWidth)
        val base = format?.attrFloat("defaultBaseColWidth") ?: format?.attrFloat("baseColWidth")
        if (base != null && base > 0f) return widthToDp(base)
        return widthToDp(8.43f)
    }

    private fun readDefaultRowHeight(root: OoxmlNode): Float {
        val height = root.child("sheetFormatPr")?.attrFloat("defaultRowHeight")
        if (height != null && height > 0f) return heightToDp(height)
        return heightToDp(15f)
    }

    /** Reads the explicit row heights (points) of the sheet. */
    private fun readRowHeights(rowNodes: List<OoxmlNode>): Map<Int, Float> {
        val result = HashMap<Int, Float>()
        for (rowNode in rowNodes) {
            val rowNumber = rowNode.attrInt("r") ?: continue
            val height = rowNode.attrFloat("ht") ?: continue
            if (height <= 0f) continue
            result[rowNumber] = heightToDp(height)
        }
        return result
    }

    /** Collects the master formulas of the shared formula groups. */
    private fun readSharedFormulas(rowNodes: List<OoxmlNode>): Map<Int, String> {
        val result = HashMap<Int, String>()
        for (rowNode in rowNodes) {
            for (cellNode in rowNode.children("c")) {
                val formula = cellNode.child("f") ?: continue
                val si = formula.attrInt("si") ?: continue
                val text = formula.text.trim()
                if (text.isNotEmpty()) result[si] = text
            }
        }
        return result
    }

    /** Reads every cell of the sheet into a row -> column -> draft map. */
    private fun readCells(
        rowNodes: List<OoxmlNode>,
        sharedStrings: List<String>,
        sharedFormulas: Map<Int, String>,
    ): Map<Int, MutableMap<Int, CellDraft>> {
        val result = LinkedHashMap<Int, MutableMap<Int, CellDraft>>()
        for (rowNode in rowNodes) {
            val rowNumber = rowNode.attrInt("r") ?: continue
            val row = result.getOrPut(rowNumber) { LinkedHashMap() }
            for (cellNode in rowNode.children("c")) {
                val reference = cellNode.attr("r")
                val parsed = reference?.let { columnOf(it) } ?: -1
                val column = if (parsed >= 0) parsed else (row.keys.maxOrNull()?.plus(1) ?: 0)
                if (column < 0) continue
                val type = cellNode.attr("t")?.trim()?.lowercase()
                val raw = readRawValue(cellNode, type, sharedStrings)
                row[column] = CellDraft(
                    rawValue = raw,
                    type = type,
                    formula = readFormula(cellNode, sharedFormulas),
                    styleIndex = cellNode.attrInt("s") ?: 0,
                )
            }
        }
        return result
    }

    /** The cached value of a cell as it is stored in the file. */
    private fun readRawValue(
        cellNode: OoxmlNode,
        type: String?,
        sharedStrings: List<String>,
    ): String {
        val value = cellNode.child("v")?.text?.trim()
        return when (type) {
            "s" -> {
                val index = value?.toIntOrNull()
                if (index != null) sharedStrings.getOrNull(index).orEmpty() else value.orEmpty()
            }

            "b" -> when (value) {
                "1" -> "TRUE"
                "0" -> "FALSE"
                else -> value.orEmpty()
            }

            "inlinestr" -> richText(cellNode.child("is") ?: return "")
            "str", "e", "d" -> value.orEmpty()
            else -> value.orEmpty()
        }
    }

    private fun readFormula(cellNode: OoxmlNode, sharedFormulas: Map<Int, String>): String? {
        val node = cellNode.child("f") ?: return null
        var text = node.text.trim()
        if (text.isEmpty()) {
            val si = node.attrInt("si")
            if (si != null) text = sharedFormulas[si]?.trim().orEmpty()
        }
        if (text.isEmpty()) return null
        return text.removePrefix("=").trim()
    }

    /** Parses the column part of a cell reference such as "AA12". */
    private fun columnOf(reference: String): Int {
        var column = 0
        var index = 0
        while (index < reference.length && reference[index].isLetter()) {
            column = column * 26 + (reference[index].uppercaseChar() - 'A' + 1)
            index++
        }
        return if (index == 0) -1 else column - 1
    }

    /** Parses the row part of a cell reference such as "AA12". */
    private fun rowOf(reference: String): Int {
        var index = 0
        while (index < reference.length && reference[index].isLetter()) index++
        return reference.substring(index).toIntOrNull() ?: -1
    }

    private fun readMerges(root: OoxmlNode): List<MergeRange> {
        val result = ArrayList<MergeRange>()
        val merges = root.child("mergeCells") ?: return result
        for (node in merges.children("mergeCell")) {
            val ref = node.attr("ref") ?: continue
            val parts = ref.split(':')
            val first = parts.firstOrNull() ?: continue
            val firstColumn = columnOf(first)
            val firstRow = rowOf(first)
            if (firstColumn < 0 || firstRow < 0) continue
            val last = parts.getOrNull(1) ?: first
            val lastColumn = columnOf(last).let { if (it < 0) firstColumn else it }
            val lastRow = rowOf(last).let { if (it < 0) firstRow else it }
            result.add(MergeRange(firstRow, lastRow, firstColumn, lastColumn))
        }
        return result
    }

    /** Materialises the rows, applying the merged ranges of the sheet. */
    private fun buildRows(
        drafts: Map<Int, MutableMap<Int, CellDraft>>,
        merges: List<MergeRange>,
        styles: WorkbookStyles,
        rowHeights: Map<Int, Float>,
        defaultRowHeightDp: Float,
    ): List<XlsxRow> {
        val anchors = HashMap<Long, MergeRange>()
        val covered = HashSet<Long>()
        for (merge in merges) {
            anchors[key(merge.firstRow, merge.firstColumn)] = merge
            for (row in merge.firstRow..merge.lastRow) {
                for (column in merge.firstColumn..merge.lastColumn) {
                    if (row != merge.firstRow || column != merge.firstColumn) {
                        covered.add(key(row, column))
                    }
                }
            }
        }

        val rowNumbers = TreeSet<Int>(drafts.keys)
        for (merge in merges) {
            for (row in merge.firstRow..merge.lastRow) rowNumbers.add(row)
        }

        val rows = ArrayList<XlsxRow>(rowNumbers.size)
        for (rowNumber in rowNumbers) {
            val draftRow = drafts[rowNumber]
            val columns = TreeSet<Int>()
            draftRow?.keys?.let { columns.addAll(it) }
            for (merge in merges) {
                if (rowNumber < merge.firstRow || rowNumber > merge.lastRow) continue
                columns.add(merge.firstColumn)
                for (column in merge.firstColumn..merge.lastColumn) columns.add(column)
            }

            val cells = ArrayList<XlsxCell>(columns.size)
            for (column in columns) {
                val draft = draftRow?.get(column)
                val merge = anchors[key(rowNumber, column)]
                val isCovered = covered.contains(key(rowNumber, column))
                val anchor = merge?.let { drafts[it.firstRow]?.get(it.firstColumn) }
                val styleIndex = when {
                    draft != null -> draft.styleIndex
                    anchor != null -> anchor.styleIndex
                    else -> 0
                }
                val numberFormat = styles.formatCode(styleIndex)
                val raw = draft?.rawValue.orEmpty()
                val value = if (draft == null || draft.type == null || draft.type == "n") {
                    formatNumber(raw, numberFormat)
                } else {
                    raw
                }
                cells.add(
                    XlsxCell(
                        column = column,
                        row = rowNumber,
                        value = value,
                        rawValue = raw,
                        formula = draft?.formula,
                        style = styles.styleOf(styleIndex),
                        columnSpan = merge?.columnSpan ?: 1,
                        rowSpan = merge?.rowSpan ?: 1,
                        isCovered = isCovered,
                    ),
                )
            }

            val height = rowHeights[rowNumber] ?: defaultRowHeightDp
            rows.add(XlsxRow(rowNumber, height, cells))
        }
        return rows
    }

    private fun key(row: Int, column: Int): Long = row.toLong() * 1_000_000L + column

    // -------------------------------------------------------- number formats

    /** Applies a number format code to a raw numeric cell value. */
    private fun formatNumber(raw: String, formatCode: String?): String {
        val trimmed = raw.trim()
        val number = trimmed.toDoubleOrNull() ?: return trimmed
        val code = formatCode?.trim().orEmpty()
        if (code.isEmpty() || code.equals("General", true) || code == "@") return trimmed

        val section = code.split(';').firstOrNull()?.trim().orEmpty()
        if (section.isEmpty()) return trimmed

        val stripped = section.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\\\."), "")
        if (isDateFormat(stripped)) return formatSerialDate(number)

        val symbol = when {
            section.contains("¥") -> "¥"
            section.contains("$") -> "$"
            section.contains("€") -> "€"
            section.contains("£") -> "£"
            else -> ""
        }
        val percent = section.contains("%")
        val numeric = stripped.replace("%", "").replace(symbol, "")
        val decimals = decimalsOf(numeric)
        val grouped = numeric.contains(',')
        val value = if (percent) number * 100f else number
        val body = formatDecimal(value, decimals, grouped)
        return symbol + body + if (percent) "%" else ""
    }

    private fun isDateFormat(code: String): Boolean {
        val lower = code.lowercase()
        if (lower.contains('y') && (lower.contains('m') || lower.contains('d'))) return true
        return false
    }

    /** Counts the decimal places of the numeric placeholder of a format code. */
    private fun decimalsOf(code: String): Int {
        val dot = code.indexOf('.')
        if (dot < 0) return 0
        var count = 0
        var index = dot + 1
        while (index < code.length) {
            val char = code[index]
            if (char == '0' || char == '#') {
                count++
            } else {
                break
            }
            index++
        }
        return count
    }

    private fun formatDecimal(value: Double, decimals: Int, grouped: Boolean): String {
        val rounded = String.format(Locale.US, "%.${decimals}f", value)
        val dot = rounded.indexOf('.')
        val sign = if (rounded.startsWith("-")) "-" else ""
        val integerPart = if (dot >= 0) rounded.substring(0, dot) else rounded
        val fraction = if (dot >= 0) rounded.substring(dot) else ""
        val digits = integerPart.removePrefix("-")
        val groupedDigits = if (grouped) {
            digits.reversed().chunked(3).joinToString(",").reversed()
        } else {
            digits
        }
        return "$sign$groupedDigits$fraction"
    }

    /** Excel serial (days since 1899-12-30) rendered as yyyy-MM-dd. */
    private fun formatSerialDate(serial: Double): String {
        val days = Math.floor(serial).toLong()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(days * 86_400_000L))
    }

    // ------------------------------------------------------------- internals

    private class FontDef(
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val color: OfficeColor?,
    )

    private class XfDef(
        val numFmtId: Int,
        val fontId: Int,
        val fillId: Int,
        val borderId: Int,
        val horizontal: String?,
        val vertical: String?,
        val wrapText: Boolean,
    )

    private class CellDraft(
        val rawValue: String,
        val type: String?,
        val formula: String?,
        val styleIndex: Int,
    )

    private class MergeRange(
        val firstRow: Int,
        val lastRow: Int,
        val firstColumn: Int,
        val lastColumn: Int,
    ) {
        val columnSpan: Int get() = lastColumn - firstColumn + 1
        val rowSpan: Int get() = lastRow - firstRow + 1
    }

    private class WorkbookStyles(
        val numberFormats: Map<Int, String>,
        val fonts: List<FontDef>,
        val fills: List<OfficeColor?>,
        val borders: List<Set<String>>,
        val xfs: List<XfDef>,
    ) {
        fun formatCode(styleIndex: Int): String? {
            val xf = xfs.getOrNull(styleIndex) ?: return null
            return numberFormats[xf.numFmtId] ?: XlsxParser.BUILTIN_FORMATS[xf.numFmtId]
        }

        fun styleOf(styleIndex: Int): XlsxCellStyle {
            val xf = xfs.getOrNull(styleIndex) ?: return XlsxCellStyle()
            val font = fonts.getOrNull(xf.fontId)
            val format = numberFormats[xf.numFmtId] ?: XlsxParser.BUILTIN_FORMATS[xf.numFmtId]
            return XlsxCellStyle(
                bold = font?.bold == true,
                italic = font?.italic == true,
                underline = font?.underline == true,
                fontColor = font?.color,
                background = fills.getOrNull(xf.fillId),
                horizontal = xf.horizontal,
                vertical = xf.vertical,
                wrapText = xf.wrapText,
                numberFormat = format,
                borders = borders.getOrNull(xf.borderId) ?: emptySet(),
            )
        }

        companion object {
            fun empty(): WorkbookStyles = WorkbookStyles(emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
}
