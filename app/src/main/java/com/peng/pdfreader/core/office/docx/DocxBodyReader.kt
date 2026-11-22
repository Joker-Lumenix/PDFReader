package com.peng.pdfreader.core.office.docx

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.SaxCollector
import com.peng.pdfreader.core.office.XmlNode
import com.peng.pdfreader.core.util.Logger
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads the body of a Word document with a streaming parser.
 *
 * The body of a big document does not fit into the memory of the phone as a
 * tree, so it is read with a streaming parser: the paragraphs are delivered
 * one after the other and the tree of the document is never built. Only the
 * small parts - the properties of a paragraph, of a run, of a cell and of the
 * section - are collected as a tree.
 *
 * Every lookup is namespace agnostic (only the local names are compared),
 * which keeps the reader working for the many Word dialects.
 */
internal class DocxBodyReader(
    private val styles: DocxStyleTable,
    private val numbering: DocxNumberingTable,
    private val images: DocxImageReader,
) : DefaultHandler() {

    /** The blocks of the document. */
    val blocks = ArrayList<DocxBlock>()

    /** Whether the document was too big and was cut off. */
    var truncated = false
        private set

    /** The section of the document, the last one wins. */
    var section: DocxSection? = null
        private set

    /** The title of the document, taken from its first heading. */
    var fallbackTitle: String? = null
        private set

    private companion object {
        const val TAG = "DocxReader"
    }

    /** The counters of the numbered lists. */
    private val counters = HashMap<String, IntArray>()

    /**
     * The number of the paragraphs that were read.
     *
     * The number is the place of the paragraph in the document, it is used to
     * write a changed text back into the file.
     */
    private var paragraphCounter = 0

    /** The tables that are open, the last one is the current one. */
    private val tables = ArrayList<TableBuilder>()

    /** The paragraph that is read at the moment. */
    private var paragraph: ParagraphBuilder? = null

    /** The depth of the open paragraphs. */
    private var paragraphDepth = 0

    /** The run that is read at the moment. */
    private var run: RunBuilder? = null

    /** The depth of the open runs. */
    private var runDepth = 0

    /** The collector of a small part of the document. */
    private var collector: SaxCollector? = null

    /** The picture that is read at the moment. */
    private var picture: PictureBuilder? = null

    /** Whether the parser is inside the text of a run. */
    private var inText = false

    /**
     * The text of the text boxes of the drawing that is read at the moment.
     *
     * A text box holds a text of its own, it is not part of the paragraph
     * that carries the drawing. The text is read and shown as a block of its
     * own, otherwise the content of the document would be incomplete.
     */
    private val textBoxes = ArrayList<String>()

    /** The text of the text box that is read at the moment. */
    private var textBox: StringBuilder? = null

    /** Whether the parser is inside the fallback of a drawing. */
    private var inFallback = false

    /** The number of the text boxes when the fallback began. */
    private var fallbackStart = 0

    /**
     * Reads the body of the document.
     *
     * @return true when the body was read
     */
    fun read(input: InputStream): Boolean = try {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        val ok = runCatching { factory.newSAXParser().parse(input, this) }
            .onFailure { Logger.e(TAG, "the streaming parser of the document failed", it) }
            .isSuccess
        ok
    } catch (error: Exception) {
        Logger.e(TAG, "the streaming parser of the document failed", error)
        false
    } catch (error: OutOfMemoryError) {
        Logger.e(TAG, "the document does not fit into the memory of the phone")
        false
    }

    /**
     * Reads the body of the document out of a tree.
     *
     * The tree is the fallback for the platforms whose streaming parser is
     * not available. It is only used for a document that fits into the memory
     * of the phone, the big ones are read with the streaming parser.
     */
    fun readTree(root: XmlNode?): Boolean {
        if (root == null) return false
        walk(root)
        return true
    }

    /** Walks over a tree and delivers the events of the parser. */
    private fun walk(node: XmlNode) {
        onStart(node.localName) { attributesOf(node) }
        if (node.localName == "t") onText(node.text)
        node.nodes().forEach { walk(it) }
        onEnd(node.localName)
    }

    /** The attributes of a node, the namespace prefixes are left out. */
    private fun attributesOf(node: XmlNode): Map<String, String> {
        val result = HashMap<String, String>()
        for (name in ATTRIBUTE_NAMES) {
            node.attr(name)?.let { result[name] = it }
        }
        return result
    }

    // ------------------------------------------------------------------- sax

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes?,
    ) {
        val name = localName?.takeIf { it.isNotEmpty() }
            ?: qName?.substringAfterLast(':').orEmpty()
        onStart(name) { attributes?.toMap() ?: emptyMap() }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        onEnd(
            localName?.takeIf { it.isNotEmpty() }
                ?: qName?.substringAfterLast(':').orEmpty(),
        )
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (ch == null || length <= 0) return
        onText(String(ch, start, length))
    }

    /**
     * Opens an element of the document.
     *
     * The attributes are only read for the elements that need them - a big
     * document has hundreds of thousands of elements, and the map of the
     * attributes of each of them would cost more time than the reading.
     */
    private fun onStart(name: String, attributes: () -> Map<String, String>) {
        val current = collector
        if (current != null) {
            current.start(name, attributes())
            return
        }
        // Inside a drawing only the picture itself is of interest, the text
        // boxes of a drawing are read as well - they hold a text of their own
        // that would be lost otherwise.
        if (picture != null) {
            when (name) {
                "extent" -> picture?.readExtent(attributes())
                "blip" -> picture?.readBlip(attributes())
                "imagedata" -> picture?.readImageData(attributes())
                "txbxContent" -> textBox = StringBuilder()
                "p" -> textBox?.append('\n')
                "Fallback" -> {
                    // The fallback of a drawing holds the same text boxes as
                    // its choice, it is only used by the readers that do not
                    // know the choice.
                    inFallback = true
                    fallbackStart = textBoxes.size
                }
            }
            return
        }
        when (name) {
            "pPr" -> collect("pPr", attributes())
            "rPr" -> collect("rPr", attributes())
            "sectPr" -> collect("sectPr", attributes())
            "tcPr" -> collect("tcPr", attributes())
            "tblGrid" -> collect("tblGrid", attributes())
            "p" -> startParagraph()
            "r" -> startRun()
            "t" -> inText = true
            "tab" -> run?.appendText("\t")
            "br" -> run?.appendBreak(attributes())
            "cr" -> run?.appendText("\n")
            "noBreakHyphen" -> run?.appendText("-")
            "drawing", "pict" -> picture = PictureBuilder()
            "tbl" -> tables += TableBuilder()
            "tr" -> tables.lastOrNull()?.startRow()
            "tc" -> tables.lastOrNull()?.startCell()
        }
    }

    /** Closes an element of the document. */
    private fun onEnd(name: String) {
        val current = collector
        if (current != null) {
            current.end()
            if (current.isComplete) {
                val node = current.root
                collector = null
                applyCollected(name, node)
            }
            return
        }
        if (picture != null) {
            when (name) {
                "drawing", "pict" -> endPicture()
                "txbxContent" -> endTextBox()
                "Fallback" -> endFallback()
            }
            return
        }
        when (name) {
            "t" -> inText = false
            "p" -> endParagraph()
            "r" -> endRun()
            "tbl" -> endTable()
            "tr" -> tables.lastOrNull()?.endRow()
            "tc" -> tables.lastOrNull()?.endCell()
        }
    }

    /** The text of the document. */
    private fun onText(value: String) {
        collector?.text(value)
        // The text of a text box belongs to the box, not to the paragraph
        // that carries the drawing.
        if (textBox != null) {
            textBox?.append(value)
            return
        }
        if (inText && runDepth == 1) run?.appendText(value)
    }

    /** Starts the collection of a small part of the document. */
    private fun collect(name: String, attributes: Map<String, String>) {
        val created = SaxCollector()
        created.start(name, attributes)
        collector = created
    }

    /** Applies a part of the document that was collected as a tree. */
    private fun applyCollected(name: String, node: XmlNode?) {
        when (name) {
            "pPr" -> paragraph?.applyProperties(node, styles, numbering, counters)
            "rPr" -> run?.applyProperties(node)
            "sectPr" -> section = DocxSection.read(node)
            "tcPr" -> tables.lastOrNull()?.cell?.applyProperties(node)
            "tblGrid" -> tables.lastOrNull()?.applyGrid(node)
        }
    }

    // ------------------------------------------------------------- paragraphs

    private fun startParagraph() {
        paragraphDepth++
        if (paragraphDepth == 1) {
            paragraph = ParagraphBuilder()
            // The paragraphs are numbered in the order they are read, the
            // editor counts them the same way when it writes a changed text
            // back into the document.
            paragraph?.sourceIndex = paragraphCounter++
        }
    }

    private fun endParagraph() {
        paragraphDepth--
        if (paragraphDepth > 0) return
        val current = paragraph ?: return
        paragraph = null
        if (fallbackTitle == null &&
            (current.styleId == "Title" || current.headingLevel == 1)
        ) {
            fallbackTitle = current.plainText().trim().takeIf { it.isNotEmpty() }
        }
        attach(current.build())
        current.image?.let { image ->
            attach(
                DocxBlock.Image(
                    bytes = image.bytes,
                    widthPt = image.widthPt,
                    heightPt = image.heightPt,
                    alignment = "left",
                ),
            )
        }
        // The text boxes of the drawing hold a text of their own, they are
        // shown as the paragraphs that follow the drawing.
        attachTextBoxes()
    }

    /**
     * Attaches the text of the text boxes of a drawing.
     *
     * The text boxes are not part of the flow of the document - they float
     * over it - so they are shown after the paragraph that carries them.
     */
    private fun attachTextBoxes() {
        if (textBoxes.isEmpty()) return
        val boxes = ArrayList(textBoxes)
        textBoxes.clear()
        boxes.forEach { text ->
            attach(
                DocxBlock.Paragraph(
                    runs = listOf(DocxRun(text = text)),
                    styleId = null,
                    headingLevel = 0,
                    alignment = "left",
                    indentLevel = 0,
                    listLabel = null,
                    spaceBeforePt = 0f,
                    spaceAfterPt = 0f,
                    lineSpacing = 1f,
                    pageBreakBefore = false,
                ),
            )
        }
    }

    private fun attach(block: DocxBlock) {
        val cell = tables.lastOrNull()?.cell
        if (cell != null) {
            cell.blocks += block
            return
        }
        if (blocks.size >= MAX_BLOCKS) {
            truncated = true
            return
        }
        blocks += block
    }

    // ------------------------------------------------------------------- runs

    private fun startRun() {
        runDepth++
        if (runDepth == 1) run = RunBuilder()
    }

    private fun endRun() {
        runDepth--
        if (runDepth > 0) return
        val current = run ?: return
        run = null
        val target = paragraph ?: return
        val style = styles.info(target.rawStyleId)
        val properties = current.properties
        val charStyle = styles.info(properties?.child("rStyle")?.attr("val")?.trim())
        var base = style?.run ?: styles.defaultRun
        if (target.isTitle || target.headingLevel > 0) base = base.copy(sizePt = null)
        if (charStyle != null) base = mergeRunProps(base, charStyle.run)
        val props = mergeRun(base, properties)

        if (current.text.isNotEmpty()) {
            target.runs += DocxRun(
                text = current.text.toString(),
                bold = props.bold == true,
                italic = props.italic == true,
                underline = props.underline == true,
                strike = props.strike == true,
                superscript = props.superscript == true,
                color = props.color,
                sizePt = props.sizePt,
                fontFamily = props.fontFamily,
            )
        }
        current.image?.let { image ->
            // The picture of a run is a block of its own, the reader draws
            // the blocks of a page one after the other.
            target.image = DocxParsedImage(image.bytes, image.widthPt, image.heightPt)
        }
        if (current.pageBreak) target.pageBreakBefore = true
    }

    // --------------------------------------------------------------- pictures

    private fun endPicture() {
        val current = picture ?: return
        picture = null
        val target = run ?: return
        val relationship = current.relationshipId ?: return
        val bytes = images.bytes(relationship) ?: return
        target.image = DocxParsedImage(bytes, current.widthPt, current.heightPt)
    }

    /** Closes a text box of a drawing. */
    private fun endTextBox() {
        val current = textBox ?: return
        textBox = null
        val text = current.toString().trim()
        if (text.isEmpty()) return
        textBoxes += text
    }

    /** Closes the fallback of a drawing, its text boxes are dropped. */
    private fun endFallback() {
        inFallback = false
        while (textBoxes.size > fallbackStart) {
            textBoxes.removeAt(textBoxes.size - 1)
        }
    }

    // ----------------------------------------------------------------- tables

    private fun endTable() {
        val table = tables.removeLastOrNull() ?: return
        attach(table.build())
    }

    // --------------------------------------------------------------- builders

    /** A paragraph that is read at the moment. */
    private class ParagraphBuilder {

        val runs = ArrayList<DocxRun>()

        /** The number of the paragraph in the document. */
        var sourceIndex: Int = -1

        var rawStyleId: String? = null
        var styleId: String? = null
        var headingLevel = 0
        var isTitle = false
        var alignment = "left"
        var indentLevel = 0
        var listLabel: String? = null
        var image: DocxParsedImage? = null
        var spaceBeforePt = 0f
        var spaceAfterPt = 0f
        var lineSpacing = 1f
        var pageBreakBefore = false

        fun applyProperties(
            node: XmlNode?,
            styles: DocxStyleTable,
            numbering: DocxNumberingTable,
            counters: HashMap<String, IntArray>,
        ) {
            if (node == null) return
            rawStyleId = node.child("pStyle")?.attr("val")?.trim()
            val style = styles.info(rawStyleId)
            styleId = style?.canonicalId ?: rawStyleId
            headingLevel = style?.headingLevel ?: 0
            isTitle = styleId == "Title"

            val paraProps = mergePara(style?.para ?: styles.defaultPara, node)
            val numPr = node.child("numPr")
            val numId = numPr?.child("numId")?.attr("val")?.trim() ?: paraProps.numId
            val ilvl = numPr?.child("ilvl")?.attr("val")?.trim()?.toIntOrNull()
                ?: if (numPr == null) paraProps.listLevel else 0
            listLabel = if (numId != null) numbering.labelFor(numId, ilvl, counters) else null

            alignment = paraProps.alignment ?: if (isTitle) "center" else "left"
            indentLevel = max(
                if (numId != null) ilvl + 1 else 0,
                (paraProps.indentLeftPt / 24f).roundToInt().coerceAtLeast(0),
            )
            spaceBeforePt = paraProps.spaceBeforePt ?: 0f
            spaceAfterPt = paraProps.spaceAfterPt ?: 0f
            lineSpacing = paraProps.lineSpacing ?: 1f
            pageBreakBefore = node.child("pageBreakBefore")?.let { onOff(it) ?: true } ?: false
        }

        fun plainText(): String = runs.joinToString(separator = "") { it.text }

        fun build(): DocxBlock.Paragraph = DocxBlock.Paragraph(
            runs = runs,
            styleId = styleId,
            headingLevel = headingLevel,
            alignment = alignment,
            indentLevel = indentLevel,
            listLabel = listLabel,
            spaceBeforePt = spaceBeforePt,
            spaceAfterPt = spaceAfterPt,
            lineSpacing = lineSpacing,
            pageBreakBefore = pageBreakBefore,
            sourceIndex = sourceIndex,
        )
    }

    /** A run that is read at the moment. */
    private class RunBuilder {

        val text = StringBuilder()

        var properties: XmlNode? = null
        var image: DocxParsedImage? = null
        var pageBreak = false

        fun appendText(value: String) {
            text.append(value)
        }

        fun appendBreak(attributes: Map<String, String>) {
            val type = attributes["type"]
            if (type.equals("page", ignoreCase = true)) {
                pageBreak = true
                return
            }
            text.append('\n')
        }

        fun applyProperties(node: XmlNode?) {
            properties = node
        }
    }

    /** A picture that is read at the moment. */
    private class PictureBuilder {

        var relationshipId: String? = null
        var widthPt = 0f
        var heightPt = 0f

        /** The size of a picture of a drawing, in EMU. */
        fun readExtent(attributes: Map<String, String>) {
            val cx = attributes["cx"]?.trim()?.toDoubleOrNull() ?: return
            val cy = attributes["cy"]?.trim()?.toDoubleOrNull() ?: return
            widthPt = (cx / DOCX_EMU_PER_PT).toFloat()
            heightPt = (cy / DOCX_EMU_PER_PT).toFloat()
        }

        /** The relation of a picture of a drawing. */
        fun readBlip(attributes: Map<String, String>) {
            val id = attributes["embed"] ?: attributes["link"] ?: return
            relationshipId = id
        }

        /** The relation and the size of a picture of a legacy drawing. */
        fun readImageData(attributes: Map<String, String>) {
            val id = attributes["id"] ?: attributes["href"] ?: return
            relationshipId = id
            val style = attributes["style"].orEmpty()
            widthPt = styleDimension(style, "width")
            heightPt = styleDimension(style, "height")
        }
    }

    /** A table that is read at the moment. */
    private class TableBuilder {

        val gridWidths = ArrayList<Float>()
        val rows = ArrayList<List<DocxCell>>()

        private var row: ArrayList<DocxCell>? = null
        private var column = 0

        var cell: CellBuilder? = null
            private set

        fun startRow() {
            row = ArrayList()
            column = 0
        }

        fun endRow() {
            row?.let { rows += it }
            row = null
        }

        fun startCell() {
            cell = CellBuilder()
        }

        fun endCell() {
            val current = cell ?: return
            cell = null
            row?.add(current.build(widthOf(current)))
            column += current.columnSpan
        }

        /** The width of a cell, taken from the grid of the table. */
        private fun widthOf(cell: CellBuilder): Float {
            var width = 0f
            for (index in column until min(column + cell.columnSpan, gridWidths.size)) {
                width += gridWidths[index]
            }
            if (width <= 0f) width = cell.widthPt
            return width
        }

        fun applyGrid(node: XmlNode?) {
            if (node == null) return
            gridWidths.clear()
            node.children("gridCol").forEach { column ->
                val width = column.attr("w")?.trim()?.toFloatOrNull() ?: 0f
                if (width > 0f) gridWidths.add(width / 20f)
            }
        }

        fun build(): DocxBlock.Table {
            var widths = gridWidths.toList()
            if (widths.isEmpty()) {
                val first = rows.firstOrNull()
                if (!first.isNullOrEmpty()) {
                    val count = first.sumOf { it.columnSpan }.coerceAtLeast(1)
                    val total = first.sumOf { it.widthPt.toDouble() }.toFloat()
                    widths = List(count) { if (total > 0f) total / count else 72f }
                }
            }
            return DocxBlock.Table(rows = mergeRows(), columnWidthsPt = widths)
        }

        /**
         * The rows of the table with the merged cells.
         *
         * A cell that is merged with the cells below it stands over all of
         * them - it is the way the office software draws it. The cells that
         * belong to the cell above them get the number zero, the reader does
         * not draw them.
         */
        private fun mergeRows(): List<List<DocxCell>> {
            if (rows.isEmpty()) return rows
            // The column of every cell of every row.
            val columns = ArrayList<IntArray>()
            rows.forEach { row ->
                val line = IntArray(row.size)
                var column = 0
                row.forEachIndexed { index, cell ->
                    line[index] = column
                    column += cell.columnSpan
                }
                columns += line
            }
            // The cells that stand over the rows below them.
            val spans = HashMap<Pair<Int, Int>, Int>()
            // The cells that belong to the cell above them.
            val covered = HashSet<Pair<Int, Int>>()
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { cellIndex, cell ->
                    if (cell.vMerge != "restart") return@forEachIndexed
                    val column = columns[rowIndex][cellIndex]
                    var span = 1
                    var next = rowIndex + 1
                    while (next < rows.size) {
                        val found = cellAt(next, column, columns) ?: break
                        if (found.second.vMerge != "continue") break
                        covered += next to found.first
                        span++
                        next++
                    }
                    spans[rowIndex to column] = span
                }
            }
            return rows.mapIndexed { rowIndex, row ->
                row.mapIndexed { cellIndex, cell ->
                    val column = columns[rowIndex][cellIndex]
                    val span = spans[rowIndex to column] ?: 1
                    cell.copy(rowSpan = if (covered.contains(rowIndex to cellIndex)) 0 else span)
                }
            }
        }

        /** The cell of a row that stands in the given column. */
        private fun cellAt(
            rowIndex: Int,
            column: Int,
            columns: List<IntArray>,
        ): Pair<Int, DocxCell>? {
            val row = rows.getOrNull(rowIndex) ?: return null
            val line = columns.getOrNull(rowIndex) ?: return null
            row.forEachIndexed { index, cell ->
                val start = line[index]
                if (column >= start && column < start + cell.columnSpan) return index to cell
            }
            return null
        }
    }

    /** A cell of a table that is read at the moment. */
    private class CellBuilder {

        val blocks = ArrayList<DocxBlock>()

        var widthPt = 0f
        var background: OfficeColor? = null
        var verticalAlignment = "top"
        var columnSpan = 1

        /**
         * How the cell is merged with the cells below it.
         *
         * It is "restart" for the first cell of a merge and "continue" for
         * the cells that belong to it.
         */
        var vMerge = ""

        fun applyProperties(node: XmlNode?) {
            if (node == null) return
            node.child("gridSpan")?.attr("val")?.trim()?.toIntOrNull()?.let {
                columnSpan = it.coerceAtLeast(1)
            }
            // A cell that is merged with the cells below it names it.
            node.child("vMerge")?.let { merge ->
                // The value of the merge is "continue" when it is left out.
                vMerge = merge.attr("val")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                    ?: "continue"
            }
            node.child("shd")?.attr("fill")?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("auto", true) }
                ?.let { background = OfficeColors.parseHex(it) }
            node.child("vAlign")?.attr("val")?.trim()?.lowercase()?.let {
                verticalAlignment = when (it) {
                    "center" -> "center"
                    "bottom" -> "bottom"
                    else -> "top"
                }
            }
            node.child("tcW")?.attr("w")?.trim()?.toFloatOrNull()?.let { widthPt = it / 20f }
        }

        fun build(width: Float): DocxCell = DocxCell(
            blocks = blocks,
            widthPt = width,
            background = background,
            verticalAlignment = verticalAlignment,
            columnSpan = columnSpan,
            vMerge = vMerge,
        )
    }
}

