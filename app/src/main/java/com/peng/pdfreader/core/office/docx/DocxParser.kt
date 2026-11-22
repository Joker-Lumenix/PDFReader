package com.peng.pdfreader.core.office.docx

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.OoxmlPackage
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.XmlNode
import com.peng.pdfreader.core.util.Logger
import kotlin.math.max
import kotlin.math.roundToInt

private const val TWIP_PER_PT = 20f
private const val EMU_PER_PT = 12700f
private const val MAX_LIST_LEVEL = 9

/**
 * The number of blocks (paragraphs and tables) that are parsed.
 *
 * The limit is the safety net for a document that does not fit into the
 * memory of the phone. It is high enough for the documents of a normal
 * report, so the content of a document is complete.
 */
internal const val MAX_BLOCKS = 20000

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
 * lay the page out the way Word does.
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
    /** True when the document was too big and was cut off. */
    val truncated: Boolean = false,
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
        /**
         * The number of the paragraph in the document.
         *
         * The editor needs it to write a changed text back into the file: the
         * number is the same one the editor counts the paragraphs of the
         * document with.
         */
        val sourceIndex: Int = -1,
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
    /**
     * How the cell is merged with the cells below it.
     *
     * It is "restart" for the first cell of a merge and "continue" for the
     * cells that belong to it. The reader uses it to find the cells that
     * stand over the rows below them.
     */
    val vMerge: String = "",
    /**
     * The number of the rows the cell stands over.
     *
     * A cell that is merged with the cells below it stands over all of them.
     * A cell that belongs to the cell above it has the number zero, the
     * reader does not draw it.
     */
    val rowSpan: Int = 1,
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
 * The body of the document is read with a streaming parser, so the tree of a
 * big document is never built and the memory of the phone stays flat. The
 * small parts - the styles, the numbering and the theme - are read as a tree,
 * they are used again and again.
 *
 * The function is pure and blocking, so the caller has to run it off the main
 * thread. Every lookup is namespace agnostic (only local names are compared),
 * which keeps the parser working for the many Word dialects.
 */
object DocxParser {

    private const val TAG = "DocxReader"

    fun parse(pkg: OoxmlPackage): DocxDocument? {
        val styles = DocxStyleTable.parse(pkg.node("word/styles.xml"))
        val numbering = DocxNumberingTable.parse(pkg.node("word/numbering.xml"))
        val images = DocxImageReader(pkg)

        val reader = DocxBodyReader(styles, numbering, images)
        val stream = pkg.stream("word/document.xml")
        val parsed = if (stream != null) {
            val read = stream.use { reader.read(it) }
            // The platforms whose streaming parser is not available read the
            // document out of a tree instead.
            if (read) true else reader.readTree(pkg.node("word/document.xml"))
        } else {
            reader.readTree(pkg.node("word/document.xml"))
        }
        if (!parsed) return null

        Logger.d(TAG, "the document has ${reader.blocks.size} blocks, truncated=${reader.truncated}")

        val section = reader.section ?: DocxSection()
        val title = pkg.node("docProps/core.xml")
            ?.descendants("title")
            ?.firstOrNull()
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: reader.fallbackTitle
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
            blocks = reader.blocks,
            truncated = reader.truncated,
        )
    }
}

/**
 * The section of the document: the size of the page and its margins.
 */
