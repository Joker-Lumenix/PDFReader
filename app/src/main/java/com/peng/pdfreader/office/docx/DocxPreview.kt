package com.peng.pdfreader.office.docx

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val PageTextColor = Color(0xFF1A1A1A)
private val TableBorderColor = Color(0xFFD0D0D0)
private val PageBorderColor = Color(0xFFE2E2E2)

/**
 * Renders a parsed Word document the way Word lays a page out: a white sheet
 * with the document margins, the blocks stacked vertically and the text
 * formatted run by run.
 */
@Composable
fun DocxPreview(document: DocxDocument, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val pageWidthPt = if (document.pageWidthPt > 0f) document.pageWidthPt else 595f
        val scale = if (pageWidthPt > 0f) min(1f, maxWidth.value / pageWidthPt) else 1f
        val pageWidth = (pageWidthPt * scale).dp
        val contentWidth = (
            pageWidth - ((document.marginLeftPt + document.marginRightPt) * scale).dp
            ).coerceAtLeast(32.dp)

        Column(
            modifier = Modifier
                .width(pageWidth)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(3.dp), clip = false)
                .background(Color.White)
                .border(1.dp, PageBorderColor)
                .padding(
                    start = (document.marginLeftPt * scale).dp,
                    top = (document.marginTopPt * scale).dp,
                    end = (document.marginRightPt * scale).dp,
                    bottom = (document.marginBottomPt * scale).dp,
                ),
        ) {
            document.blocks.forEach { block ->
                BlockView(
                    block = block,
                    document = document,
                    scale = scale,
                    contentWidth = contentWidth,
                )
            }
        }
    }
}

@Composable
private fun BlockView(
    block: DocxBlock,
    document: DocxDocument,
    scale: Float,
    contentWidth: Dp,
) {
    when (block) {
        is DocxBlock.Paragraph -> ParagraphView(block, document, scale, contentWidth)
        is DocxBlock.Table -> TableView(block, document, scale, contentWidth)
        is DocxBlock.Image -> StandaloneImageView(block, scale, contentWidth)
    }
}

// ------------------------------------------------------------------ paragraph

private class ParagraphContent(
    val text: AnnotatedString,
    val images: List<DocxRun>,
)

@Composable
private fun ParagraphView(
    paragraph: DocxBlock.Paragraph,
    document: DocxDocument,
    scale: Float,
    contentWidth: Dp,
) {
    val isTitle = paragraph.styleId == "Title"
    val isSubtitle = paragraph.styleId == "Subtitle"
    val headingMultiplier = when (paragraph.headingLevel) {
        1 -> 1.6f
        2 -> 1.35f
        3 -> 1.15f
        else -> 1f
    }
    val baseSizePt = document.defaultFontPt * when {
        isTitle -> 1.9f
        isSubtitle -> 1.3f
        else -> headingMultiplier
    }
    val headingSpaceBeforePt = when (paragraph.headingLevel) {
        1 -> 12f
        2 -> 8f
        3 -> 6f
        else -> 0f
    }
    val forceBold = isTitle || paragraph.headingLevel in 1..3

    val content = remember(paragraph, baseSizePt, scale) {
        buildParagraphContent(paragraph, baseSizePt, scale, PageTextColor, forceBold)
    }
    val bitmaps = content.images.map { image ->
        remember(image.imageBytes) { decodeImageBitmap(image.imageBytes) }
    }
    val inlineContent = remember(content.images, bitmaps, scale, contentWidth) {
        val map = LinkedHashMap<String, InlineTextContent>()
        content.images.forEachIndexed { index, image ->
            val bitmap = bitmaps.getOrNull(index)
            val size = imageSize(image, contentWidth, scale, bitmap)
            map["docx-image-$index"] = InlineTextContent(
                placeholder = Placeholder(
                    width = size.first.value.sp,
                    height = size.second.value.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                ),
            ) { _ ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        map
    }

    val textAlign = when {
        isTitle -> TextAlign.Center
        paragraph.alignment == "center" -> TextAlign.Center
        paragraph.alignment == "right" -> TextAlign.Right
        paragraph.alignment == "justify" -> TextAlign.Justify
        else -> TextAlign.Left
    }
    val fontSize = (baseSizePt * scale).sp
    val lineHeight = (baseSizePt * scale * paragraph.lineSpacing.coerceAtLeast(0.6f)).sp
    val labelWidth = if (paragraph.listLabel != null) (16f * scale).dp else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (paragraph.indentLevel * 24f * scale).dp,
                top = ((paragraph.spaceBeforePt + headingSpaceBeforePt) * scale).dp,
                bottom = (paragraph.spaceAfterPt * scale).dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            val label = paragraph.listLabel
            if (label != null) {
                Text(
                    text = label,
                    modifier = Modifier.width(labelWidth),
                    color = PageTextColor,
                    fontSize = fontSize,
                    fontWeight = if (forceBold) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = lineHeight,
                )
            }
            Text(
                text = content.text,
                modifier = Modifier.weight(1f),
                color = PageTextColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = textAlign,
                inlineContent = inlineContent,
            )
        }
    }
}

