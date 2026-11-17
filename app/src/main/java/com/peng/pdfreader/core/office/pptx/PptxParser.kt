package com.peng.pdfreader.core.office.pptx

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.OoxmlNode
import com.peng.pdfreader.core.office.OoxmlPackage
import com.peng.pdfreader.core.office.OfficeColors

/**
 * Model of a PowerPoint (PresentationML) deck.
 *
 * All the geometry is expressed in "deck dp": 1 dp = 9525 EMU, so a 16:9
 * deck of 12192000 x 6858000 EMU becomes 1280 x 720 dp.
 */
data class PptxDeck(
    val slides: List<PptxSlide>,
    val widthDp: Float,
    val heightDp: Float,
    val title: String,
)

data class PptxSlide(
    val index: Int,
    val shapes: List<PptxShape>,
    val background: OfficeColor?,
    val notes: String?,
)

sealed interface PptxShape {
    val leftDp: Float
    val topDp: Float
    val widthDp: Float
    val heightDp: Float

    data class TextBox(
        val paragraphs: List<PptxParagraph>,
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
        val anchor: String,
        val fill: OfficeColor?,
    ) : PptxShape

    data class Picture(
        val bytes: ByteArray,
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
    ) : PptxShape

    data class Rectangle(
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
        val fill: OfficeColor?,
        val line: OfficeColor?,
        val lineWidthDp: Float,
        val preset: String,
        val rotation: Float,
    ) : PptxShape

    data class Table(
        val rows: List<List<PptxTableCell>>,
        val columnWidthsDp: List<Float>,
        val rowHeightsDp: List<Float>,
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
    ) : PptxShape
}

data class PptxParagraph(
    val text: String,
    val bullet: String?,
    val level: Int,
    val style: PptxTextStyle,
)

data class PptxTextStyle(
    val sizePt: Float,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val color: OfficeColor?,
    val alignment: String,
    val fontFamily: String?,
)

data class PptxTableCell(
    val text: String,
    val bold: Boolean,
    val color: OfficeColor?,
    val background: OfficeColor?,
    val alignment: String,
)

/**
 * Plain (non Composable) PresentationML parser.
 *
 * The caller is expected to run it off the main thread: the whole package is
 * read from the ZIP container and every part is parsed with the JDK DOM.
 */
object PptxParser {

    private const val DEFAULT_SLIDE_CX = 12192000L
    private const val DEFAULT_SLIDE_CY = 6858000L
    private const val TITLE_SIZE_PT = 44f
    private const val SUBTITLE_SIZE_PT = 28f
    private const val BODY_SIZE_PT = 18f
    private const val DEFAULT_LINE_WIDTH_DP = 1f
    private const val EMU_PER_DP = 9525f

    /** A placeholder without a type is a body placeholder. */
    private const val DEFAULT_PLACEHOLDER = "body"

    /** The height of a table row that does not declare one. */
    private const val DEFAULT_ROW_HEIGHT_DP = 24f

    /**
     * The number of slides that are parsed.
     *
     * A big presentation would not fit into the memory of the phone, the
     * preview shows the first slides of it.
     */
    private const val MAX_SLIDES = 200

    fun parse(pkg: OoxmlPackage): PptxDeck? {
        val presentation = pkg.node("ppt/presentation.xml") ?: return null
        val theme = OfficeColors.readTheme(pkg)
        val fonts = readThemeFonts(pkg)
        val sizeNode = presentation.child("sldSz")
            ?: presentation.descendants("sldSz").firstOrNull()
        val cx = sizeNode?.attrLong("cx") ?: DEFAULT_SLIDE_CX
        val cy = sizeNode?.attrLong("cy") ?: DEFAULT_SLIDE_CY
        val slides = parseSlides(pkg, presentation, theme, fonts)
        if (slides.isEmpty()) return null
        return PptxDeck(
            slides = slides,
            widthDp = cx.toDp(),
            heightDp = cy.toDp(),
            title = readTitle(pkg),
        )
    }

