package com.peng.pdfreader.appcore.office.docx

import com.peng.pdfreader.appcore.office.OfficeColor
import com.peng.pdfreader.appcore.office.OoxmlNode
import com.peng.pdfreader.appcore.office.OoxmlPackage
import com.peng.pdfreader.appcore.office.OfficeColors
import kotlin.math.max
import kotlin.math.roundToInt

private const val TWIP_PER_PT = 20f
private const val EMU_PER_PT = 12700f
private const val MAX_LIST_LEVEL = 9

private fun bulletChar(level: Int): String = when (level) {
    0 -> "•"
    1 -> "◦"
    2 -> "▪"
    3 -> "▫"
    else -> "•"
}

/**
 * Parsed WordprocessingML document.
 *
 * All sizes are expressed in points (1 pt = 1/72 inch) so the renderers can
 * renderer can lay the page out the way Word does.
 */
data class DocxDocument(
    val title: String,
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val marginTopPt: Float,
    val marginBottomPt: Float,
    val marginLeftPt: Float,
    val marginRightPt: Float,
    val defaultFontPt: Float,
    val blocks: List<DocxBlock>,
)

/** A single block of the document body. */
sealed interface DocxBlock {

    data class Paragraph(
        val runs: List<DocxRun>,
        val styleId: String?,
        val headingLevel: Int,
        val alignment: String,
        val indentLevel: Int,
        val listLabel: String?,
        val spaceBeforePt: Float,
        val spaceAfterPt: Float,
        val lineSpacing: Float,
        val pageBreakBefore: Boolean,
    ) : DocxBlock

    data class Table(
        val rows: List<List<DocxCell>>,
        val columnWidthsPt: List<Float>,
    ) : DocxBlock

    data class Image(
        val bytes: ByteArray,
        val widthPt: Float,
        val heightPt: Float,
        val alignment: String,
    ) : DocxBlock
}

/** A table cell holding nested blocks. */
data class DocxCell(
    val blocks: List<DocxBlock>,
    val widthPt: Float,
    val background: OfficeColor?,
    val verticalAlignment: String,
    val columnSpan: Int,
)

/** A formatted text (or picture) fragment inside a paragraph. */
data class DocxRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val superscript: Boolean = false,
    val color: OfficeColor? = null,
    val sizePt: Float? = null,
    val fontFamily: String? = null,
    val imageBytes: ByteArray? = null,
    val imageWidthPt: Float = 0f,
    val imageHeightPt: Float = 0f,
)

/**
 * Reads the WordprocessingML parts of an [OoxmlPackage] into a flat block model.
 *
 * The function is pure and blocking, so the caller has to run it off the main
 * thread. Every lookup is namespace agnostic (only local names are compared),
 * which keeps the parser working for the many Word dialects.
 */
object DocxParser {

    fun parse(pkg: OoxmlPackage): DocxDocument? {
        val root = pkg.node("word/document.xml") ?: return null
        val body = root.descendants("body").firstOrNull() ?: return null

        val styles = StyleTable.parse(pkg.node("word/styles.xml"))
        val numbering = NumberingTable.parse(pkg.node("word/numbering.xml"))
        val counters = HashMap<String, IntArray>()

        val blocks = ArrayList<DocxBlock>()
        var fallbackTitle: String? = null

        body.nodes().forEach { child ->
            when (child.localName) {
                "p" -> {
                    val paragraph = parseParagraph(child, styles, numbering, counters, pkg)
                    val standaloneImage = standaloneImageOf(paragraph)
                    if (standaloneImage != null) {
                        // a picture only paragraph is a block of its own (Word renders it centred)
                        blocks += standaloneImage
                    } else {
                        blocks += paragraph
                    }
                    if (fallbackTitle == null &&
                        (paragraph.styleId == "Title" || paragraph.headingLevel == 1)
                    ) {
                        fallbackTitle = paragraph.plainText().trim().takeIf { it.isNotEmpty() }
                    }
                }

                "tbl" -> blocks += parseTable(child, styles, numbering, counters, pkg)
            }
        }

        val section = readSection(body)
        val title = pkg.node("docProps/core.xml")
            ?.descendants("title")
            ?.firstOrNull()
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackTitle
            ?: ""

        return DocxDocument(
            title = title,
            pageWidthPt = section.widthPt,
            pageHeightPt = section.heightPt,
            marginTopPt = section.topPt,
            marginBottomPt = section.bottomPt,
            marginLeftPt = section.leftPt,
            marginRightPt = section.rightPt,
            defaultFontPt = styles.defaultRun.sizePt ?: 11f,
            blocks = blocks,
        )
    }