private fun buildParagraphContent(
    paragraph: DocxBlock.Paragraph,
    baseSizePt: Float,
    scale: Float,
    defaultColor: Color,
    forceBold: Boolean,
): ParagraphContent {
    val builder = AnnotatedString.Builder()
    val images = ArrayList<DocxRun>()

    paragraph.runs.forEach { run ->
        if (run.imageBytes != null) {
            val id = "docx-image-${images.size}"
            images += run
            builder.appendInlineContent(id, "\uFFFD")
        } else {
            if (run.text.isEmpty()) return@forEach
            builder.pushStyle(spanStyleOf(run, baseSizePt, scale, defaultColor, forceBold))
            builder.append(run.text)
            builder.pop()
        }
    }

    return ParagraphContent(builder.toAnnotatedString(), images)
}

private fun spanStyleOf(
    run: DocxRun,
    baseSizePt: Float,
    scale: Float,
    defaultColor: Color,
    forceBold: Boolean,
): SpanStyle {
    val rawSize = run.sizePt ?: baseSizePt
    val sizePt = if (run.superscript) rawSize * 0.72f else rawSize
    val decorations = mutableListOf<TextDecoration>()
    if (run.underline) decorations += TextDecoration.Underline
    if (run.strike) decorations += TextDecoration.LineThrough

    return SpanStyle(
        color = run.color ?: defaultColor,
        fontSize = (sizePt * scale).sp,
        fontWeight = if (run.bold || forceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = fontFamilyOf(run.fontFamily),
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        baselineShift = if (run.superscript) BaselineShift.Superscript else null,
    )
}

private fun fontFamilyOf(name: String?): FontFamily? {
    if (name.isNullOrBlank()) return null
    val lower = name.lowercase()
    return when {
        lower.contains("times") || name.contains("宋体") -> FontFamily.Serif
        lower.contains("consolas") || lower.contains("courier") || name.contains("等宽") ->
            FontFamily.Monospace

        else -> FontFamily.Default
    }
}

// --------------------------------------------------------------------- table

@Composable
private fun TableView(
    table: DocxBlock.Table,
    document: DocxDocument,
    scale: Float,
    contentWidth: Dp,
) {
    val widths = table.columnWidthsPt
    val totalPt = widths.sum()
    val factor = if (totalPt > 0f && contentWidth.value > 0f) {
        min(1f, contentWidth.value / totalPt)
    } else {
        1f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        table.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                var column = 0
                row.forEach { cell ->
                    val span = cell.columnSpan.coerceAtLeast(1)
                    var widthPt = 0f
                    for (index in column until minOf(column + span, widths.size)) {
                        widthPt += widths[index]
                    }
                    if (widthPt <= 0f) widthPt = cell.widthPt
                    TableCellView(
                        cell = cell,
                        document = document,
                        scale = scale,
                        width = (widthPt * factor * scale).dp,
                    )
                    column += span
                }
            }
        }
    }
}

@Composable
private fun TableCellView(
    cell: DocxCell,
    document: DocxDocument,
    scale: Float,
    width: Dp,
) {
    val alignment: Alignment = when (cell.verticalAlignment) {
        "center" -> Alignment.Center
        "bottom" -> Alignment.BottomStart
        else -> Alignment.TopStart
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, TableBorderColor)
            .background(cell.background ?: Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = alignment,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            cell.blocks.forEach { block ->
                BlockView(block, document, scale, (width - 8.dp).coerceAtLeast(16.dp))
            }
        }
    }
}

// --------------------------------------------------------------------- image

@Composable
private fun StandaloneImageView(image: DocxBlock.Image, scale: Float, contentWidth: Dp) {
    val bitmap = remember(image.bytes) { decodeImageBitmap(image.bytes) }
    val run = DocxRun(
        text = "",
        imageBytes = image.bytes,
        imageWidthPt = image.widthPt,
        imageHeightPt = image.heightPt,
    )
    val size = imageSize(run, contentWidth, scale, bitmap)
    val alignment: Alignment = if (image.alignment == "center") Alignment.Center else Alignment.TopStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size.first, size.second),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun imageSize(
    run: DocxRun,
    contentWidth: Dp,
    scale: Float,
    bitmap: ImageBitmap?,
): Pair<Dp, Dp> {
    var widthPt = run.imageWidthPt
    var heightPt = run.imageHeightPt
    if (widthPt <= 0f && bitmap != null) {
        widthPt = bitmap.width * 72f / 96f
        heightPt = bitmap.height * 72f / 96f
    }
    if (widthPt <= 0f) return 0.dp to 0.dp
    if (heightPt <= 0f) heightPt = widthPt

    val maxWidthPt = if (scale > 0f) contentWidth.value / scale else contentWidth.value
    if (widthPt > maxWidthPt) {
        heightPt *= maxWidthPt / widthPt
        widthPt = maxWidthPt
    }
    return (widthPt * scale).dp to (heightPt * scale).dp
}

private fun decodeImageBitmap(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bitmap.asImageBitmap()
}