    // ---------------------------------------------------------------- slides

    private fun parseSlides(
        pkg: OoxmlPackage,
        presentation: OoxmlNode,
        theme: Map<String, OfficeColor>,
        fonts: ThemeFonts,
    ): List<PptxSlide> {
        val parts = ArrayList<String>()
        val idList = presentation.child("sldIdLst")
        if (idList != null) {
            idList.children("sldId").forEach { sldId ->
                val relId = sldId.relAttr("id") ?: return@forEach
                val part = pkg.resolveRelationship("ppt/presentation.xml", relId) ?: return@forEach
                if (part !in parts) parts += part
            }
        }
        if (parts.isEmpty()) {
            pkg.partNames()
                .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedBy { it.slideNumber() }
                .forEach { parts += it }
        }
        val result = ArrayList<PptxSlide>()
        var index = 0
        parts.forEach { part ->
            if (index >= MAX_SLIDES) return@forEach
            val node = pkg.node(part) ?: return@forEach
            index += 1
            result += parseSlide(pkg, part, node, index, theme, fonts)
        }
        return result
    }

    private fun parseSlide(
        pkg: OoxmlPackage,
        part: String,
        node: OoxmlNode,
        index: Int,
        theme: Map<String, OfficeColor>,
        fonts: ThemeFonts,
    ): PptxSlide {
        val context = SlideContext(pkg, part, theme, fonts)
        val shapes = ArrayList<PptxShape>()
        cSld(node)?.child("spTree")?.nodes()?.forEach { child ->
            parseNode(child, context, shapes, Transform.IDENTITY)
        }
        return PptxSlide(
            index = index,
            shapes = shapes,
            background = resolveBackground(cSld(node), context),
            notes = parseNotes(context),
        )
    }

    private fun cSld(node: OoxmlNode): OoxmlNode? = node.child("cSld")

    /**
     * Reads one node of the shape tree.
     *
     * A group carries its own coordinate space, so the shapes inside of it are
     * mapped into the space of the slide with the transform of the group.
     */
    private fun parseNode(
        node: OoxmlNode,
        context: SlideContext,
        shapes: MutableList<PptxShape>,
        transform: Transform,
    ) {
        when (node.localName) {
            "sp" -> shapes += parseShape(node, context, transform)
            "pic" -> parsePicture(node, context, transform)?.let { shapes += it }
            "graphicFrame" -> parseTable(node, context, transform)?.let { shapes += it }
            "grpSp" -> {
                // The group carries its own coordinate space in the group
                // properties: the offset, the extent and the child space.
                val group = groupTransform(node.child("grpSpPr")?.child("xfrm"))
                // The shape tree of a group repeats the group properties, the
                // children follow them.
                node.nodes().filter { it.localName == "sp" || it.localName == "pic" ||
                    it.localName == "graphicFrame" || it.localName == "grpSp"
                }.forEach { child ->
                    parseNode(child, context, shapes, transform.then(group))
                }
            }
        }
    }

    /**
     * The mapping of the children of a group into the space of the slide.
     *
     * The children live in their own coordinate space (`chOff` / `chExt`), the
     * group places that space at `off` and scales it to `ext`.
     */
    private fun groupTransform(xfrm: OoxmlNode?): Transform {
        if (xfrm == null) return Transform.IDENTITY
        val off = xfrm.child("off")
        val ext = xfrm.child("ext")
        val chOff = xfrm.child("chOff")
        val chExt = xfrm.child("chExt")
        val left = (off?.attrLong("x") ?: 0L).toDp()
        val top = (off?.attrLong("y") ?: 0L).toDp()
        val width = (ext?.attrLong("cx") ?: 0L).toDp()
        val height = (ext?.attrLong("cy") ?: 0L).toDp()
        val childLeft = (chOff?.attrLong("x") ?: 0L).toDp()
        val childTop = (chOff?.attrLong("y") ?: 0L).toDp()
        val childWidth = (chExt?.attrLong("cx") ?: 0L).toDp()
        val childHeight = (chExt?.attrLong("cy") ?: 0L).toDp()
        val scaleX = if (childWidth > 0f) width / childWidth else 1f
        val scaleY = if (childHeight > 0f) height / childHeight else 1f
        return Transform(
            offsetX = left - childLeft * scaleX,
            offsetY = top - childTop * scaleY,
            scaleX = scaleX,
            scaleY = scaleY,
        )
    }