internal data class DocxSection(
    val widthPt: Float = 595f,
    val heightPt: Float = 842f,
    val topPt: Float = 72f,
    val bottomPt: Float = 72f,
    val leftPt: Float = 72f,
    val rightPt: Float = 72f,
) {
    companion object {

        /** Reads the section out of a `w:sectPr` element. */
        fun read(node: XmlNode?): DocxSection {
            if (node == null) return DocxSection()
            var widthPt = 595f
            var heightPt = 842f
            var topPt = 72f
            var bottomPt = 72f
            var leftPt = 72f
            var rightPt = 72f

            node.child("pgSz")?.let { size ->
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

            node.child("pgMar")?.let { margin ->
                margin.attr("top")?.trim()?.toFloatOrNull()?.let { topPt = it / TWIP_PER_PT }
                margin.attr("bottom")?.trim()?.toFloatOrNull()?.let { bottomPt = it / TWIP_PER_PT }
                margin.attr("left")?.trim()?.toFloatOrNull()?.let { leftPt = it / TWIP_PER_PT }
                margin.attr("right")?.trim()?.toFloatOrNull()?.let { rightPt = it / TWIP_PER_PT }
            }

            return DocxSection(widthPt, heightPt, topPt, bottomPt, leftPt, rightPt)
        }
    }
}

/**
 * The pictures of a document.
 *
 * The bytes of the pictures are the biggest part of a Word document, so only
 * a limited amount of them is kept in the memory of the phone.
 */
internal class DocxImageReader(private val pkg: OoxmlPackage) {

    private var remaining = MAX_IMAGE_BYTES

    /** The bytes of the picture with the given relationship id. */
    fun bytes(relationshipId: String): ByteArray? {
        if (remaining <= 0) return null
        val part = pkg.resolveRelationship("word/document.xml", relationshipId) ?: return null
        val bytes = readPart(pkg, part) ?: return null
        remaining -= bytes.size
        return bytes
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

    private companion object {
        /** The amount of the picture bytes that are kept in the memory. */
        const val MAX_IMAGE_BYTES = 24 * 1024 * 1024
    }
}

// ------------------------------------------------------------------- styles

internal data class DocxRunProps(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val superscript: Boolean? = null,
    val color: OfficeColor? = null,
    val sizePt: Float? = null,
    val fontFamily: String? = null,
)

internal data class DocxParaProps(
    val alignment: String? = null,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val lineSpacing: Float? = null,
    val indentLeftPt: Float = 0f,
    val indentFirstLinePt: Float = 0f,
    /** The numbering id inherited from the paragraph style. */
    val numId: String? = null,
    val listLevel: Int = 0,
)

internal class DocxStyleTable(
    private val raw: Map<String, RawStyle>,
    val defaultRun: DocxRunProps,
    val defaultPara: DocxParaProps,
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
        val run = mergeRunProps(base?.run ?: defaultRun, mergeRun(DocxRunProps(), entry.runNode))
        val para = mergeParaProps(base?.para ?: defaultPara, mergePara(DocxParaProps(), entry.paraNode))
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

    internal data class RawStyle(
        val id: String,
        val name: String,
        val basedOn: String?,
        val runNode: XmlNode?,
        val paraNode: XmlNode?,
    )

    internal data class StyleInfo(
        val id: String,
        val canonicalId: String,
        val name: String,
        val basedOn: String?,
        val run: DocxRunProps,
        val para: DocxParaProps,
        val headingLevel: Int,
    )

    companion object {
        fun parse(node: XmlNode?): DocxStyleTable {
            val raw = HashMap<String, RawStyle>()
            var defaultRun = DocxRunProps()
            var defaultPara = DocxParaProps()

            node?.children("docDefaults")?.firstOrNull()?.let { defaults ->
                defaults.child("rPrDefault")?.child("rPr")?.let { defaultRun = mergeRun(DocxRunProps(), it) }
                defaults.child("pPrDefault")?.child("pPr")?.let { defaultPara = mergePara(DocxParaProps(), it) }
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

            return DocxStyleTable(raw, defaultRun, defaultPara)
        }
    }
}

internal fun mergeRun(base: DocxRunProps, node: XmlNode?): DocxRunProps {
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

    return DocxRunProps(bold, italic, underline, strike, superscript, color, sizePt, fontFamily)
}

internal fun mergeRunProps(base: DocxRunProps, other: DocxRunProps): DocxRunProps = DocxRunProps(
    bold = other.bold ?: base.bold,
    italic = other.italic ?: base.italic,
    underline = other.underline ?: base.underline,
    strike = other.strike ?: base.strike,
    superscript = other.superscript ?: base.superscript,
    color = other.color ?: base.color,
    sizePt = other.sizePt ?: base.sizePt,
    fontFamily = other.fontFamily ?: base.fontFamily,
)

internal fun mergePara(base: DocxParaProps, node: XmlNode?): DocxParaProps {
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

    val numPr = node.child("numPr")
    val numId = numPr?.child("numId")?.attr("val")?.trim() ?: inheritedNumId
    val listLevel = numPr?.child("ilvl")?.attr("val")?.trim()?.toIntOrNull()
        ?: if (numPr == null) inheritedLevel else 0
    return DocxParaProps(
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

internal fun mergeParaProps(base: DocxParaProps, other: DocxParaProps): DocxParaProps = DocxParaProps(
    alignment = other.alignment ?: base.alignment,
    spaceBeforePt = other.spaceBeforePt ?: base.spaceBeforePt,
    spaceAfterPt = other.spaceAfterPt ?: base.spaceAfterPt,
    lineSpacing = other.lineSpacing ?: base.lineSpacing,
    indentLeftPt = max(base.indentLeftPt, other.indentLeftPt),
    indentFirstLinePt = other.indentFirstLinePt,
    numId = other.numId ?: base.numId,
    listLevel = if (other.numId != null) other.listLevel else base.listLevel,
)

internal fun onOff(node: XmlNode): Boolean? {
    val value = node.attr("val")?.trim()?.lowercase() ?: return true
    return value !in setOf("0", "false", "off")
}

internal fun normalizeAlignment(value: String?): String? = when (value?.trim()?.lowercase()) {
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

// --------------------------------------------------------------- numbering

internal data class DocxLevelInfo(
    val ilvl: Int,
    val numFmt: String,
    val lvlText: String,
    val start: Int,
)

internal class DocxNumberingTable(
    private val abstracts: Map<String, List<DocxLevelInfo>>,
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
        fun parse(node: XmlNode?): DocxNumberingTable {
            val abstracts = HashMap<String, List<DocxLevelInfo>>()
            val numToAbstract = HashMap<String, String>()
            val startOverrides = HashMap<String, Map<Int, Int>>()

            node?.children("abstractNum")?.forEach { abstract ->
                val id = abstract.attr("abstractNumId")?.trim() ?: return@forEach
                val levels = abstract.children("lvl").map { level ->
                    DocxLevelInfo(
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

            return DocxNumberingTable(abstracts, numToAbstract, startOverrides)
        }
    }
}

/** The points per EMU, the pictures of a document are measured in EMU. */
internal const val DOCX_EMU_PER_PT = EMU_PER_PT
