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
    /**
     * The picture that fills the background of the slide.
     *
     * A slide with a picture as its background shows it behind the shapes.
     */
    val backgroundImage: ByteArray? = null,
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
        /**
         * The number of the shape in the slide.
         *
         * The editor needs it to write a changed text back into the file: the
         * number is the same one the editor counts the shapes of the slide
         * with.
         */
        val sourceIndex: Int = -1,
        /**
         * The space between the text and the border of the box, in deck dp.
         *
         * The office software keeps the text away from the border, the file
         * only names the space when it differs from the usual one.
         */
        val insetLeftDp: Float = DEFAULT_INSET_LEFT_DP,
        val insetTopDp: Float = DEFAULT_INSET_TOP_DP,
        val insetRightDp: Float = DEFAULT_INSET_RIGHT_DP,
        val insetBottomDp: Float = DEFAULT_INSET_BOTTOM_DP,
        /**
         * How the text follows the size of the box.
         *
         * "none" keeps the text as it is, "shape" lets the box grow with the
         * text and "text" shrinks the text into the box.
         */
        val autoFit: String = "none",
        /**
         * The factor the text is scaled by, when the file names it.
         *
         * A file that shrinks the text into its box writes the factor down,
         * the reader uses it instead of guessing.
         */
        val fontScale: Float = 1f,
        /**
         * Whether the text of the box breaks into more lines.
         *
         * A box that does not wrap lets its text run over its border, the way
         * a title of a slide often does.
         */
        val wrap: Boolean = true,
    ) : PptxShape

    data class Picture(
        val bytes: ByteArray,
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
        /**
         * The part of the picture that is cut away, the fractions of the
         * picture on every side.
         */
        val cropLeft: Float = 0f,
        val cropTop: Float = 0f,
        val cropRight: Float = 0f,
        val cropBottom: Float = 0f,
        /** The turn of the picture, in degree. */
        val rotation: Float = 0f,
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
        /** The gradient of the shape, when it has one. */
        val gradient: PptxGradient? = null,
    ) : PptxShape

    data class Table(
        val rows: List<List<PptxTableCell>>,
        val columnWidthsDp: List<Float>,
        val rowHeightsDp: List<Float>,
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
        /** The style of the table, the colours of its rows. */
        val style: PptxTableStyle? = null,
    ) : PptxShape

    data class Chart(
        val chart: PptxChart,
        override val leftDp: Float,
        override val topDp: Float,
        override val widthDp: Float,
        override val heightDp: Float,
    ) : PptxShape
}

/**
 * The style of a table.
 *
 * A table names its style by a number, the style paints the header row, the
 * rows that are banded and the lines between the cells. A cell that has a
 * colour of its own keeps it.
 */
data class PptxTableStyle(
    /** The colour of the first row. */
    val firstRowFill: OfficeColor?,
    /** The colour of the text of the first row. */
    val firstRowText: OfficeColor?,
    /** Whether the text of the first row is bold. */
    val firstRowBold: Boolean = false,
    /** The colour of the banded rows. */
    val bandFill: OfficeColor?,
    /** The colour of the rows between the banded ones. */
    val bandAltFill: OfficeColor?,
    /** The colour of the text of the rows. */
    val text: OfficeColor?,
    /** The colour of the lines between the cells. */
    val border: OfficeColor?,
    /** The width of the lines between the cells, in deck dp. */
    val borderWidthDp: Float = 1f,
)

/**
 * One part of the text of a paragraph.
 */
data class PptxRun(
    /** The text of the part. */
    val text: String,
    /** The style of the part. */
    val style: PptxTextStyle,
)

/**
 * One stop of a gradient fill.
 */
data class PptxGradientStop(
    /** The place of the stop, 0 is the start and 1 the end. */
    val position: Float,
    val color: OfficeColor,
)

/**
 * A gradient fill of a shape.
 */
data class PptxGradient(
    val stops: List<PptxGradientStop>,
    /** The direction of the gradient in degrees. */
    val angleDeg: Float,
)