    // ------------------------------------------------------------ background

    private fun resolveBackground(cSld: OoxmlNode?, context: SlideContext): OfficeColor? {
        val own = backgroundColor(cSld?.child("bg"), context.theme)
        if (own != null) return own
        val layout = backgroundColor(
            context.layoutNode?.child("cSld")?.child("bg"),
            context.theme,
        )
        if (layout != null) return layout
        val master = backgroundColor(
            context.masterNode?.child("cSld")?.child("bg"),
            context.theme,
        )
        return master ?: OfficeColor.White
    }

    private fun backgroundColor(bg: OoxmlNode?, theme: Map<String, OfficeColor>): OfficeColor? {
        if (bg == null) return null
        val props = bg.child("bgPr")
        if (props != null) {
            fillOf(props, theme)?.let { return it }
        }
        val ref = bg.child("bgRef")
        if (ref != null) {
            val color = ref.child("schemeClr")?.let { OfficeColors.colorOf(it, theme) }
            if (color != null) return color
        }
        return null
    }

    // --------------------------------------------------------------- shapes

    private fun parseShape(
        sp: OoxmlNode,
        context: SlideContext,
        transform: Transform,
    ): List<PptxShape> {
        val spPr = sp.child("spPr")
        val ph = sp.child("nvSpPr")?.child("nvPr")?.child("ph")
        // A placeholder without a type is a body placeholder.
        val phType = ph?.attr("type") ?: DEFAULT_PLACEHOLDER
        val phIdx = ph?.attr("idx")
        val layoutPh = matchPlaceholder(context.layoutPlaceholders, phType, phIdx)
        val masterPh = matchPlaceholder(context.masterPlaceholders, phType, phIdx)

        val xfrm = spPr?.child("xfrm")
            ?: layoutPh?.xfrm
            ?: masterPh?.xfrm
        // A shape without any geometry is rare, but it must not disappear.
        val off = xfrm?.child("off")
        val ext = xfrm?.child("ext")
        val left = transform.mapX((off?.attrLong("x") ?: 0L).toDp())
        val top = transform.mapY((off?.attrLong("y") ?: 0L).toDp())
        val width = transform.mapWidth((ext?.attrLong("cx") ?: 0L).toDp())
        val height = transform.mapHeight((ext?.attrLong("cy") ?: 0L).toDp())
        val rotation = (xfrm?.attrFloat("rot") ?: 0f) / 60000f
        val preset = spPr?.child("prstGeom")?.attr("prst") ?: "rect"
        val fill = fillOf(spPr, context.theme)
        val line = lineOf(spPr, context.theme)
        val lineWidth = lineWidthOf(spPr)

        val txBody = sp.child("txBody")
        val textContext = buildTextContext(sp, context, phType, layoutPh, masterPh)
        val paragraphs = txBody?.children("p")
            ?.map { parseParagraph(it, textContext) }
            .orEmpty()

        val result = ArrayList<PptxShape>()
        val hasGeometry = fill != null || line != null
        if (hasGeometry) {
            result += PptxShape.Rectangle(
                leftDp = left,
                topDp = top,
                widthDp = width,
                heightDp = height,
                fill = fill,
                line = line,
                lineWidthDp = lineWidth,
                preset = preset,
                rotation = rotation,
            )
        }
        if (paragraphs.isNotEmpty()) {
            val anchor = anchorName(txBody?.child("bodyPr")?.attr("anchor"))
            result += PptxShape.TextBox(
                paragraphs = paragraphs,
                leftDp = left,
                topDp = top,
                widthDp = width,
                heightDp = height,
                anchor = anchor,
                fill = if (hasGeometry) null else fill,
            )
        }
        return result
    }

