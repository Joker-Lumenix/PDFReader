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

    private val counters = HashMap<String, IntArray>()

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
        onStart(node.localName, attributesOf(node))
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
        onStart(
            localName?.takeIf { it.isNotEmpty() }
                ?: qName?.substringAfterLast(':').orEmpty(),
            attributes?.toMap() ?: emptyMap(),
        )
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

    /** Opens an element of the document. */
    private fun onStart(name: String, attributes: Map<String, String>) {
        val current = collector
        if (current != null) {
            current.start(name, attributes)
            return
        }
        // Inside a drawing only the picture itself is of interest, the text
        // boxes of a drawing are not part of the paragraph.
        if (picture != null) {
            when (name) {
                "extent" -> picture?.readExtent(attributes)
                "blip" -> picture?.readBlip(attributes)
                "imagedata" -> picture?.readImageData(attributes)
            }
            return
        }
        when (name) {
            "pPr" -> collect("pPr", attributes)
            "rPr" -> collect("rPr", attributes)
            "sectPr" -> collect("sectPr", attributes)
            "tcPr" -> collect("tcPr", attributes)
            "tblGrid" -> collect("tblGrid", attributes)
            "p" -> startParagraph()
            "r" -> startRun()
            "t" -> inText = true
            "tab" -> run?.appendText("\t")
            "br" -> run?.appendBreak(attributes)
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
            if (name == "drawing" || name == "pict") endPicture()
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
        if (paragraphDepth == 1) paragraph = ParagraphBuilder()
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
            target.runs += DocxRun(
                text = "",
                bold = props.bold == true,
                italic = props.italic == true,
                color = props.color,
                sizePt = props.sizePt,
                fontFamily = props.fontFamily,
                imageBytes = image.bytes,
                imageWidthPt = image.widthPt,
                imageHeightPt = image.heightPt,
            )
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

    // ----------------------------------------------------------------- tables

    private fun endTable() {
        val table = tables.removeLastOrNull() ?: return
        attach(table.build())
    }

    // --------------------------------------------------------------- builders

    /** A paragraph that is read at the moment. */
    private class ParagraphBuilder {

        val runs = ArrayList<DocxRun>()

        var rawStyleId: String? = null
        var styleId: String? = null
        var headingLevel = 0
        var isTitle = false
        var alignment = "left"
        var indentLevel = 0
        var listLabel: String? = null
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
            return DocxBlock.Table(rows = rows, columnWidthsPt = widths)
        }
    }

    /** A cell of a table that is read at the moment. */
    private class CellBuilder {

        val blocks = ArrayList<DocxBlock>()

        var widthPt = 0f
        var background: OfficeColor? = null
        var verticalAlignment = "top"
        var columnSpan = 1

        fun applyProperties(node: XmlNode?) {
            if (node == null) return
            node.child("gridSpan")?.attr("val")?.trim()?.toIntOrNull()?.let {
                columnSpan = it.coerceAtLeast(1)
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
