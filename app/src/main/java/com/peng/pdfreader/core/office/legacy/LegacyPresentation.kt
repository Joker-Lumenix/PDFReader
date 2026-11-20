package com.peng.pdfreader.core.office.legacy

import com.peng.pdfreader.core.office.pptx.PptxDeck
import com.peng.pdfreader.core.office.pptx.PptxParagraph
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.pptx.PptxSlide
import com.peng.pdfreader.core.office.pptx.PptxTextStyle
import com.peng.pdfreader.core.util.Logger
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import java.io.File

/**
 * Reads the legacy binary format of the presentations (`.ppt`).
 *
 * The format is the compound file of Office 97, the reader of Apache POI
 * opens it. The geometry of the shapes is not read: POI hands it out in
 * `java.awt` types, and Android does not know them. The text boxes of a slide
 * are therefore laid out one after the other.
 */
internal object LegacyPresentation {

    /** The width of a slide of 4:3 in dp. */
    private const val DEFAULT_WIDTH_DP = 960f

    /** The height of a slide of 4:3 in dp. */
    private const val DEFAULT_HEIGHT_DP = 720f

    /** The margin of the text boxes of a slide in dp. */
    private const val MARGIN_DP = 32f

    /** The gap between two text boxes of a slide in dp. */
    private const val GAP_DP = 18f

    /** The size of the text of a shape that does not tell its own size. */
    private const val DEFAULT_SIZE_PT = 18f

    /** The height a line of the text of a shape needs in dp. */
    private const val LINE_HEIGHT_DP = 26f

    /** Reads the deck of a legacy presentation. */
    fun read(file: File): PptxDeck? = runCatching {
        file.inputStream().use { input ->
            val show = HSLFSlideShow(input)
            val slides = show.slides.mapIndexed { index, slide -> slideOf(index, slide) }
            PptxDeck(
                slides = slides,
                widthDp = DEFAULT_WIDTH_DP,
                heightDp = DEFAULT_HEIGHT_DP,
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

    /** Reads one slide of the deck. */
    private fun slideOf(index: Int, slide: org.apache.poi.hslf.usermodel.HSLFSlide): PptxSlide {
        val shapes = ArrayList<PptxShape>()
        var top = MARGIN_DP
        for (shape in slide.shapes) {
            val box = shapeOf(shape, top) ?: continue
            shapes += box
            top += box.heightDp + GAP_DP
        }
        return PptxSlide(
            index = index,
            shapes = shapes,
            background = null,
            notes = null,
        )
    }

    /** Reads one shape of a slide. */
    private fun shapeOf(shape: HSLFShape, topDp: Float): PptxShape.TextBox? {
        val textShape = shape as? HSLFTextShape ?: return null
        val text = textShape.text?.takeIf { it.isNotBlank() } ?: return null
        val style = PptxTextStyle(
            sizePt = DEFAULT_SIZE_PT,
            bold = false,
            italic = false,
            underline = false,
            color = null,
            alignment = "left",
            fontFamily = null,
        )
        val lines = text.split('\n', '\u000B').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val paragraphs = lines.map { line ->
            PptxParagraph(
                text = line,
                bullet = null,
                level = 0,
                style = style,
            )
        }
        return PptxShape.TextBox(
            paragraphs = paragraphs,
            leftDp = MARGIN_DP,
            topDp = topDp,
            widthDp = DEFAULT_WIDTH_DP - MARGIN_DP * 2,
            heightDp = lines.size * LINE_HEIGHT_DP,
            anchor = "t",
            fill = null,
        )
    }

    private const val TAG = "LegacyPresentation"
}