    private fun parsePicture(
        pic: OoxmlNode,
        context: SlideContext,
        transform: Transform,
    ): PptxShape.Picture? {
        val xfrm = pic.child("spPr")?.child("xfrm") ?: return null
        val off = xfrm.child("off")
        val ext = xfrm.child("ext")
        val blip = pic.child("blipFill")?.child("blip")
        val embed = blip?.relAttr("embed") ?: blip?.relAttr("link") ?: return null
        val part = context.pkg.resolveRelationship(context.slidePart, embed) ?: return null
        val bytes = context.pkg.bytes(part) ?: return null
        return PptxShape.Picture(
            bytes = bytes,
            leftDp = transform.mapX((off?.attrLong("x") ?: 0L).toDp()),
            topDp = transform.mapY((off?.attrLong("y") ?: 0L).toDp()),
            widthDp = transform.mapWidth((ext?.attrLong("cx") ?: 0L).toDp()),
            heightDp = transform.mapHeight((ext?.attrLong("cy") ?: 0L).toDp()),
        )
    }

    private fun parseTable(
        frame: OoxmlNode,
        context: SlideContext,
        transform: Transform,
    ): PptxShape.Table? {
        val tbl = frame.child("graphic")?.child("graphicData")?.child("tbl") ?: return null
        val columns = tbl.child("tblGrid")?.children("gridCol").orEmpty()
        val columnWidths = columns.map { transform.mapWidth((it.attrLong("w") ?: 0L).toDp()) }
        if (columnWidths.isEmpty()) return null
        val rows = ArrayList<List<PptxTableCell>>()
        val rowHeights = ArrayList<Float>()
        tbl.children("tr").forEach { tr ->
            // The height of a row is a minimum, many decks leave it out.
            rowHeights += transform.mapHeight((tr.attrLong("h") ?: 0L).toDp())
            rows += tr.children("tc").map { tc -> parseCell(tc, context) }
        }
        val xfrm = frame.child("xfrm")
        val off = xfrm?.child("off")
        val ext = xfrm?.child("ext")
        val width = transform.mapWidth((ext?.attrLong("cx") ?: 0L).toDp())
        val height = transform.mapHeight((ext?.attrLong("cy") ?: 0L).toDp())
        // A table without row heights shares the height of the frame.
        if (rowHeights.all { it <= 0f } && rows.isNotEmpty()) {
            val share = height / rows.size
            for (index in rowHeights.indices) rowHeights[index] = share
        } else {
            for (index in rowHeights.indices) {
                if (rowHeights[index] <= 0f) rowHeights[index] = DEFAULT_ROW_HEIGHT_DP
            }
        }
        return PptxShape.Table(
            rows = rows,
            columnWidthsDp = columnWidths,
            rowHeightsDp = rowHeights,
            leftDp = transform.mapX((off?.attrLong("x") ?: 0L).toDp()),
            topDp = transform.mapY((off?.attrLong("y") ?: 0L).toDp()),
            widthDp = width,
            heightDp = height,
        )
    }