data class PptxParagraph(
    val text: String,
    /**
     * The parts of the text, every part has its own style.
     *
     * A paragraph often holds a word that is bold or that has another
     * colour, the parts keep the style of the file.
     */
    val runs: List<PptxRun> = emptyList(),
    val bullet: String?,
    val level: Int,
    val style: PptxTextStyle,
    /**
     * The distance between the lines of the paragraph.
     *
     * One is the normal distance, a value of 0.9 is the ninety per cent that
     * many templates use.
     */
    val lineSpacing: Float = 1f,
    /**
     * The exact distance between the lines, in point.
     *
     * A file that names the distance in point uses it instead of the factor.
     */
    val lineSpacingPt: Float? = null,
    /** The space above the paragraph, in point. */
    val spaceBeforePt: Float = 0f,
    /** The space below the paragraph, in point. */
    val spaceAfterPt: Float = 0f,
    /** The left margin of the paragraph, in deck dp. */
    val marginLeftDp: Float = 0f,
    /** The indent of the first line of the paragraph, in deck dp. */
    val indentDp: Float = 0f,
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

    fun parse(pkg: OoxmlPackage): PptxDeck? = Reader(pkg).readAll()

    /**
     * Reads a deck slide by slide.
     *
     * The slides of a big deck are read one after the other, so the reader
     * can show the first slide while the rest of the deck is still read. The
     * package has to stay open until every slide is read.
     */
    class Reader(private val pkg: OoxmlPackage) {

        private val theme = OfficeColors.readTheme(pkg)
        private val fonts = readThemeFonts(pkg)

        /** The parts of the slides of the deck. */
        private val parts: List<String>

        /** The width of the deck in deck dp. */
        val widthDp: Float

        /** The height of the deck in deck dp. */
        val heightDp: Float

        /** The title of the deck. */
        val title: String

        init {
            val presentation = pkg.node("ppt/presentation.xml")
            val sizeNode = presentation?.child("sldSz")
                ?: presentation?.descendants("sldSz")?.firstOrNull()
            val cx = sizeNode?.attrLong("cx") ?: DEFAULT_SLIDE_CX
            val cy = sizeNode?.attrLong("cy") ?: DEFAULT_SLIDE_CY
            widthDp = cx.toDp()
            heightDp = cy.toDp()
            title = readTitle(pkg)
            parts = slideParts(pkg, presentation)
        }

        /** The number of the slides of the deck. */
        val slideCount: Int get() = parts.size

        /**
         * The part of the slide with the given index.
         *
         * The editor needs it to write a changed text back into the file.
         */
        fun slidePart(index: Int): String? = parts.getOrNull(index)

        /** Reads one slide of the deck. */
        fun readSlide(index: Int): PptxSlide? {
            val part = parts.getOrNull(index) ?: return null
            val node = pkg.node(part) ?: return null
            return parseSlide(pkg, part, node, index + 1, theme, fonts)
        }

        /** Reads the whole deck. */
        fun readAll(): PptxDeck? {
            val slides = ArrayList<PptxSlide>()
            for (index in 0 until minOf(parts.size, MAX_SLIDES)) {
                readSlide(index)?.let { slides += it }
            }
            if (slides.isEmpty()) return null
            return PptxDeck(slides = slides, widthDp = widthDp, heightDp = heightDp, title = title)
        }
    }

    /** The parts of the slides of the deck, in the order of the deck. */
    private fun slideParts(pkg: OoxmlPackage, presentation: OoxmlNode?): List<String> {
        val parts = ArrayList<String>()
        val idList = presentation?.child("sldIdLst")
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
        return parts
    }

    // ---------------------------------------------------------------- slides

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
        // The decoration of the master and of the layout lies behind the
        // shapes of the slide. The number of the shapes of the slide does not
        // change, the editor counts them in the file of the slide.
        val slideStart = context.shapeIndex
        // A slide can hide the shapes of the master.
        if (node.attrBool("showMasterSp") != false) {
            shapes += parseDecoration(context.masterNode, context)
        }
        shapes += parseDecoration(context.layoutNode, context)
        context.shapeIndex = slideStart
        cSld(node)?.child("spTree")?.nodes()?.forEach { child ->
            parseNode(child, context, shapes, Transform.IDENTITY)
        }
        return PptxSlide(
            index = index,
            shapes = shapes,
            background = resolveBackground(cSld(node), context),
            notes = parseNotes(context),
            backgroundImage = resolveBackgroundImage(cSld(node), context),
        )
    }

    /**
     * The decoration of the master or of the layout of a slide.
     *
     * The placeholders of the master and of the layout are only a hint for
     * the author, the office software does not draw them - the slide carries
     * its own text in them.
     */
    private fun parseDecoration(node: OoxmlNode?, context: SlideContext): List<PptxShape> {
        val spTree = node?.child("cSld")?.child("spTree") ?: return emptyList()
        val shapes = ArrayList<PptxShape>()
        spTree.nodes()?.forEach { child ->
            parseNode(child, context, shapes, Transform.IDENTITY, skipPlaceholders = true)
        }
        return shapes
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
        skipPlaceholders: Boolean = false,
    ) {
        when (node.localName) {
            "sp" -> {
                // A placeholder of the master or of the layout is only a hint
                // for the author, the office software does not draw it.
                if (skipPlaceholders && isPlaceholder(node)) return
                val index = context.shapeIndex++
                shapes += parseShape(node, context, transform, index)
            }
            "pic" -> parsePicture(node, context, transform)?.let { shapes += it }
            "graphicFrame" -> {
                // A frame holds a table or a chart, the slide names the part
                // of it.
                parseTable(node, context, transform)?.let { shapes += it }
                    ?: parseChart(node, context, transform)?.let { shapes += it }
            }
            "grpSp" -> {
                // The group carries its own coordinate space in the group
                // properties: the offset, the extent and the child space.
                val group = groupTransform(node.child("grpSpPr")?.child("xfrm"))
                // The shape tree of a group repeats the group properties, the
                // children follow them.
                node.nodes().filter { it.localName == "sp" || it.localName == "pic" ||
                    it.localName == "graphicFrame" || it.localName == "grpSp"
                }.forEach { child ->
                    parseNode(child, context, shapes, transform.then(group), skipPlaceholders)
                }
            }
        }
    }

    /** Whether a shape is a placeholder. */
    private fun isPlaceholder(shape: OoxmlNode): Boolean =
        shape.child("nvSpPr")?.child("nvPr")?.child("ph") != null

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

    /**
     * The picture that fills the background of the slide.
     *
     * A slide can have a picture as its background, the slide itself or the
     * layout of it can name it.
     */
    private fun resolveBackgroundImage(cSld: OoxmlNode?, context: SlideContext): ByteArray? {
        val own = backgroundImageOf(cSld?.child("bg"), context)
        if (own != null) return own
        return backgroundImageOf(context.layoutNode?.child("cSld")?.child("bg"), context)
            ?: backgroundImageOf(context.masterNode?.child("cSld")?.child("bg"), context)
    }

    /** The picture of a background, null when the background has none. */
    private fun backgroundImageOf(bg: OoxmlNode?, context: SlideContext): ByteArray? {
        val props = bg?.child("bgPr") ?: return null
        val blip = props.child("blipFill")?.child("blip") ?: return null
        val embed = blip.relAttr("embed") ?: blip.relAttr("link") ?: return null
        val part = context.pkg.resolveRelationship(context.slidePart, embed) ?: return null
        return context.pkg.bytes(part)
    }

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
        sourceIndex: Int,
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
                gradient = gradientOf(spPr, context.theme),
            )
        }
        if (paragraphs.isNotEmpty()) {
            val bodyPr = txBody?.child("bodyPr")
            val anchor = anchorName(bodyPr?.attr("anchor"))
            val insets = insetsOf(bodyPr)
            result += PptxShape.TextBox(
                paragraphs = paragraphs,
                leftDp = left,
                topDp = top,
                widthDp = width,
                heightDp = height,
                anchor = anchor,
                fill = if (hasGeometry) null else fill,
                sourceIndex = sourceIndex,
                insetLeftDp = insets[0],
                insetTopDp = insets[1],
                insetRightDp = insets[2],
                insetBottomDp = insets[3],
                autoFit = autoFitOf(bodyPr),
                fontScale = fontScaleOf(bodyPr),
                wrap = bodyPr?.attr("wrap") != "none",
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
        val blipFill = pic.child("blipFill")
        val blip = blipFill?.child("blip")
        val embed = blip?.relAttr("embed") ?: blip?.relAttr("link") ?: return null
        val part = context.pkg.resolveRelationship(context.slidePart, embed) ?: return null
        val bytes = context.pkg.bytes(part) ?: return null
        // The picture can be cut away on every side, the file writes the cut
        // down in hundred thousandths of a percent.
        val srcRect = blipFill?.child("srcRect")
        return PptxShape.Picture(
            bytes = bytes,
            leftDp = transform.mapX((off?.attrLong("x") ?: 0L).toDp()),
            topDp = transform.mapY((off?.attrLong("y") ?: 0L).toDp()),
            widthDp = transform.mapWidth((ext?.attrLong("cx") ?: 0L).toDp()),
            heightDp = transform.mapHeight((ext?.attrLong("cy") ?: 0L).toDp()),
            cropLeft = cropOf(srcRect?.attrInt("l")),
            cropTop = cropOf(srcRect?.attrInt("t")),
            cropRight = cropOf(srcRect?.attrInt("r")),
            cropBottom = cropOf(srcRect?.attrInt("b")),
            rotation = (xfrm.attrFloat("rot") ?: 0f) / 60000f,
        )
    }

    /** The part of a picture that is cut away, as a fraction of the picture. */
    private fun cropOf(value: Int?): Float =
        ((value ?: 0) / 100000f).coerceIn(0f, 0.9f)

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
            style = tableStyle(context, tbl.child("tblPr")?.child("tableStyleId")?.text?.trim()),
        )
    }

    // ---------------------------------------------------------------- charts

    /**
     * The chart of a frame.
     *
     * The frame names the part of the chart with a relationship, the chart
     * lives in `ppt/charts/chart1.xml`.
     */
    private fun parseChart(
        frame: OoxmlNode,
        context: SlideContext,
        transform: Transform,
    ): PptxShape.Chart? {
        val data = frame.child("graphic")?.child("graphicData") ?: return null
        val reference = data.child("chart")?.relAttr("id") ?: return null
        val part = context.pkg.resolveRelationship(context.slidePart, reference) ?: return null
        val chart = PptxChartParser.parse(context.pkg, part, context.theme) ?: return null
        val xfrm = frame.child("xfrm")
        val off = xfrm?.child("off")
        val ext = xfrm?.child("ext")
        return PptxShape.Chart(
            chart = chart,
            leftDp = transform.mapX((off?.attrLong("x") ?: 0L).toDp()),
            topDp = transform.mapY((off?.attrLong("y") ?: 0L).toDp()),
            widthDp = transform.mapWidth((ext?.attrLong("cx") ?: 0L).toDp()),
            heightDp = transform.mapHeight((ext?.attrLong("cy") ?: 0L).toDp()),
        )
    }

    // ----------------------------------------------------------- table style
    /**
     * The style of a table.
     *
     * The file names the style by its number. A file that carries the style
     * uses it, a file that does not carry it uses the style of the office
     * software, which is built from the accent colour of the theme.
     */
    private fun tableStyle(context: SlideContext, styleId: String?): PptxTableStyle {
        val accent = context.theme["accent1"] ?: DEFAULT_ACCENT
        val defined = styleId?.let { definedTableStyle(context, it) }
        if (defined != null) return defined
        return defaultTableStyle(accent)
    }

    /**
     * The style of the table that the file carries.
     *
     * The styles of the files live in `ppt/tableStyles.xml`, most of the
     * files do not carry them at all.
     */
    private fun definedTableStyle(context: SlideContext, styleId: String): PptxTableStyle? {
        val root = context.pkg.node(TABLE_STYLES_PART) ?: return null
        val style = root.children("tblStyle").firstOrNull { it.attr("styleId") == styleId }
            ?: return null
        val parts = style.children("tblStylePr").associateBy { it.attr("type") }
        val whole = parts["wholeTable"]
        val first = parts["firstRow"]
        val band1 = parts["band1Horz"]
        val band2 = parts["band2Horz"]
        return PptxTableStyle(
            firstRowFill = fillOfStylePart(first, context.theme) ?: fillOfStylePart(whole, context.theme),
            firstRowText = textOfStylePart(first, context.theme),
            firstRowBold = first?.child("rPr")?.attrBool("b") == true,
            bandFill = fillOfStylePart(band1, context.theme),
            bandAltFill = fillOfStylePart(band2, context.theme),
            text = textOfStylePart(whole, context.theme),
            border = borderOfStylePart(whole ?: first, context.theme),
            borderWidthDp = 1f,
        )
    }

    /** The colour that a part of a style paints the cells with. */
    private fun fillOfStylePart(part: OoxmlNode?, theme: Map<String, OfficeColor>): OfficeColor? {
        val fill = part?.child("tcPr")?.child("fillRef") ?: return null
        return OfficeColors.colorOf(fill, theme)
    }

    /** The colour that a part of a style paints the text with. */
    private fun textOfStylePart(part: OoxmlNode?, theme: Map<String, OfficeColor>): OfficeColor? {
        val fill = part?.child("rPr")?.child("solidFill") ?: return null
        return OfficeColors.colorOf(fill, theme)
    }

    /** The colour of the lines between the cells. */
    private fun borderOfStylePart(part: OoxmlNode?, theme: Map<String, OfficeColor>): OfficeColor? {
        val border = part?.child("tcPr")?.child("tcBdr") ?: return null
        border.nodes().forEach { side ->
            val fill = side.child("solidFill") ?: return@forEach
            OfficeColors.colorOf(fill, theme)?.let { return it }
        }
        return null
    }

    /**
     * The style of the office software.
     *
     * The header row is painted with the accent colour of the theme, the rows
     * of the body are banded with a light tone of it.
     */
    private fun defaultTableStyle(accent: OfficeColor): PptxTableStyle = PptxTableStyle(
        firstRowFill = accent,
        firstRowText = OfficeColor.White,
        firstRowBold = true,
        bandFill = accent.lighten(0.2f, 0.8f),
        bandAltFill = accent.lighten(0.2f, 0.9f),
        text = null,
        border = OfficeColor.White,
        borderWidthDp = 1f,
    )

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
        val runs = ArrayList<PptxRun>()
        p.nodes().forEach { node ->
            when (node.localName) {
                "r", "fld" -> {
                    val text = node.child("t")?.text ?: ""
                    builder.append(text)
                    runs += PptxRun(text, styleOf(node.child("rPr"), pPr, chain, context))
                }
                "br" -> {
                    builder.append('\n')
                    runs += PptxRun(
                        "\n",
                        runs.lastOrNull()?.style ?: styleOf(null, pPr, chain, context),
                    )
                }
            }
        }
        val alignment = resolveAlignment(pPr, chain)
        val resolved = runs.firstOrNull()?.style ?: styleOf(null, pPr, chain, context)
        val spacing = lineSpacingOf(pPr, chain)
        return PptxParagraph(
            text = builder.toString(),
            runs = runs,
            bullet = resolveBullet(pPr, chain),
            level = level,
            style = resolved.copy(alignment = alignment),
            lineSpacing = spacing.first,
            lineSpacingPt = spacing.second,
            spaceBeforePt = spaceOf(pPr, chain, "spcBef"),
            spaceAfterPt = spaceOf(pPr, chain, "spcAft"),
            marginLeftDp = marginOf(pPr, chain, "marL"),
            indentDp = marginOf(pPr, chain, "indent"),
        )
    }

    /**
     * The distance between the lines of a paragraph.
     *
     * A file that names the distance in percent gives a factor, a file that
     * names it in point gives the exact distance.
     */
    private fun lineSpacingOf(pPr: OoxmlNode?, chain: List<OoxmlNode>): Pair<Float, Float?> {
        val spacing = pPr?.child("lnSpc")
            ?: chain.firstNotNullOfOrNull { it.child("lnSpc") }
            ?: return 1f to null
        spacing.child("spcPct")?.attrInt("val")?.let { return it / 100000f to null }
        spacing.child("spcPts")?.attrInt("val")?.let { return 1f to it / 100f }
        return 1f to null
    }

    /**
     * The space above or below a paragraph, in point.
     */
    private fun spaceOf(pPr: OoxmlNode?, chain: List<OoxmlNode>, name: String): Float {
        val space = pPr?.child(name)
            ?: chain.firstNotNullOfOrNull { it.child(name) }
            ?: return 0f
        space.child("spcPts")?.attrInt("val")?.let { return it / 100f }
        return 0f
    }

    /**
     * The margin or the indent of a paragraph, in deck dp.
     */
    private fun marginOf(pPr: OoxmlNode?, chain: List<OoxmlNode>, name: String): Float {
        val raw = pPr?.attrLong(name)
            ?: chain.firstNotNullOfOrNull { it.attrLong(name) }
            ?: return 0f
        return raw.toDp()
    }

    /**
     * The space between the text of a box and its border, in deck dp.
     *
     * The file only names the space when it differs from the usual one: a
     * tenth of an inch on the sides and a twentieth on top and bottom.
     */
    private fun insetsOf(bodyPr: OoxmlNode?): FloatArray {
        if (bodyPr == null) {
            return floatArrayOf(
                DEFAULT_INSET_LEFT_DP,
                DEFAULT_INSET_TOP_DP,
                DEFAULT_INSET_RIGHT_DP,
                DEFAULT_INSET_BOTTOM_DP,
            )
        }
        return floatArrayOf(
            (bodyPr.attrLong("lIns") ?: DEFAULT_INSET_LEFT_EMU).toDp(),
            (bodyPr.attrLong("tIns") ?: DEFAULT_INSET_TOP_EMU).toDp(),
            (bodyPr.attrLong("rIns") ?: DEFAULT_INSET_RIGHT_EMU).toDp(),
            (bodyPr.attrLong("bIns") ?: DEFAULT_INSET_BOTTOM_EMU).toDp(),
        )
    }

    /**
     * How the text of a box follows its size.
     */
    private fun autoFitOf(bodyPr: OoxmlNode?): String = when {
        bodyPr == null -> "none"
        bodyPr.child("spAutoFit") != null -> "shape"
        bodyPr.child("normAutofit") != null -> "text"
        else -> "none"
    }

    /**
     * The factor the text of a box is scaled by.
     *
     * A file that shrinks the text into its box writes the factor down.
     */
    private fun fontScaleOf(bodyPr: OoxmlNode?): Float {
        val scale = bodyPr?.child("normAutofit")?.attrInt("fontScale") ?: return 1f
        return (scale / 100000f).coerceIn(0.1f, 1f)
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

    /**
     * The gradient of a shape.
     *
     * The stops of the gradient are read with their place and their colour,
     * so the fill looks like the one of the presentation.
     */
    private fun gradientOf(spPr: OoxmlNode?, theme: Map<String, OfficeColor>): PptxGradient? {
        if (spPr == null) return null
        val gradient = spPr.child("gradFill") ?: return null
        val list = gradient.child("gsLst") ?: gradient
        val stops = ArrayList<PptxGradientStop>()
        list.children("gs").forEach { stop ->
            val color = OfficeColors.colorOf(stop, theme) ?: return@forEach
            val position = (stop.attrInt("pos") ?: 0) / 100000f
            stops += PptxGradientStop(position.coerceIn(0f, 1f), color)
        }
        if (stops.size < 2) return null
        val angle = gradient.child("lin")?.attrInt("ang")?.div(60000f) ?: 0f
        return PptxGradient(stops = stops, angleDeg = angle)
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

/**
 * The space between the text of a box and its border, in deck dp.
 *
 * The office software keeps a tenth of an inch away from the sides and a
 * twentieth away from the top and the bottom, a file only names the space
 * when it differs from it.
 */
private const val DEFAULT_INSET_LEFT_EMU = 91440L
private const val DEFAULT_INSET_TOP_EMU = 45720L
private const val DEFAULT_INSET_RIGHT_EMU = 91440L
private const val DEFAULT_INSET_BOTTOM_EMU = 45720L

/** The same space, in deck dp. */
private const val DEFAULT_INSET_LEFT_DP = 9.6f
private const val DEFAULT_INSET_TOP_DP = 4.8f
private const val DEFAULT_INSET_RIGHT_DP = 9.6f
private const val DEFAULT_INSET_BOTTOM_DP = 4.8f

/** The part that carries the styles of the tables. */
private const val TABLE_STYLES_PART = "ppt/tableStyles.xml"

/** The accent colour of the theme, used when the theme does not name one. */
private val DEFAULT_ACCENT = OfficeColor(0.31f, 0.53f, 0.87f, 1f)

/**
 * A lighter tone of a colour.
 *
 * The colour is first darkened and then mixed with white, the way the theme
 * of a deck paints the banded rows of a table.
 */
private fun OfficeColor.lighten(modifier: Float, offset: Float): OfficeColor = OfficeColor(
    ((red * modifier + (1f - red) * offset).coerceIn(0f, 1f)),
    ((green * modifier + (1f - green) * offset).coerceIn(0f, 1f)),
    ((blue * modifier + (1f - blue) * offset).coerceIn(0f, 1f)),
    alpha,
)

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

    /**
     * The number of the shapes that were read.
     *
     * The number is the place of the shape in the shape tree of the slide, it
     * is used to write a changed text back into the file.
     */
    var shapeIndex = 0

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