    // ---------------------------------------------------------------- sections

    private data class SectionInfo(
        val widthPt: Float,
        val heightPt: Float,
        val topPt: Float,
        val bottomPt: Float,
        val leftPt: Float,
        val rightPt: Float,
    )

    private fun readSection(body: OoxmlNode): SectionInfo {
        val sect = body.nodes().lastOrNull { it.localName == "sectPr" }
            ?: body.descendants("sectPr").lastOrNull()

        var widthPt = 595f
        var heightPt = 842f
        var topPt = 72f
        var bottomPt = 72f
        var leftPt = 72f
        var rightPt = 72f

        sect?.child("pgSz")?.let { size ->
            val w = size.attr("w")?.trim()?.toFloatOrNull()
            val h = size.attr("h")?.trim()?.toFloatOrNull()
            if (w != null && w > 0f) widthPt = w / TWIP_PER_PT
            if (h != null && h > 0f) heightPt = h / TWIP_PER_PT
            if (size.attr("orient")?.trim()?.lowercase() == "landscape" && widthPt < heightPt) {
                val swap = widthPt
                widthPt = heightPt
                heightPt = swap
            }
        }

        sect?.child("pgMar")?.let { margin ->
            margin.attr("top")?.trim()?.toFloatOrNull()?.let { topPt = it / TWIP_PER_PT }
            margin.attr("bottom")?.trim()?.toFloatOrNull()?.let { bottomPt = it / TWIP_PER_PT }
            margin.attr("left")?.trim()?.toFloatOrNull()?.let { leftPt = it / TWIP_PER_PT }
            margin.attr("right")?.trim()?.toFloatOrNull()?.let { rightPt = it / TWIP_PER_PT }
        }

        return SectionInfo(widthPt, heightPt, topPt, bottomPt, leftPt, rightPt)
    }

    // ------------------------------------------------------------- paragraphs

    private fun parseParagraph(
        paragraph: OoxmlNode,
        styles: StyleTable,
        numbering: NumberingTable,
        counters: HashMap<String, IntArray>,
        pkg: OoxmlPackage,
    ): DocxBlock.Paragraph {
        val pPr = paragraph.child("pPr")
        val rawStyleId = pPr?.child("pStyle")?.attr("val")?.trim()
        val style = styles.info(rawStyleId)
        val styleId = style?.canonicalId ?: rawStyleId
        val headingLevel = style?.headingLevel ?: 0
        val isTitle = styleId == "Title"

        val paraProps = mergePara(style?.para ?: styles.defaultPara, pPr)

        val numPr = pPr?.child("numPr")
        val numId = numPr?.child("numId")?.attr("val")?.trim() ?: paraProps.numId
        val ilvl = numPr?.child("ilvl")?.attr("val")?.trim()?.toIntOrNull()
            ?: if (numPr == null) paraProps.listLevel else 0
        val listLabel = if (numId != null) numbering.labelFor(numId, ilvl, counters) else null

        val runs = parseRuns(
            paragraph = paragraph,
            styles = styles,
            style = style,
            dropStyleSize = isTitle || headingLevel > 0,
            pkg = pkg,
        )

        val alignment = paraProps.alignment ?: if (isTitle) "center" else "left"
        val indentLevel = max(
            if (numId != null) ilvl + 1 else 0,
            (paraProps.indentLeftPt / 24f).roundToInt().coerceAtLeast(0),
        )

        return DocxBlock.Paragraph(
            runs = runs,
            styleId = styleId,
            headingLevel = headingLevel,
            alignment = alignment,
            indentLevel = indentLevel,
            listLabel = listLabel,
            spaceBeforePt = paraProps.spaceBeforePt ?: 0f,
            spaceAfterPt = paraProps.spaceAfterPt ?: 0f,
            lineSpacing = paraProps.lineSpacing ?: 1f,
            pageBreakBefore = pPr?.child("pageBreakBefore")?.let { onOff(it) ?: true } ?: false,
        )
    }