    private fun parseCell(tc: OoxmlNode, context: SlideContext): PptxTableCell {
        val txBody = tc.child("txBody")
        val paragraphs = txBody?.children("p").orEmpty()
        val text = paragraphs.joinToString("\n") { paragraph ->
            paragraph.children("r").joinToString("") { run -> run.child("t")?.text ?: "" }
        }
        val firstRun = paragraphs.firstOrNull()?.children("r")?.firstOrNull()
        val rPr = firstRun?.child("rPr")
        val bold = rPr?.attrBool("b")
            ?: paragraphs.firstOrNull()?.child("pPr")?.child("defRPr")?.attrBool("b")
            ?: false
        val color = rPr?.child("solidFill")?.let { OfficeColors.colorOf(it, context.theme) }
            ?: context.defaultTextColor
        val background = tc.child("tcPr")?.child("solidFill")
            ?.let { OfficeColors.colorOf(it, context.theme) }
        val alignment = when (paragraphs.firstOrNull()?.child("pPr")?.attr("algn")) {
            "ctr" -> "center"
            "r" -> "right"
            "just" -> "justify"
            else -> "left"
        }
        return PptxTableCell(
            text = text,
            bold = bold,
            color = color,
            background = background,
            alignment = alignment,
        )
    }

    // ------------------------------------------------------------ text model

    private fun buildTextContext(
        sp: OoxmlNode,
        context: SlideContext,
        phType: String,
        layoutPh: PhInfo?,
        masterPh: PhInfo?,
    ): TextContext {
        val own = sp.child("txBody")?.child("lstStyle")
        val listStyles = listOfNotNull(own, layoutPh?.lstStyle, masterPh?.lstStyle)
        val kind = when (phType) {
            "title", "ctrTitle" -> "title"
            "body", "subTitle", "obj", "objTx" -> "body"
            else -> "other"
        }
        val defaultSize = when (phType) {
            "title", "ctrTitle" -> TITLE_SIZE_PT
            "subTitle" -> SUBTITLE_SIZE_PT
            else -> BODY_SIZE_PT
        }
        val defaultFont = if (kind == "title") context.fonts.major else context.fonts.minor
        return TextContext(
            listStyles = listStyles,
            masterStyles = context.masterStyles,
            kind = kind,
            defaultSizePt = defaultSize,
            defaultColor = context.defaultTextColor,
            defaultFont = defaultFont,
            theme = context.theme,
        )
    }

    private fun parseParagraph(p: OoxmlNode, context: TextContext): PptxParagraph {
        val pPr = p.child("pPr")
        val level = (pPr?.attrInt("lvl") ?: 0).coerceIn(0, 8)
        val chain = context.levelChain(level)
        val builder = StringBuilder()
        var style: PptxTextStyle? = null
        p.nodes().forEach { node ->
            when (node.localName) {
                "r", "fld" -> {
                    builder.append(node.child("t")?.text ?: "")
                    if (style == null) style = styleOf(node.child("rPr"), pPr, chain, context)
                }
                "br" -> builder.append('\n')
            }
        }
        val alignment = resolveAlignment(pPr, chain)
        val resolved = style ?: styleOf(null, pPr, chain, context)
        return PptxParagraph(
            text = builder.toString(),
            bullet = resolveBullet(pPr, chain),
            level = level,
            style = resolved.copy(alignment = alignment),
        )
    }

    private fun styleOf(
        rPr: OoxmlNode?,
        pPr: OoxmlNode?,
        chain: List<OoxmlNode>,
        context: TextContext,
    ): PptxTextStyle {
        val sources = ArrayList<OoxmlNode?>()
        sources += rPr
        sources += pPr?.child("defRPr")
        chain.forEach { sources += it.child("defRPr") }

        val sizePt = firstFloat(sources) { it.attrFloat("sz") }?.div(100f) ?: context.defaultSizePt
        val bold = firstBool(sources) { it.attrBool("b") } ?: false
        val italic = firstBool(sources) { it.attrBool("i") } ?: false
        val underline = firstUnderline(sources) ?: false
        val color = firstColor(sources, context.theme) ?: context.defaultColor
        val font = firstFont(sources) ?: context.defaultFont
        return PptxTextStyle(
            sizePt = sizePt,
            bold = bold,
            italic = italic,
            underline = underline,
            color = color,
            alignment = "left",
            fontFamily = font,
        )
    }

    private fun resolveAlignment(pPr: OoxmlNode?, chain: List<OoxmlNode>): String {
        val raw = pPr?.attr("algn") ?: chain.firstNotNullOfOrNull { it.attr("algn") }
        return when (raw) {
            "ctr" -> "center"
            "r" -> "right"
            "just" -> "justify"
            else -> "left"
        }
    }

