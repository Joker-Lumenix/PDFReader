package com.peng.pdfreader.core.office.legacy

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.xlsx.XlsxCell
import com.peng.pdfreader.core.office.xlsx.XlsxCellStyle
import com.peng.pdfreader.core.office.xlsx.XlsxRow
import com.peng.pdfreader.core.office.xlsx.XlsxSheet
import com.peng.pdfreader.core.office.xlsx.XlsxWorkbook
import com.peng.pdfreader.core.util.Logger
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import java.io.File

/**
 * Reads the legacy binary format of the workbooks (`.xls`).
 *
 * The format is the compound file of Office 97, the reader of Apache POI
 * opens it. The values, the formulas and the styles of the cells are taken
 * out of the records of the file.
 */
internal object LegacyWorkbook {

    /** The dp a column of the default width is wide. */
    private const val DEFAULT_COLUMN_DP = 48f

    /** The dp a character of a column is wide. */
    private const val DP_PER_CHARACTER = 5.5f

    /** The number of rows that are read, a spreadsheet is endless. */
    private const val MAX_ROWS = 600

    /** The number of columns that are read. */
    private const val MAX_COLUMNS = 80

    /** Reads the workbook of a legacy spreadsheet. */
    fun read(file: File): XlsxWorkbook? = runCatching {
        file.inputStream().use { input ->
            val book = HSSFWorkbook(input)
            val sheets = (0 until book.numberOfSheets).map { index -> sheetOf(book, index) }
            XlsxWorkbook(sheets = sheets, activeSheetIndex = book.activeSheetIndex)
        }
    }.onFailure {
        Logger.e(TAG, "the legacy workbook could not be read", it)
    }.getOrNull()

    /** Reads one sheet of the workbook. */
    private fun sheetOf(book: HSSFWorkbook, index: Int): XlsxSheet {
        val sheet = book.getSheetAt(index)
        val rows = ArrayList<XlsxRow>()
        val first = sheet.firstRowNum
        val last = minOf(sheet.lastRowNum, first + MAX_ROWS)
        for (number in first..last) {
            val row = sheet.getRow(number) ?: continue
            val cells = cellsOf(book, row)
            if (cells.isEmpty()) continue
            rows += XlsxRow(
                index = number + 1,
                heightDp = row.height / 20f,
                cells = cells,
            )
        }
        val columns = (0 until minOf(MAX_COLUMNS, maxColumnOf(sheet))).map { column ->
            sheet.getColumnWidth(column) / 256f * DP_PER_CHARACTER
        }
        return XlsxSheet(
            name = sheet.sheetName,
            rows = rows,
            columnWidthsDp = columns,
            defaultColumnWidthDp = DEFAULT_COLUMN_DP,
            frozenRows = 0,
            frozenColumns = 0,
            tabColor = null,
        )
    }

    /** The number of the columns of the sheet. */
    private fun maxColumnOf(sheet: org.apache.poi.hssf.usermodel.HSSFSheet): Int {
        var max = 0
        for (number in sheet.firstRowNum..minOf(sheet.lastRowNum, sheet.firstRowNum + MAX_ROWS)) {
            val row = sheet.getRow(number) ?: continue
            max = maxOf(max, row.lastCellNum.toInt())
        }
        return max
    }

    /** The cells of a row. */
    private fun cellsOf(book: HSSFWorkbook, row: Row): List<XlsxCell> {
        val cells = ArrayList<XlsxCell>()
        val last = minOf(row.lastCellNum.toInt(), MAX_COLUMNS)
        for (column in 0 until last) {
            val cell = row.getCell(column) ?: continue
            val value = valueOf(cell)
            if (value.isEmpty()) continue
            cells += XlsxCell(
                column = column,
                row = row.rowNum + 1,
                value = value,
                rawValue = value,
                formula = formulaOf(cell),
                style = styleOf(book, cell),
                columnSpan = 1,
                rowSpan = 1,
                isCovered = false,
            )
        }
        return cells
    }

    /** The text of a cell. */
    private fun valueOf(cell: Cell): String {
        val type = if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType
        return when (type) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.ERROR -> "#error"
            else -> ""
        }
    }

    /** The formula of a cell. */
    private fun formulaOf(cell: Cell): String? {
        if (cell.cellType != CellType.FORMULA) return null
        return runCatching { cell.cellFormula }.getOrNull()
    }

    /** The style of a cell. */
    private fun styleOf(book: HSSFWorkbook, cell: Cell): XlsxCellStyle {
        val style = cell.cellStyle as? HSSFCellStyle
        val font = runCatching { book.getFontAt(style?.fontIndex ?: 0) }.getOrNull()
        val background = runCatching { style?.fillForegroundColorColor?.triplet }.getOrNull()
        return XlsxCellStyle(
            bold = font?.bold == true,
            italic = font?.italic == true,
            underline = font?.underline?.toInt() == 1,
            fontColor = null,
            background = background?.let { triplet ->
                OfficeColor(
                    red = (triplet[0].toInt() and 0xFF) / 255f,
                    green = (triplet[1].toInt() and 0xFF) / 255f,
                    blue = (triplet[2].toInt() and 0xFF) / 255f,
                    alpha = 1f,
                )
            },
            horizontal = null,
            vertical = null,
            wrapText = style?.wrapText == true,
            numberFormat = null,
            borders = emptySet(),
        )
    }

    private const val TAG = "LegacyWorkbook"
}