/** A picture of a document, ready to be drawn. */
internal data class DocxParsedImage(
    val bytes: ByteArray,
    val widthPt: Float,
    val heightPt: Float,
)

/** The size of a picture of a legacy drawing, in points. */
private fun styleDimension(style: String, key: String): Float {
    val match = Regex("$key\\s*:\\s*([0-9.]+)\\s*pt").find(style) ?: return 0f
    return match.groupValues[1].toFloatOrNull() ?: 0f
}

/** The attributes of an element, the namespace prefixes are left out. */
private fun Attributes.toMap(): Map<String, String> {
    val result = HashMap<String, String>()
    for (index in 0 until length) {
        val name = localNameAt(index)
        if (name.isNotEmpty() && !result.containsKey(name)) result[name] = getValue(index)
    }
    return result
}

/** The name of the attribute, the namespace prefix is left out. */
private fun Attributes.localNameAt(index: Int): String {
    val local = getLocalName(index)
    if (local.isNotEmpty()) return local
    return getQName(index).substringAfterLast(':')
}

/** The attributes that are read out of a tree. */
private val ATTRIBUTE_NAMES = listOf(
    "val",
    "type",
    "id",
    "href",
    "embed",
    "link",
    "style",
    "cx",
    "cy",
    "w",
    "h",
    "before",
    "after",
    "line",
    "lineRule",
    "left",
    "right",
    "start",
    "end",
    "firstLine",
    "hanging",
    "ilvl",
    "numId",
    "fill",
    "top",
    "bottom",
)