    private fun resolveBullet(pPr: OoxmlNode?, chain: List<OoxmlNode>): String? {
        if (pPr != null) {
            if (pPr.child("buNone") != null) return null
            bulletOf(pPr)?.let { return it }
        }
        chain.forEach { level ->
            if (level.child("buNone") != null) return null
            bulletOf(level)?.let { return it }
        }
        return null
    }

    private fun bulletOf(props: OoxmlNode): String? {
        props.child("buChar")?.attr("char")?.let { return it }
        props.child("buAutoNum")?.attr("type")?.let { return autoNumberLabel(it) }
        return null
    }

    private fun autoNumberLabel(type: String): String = when (type) {
        "arabicPeriod" -> "1."
        "arabicParenR" -> "1)"
        "arabicParenBoth" -> "(1)"
        "alphaLcPeriod" -> "a."
        "alphaLcParenR" -> "a)"
        "alphaUcPeriod" -> "A."
        "alphaUcParenR" -> "A)"
        "romanLcPeriod" -> "i."
        "romanLcParenR" -> "i)"
        "romanUcPeriod" -> "I."
        "romanUcParenR" -> "I)"
        else -> "1."
    }

    private inline fun firstFloat(
        sources: List<OoxmlNode?>,
        selector: (OoxmlNode) -> Float?,
    ): Float? {
        sources.forEach { source ->
            if (source != null) {
                val value = selector(source)
                if (value != null) return value
            }
        }
        return null
    }

    private inline fun firstBool(
        sources: List<OoxmlNode?>,
        selector: (OoxmlNode) -> Boolean?,
    ): Boolean? {
        sources.forEach { source ->
            if (source != null) {
                val value = selector(source)
                if (value != null) return value
            }
        }
        return null
    }

    private fun firstUnderline(sources: List<OoxmlNode?>): Boolean? {
        sources.forEach { source ->
            if (source != null) {
                val value = source.attr("u") ?: return@forEach
                return !value.equals("none", ignoreCase = true)
            }
        }
        return null
    }

    private fun firstColor(sources: List<OoxmlNode?>, theme: Map<String, OfficeColor>): OfficeColor? {
        sources.forEach { source ->
            if (source != null) {
                val color = source.child("solidFill")?.let { OfficeColors.colorOf(it, theme) }
                if (color != null) return color
            }
        }
        return null
    }

    private fun firstFont(sources: List<OoxmlNode?>): String? {
        sources.forEach { source ->
            if (source != null) {
                val typeface = source.child("latin")?.attr("typeface")
                if (!typeface.isNullOrBlank()) return typeface
            }
        }
        return null
    }

    // ------------------------------------------------------------- helpers

    private fun fillOf(spPr: OoxmlNode?, theme: Map<String, OfficeColor>): OfficeColor? {
        if (spPr == null) return null
        val solid = spPr.child("solidFill")
        if (solid != null) return OfficeColors.colorOf(solid, theme)
        val gradient = spPr.child("gradFill")
        if (gradient != null) {
            val stop = gradient.descendants("gs").firstOrNull()
            if (stop != null) return OfficeColors.colorOf(stop, theme)
        }
        return null
    }

    private fun lineOf(spPr: OoxmlNode?, theme: Map<String, OfficeColor>): OfficeColor? {
        val ln = spPr?.child("ln") ?: return null
        val solid = ln.child("solidFill") ?: return null
        return OfficeColors.colorOf(solid, theme)
    }

    private fun lineWidthOf(spPr: OoxmlNode?): Float {
        val width = spPr?.child("ln")?.attrLong("w") ?: return DEFAULT_LINE_WIDTH_DP
        if (width <= 0L) return 0f
        return width.toDp()
    }

