package com.peng.pdfreader.core.office.legacy

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.pptx.PptxDeck
import com.peng.pdfreader.core.office.pptx.PptxParagraph
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.pptx.PptxTableCell
import com.peng.pdfreader.core.office.pptx.PptxSlide
import com.peng.pdfreader.core.office.pptx.PptxTextStyle
import com.peng.pdfreader.core.util.Logger
import org.apache.poi.hslf.record.Record
import org.apache.poi.hslf.record.RecordContainer
import org.apache.poi.hslf.record.TextBytesAtom
import org.apache.poi.hslf.record.TextCharsAtom
import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFGroupShape
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSimpleShape
import org.apache.poi.hslf.usermodel.HSLFSlide
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextShape
import java.io.File

/**
 * Reads the legacy binary format of the presentations (`.ppt`).
 *
 * The format is the compound file of Office 97, the reader of Apache POI
 * opens it. The shapes of a slide are read with their position and their
 * colour, so the slides look like the ones of PowerPoint.
 *
 * POI describes the position of a shape with the types of the abstract window
 * toolkit of Java. Android does not know them, the app carries the few types
 * that are needed (see the package `java.awt` of the app). A shape that
 * cannot be read does not stop the deck: the text of the slide is read out of
 * the records of the file instead.
 */
internal object LegacyPresentation {

    /** The width of a slide of 4:3 in dp, used when the file does not tell it. */
    private const val DEFAULT_WIDTH_DP = 960f

    /** The height of a slide of 4:3 in dp, used when the file does not tell it. */
    private const val DEFAULT_HEIGHT_DP = 720f

    /** The margin of the text boxes of a slide in dp. */
    private const val MARGIN_DP = 32f

    /** The gap between two text boxes of a slide in dp. */
    private const val GAP_DP = 18f

    /** The size of the text of a shape that does not tell its own size. */
    private const val DEFAULT_SIZE_PT = 18f

    /** The height a line of the text of a shape needs in dp. */
    private const val LINE_HEIGHT_DP = 26f

    /** The points of a dp, a dp is a 1/96 of an inch. */
    private const val POINTS_PER_DP = 0.75f

    /** Reads the deck of a legacy presentation. */
    fun read(file: File): PptxDeck? = runCatching {
        file.inputStream().use { input ->
            val show = HSLFSlideShow(input)
            val size = runCatching { show.pageSize }.getOrNull()
            val widthDp = ((size?.width ?: 0).toFloat() / POINTS_PER_DP)
                .takeIf { it > 0f } ?: DEFAULT_WIDTH_DP
            val heightDp = ((size?.height ?: 0).toFloat() / POINTS_PER_DP)
                .takeIf { it > 0f } ?: DEFAULT_HEIGHT_DP
            val slides = show.slides.mapIndexed { index, slide ->
                slideOf(index, slide, widthDp, heightDp)
            }
            PptxDeck(
                slides = slides,
                widthDp = widthDp,
                heightDp = heightDp,
                title = slides.firstNotNullOfOrNull { slide ->
                    slide.shapes.filterIsInstance<PptxShape.TextBox>()
                        .firstOrNull { box -> box.paragraphs.any { it.text.isNotBlank() } }
                        ?.paragraphs?.firstOrNull { it.text.isNotBlank() }?.text
                }.orEmpty(),
            )
        }
    }.onFailure {
        Logger.e(TAG, "the legacy presentation could not be read", it)
    }.getOrNull()

    /**
     * Reads one slide of the deck.
     *
     * The shapes are read in the order of the slide, so they are painted in
     * the same order as in PowerPoint.
     */
    private fun slideOf(index: Int, slide: HSLFSlide, widthDp: Float, heightDp: Float): PptxSlide {
        val shapes = ArrayList<PptxShape>()
        var top = MARGIN_DP
        val slideShapes = runCatching { slide.shapes }.getOrNull()
        if (slideShapes != null) {
            for (shape in slideShapes) {
                shapesOf(shape, shapes, top)
            }
        }
        if (shapes.isEmpty()) {
            // The way out: the text out of the records of the slide.
            for (box in readBoxes(slide, widthDp, heightDp)) {
                shapes += boxOf(box, top, widthDp)
                top += box.heightDp + GAP_DP
            }
        }
        return PptxSlide(
            index = index,
            shapes = shapes,
            background = null,
            notes = null,
        )
    }

