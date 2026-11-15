package com.peng.pdfreader.office.xlsx

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peng.pdfreader.ui.components.EmptyState

private const val RowHeightDp = 30f
private const val HeaderHeightDp = 28f
private const val GutterWidthDp = 44f
private const val TabBarHeightDp = 40f
private const val MinColumnWidthDp = 44f
private const val MaxColumnWidthDp = 320f
private const val EmptySheetText = "该工作表没有可见单元格"

private val GridLineColor = Color(0xFFD9D9D9)
private val HeaderBandColor = Color(0xFFF2F2F2)
private val DefaultColumnWidth = 100.dp

/**
 * Excel-like preview of a parsed workbook: a pinned column-letter header, a pinned
 * row-number gutter, a virtualised cell grid with merged ranges, and a sheet tab bar.
 */
@Composable
fun XlsxPreview(workbook: XlsxWorkbook, modifier: Modifier = Modifier) {
    val sheets = workbook.sheets
    if (sheets.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = EmptySheetText, hint = "")
        }
        return
    }

    var activeIndex by remember(workbook) {
        mutableStateOf(workbook.activeSheetIndex.coerceIn(0, sheets.lastIndex))
    }
    val sheet = sheets.getOrNull(activeIndex) ?: sheets.first()

    val listStates = remember(workbook) { mutableMapOf<Int, LazyListState>() }
    val scrollStates = remember(workbook) { mutableMapOf<Int, ScrollState>() }
    val listState = remember(activeIndex, workbook) { listStates.getOrPut(activeIndex) { LazyListState() } }
    val horizontalState = remember(activeIndex, workbook) {
        scrollStates.getOrPut(activeIndex) { ScrollState(0) }
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (sheet.rows.isEmpty()) {
                EmptyState(
                    title = EmptySheetText,
                    hint = "",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                SheetGrid(sheet = sheet, listState = listState, horizontalState = horizontalState)
            }
        }
        SheetTabBar(
            sheets = sheets,
            activeIndex = activeIndex,
            onSelect = { activeIndex = it },
        )
    }
}

@Composable
private fun SheetGrid(
    sheet: XlsxSheet,
    listState: LazyListState,
    horizontalState: ScrollState,
) {
    val context = remember(sheet) { buildRenderContext(sheet) }
    Column(Modifier.fillMaxSize()) {
        ColumnHeaderRow(context, horizontalState)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(count = context.blocks.size) { index ->
                RenderBlock(context.blocks[index], context, horizontalState)
            }
        }
    }
}

