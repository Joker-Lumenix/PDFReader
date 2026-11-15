package com.peng.pdfreader.office.pptx

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peng.pdfreader.ui.components.CountChip

private const val PT_TO_DP = 4f / 3f
private const val INDENT_DP_PER_LEVEL = 24f
private const val THUMB_WIDTH_DP = 92f
private const val TABLE_TEXT_SIZE_PT = 18f
private const val GRID_COLOR = 0xFFBFBFBF

/**
 * Slide viewer for a [PptxDeck].
 *
 * The current slide is drawn on a white canvas with the deck aspect ratio,
 * scaled to fit the available width, and a rail with the thumbnails of every
 * slide allows switching between them.
 */
@Composable
fun PptxPreview(deck: PptxDeck, modifier: Modifier = Modifier) {
    val slides = deck.slides
    var currentIndex by remember(deck) { mutableIntStateOf(0) }
    if (slides.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "空演示文稿",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val index = currentIndex.coerceIn(0, slides.lastIndex)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            PptxHeader(deck, slides.size)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SlideCanvas(deck, slides[index], Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        CountChip("${index + 1} / ${slides.size}")
                        Spacer(Modifier.height(16.dp))
                    }
                    PptxThumbnailRail(
                        deck = deck,
                        slides = slides,
                        currentIndex = index,
                        onSelect = { currentIndex = it },
                        vertical = true,
                        modifier = Modifier
                            .width(112.dp)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SlideCanvas(deck, slides[index], Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    CountChip("${index + 1} / ${slides.size}")
                    Spacer(Modifier.height(12.dp))
                }
                PptxThumbnailRail(
                    deck = deck,
                    slides = slides,
                    currentIndex = index,
                    onSelect = { currentIndex = it },
                    vertical = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PptxHeader(deck: PptxDeck, slideCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = deck.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        CountChip("$slideCount 页")
    }
}

// ------------------------------------------------------------------ canvas

@Composable
private fun SlideCanvas(deck: PptxDeck, slide: PptxSlide, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val available = maxWidth
        val scale = if (deck.widthDp > 0f && available.value > 0f) {
            available.value / deck.widthDp
        } else {
            1f
        }
        val width = (deck.widthDp * scale).coerceAtLeast(1f)
        val height = (deck.heightDp * scale).coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .size(width.dp, height.dp)
                .shadow(8.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(2.dp))
                .background(slide.background ?: Color.White),
        ) {
            SlideContent(slide, scale)
        }
    }
}

/**
 * Places every shape of a slide absolutely, using the deck coordinates
 * multiplied by [scale]. The shapes never intercept touches.
 */
@Composable
private fun SlideContent(slide: PptxSlide, scale: Float) {
    val density = LocalDensity.current
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            slide.shapes.forEach { shape ->
                when (shape) {
                    is PptxShape.TextBox -> SlideTextBox(shape, scale)
                    is PptxShape.Picture -> SlidePicture(shape)
                    is PptxShape.Rectangle -> SlideRectangle(shape, scale)
                    is PptxShape.Table -> SlideTable(shape, scale)
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { position, measurable ->
            val shape = slide.shapes.getOrNull(position)
            val widthPx = with(density) { ((shape?.widthDp ?: 0f) * scale).dp.roundToPx() }
            val heightPx = with(density) { ((shape?.heightDp ?: 0f) * scale).dp.roundToPx() }
            val boxConstraints = if (widthPx > 0 && heightPx > 0) {
                Constraints.fixed(widthPx, heightPx)
            } else {
                Constraints(
                    minWidth = widthPx.coerceAtLeast(0),
                    maxWidth = widthPx.coerceAtLeast(0),
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
            }
            measurable.measure(boxConstraints)
        }
        val layoutWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeables.maxOfOrNull { it.width } ?: 0
        }
        val layoutHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            placeables.maxOfOrNull { it.height } ?: 0
        }
        layout(layoutWidth, layoutHeight) {
            placeables.forEachIndexed { position, placeable ->
                val shape = slide.shapes.getOrNull(position) ?: return@forEachIndexed
                val x = with(density) { (shape.leftDp * scale).dp.roundToPx() }
                val y = with(density) { (shape.topDp * scale).dp.roundToPx() }
                placeable.place(x, y)
            }
        }
    }
}

// ------------------------------------------------------------------ shapes

@Composable
private fun SlideTextBox(shape: PptxShape.TextBox, scale: Float) {
    Box(Modifier.fillMaxSize()) {
        if (shape.fill != null) {
            Box(Modifier.fillMaxSize().background(shape.fill))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (4f * scale).dp, vertical = (2f * scale).dp),
            verticalArrangement = when (shape.anchor) {
                "center" -> Arrangement.Center
                "bottom" -> Arrangement.Bottom
                else -> Arrangement.Top
            },
        ) {
            shape.paragraphs.forEach { paragraph ->
                SlideParagraph(paragraph, scale)
            }
        }
    }
}