    /**
     * The shapes of a group are the shapes of the slide.
     *
     * The position of a shape of a group is the one of the file, the group
     * itself does not move them.
     */
    private fun shapesOf(shape: HSLFShape, into: MutableList<PptxShape>, top: Float) {
        when (shape) {
            is HSLFPictureShape -> pictureOf(shape)?.let { into += it }
            is HSLFTable -> tableOf(shape)?.let { into += it }
            is HSLFTextShape -> textOf(shape, top)?.let { box ->
                into += box
            }

            is HSLFGroupShape -> runCatching { shape.shapes }.getOrNull()
                ?.forEach { shapesOf(it, into, top) }

            is HSLFSimpleShape -> filledOf(shape)?.let { into += it }
            else -> Unit
        }
    }

    /**
     * The table of a slide.
     *
     * The cells of the table are drawn with their own text, the borders of
     * the cells are not drawn.
     */
    private fun tableOf(shape: HSLFTable): PptxShape.Table? {
        val rows = runCatching { shape.numberOfRows }.getOrNull() ?: return null
        val columns = runCatching { shape.numberOfColumns }.getOrNull() ?: return null
        if (rows <= 0 || columns <= 0) return null
        val cells = ArrayList<List<PptxTableCell>>()
        for (row in 0 until rows) {
            val line = ArrayList<PptxTableCell>()
            for (column in 0 until columns) {
                val cell = runCatching { shape.getCell(row, column) }.getOrNull() ?: continue
                val text = runCatching { cell.text }.getOrNull().orEmpty()
                line += PptxTableCell(
                    text = text,
                    bold = false,
                    color = null,
                    background = null,
                    alignment = "left",
                )
            }
            cells += line
        }
        val anchor = runCatching { shape.anchor }.getOrNull() ?: return null
        val columnWidths = (0 until columns).map { column ->
            val width = runCatching { shape.getColumnWidth(column) }.getOrDefault(0.0)
            (width / POINTS_PER_DP).toFloat()
        }
        val rowHeights = (0 until rows).map { row ->
            val height = runCatching { shape.getRowHeight(row) }.getOrDefault(0.0)
            (height / POINTS_PER_DP).toFloat()
        }
        return PptxShape.Table(
            rows = cells,
            columnWidthsDp = columnWidths,
            rowHeightsDp = rowHeights,
            leftDp = (anchor.getX() / POINTS_PER_DP).toFloat(),
            topDp = (anchor.getY() / POINTS_PER_DP).toFloat(),
            widthDp = (anchor.getWidth() / POINTS_PER_DP).toFloat(),
            heightDp = (anchor.getHeight() / POINTS_PER_DP).toFloat(),
        )
    }

    /**
     * The picture of a shape.
     *
     * The bytes of the picture are the ones of the file, the reader decodes
     * them when it paints the shape.
     */
    private fun pictureOf(shape: HSLFPictureShape): PptxShape.Picture? {
        val bytes = runCatching { shape.pictureData?.rawData }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val anchor = runCatching { shape.anchor }.getOrNull() ?: return null
        return PptxShape.Picture(
            bytes = bytes,
            leftDp = (anchor.getX() / POINTS_PER_DP).toFloat(),
            topDp = (anchor.getY() / POINTS_PER_DP).toFloat(),
            widthDp = (anchor.getWidth() / POINTS_PER_DP).toFloat(),
            heightDp = (anchor.getHeight() / POINTS_PER_DP).toFloat(),
        )
    }