@Composable
private fun ColumnHeaderRow(context: XlsxRenderContext, horizontalState: ScrollState) {
    Row(
        modifier = Modifier
            .height(HeaderHeightDp.dp)
            .horizontalScroll(horizontalState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(GutterWidthDp.dp)
                .height(HeaderHeightDp.dp)
                .background(HeaderBandColor)
                .drawBehind { drawCellLines(GridLineColor, lineStrokePx()) },
        )
        for (column in 0 until context.visibleColumns) {
            Box(
                modifier = Modifier
                    .width(context.widthOf(column))
                    .height(HeaderHeightDp.dp)
                    .background(HeaderBandColor)
                    .drawBehind { drawCellLines(GridLineColor, lineStrokePx()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = columnLetter(column),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RenderBlock(
    block: XlsxRenderBlock,
    context: XlsxRenderContext,
    horizontalState: ScrollState,
) {
    Box(
        modifier = Modifier
            .height(block.heightDp.dp)
            .horizontalScroll(horizontalState),
    ) {
        Column(Modifier.fillMaxHeight()) {
            block.subRows.forEachIndexed { index, cells ->
                Row(Modifier.height(RowHeightDp.dp)) {
                    GutterNumber(block.rowNumbers.getOrElse(index) { 0 })
                    val reserved = block.reservedColumns.getOrElse(index) { emptySet<Int>() }
                    for (column in 0 until context.visibleColumns) {
                        val cell = cells[column]
                        val anchor = block.anchor
                        val isVerticalAnchor = anchor != null && column == anchor.column && index == 0
                        when {
                            cell == null -> EmptyCell(context.widthOf(column), RowHeightDp.dp)
                            isVerticalAnchor -> Spacer(
                                Modifier.width(context.spanWidth(cell.column, cell.columnSpan)),
                            )
                            cell.isCovered && column in reserved -> Unit
                            cell.isCovered -> Spacer(Modifier.width(context.widthOf(column)))
                            cell.columnSpan > 1 -> CellView(
                                cell = cell,
                                width = context.spanWidth(cell.column, cell.columnSpan),
                                height = RowHeightDp.dp,
                            )
                            else -> CellView(
                                cell = cell,
                                width = context.widthOf(column),
                                height = RowHeightDp.dp,
                            )
                        }
                    }
                }
            }
        }
        val anchor = block.anchor
        if (anchor != null) {
            val span = anchor.columnSpan.coerceAtLeast(1)
            AnchorCell(
                cell = anchor,
                width = context.spanWidth(anchor.column, span),
                height = block.heightDp.dp,
                modifier = Modifier.offset(
                    x = GutterWidthDp.dp + context.offsetOf(anchor.column),
                    y = 0.dp,
                ),
            )
        }
    }
}

@Composable
private fun GutterNumber(number: Int) {
    Box(
        modifier = Modifier
            .width(GutterWidthDp.dp)
            .height(RowHeightDp.dp)
            .background(HeaderBandColor)
            .drawBehind { drawCellLines(GridLineColor, lineStrokePx()) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyCell(width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .drawBehind { drawCellLines(GridLineColor, lineStrokePx()) },
    )
}

@Composable
private fun CellView(cell: XlsxCell, width: Dp, height: Dp) {
    val fill = cell.style.background
    val lineColor = if (fill != null) darken(fill) else GridLineColor
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(fill ?: Color.Transparent)
            .drawBehind { drawCellLines(lineColor, lineStrokePx()) },
    ) {
        CellText(cell)
    }
}

@Composable
private fun AnchorCell(cell: XlsxCell, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val fill = cell.style.background
    val lineColor = if (fill != null) darken(fill) else GridLineColor
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(fill ?: Color.Transparent)
            .drawBehind { drawCellLines(lineColor, lineStrokePx(), left = true) },
    ) {
        CellText(cell)
    }
}

@Composable
private fun BoxScope.CellText(cell: XlsxCell) {
    val style = cell.style
    val textColor = style.fontColor ?: MaterialTheme.colorScheme.onSurface
    val alignment = when (style.horizontal?.lowercase()) {
        "center", "centre" -> TextAlign.Center
        "right" -> TextAlign.End
        "left" -> TextAlign.Start
        else -> if (cell.rawValue.trim().toDoubleOrNull() != null) TextAlign.End else TextAlign.Start
    }
    val vertical = when (style.vertical?.lowercase()) {
        "top" -> Alignment.TopStart
        "bottom" -> Alignment.BottomStart
        else -> Alignment.CenterStart
    }
    Text(
        text = cell.value,
        modifier = Modifier
            .fillMaxWidth()
            .align(vertical)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = textColor,
        fontSize = 12.sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
        textAlign = alignment,
        maxLines = if (style.wrapText) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = style.wrapText,
    )
}

@Composable
private fun SheetTabBar(sheets: List<XlsxSheet>, activeIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TabBarHeightDp.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sheets.forEachIndexed { index, sheet ->
            SheetTab(
                sheet = sheet,
                active = index == activeIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun SheetTab(sheet: XlsxSheet, active: Boolean, onClick: () -> Unit) {
    val labelColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tabColor = sheet.tabColor
            if (tabColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tabColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = sheet.name,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

private class XlsxRenderContext(
    val columnWidths: List<Dp>,
    val blocks: List<XlsxRenderBlock>,
) {
    val visibleColumns: Int = columnWidths.size

    fun widthOf(column: Int): Dp = columnWidths.getOrElse(column) { DefaultColumnWidth }

    fun spanWidth(column: Int, span: Int): Dp {
        var total = 0.dp
        for (index in column until column + span.coerceAtLeast(1)) {
            total += widthOf(index)
        }
        return total
    }

    fun offsetOf(column: Int): Dp {
        var total = 0.dp
        for (index in 0 until column) {
            total += widthOf(index)
        }
        return total
    }
}

private class XlsxRenderBlock(
    val rowNumbers: List<Int>,
    val subRows: List<Map<Int, XlsxCell>>,
    val reservedColumns: List<Set<Int>>,
    val anchor: XlsxCell?,
    val heightDp: Float,
)

private fun buildRenderContext(sheet: XlsxSheet): XlsxRenderContext {
    var visibleColumns = 1
    sheet.rows.forEach { row ->
        row.cells.forEach { cell ->
            val end = if (cell.isCovered) {
                cell.column + 1
            } else {
                cell.column + cell.columnSpan.coerceAtLeast(1)
            }
            if (end > visibleColumns) visibleColumns = end
        }
    }
    val widths = List(visibleColumns) { index ->
        val raw = sheet.columnWidthsDp.getOrNull(index) ?: sheet.defaultColumnWidthDp
        raw.dp.coerceIn(MinColumnWidthDp.dp, MaxColumnWidthDp.dp)
    }

    val blocks = mutableListOf<XlsxRenderBlock>()
    var index = 0
    while (index < sheet.rows.size) {
        val row = sheet.rows[index]
        val anchor = row.cells.firstOrNull { !it.isCovered && it.rowSpan > 1 }
        val span = anchor?.rowSpan?.coerceAtLeast(1) ?: 1
        val end = minOf(index + span, sheet.rows.size)
        val subRows = (index until end).map { current ->
            sheet.rows[current].cells.associateBy { it.column }
        }
        val numbers = (index until end).map { current -> sheet.rows[current].index }
        val reserved = subRows.map { cells ->
            val columns = mutableSetOf<Int>()
            cells.values.forEach { cell ->
                if (!cell.isCovered && cell.columnSpan > 1) {
                    for (offset in 1 until cell.columnSpan) {
                        columns += cell.column + offset
                    }
                }
            }
            columns
        }
        blocks += XlsxRenderBlock(
            rowNumbers = numbers,
            subRows = subRows,
            reservedColumns = reserved,
            anchor = anchor,
            heightDp = (end - index) * RowHeightDp,
        )
        index = end
    }
    return XlsxRenderContext(columnWidths = widths, blocks = blocks)
}

private fun columnLetter(index: Int): String {
    var value = index
    val builder = StringBuilder()
    while (value >= 0) {
        builder.append('A' + (value % 26))
        value = value / 26 - 1
    }
    return builder.reverse().toString()
}

private fun darken(color: Color, factor: Float = 0.72f): Color = Color(
    red = color.red * factor,
    green = color.green * factor,
    blue = color.blue * factor,
    alpha = color.alpha,
)

private fun Density.lineStrokePx(): Float = 0.5.dp.toPx()

private fun DrawScope.drawCellLines(color: Color, stroke: Float, left: Boolean = false) {
    drawLine(
        color = color,
        start = Offset(size.width - stroke / 2f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = stroke,
    )
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2f),
        end = Offset(size.width, size.height - stroke / 2f),
        strokeWidth = stroke,
    )
    if (left) {
        drawLine(
            color = color,
            start = Offset(stroke / 2f, 0f),
            end = Offset(stroke / 2f, size.height),
            strokeWidth = stroke,
        )
    }
}