@Composable
private fun SlideParagraph(paragraph: PptxParagraph, scale: Float) {
    val style = paragraph.style
    val color = style.color ?: MaterialTheme.colorScheme.onSurface
    val textStyle = TextStyle(
        fontSize = (style.sizePt * PT_TO_DP * scale).coerceAtLeast(1f).sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
        color = color,
        fontFamily = fontFamilyOf(style.fontFamily),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (INDENT_DP_PER_LEVEL * paragraph.level * scale).dp),
        horizontalArrangement = when (style.alignment) {
            "center" -> Arrangement.Center
            "right" -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.Top,
    ) {
        val bullet = paragraph.bullet
        if (bullet != null) {
            Text(
                text = bullet,
                style = textStyle,
                modifier = Modifier.padding(end = (4f * scale).dp),
            )
        }
        Text(
            text = paragraph.text,
            style = textStyle,
            textAlign = when (style.alignment) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.Right
                "justify" -> TextAlign.Justify
                else -> TextAlign.Start
            },
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun SlidePicture(shape: PptxShape.Picture) {
    val bitmap = remember(shape.bytes) {
        runCatching {
            BitmapFactory.decodeByteArray(shape.bytes, 0, shape.bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) {
        Box(Modifier.fillMaxSize().background(Color(0x22000000)))
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SlideRectangle(shape: PptxShape.Rectangle, scale: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(rotationZ = shape.rotation),
    ) {
        val strokeWidth = (shape.lineWidthDp * scale).dp.toPx().coerceAtLeast(0.6f)
        val fill = shape.fill
        val line = shape.line
        val preset = shape.preset
        when {
            preset == "ellipse" -> {
                if (fill != null) drawOval(color = fill)
                if (line != null) drawOval(color = line, style = Stroke(width = strokeWidth))
            }
            preset == "triangle" || preset == "rtTriangle" -> {
                val path = Path()
                if (preset == "triangle") {
                    path.moveTo(size.width / 2f, 0f)
                    path.lineTo(size.width, size.height)
                    path.lineTo(0f, size.height)
                } else {
                    path.moveTo(0f, 0f)
                    path.lineTo(size.width, 0f)
                    path.lineTo(size.width, size.height)
                }
                path.close()
                if (fill != null) drawPath(path, color = fill)
                if (line != null) drawPath(path, color = line, style = Stroke(width = strokeWidth))
            }
            preset.startsWith("round") || preset.startsWith("snip") -> {
                val radius = CornerRadius(minOf(size.width, size.height) * 0.18f)
                if (fill != null) drawRoundRect(color = fill, cornerRadius = radius)
                if (line != null) {
                    drawRoundRect(color = line, cornerRadius = radius, style = Stroke(width = strokeWidth))
                }
            }
            else -> {
                if (fill != null) drawRect(color = fill)
                if (line != null) drawRect(color = line, style = Stroke(width = strokeWidth))
            }
        }
    }
}

@Composable
private fun SlideTable(shape: PptxShape.Table, scale: Float) {
    val gridColor = Color(GRID_COLOR)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val widths = shape.columnWidthsDp.map { (it * scale).dp.toPx() }
            val heights = shape.rowHeightsDp.map { (it * scale).dp.toPx() }
            var bottom = 0f
            shape.rows.forEachIndexed { rowIndex, row ->
                var left = 0f
                row.forEachIndexed { cellIndex, cell ->
                    val cellWidth = widths.getOrElse(cellIndex) { 0f }
                    val cellHeight = heights.getOrElse(rowIndex) { 0f }
                    cell.background?.let {
                        drawRect(
                            color = it,
                            topLeft = Offset(left, bottom),
                            size = Size(cellWidth, cellHeight),
                        )
                    }
                    left += cellWidth
                }
                bottom += heights.getOrElse(rowIndex) { 0f }
            }
            val stroke = 0.75.dp.toPx()
            var lineX = 0f
            widths.forEach { columnWidth ->
                drawLine(
                    color = gridColor,
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, bottom),
                    strokeWidth = stroke,
                )
                lineX += columnWidth
            }
            var lineY = 0f
            heights.forEach { rowHeight ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, lineY),
                    end = Offset(lineX, lineY),
                    strokeWidth = stroke,
                )
                lineY += rowHeight
            }
        }
        Column(Modifier.fillMaxSize()) {
            shape.rows.forEachIndexed { rowIndex, row ->
                val rawHeight = shape.rowHeightsDp.getOrElse(rowIndex) { 0f } * scale
                val rowHeight = (if (rawHeight <= 0f) 12f else rawHeight).dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEachIndexed { cellIndex, cell ->
                        val cellWidth = (shape.columnWidthsDp.getOrElse(cellIndex) { 0f } * scale).dp
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .padding(horizontal = (3f * scale).dp),
                            contentAlignment = when (cell.alignment) {
                                "center" -> Alignment.Center
                                "right" -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            },
                        ) {
                            Text(
                                text = cell.text,
                                style = TextStyle(
                                    fontSize = (TABLE_TEXT_SIZE_PT * PT_TO_DP * scale).coerceAtLeast(1f).sp,
                                    fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                    color = cell.color ?: MaterialTheme.colorScheme.onSurface,
                                    textAlign = when (cell.alignment) {
                                        "center" -> TextAlign.Center
                                        "right" -> TextAlign.Right
                                        "justify" -> TextAlign.Justify
                                        else -> TextAlign.Start
                                    },
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fontFamilyOf(typeface: String?): FontFamily {
    val name = typeface?.lowercase()?.trim().orEmpty()
    if (name.isEmpty()) return FontFamily.Default
    return when {
        name.contains("courier") || name.contains("mono") -> FontFamily.Monospace
        name.contains("times") -> FontFamily.Serif
        name.contains("script") || name.contains("brush") -> FontFamily.Cursive
        name.contains("serif") && !name.contains("sans") -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
}

// -------------------------------------------------------------- thumbnails

@Composable
private fun PptxThumbnailRail(
    deck: PptxDeck,
    slides: List<PptxSlide>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    if (vertical) {
        LazyColumn(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(slides.size) { index ->
                SlideThumbnail(deck, slides[index], index, index == currentIndex, primary, onSelect)
            }
        }
    } else {
        LazyRow(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(slides.size) { index ->
                SlideThumbnail(deck, slides[index], index, index == currentIndex, primary, onSelect)
            }
        }
    }
}

@Composable
private fun SlideThumbnail(
    deck: PptxDeck,
    slide: PptxSlide,
    index: Int,
    active: Boolean,
    primary: Color,
    onSelect: (Int) -> Unit,
) {
    val ratio = if (deck.widthDp > 0f) deck.heightDp / deck.widthDp else 0.5625f
    val width = THUMB_WIDTH_DP.dp
    val height = (THUMB_WIDTH_DP * ratio).dp
    val scale = if (deck.widthDp > 0f) THUMB_WIDTH_DP / deck.widthDp else 0.08f
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(slide.background ?: Color.White)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) primary else Color(0x33000000),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable { onSelect(index) },
    ) {
        SlideContent(slide, scale)
    }
}