    /**
     * The filled shape of a slide.
     *
     * A shape that has no colour is not painted at all, so only the shapes
     * that stand out in the slide are drawn.
     */
    private fun filledOf(shape: HSLFSimpleShape): PptxShape.Rectangle? {
        val fill = runCatching { shape.lineBackgroundColor }.getOrNull() ?: return null
        val line = runCatching { shape.lineColor }.getOrNull()
        val anchor = runCatching { shape.anchor }.getOrNull() ?: return null
        return PptxShape.Rectangle(
            leftDp = (anchor.getX() / POINTS_PER_DP).toFloat(),
            topDp = (anchor.getY() / POINTS_PER_DP).toFloat(),
            widthDp = (anchor.getWidth() / POINTS_PER_DP).toFloat(),
            heightDp = (anchor.getHeight() / POINTS_PER_DP).toFloat(),
            fill = colorOf(fill),
            line = line?.let { colorOf(it) },
            lineWidthDp = runCatching { shape.lineWidth }.getOrDefault(1.0).toFloat(),
            preset = "rect",
            rotation = 0f,
        )
    }

    /** The colour of Java, as the colour of the office documents. */
    private fun colorOf(color: java.awt.Color): OfficeColor = OfficeColor(
        red = color.red / 255f,
        green = color.green / 255f,
        blue = color.blue / 255f,
        alpha = color.alpha / 255f,
    )

    /** The text box of a shape. */
    private fun textOf(shape: HSLFTextShape, top: Float): PptxShape.TextBox? {
        val text = runCatching { shape.text }.getOrNull().orEmpty()
        if (text.isBlank()) return null
        val anchor = runCatching { shape.anchor }.getOrNull()
        val lines = text.split('\n', '\u000B').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val left = anchor?.let { (it.getX() / POINTS_PER_DP).toFloat() } ?: MARGIN_DP
        val topDp = anchor?.let { (it.getY() / POINTS_PER_DP).toFloat() } ?: top
        val width = anchor?.let { (it.getWidth() / POINTS_PER_DP).toFloat() }
            ?.takeIf { it > 0f } ?: (DEFAULT_WIDTH_DP - MARGIN_DP * 2)
        val height = anchor?.let { (it.getHeight() / POINTS_PER_DP).toFloat() }
            ?.takeIf { it > 0f } ?: (lines.size * LINE_HEIGHT_DP)
        return PptxShape.TextBox(
            paragraphs = paragraphsOf(shape, text),
            leftDp = left,
            topDp = topDp,
            widthDp = width,
            heightDp = height,
            anchor = "t",
            fill = null,
        )
    }

    /**
     * The paragraphs of a text shape.
     *
     * The size and the weight of the text are the ones of the file, so the
     * text keeps the place it has in the slide.
     */
    private fun paragraphsOf(shape: HSLFTextShape, text: String): List<PptxParagraph> {
        val paragraphs = runCatching { shape.textParagraphs }.getOrNull().orEmpty()
        if (paragraphs.isEmpty()) return paragraphsOf(text)
        val result = ArrayList<PptxParagraph>()
        for (paragraph in paragraphs) {
            val runs = runCatching { paragraph.textRuns }.getOrNull().orEmpty()
            if (runs.isEmpty()) continue
            val value = runs.joinToString("") { runCatching { it.rawText }.getOrDefault("") }
            if (value.isBlank()) continue
            val size = runs.firstNotNullOfOrNull { runCatching { it.fontSize }.getOrNull() }
                ?.takeIf { it > 0.0 }
                ?.toFloat()
                ?: DEFAULT_SIZE_PT
            result += PptxParagraph(
                text = value,
                bullet = null,
                level = 0,
                style = PptxTextStyle(
                    sizePt = size,
                    bold = runs.any { runCatching { it.isBold }.getOrDefault(false) },
                    italic = runs.any { runCatching { it.isItalic }.getOrDefault(false) },
                    underline = runs.any { runCatching { it.isUnderlined }.getOrDefault(false) },
                    color = null,
                    alignment = "left",
                    fontFamily = runs.firstNotNullOfOrNull { runCatching { it.fontFamily }.getOrNull() },
                ),
            )
        }
        return result.ifEmpty { paragraphsOf(text) }
    }