    private fun anchorName(anchor: String?): String = when (anchor) {
        "ctr" -> "center"
        "b" -> "bottom"
        "just" -> "center"
        else -> "top"
    }

    private fun matchPlaceholder(
        list: List<PhInfo>,
        type: String?,
        idx: String?,
    ): PhInfo? {
        if (list.isEmpty()) return null
        if (idx != null) {
            list.firstOrNull { it.idx == idx && compatible(it.type, type) }?.let { return it }
            list.firstOrNull { it.idx == idx }?.let { return it }
        }
        if (type != null) {
            list.firstOrNull { it.type == type }?.let { return it }
            list.firstOrNull { it.idx == null && compatible(it.type, type) }?.let { return it }
        }
        return null
    }

    private fun compatible(layoutType: String?, slideType: String?): Boolean {
        if (layoutType == null || slideType == null) return true
        if (layoutType == slideType) return true
        val titles = setOf("title", "ctrTitle")
        return layoutType in titles && slideType in titles
    }

    private fun readThemeFonts(pkg: OoxmlPackage): ThemeFonts {
        val theme = pkg.node("ppt/theme/theme1.xml") ?: return ThemeFonts(null, null)
        val scheme = theme.descendants("fontScheme").firstOrNull() ?: return ThemeFonts(null, null)
        val major = scheme.child("majorFont")?.child("latin")?.attr("typeface")
        val minor = scheme.child("minorFont")?.child("latin")?.attr("typeface")
        return ThemeFonts(major, minor)
    }

    private fun readTitle(pkg: OoxmlPackage): String {
        val core = pkg.node("docProps/core.xml")
        val title = core?.descendants("title")?.firstOrNull()?.text?.trim()
        return title.orEmpty()
    }