    /** A paragraph that only holds a picture becomes a standalone image block. */
    private fun standaloneImageOf(paragraph: DocxBlock.Paragraph): DocxBlock.Image? {
        val picture = paragraph.runs.firstOrNull { it.imageBytes != null } ?: return null
        val hasText = paragraph.runs.any { it.text.isNotBlank() }
        if (hasText) return null
        return DocxBlock.Image(
            bytes = picture.imageBytes ?: return null,
            widthPt = picture.imageWidthPt,
            heightPt = picture.imageHeightPt,
            alignment = paragraph.alignment,
        )
    }

    private fun parseRuns(
        paragraph: OoxmlNode,
        styles: StyleTable,
        style: StyleInfo?,
        dropStyleSize: Boolean,
        pkg: OoxmlPackage,
    ): List<DocxRun> {
        val result = ArrayList<DocxRun>()

        collectRunNodes(paragraph).forEach { runNode ->
            val rPr = runNode.child("rPr")
            val charStyle = styles.info(rPr?.child("rStyle")?.attr("val")?.trim())
            var base = style?.run ?: styles.defaultRun
            if (dropStyleSize) base = base.copy(sizePt = null)
            if (charStyle != null) base = mergeRunProps(base, charStyle.run)
            val props = mergeRun(base, rPr)

            val text = StringBuilder()
            var image: ParsedImage? = null
            runNode.nodes().forEach { child ->
                when (child.localName) {
                    "t" -> text.append(child.text)
                    "tab" -> text.append('\t')
                    "br", "cr" -> text.append('\n')
                    "noBreakHyphen" -> text.append('-')
                    "drawing" -> if (image == null) image = parseDrawing(child, pkg)
                    "pict" -> if (image == null) image = parsePicture(child, pkg)
                }
            }

            val content = text.toString()
            if (content.isNotEmpty()) {
                result += DocxRun(
                    text = content,
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

            image?.let { picture ->
                result += DocxRun(
                    text = "",
                    bold = props.bold == true,
                    italic = props.italic == true,
                    color = props.color,
                    sizePt = props.sizePt,
                    fontFamily = props.fontFamily,
                    imageBytes = picture.bytes,
                    imageWidthPt = picture.widthPt,
                    imageHeightPt = picture.heightPt,
                )
            }
        }

        return result
    }

    private fun collectRunNodes(paragraph: OoxmlNode): List<OoxmlNode> {
        val result = ArrayList<OoxmlNode>()

        fun walk(node: OoxmlNode, depth: Int) {
            if (depth > 5) return
            node.nodes().forEach { child ->
                when (child.localName) {
                    "r" -> result += child
                    "hyperlink", "ins", "del", "moveTo", "moveFrom",
                    "smartTag", "sdt", "sdtContent", "bdo", "dir",
                    -> walk(child, depth + 1)
                }
            }
        }

        walk(paragraph, 0)
        return result
    }

    // ----------------------------------------------------------------- images

    private data class ParsedImage(
        val bytes: ByteArray,
        val widthPt: Float,
        val heightPt: Float,
    )

    private fun parseDrawing(drawing: OoxmlNode, pkg: OoxmlPackage): ParsedImage? {
        val container = drawing.descendants("inline").firstOrNull()
            ?: drawing.descendants("anchor").firstOrNull()
            ?: drawing
        val extent = container.descendants("extent").firstOrNull()
        val cx = extent?.attr("cx")?.trim()?.toDoubleOrNull() ?: 0.0
        val cy = extent?.attr("cy")?.trim()?.toDoubleOrNull() ?: 0.0
        val blip = container.descendants("blip").firstOrNull {
            it.attr("embed") != null || it.attr("link") != null
        } ?: container.descendants("blip").firstOrNull()
        val embed = blip?.attr("embed") ?: blip?.attr("link") ?: return null
        val bytes = readPart(pkg, pkg.resolveRelationship("word/document.xml", embed)) ?: return null
        return ParsedImage(bytes, (cx / EMU_PER_PT).toFloat(), (cy / EMU_PER_PT).toFloat())
    }

    private fun parsePicture(picture: OoxmlNode, pkg: OoxmlPackage): ParsedImage? {
        val data = picture.descendants("imagedata").firstOrNull() ?: return null
        val id = data.attr("id") ?: data.attr("href") ?: return null
        val bytes = readPart(pkg, pkg.resolveRelationship("word/document.xml", id)) ?: return null
        val style = data.attr("style").orEmpty()
        val width = styleDimension(style, "width")
        val height = styleDimension(style, "height")
        return ParsedImage(bytes, width, height)
    }

    private fun styleDimension(style: String, key: String): Float {
        val match = Regex("$key\\s*:\\s*([0-9.]+)\\s*pt").find(style) ?: return 0f
        return match.groupValues[1].toFloatOrNull() ?: 0f
    }

    private fun readPart(pkg: OoxmlPackage, part: String?): ByteArray? {
        if (part.isNullOrBlank()) return null
        val candidates = LinkedHashSet<String>()
        candidates += part
        if (!part.startsWith("word/")) candidates += "word/$part"
        if (part.startsWith("word/")) candidates += part.removePrefix("word/")
        candidates.forEach { candidate ->
            pkg.bytes(candidate)?.let { return it }
        }
        return null
    }

    // ----------------------------------------------------------------- tables

    private fun parseTable(
        table: OoxmlNode,
        styles: StyleTable,
        numbering: NumberingTable,
        counters: HashMap<String, IntArray>,
        pkg: OoxmlPackage,
    ): DocxBlock.Table {
        val gridWidths = table.child("tblGrid")
            ?.children("gridCol")
            ?.map { (it.attr("w")?.trim()?.toFloatOrNull() ?: 0f) / TWIP_PER_PT }
            ?.filter { it > 0f }
            ?: emptyList()

        val rows = ArrayList<List<DocxCell>>()
        table.children("tr").forEach { row ->
            val cells = ArrayList<DocxCell>()
            var column = 0
            row.children("tc").forEach { cell ->
                val parsed = parseCell(cell, gridWidths, column, styles, numbering, counters, pkg)
                cells += parsed
                column += parsed.columnSpan
            }
            rows.add(cells)
        }

        var widths = gridWidths
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

    private fun parseCell(
        cell: OoxmlNode,
        gridWidths: List<Float>,
        column: Int,
        styles: StyleTable,
        numbering: NumberingTable,
        counters: HashMap<String, IntArray>,
        pkg: OoxmlPackage,
    ): DocxCell {
        val tcPr = cell.child("tcPr")
        val span = tcPr?.child("gridSpan")
            ?.attr("val")
            ?.trim()
            ?.toIntOrNull()
            ?.coerceAtLeast(1) ?: 1
        val background = tcPr?.child("shd")
            ?.attr("fill")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("auto", true) }
            ?.let { OfficeColors.parseHex(it) }
        val verticalAlignment = when (tcPr?.child("vAlign")?.attr("val")?.trim()?.lowercase()) {
            "center" -> "center"
            "bottom" -> "bottom"
            else -> "top"
        }

        var widthPt = 0f
        if (gridWidths.isNotEmpty()) {
            for (index in column until minOf(column + span, gridWidths.size)) {
                widthPt += gridWidths[index]
            }
        }
        if (widthPt <= 0f) {
            widthPt = tcPr?.child("tcW")
                ?.attr("w")
                ?.trim()
                ?.toFloatOrNull()
                ?.let { it / TWIP_PER_PT } ?: 0f
        }

        return DocxCell(
            blocks = parseBlocks(cell, styles, numbering, counters, pkg),
            widthPt = widthPt,
            background = background,
            verticalAlignment = verticalAlignment,
            columnSpan = span,
        )
    }

    private fun parseBlocks(
        container: OoxmlNode,
        styles: StyleTable,
        numbering: NumberingTable,
        counters: HashMap<String, IntArray>,
        pkg: OoxmlPackage,
    ): List<DocxBlock> {
        val blocks = ArrayList<DocxBlock>()
        container.nodes().forEach { child ->
            when (child.localName) {
                "p" -> blocks += parseParagraph(child, styles, numbering, counters, pkg)
                "tbl" -> blocks += parseTable(child, styles, numbering, counters, pkg)
            }
        }
        return blocks
    }

    // ------------------------------------------------------------------ styles

    private data class RawStyle(
        val id: String,
        val name: String,
        val basedOn: String?,
        val runNode: OoxmlNode?,
        val paraNode: OoxmlNode?,
    )

    private data class StyleInfo(
        val id: String,
        val canonicalId: String,
        val name: String,
        val basedOn: String?,
        val run: RunProps,
        val para: ParaProps,
        val headingLevel: Int,
    )

    private data class RunProps(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val strike: Boolean? = null,
        val superscript: Boolean? = null,
        val color: OfficeColor? = null,
        val sizePt: Float? = null,
        val fontFamily: String? = null,
    )

    private data class ParaProps(
        val alignment: String? = null,
        val spaceBeforePt: Float? = null,
        val spaceAfterPt: Float? = null,
        val lineSpacing: Float? = null,
        val indentLeftPt: Float = 0f,
        val indentFirstLinePt: Float = 0f,
        /** Numbering id inherited from the paragraph style (w:numPr/w:numId). */
        val numId: String? = null,
        val listLevel: Int = 0,
    )

    private class StyleTable(
        private val raw: Map<String, RawStyle>,
        val defaultRun: RunProps,
        val defaultPara: ParaProps,
    ) {
        private val cache = HashMap<String, StyleInfo>()

        fun info(styleId: String?): StyleInfo? {
            if (styleId.isNullOrBlank()) return null
            cache[styleId]?.let { return it }
            return resolve(styleId, HashSet())
        }

        private fun resolve(id: String, seen: MutableSet<String>): StyleInfo? {
            cache[id]?.let { return it }
            val entry = raw[id] ?: return null
            if (!seen.add(id)) return null
            val base = entry.basedOn?.let { resolve(it, seen) }
            val run = mergeRunProps(base?.run ?: defaultRun, mergeRun(RunProps(), entry.runNode))
            val para = mergeParaProps(base?.para ?: defaultPara, mergePara(ParaProps(), entry.paraNode))
            val canonical = canonicalId(entry.id, entry.name)
            val info = StyleInfo(
                id = entry.id,
                canonicalId = canonical,
                name = entry.name,
                basedOn = entry.basedOn,
                run = run,
                para = para,
                headingLevel = headingLevelOf(canonical, entry.name),
            )
            cache[id] = info
            return info
        }

        companion object {
            fun parse(node: OoxmlNode?): StyleTable {
                val raw = HashMap<String, RawStyle>()
                var defaultRun = RunProps()
                var defaultPara = ParaProps()

                node?.children("docDefaults")?.firstOrNull()?.let { defaults ->
                    defaults.child("rPrDefault")?.child("rPr")?.let { defaultRun = mergeRun(RunProps(), it) }
                    defaults.child("pPrDefault")?.child("pPr")?.let { defaultPara = mergePara(ParaProps(), it) }
                }

                node?.children("style")?.forEach { style ->
                    val id = style.attr("styleId")?.trim() ?: return@forEach
                    raw[id] = RawStyle(
                        id = id,
                        name = style.child("name")?.attr("val")?.trim().orEmpty(),
                        basedOn = style.child("basedOn")?.attr("val")?.trim(),
                        runNode = style.child("rPr"),
                        paraNode = style.child("pPr"),
                    )
                }

                return StyleTable(raw, defaultRun, defaultPara)
            }
        }
    }

    private fun mergeRun(base: RunProps, node: OoxmlNode?): RunProps {
        if (node == null) return base
        var bold = base.bold
        var italic = base.italic
        var underline = base.underline
        var strike = base.strike
        var superscript = base.superscript
        var color = base.color
        var sizePt = base.sizePt
        var fontFamily = base.fontFamily

        node.child("b")?.let { onOff(it)?.let { value -> bold = value } }
        node.child("i")?.let { onOff(it)?.let { value -> italic = value } }
        node.child("u")?.let { underline = it.attr("val")?.trim()?.lowercase() != "none" }
        node.child("strike")?.let { onOff(it)?.let { value -> strike = value } }
        node.child("dstrike")?.let { onOff(it)?.let { value -> strike = value } }
        node.child("vertAlign")?.let {
            superscript = it.attr("val")?.trim()?.lowercase() == "superscript"
        }
        node.child("color")?.let {
            val value = it.attr("val")?.trim()
            color = if (value.isNullOrEmpty() || value.equals("auto", true)) null else OfficeColors.parseHex(value)
        }
        node.child("sz")?.let {
            sizePt = it.attr("val")?.trim()?.toFloatOrNull()?.let { value -> value / 2f }
        }
        node.child("szCs")?.let {
            if (sizePt == null) {
                sizePt = it.attr("val")?.trim()?.toFloatOrNull()?.let { value -> value / 2f }
            }
        }
        node.child("rFonts")?.let {
            fontFamily = it.attr("ascii")
                ?: it.attr("hAnsi")
                ?: it.attr("eastAsia")
                ?: fontFamily
        }

        return RunProps(bold, italic, underline, strike, superscript, color, sizePt, fontFamily)
    }

    private fun mergeRunProps(base: RunProps, other: RunProps): RunProps = RunProps(
        bold = other.bold ?: base.bold,
        italic = other.italic ?: base.italic,
        underline = other.underline ?: base.underline,
        strike = other.strike ?: base.strike,
        superscript = other.superscript ?: base.superscript,
        color = other.color ?: base.color,
        sizePt = other.sizePt ?: base.sizePt,
        fontFamily = other.fontFamily ?: base.fontFamily,
    )

    private fun mergePara(base: ParaProps, node: OoxmlNode?): ParaProps {
        if (node == null) return base
        var alignment = base.alignment
        var spaceBefore = base.spaceBeforePt
        var spaceAfter = base.spaceAfterPt
        var lineSpacing = base.lineSpacing
        var indentLeft = base.indentLeftPt
        var indentFirstLine = base.indentFirstLinePt
        var inheritedNumId = base.numId
        var inheritedLevel = base.listLevel

        node.child("jc")?.let { normalizeAlignment(it.attr("val"))?.let { value -> alignment = value } }
        node.child("spacing")?.let { spacing ->
            spacing.attr("before")?.trim()?.toFloatOrNull()?.let { spaceBefore = it / TWIP_PER_PT }
            spacing.attr("after")?.trim()?.toFloatOrNull()?.let { spaceAfter = it / TWIP_PER_PT }
            spacing.attr("line")?.trim()?.toFloatOrNull()?.let { value ->
                val rule = spacing.attr("lineRule")?.trim()?.lowercase()
                lineSpacing = if (rule == "exact" || rule == "atLeast") {
                    (value / 240f) * 0.85f
                } else {
                    value / 240f
                }
            }
        }
        node.child("ind")?.let { indent ->
            indent.attr("left")?.trim()?.toFloatOrNull()?.let { indentLeft = it / TWIP_PER_PT }
            indent.attr("start")?.trim()?.toFloatOrNull()?.let { indentLeft = it / TWIP_PER_PT }
            indent.attr("firstLine")?.trim()?.toFloatOrNull()?.let { indentFirstLine = it / TWIP_PER_PT }
            indent.attr("hanging")?.trim()?.toFloatOrNull()?.let { indentFirstLine = -it / TWIP_PER_PT }
        }

        val numPr = node?.child("numPr")
        val numId = numPr?.child("numId")?.attr("val")?.trim() ?: inheritedNumId
        val listLevel = numPr?.child("ilvl")?.attr("val")?.trim()?.toIntOrNull()
            ?: if (numPr == null) inheritedLevel else 0
        return ParaProps(
            alignment,
            spaceBefore,
            spaceAfter,
            lineSpacing,
            indentLeft,
            indentFirstLine,
            numId,
            listLevel,
        )
    }

    private fun mergeParaProps(base: ParaProps, other: ParaProps): ParaProps = ParaProps(
        alignment = other.alignment ?: base.alignment,
        spaceBeforePt = other.spaceBeforePt ?: base.spaceBeforePt,
        spaceAfterPt = other.spaceAfterPt ?: base.spaceAfterPt,
        lineSpacing = other.lineSpacing ?: base.lineSpacing,
        indentLeftPt = max(base.indentLeftPt, other.indentLeftPt),
        indentFirstLinePt = other.indentFirstLinePt,
        numId = other.numId ?: base.numId,
        listLevel = if (other.numId != null) other.listLevel else base.listLevel,
    )

    private fun onOff(node: OoxmlNode): Boolean? {
        val value = node.attr("val")?.trim()?.lowercase() ?: return true
        return value !in setOf("0", "false", "off")
    }

    private fun normalizeAlignment(value: String?): String? = when (value?.trim()?.lowercase()) {
        "left", "start" -> "left"
        "center" -> "center"
        "right", "end" -> "right"
        "both", "distribute", "justify" -> "justify"
        else -> null
    }

    private val headingRegex = Regex("heading\\s*([1-6])")
    private val headingCnRegex = Regex("标题\\s*([1-6])")

    private fun headingNumberOf(value: String): Int? {
        headingRegex.find(value)?.let { return it.groupValues[1].toIntOrNull() }
        headingCnRegex.find(value)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    private fun canonicalId(id: String, name: String): String {
        val lowerName = name.lowercase().trim()
        val lowerId = id.lowercase().replace(" ", "")
        val heading = headingNumberOf(lowerName) ?: headingNumberOf(lowerId)
        if (heading != null) return "Heading$heading"
        if (lowerName == "title" || lowerName == "标题" || lowerId == "title") return "Title"
        if (lowerName == "subtitle" || lowerName == "副标题" || lowerId == "subtitle") return "Subtitle"
        return id
    }

    private fun headingLevelOf(canonical: String, name: String): Int {
        if (canonical.startsWith("Heading")) {
            canonical.removePrefix("Heading").toIntOrNull()?.let { return it.coerceIn(1, 6) }
        }
        return headingNumberOf(name.lowercase().trim()) ?: 0
    }

    // -------------------------------------------------------------- numbering

    private data class LevelInfo(
        val ilvl: Int,
        val numFmt: String,
        val lvlText: String,
        val start: Int,
    )

    private class NumberingTable(
        private val abstracts: Map<String, List<LevelInfo>>,
        private val numToAbstract: Map<String, String>,
        private val startOverrides: Map<String, Map<Int, Int>>,
    ) {
        fun labelFor(numId: String, ilvl: Int, counters: HashMap<String, IntArray>): String? {
            val abstractId = numToAbstract[numId] ?: return null
            val levels = abstracts[abstractId] ?: return null
            val level = levels.firstOrNull { it.ilvl == ilvl } ?: return null
            if (level.numFmt == "none") return null

            val counter = counters.getOrPut(numId) {
                val array = IntArray(MAX_LIST_LEVEL)
                levels.forEach { entry ->
                    val override = startOverrides[numId]?.get(entry.ilvl)
                    val start = override ?: entry.start
                    if (entry.ilvl in array.indices) {
                        array[entry.ilvl] = (start - 1).coerceAtLeast(0)
                    }
                }
                array
            }
            if (ilvl !in counter.indices) return null
            counter[ilvl] += 1
            for (index in ilvl + 1 until counter.size) counter[index] = 0

            if (level.numFmt == "bullet") return bulletChar(ilvl)

            val pattern = level.lvlText.ifEmpty { "%1." }
            return Regex("%(\\d)").replace(pattern) { match ->
                val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: 0
                counter.getOrElse(index) { 0 }.toString()
            }
        }

        companion object {
            fun parse(node: OoxmlNode?): NumberingTable {
                val abstracts = HashMap<String, List<LevelInfo>>()
                val numToAbstract = HashMap<String, String>()
                val startOverrides = HashMap<String, Map<Int, Int>>()

                node?.children("abstractNum")?.forEach { abstract ->
                    val id = abstract.attr("abstractNumId")?.trim() ?: return@forEach
                    val levels = abstract.children("lvl").map { level ->
                        LevelInfo(
                            ilvl = level.attr("ilvl")?.trim()?.toIntOrNull() ?: 0,
                            numFmt = level.child("numFmt")?.attr("val")?.trim()?.lowercase() ?: "decimal",
                            lvlText = level.child("lvlText")?.attr("val").orEmpty(),
                            start = level.child("start")?.attr("val")?.trim()?.toIntOrNull() ?: 1,
                        )
                    }
                    abstracts[id] = levels
                }

                node?.children("num")?.forEach { num ->
                    val numId = num.attr("numId")?.trim() ?: return@forEach
                    numToAbstract[numId] = num.child("abstractNumId")?.attr("val")?.trim().orEmpty()
                    val overrides = HashMap<Int, Int>()
                    num.children("lvlOverride").forEach { lvlOverride ->
                        val ilvl = lvlOverride.attr("ilvl")?.trim()?.toIntOrNull() ?: 0
                        lvlOverride.child("startOverride")
                            ?.attr("val")
                            ?.trim()
                            ?.toIntOrNull()
                            ?.let { overrides[ilvl] = it }
                    }
                    if (overrides.isNotEmpty()) startOverrides[numId] = overrides
                }

                return NumberingTable(abstracts, numToAbstract, startOverrides)
            }
        }
    }
}

private fun DocxBlock.Paragraph.plainText(): String = runs.joinToString(separator = "") { it.text }