    /** The paragraphs of a text. */
    private fun paragraphsOf(text: String): List<PptxParagraph> {
        val style = PptxTextStyle(
            sizePt = DEFAULT_SIZE_PT,
            bold = false,
            italic = false,
            underline = false,
            color = null,
            alignment = "left",
            fontFamily = null,
        )
        return text.split('\n', '\u000B')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> PptxParagraph(text = line, bullet = null, level = 0, style = style) }
    }

    /** A text box of a slide, with the position of the file. */
    private class SlideBox(
        val text: String,
        val leftDp: Float,
        val topDp: Float,
        val widthDp: Float,
        val heightDp: Float,
        val positioned: Boolean,
    )

    /**
     * The text boxes of a slide.
     *
     * They are read out of the shapes of the slide. When the shapes cannot be
     * read, the text is taken out of the records of the file.
     */
    private fun readBoxes(slide: HSLFSlide, widthDp: Float, heightDp: Float): List<SlideBox> {
        val boxes = ArrayList<SlideBox>()
        val slideShapes = runCatching { slide.shapes }.getOrNull()
        if (slideShapes != null) {
            for (shape in slideShapes) {
                if (shape !is HSLFTextShape) continue
                val text = runCatching { shape.text }.getOrNull().orEmpty()
                if (text.isBlank()) continue
                val anchor = runCatching { shape.anchor }.getOrNull()
                val lines = text.split('\n', '\u000B').count { it.isNotBlank() }
                boxes += SlideBox(
                    text = text,
                    leftDp = anchor?.let { (it.getX() / POINTS_PER_DP).toFloat() } ?: MARGIN_DP,
                    topDp = anchor?.let { (it.getY() / POINTS_PER_DP).toFloat() } ?: 0f,
                    widthDp = anchor?.let { (it.getWidth() / POINTS_PER_DP).toFloat() }
                        ?.takeIf { it > 0f } ?: (widthDp - MARGIN_DP * 2),
                    heightDp = anchor?.let { (it.getHeight() / POINTS_PER_DP).toFloat() }
                        ?.takeIf { it > 0f } ?: (lines * LINE_HEIGHT_DP),
                    positioned = anchor != null,
                )
            }
        }
        if (boxes.isNotEmpty()) return boxes
        return boxesOfRecords(slide, widthDp, heightDp)
    }

    /**
     * The text of a slide, read out of the records of the file.
     *
     * It is the way out for a deck that POI cannot read: every text atom of
     * the slide is taken, so the content of the slide is complete.
     */
    private fun boxesOfRecords(slide: HSLFSlide, widthDp: Float, heightDp: Float): List<SlideBox> {
        val container = runCatching { slide.getSlideRecord() }.getOrNull() ?: return emptyList()
        val texts = ArrayList<String>()
        collectText(container, texts)
        if (texts.isEmpty()) return emptyList()
        val result = ArrayList<SlideBox>()
        var top = MARGIN_DP
        for (text in texts) {
            if (text.isBlank()) continue
            result += SlideBox(
                text = text,
                leftDp = MARGIN_DP,
                topDp = top,
                widthDp = widthDp - MARGIN_DP * 2,
                heightDp = text.split('\n').size * LINE_HEIGHT_DP,
                positioned = true,
            )
            top += GAP_DP
        }
        return result
    }

    /** Collects the text atoms of a record and of the records below it. */
    private fun collectText(record: Record, into: MutableList<String>) {
        when (record) {
            is TextCharsAtom -> into += record.text
            is TextBytesAtom -> into += record.text
            is RecordContainer -> record.childRecords.forEach { collectText(it, into) }
            else -> Unit
        }
    }

    /** The text box of a slide. */
    private fun boxOf(box: SlideBox, top: Float, widthDp: Float): PptxShape.TextBox = PptxShape.TextBox(
        paragraphs = paragraphsOf(box.text),
        leftDp = if (box.positioned) box.leftDp else MARGIN_DP,
        topDp = if (box.positioned) box.topDp else top,
        widthDp = box.widthDp.takeIf { it > 0f } ?: (widthDp - MARGIN_DP * 2),
        heightDp = box.heightDp.takeIf { it > 0f } ?: LINE_HEIGHT_DP,
        anchor = "t",
        fill = null,
    )

    private const val TAG = "LegacyPresentation"
}