    private fun parseNotes(context: SlideContext): String? {
        val part = findRelatedPart(context.pkg, context.slidePart, "/notesSlide") ?: return null
        val node = context.pkg.node(part) ?: return null
        val lines = ArrayList<String>()
        node.child("cSld")?.child("spTree")?.nodes()
            ?.filter { it.localName == "sp" }
            ?.forEach { sp ->
                val phType = sp.child("nvSpPr")?.child("nvPr")?.child("ph")?.attr("type")
                if (phType == "sldImg" || phType == "sldNum" || phType == "hdr" ||
                    phType == "ftr" || phType == "dt"
                ) {
                    return@forEach
                }
                val body = sp.child("txBody") ?: return@forEach
                val text = body.children("p").joinToString("\n") { paragraph ->
                    paragraph.children("r").joinToString("") { run -> run.child("t")?.text ?: "" }
                }
                if (text.isNotBlank()) lines += text.trim()
            }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun String.slideNumber(): Int {
        val digits = filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

}

/** EMU -> deck dp (1 dp = 9525 EMU). */
private fun Long.toDp(): Float =
    if (this in -1_000_000_000L..1_000_000_000L) {
        OfficeColors.emuToDp(this.toInt())
    } else {
        this / 9525f
    }

// -------------------------------------------------------------- internals

/**
 * The mapping of a coordinate space into the space of the slide.
 *
 * A group of shapes lives in its own coordinate space, the transform maps it
 * into the space of the slide: the shapes are moved by the offset and scaled
 * by the factors.
 */
private class Transform(
    val offsetX: Float,
    val offsetY: Float,
    val scaleX: Float,
    val scaleY: Float,
) {
    fun mapX(value: Float): Float = value * scaleX + offsetX

    fun mapY(value: Float): Float = value * scaleY + offsetY

    fun mapWidth(value: Float): Float = value * scaleX

    fun mapHeight(value: Float): Float = value * scaleY

    /** Combines this transform with the one of a group inside of it. */
    fun then(group: Transform): Transform = Transform(
        offsetX = mapX(group.offsetX),
        offsetY = mapY(group.offsetY),
        scaleX = scaleX * group.scaleX,
        scaleY = scaleY * group.scaleY,
    )

    companion object {
        /** The identity: the coordinates are used as they are. */
        val IDENTITY = Transform(0f, 0f, 1f, 1f)
    }
}

private class ThemeFonts(val major: String?, val minor: String?)

private class PhInfo(
    val type: String?,
    val idx: String?,
    val xfrm: OoxmlNode?,
    val lstStyle: OoxmlNode?,
)

/**
 * Everything a shape needs to resolve inherited text properties.
 */
private class TextContext(
    val listStyles: List<OoxmlNode>,
    val masterStyles: OoxmlNode?,
    val kind: String,
    val defaultSizePt: Float,
    val defaultColor: OfficeColor?,
    val defaultFont: String?,
    val theme: Map<String, OfficeColor>,
) {
    fun levelChain(level: Int): List<OoxmlNode> {
        val name = "lvl${level + 1}pPr"
        val chain = ArrayList<OoxmlNode>()
        listStyles.forEach { style ->
            style.children(name).firstOrNull()?.let { chain += it }
        }
        masterStyles?.child("${kind}Style")?.children(name)?.firstOrNull()?.let { chain += it }
        return chain
    }
}

/**
 * Per slide context: resolves the layout / master chain of a slide.
 */
private class SlideContext(
    val pkg: OoxmlPackage,
    val slidePart: String,
    val theme: Map<String, OfficeColor>,
    val fonts: ThemeFonts,
) {
    val layoutPart: String? = findRelatedPart(pkg, slidePart, "/slideLayout")
    val masterPart: String? = layoutPart?.let { findRelatedPart(pkg, it, "/slideMaster") }
    val layoutNode: OoxmlNode? = layoutPart?.let { pkg.node(it) }
    val masterNode: OoxmlNode? = masterPart?.let { pkg.node(it) }
    val layoutPlaceholders: List<PhInfo> = placeholdersOf(layoutNode)
    val masterPlaceholders: List<PhInfo> = placeholdersOf(masterNode)
    val masterStyles: OoxmlNode? = masterNode?.child("txStyles")
    val defaultTextColor: OfficeColor? = resolveDefaultTextColor()

    private fun resolveDefaultTextColor(): OfficeColor? {
        val slot = masterNode?.child("clrMap")?.attr("tx1") ?: "dk1"
        return theme[slot] ?: theme["dk1"] ?: theme["tx1"]
    }

    private fun placeholdersOf(node: OoxmlNode?): List<PhInfo> {
        val spTree = node?.child("cSld")?.child("spTree") ?: return emptyList()
        val result = ArrayList<PhInfo>()
        spTree.nodes().filter { it.localName == "sp" }.forEach { sp ->
            val ph = sp.child("nvSpPr")?.child("nvPr")?.child("ph") ?: return@forEach
            result += PhInfo(
                // A placeholder without a type is a body placeholder.
                type = ph.attr("type") ?: "body",
                idx = ph.attr("idx"),
                xfrm = sp.child("spPr")?.child("xfrm"),
                lstStyle = sp.child("txBody")?.child("lstStyle"),
            )
        }
        return result
    }
}

/**
 * Finds the part a relationship of the given type points at.
 */
private fun findRelatedPart(pkg: OoxmlPackage, base: String, typeSuffix: String): String? {
    val relationship = pkg.relationshipsOfType(base, typeSuffix).firstOrNull() ?: return null
    val id = relationship.attr("Id") ?: return null
    return pkg.resolveRelationship(base, id)
}

/**
 * Namespace aware attribute lookup: `r:id` and `r:embed` share their local
 * name with other attributes (`p:id`), so the prefixed form wins.
 */
private fun OoxmlNode.relAttr(localName: String): String? {
    val attributes = element.attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        val nodeName = attribute.nodeName
        if (!nodeName.contains(':')) continue
        if (nodeName.substringAfterLast(':').equals(localName, ignoreCase = true)) {
            return attribute.nodeValue
        }
    }
    return attr(localName)
}
