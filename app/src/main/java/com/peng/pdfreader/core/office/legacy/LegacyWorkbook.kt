package com.peng.pdfreader.core.office.legacy

import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.xlsx.XlsxCell
import com.peng.pdfreader.core.office.xlsx.XlsxCellStyle
import com.peng.pdfreader.core.office.xlsx.XlsxRow
import com.peng.pdfreader.core.office.xlsx.XlsxSheet
import com.peng.pdfreader.core.office.xlsx.XlsxWorkbook
import com.peng.pdfreader.core.util.Logger
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory
import org.apache.poi.hssf.eventusermodel.HSSFListener
import org.apache.poi.hssf.eventusermodel.HSSFRequest
import org.apache.poi.hssf.record.BOFRecord
import org.apache.poi.hssf.record.BoundSheetRecord
import org.apache.poi.hssf.record.CellRecord
import org.apache.poi.hssf.record.ColumnInfoRecord
import org.apache.poi.hssf.record.EOFRecord
import org.apache.poi.hssf.record.FormulaRecord
import org.apache.poi.hssf.record.LabelSSTRecord
import org.apache.poi.hssf.record.NumberRecord
import org.apache.poi.hssf.record.Record
import org.apache.poi.hssf.record.RowRecord
import org.apache.poi.hssf.record.SSTRecord
import org.apache.poi.hssf.record.StringRecord
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import java.io.File
import java.io.FileInputStream

/**
 * Reads the legacy binary format of the workbooks (`.xls`).
 *
 * The format is the compound file of Office 97. The reader of Apache POI
 * opens it in two ways: the model of the workbook, which reads every cell of
 * every sheet into the memory, and the records of the file, which are read
 * one after the other.
 *
 * A big workbook does not fit into the memory of the phone: the model of a
 * spreadsheet of nine megabytes needs a minute and more. So the workbook is
 * read with the records of the file, the reader keeps the rows and the
 * columns that it shows and lets the rest of the file go by.
 *
 * The model of POI is the way out, when the records cannot be read.
 */
internal object LegacyWorkbook {

    /** The dp a column of the default width is wide. */
    private const val DEFAULT_COLUMN_DP = 48f

    /** The dp a character of a column is wide. */
    private const val DP_PER_CHARACTER = 5.5f

    /** The number of rows that are read, a spreadsheet is endless. */
    private const val MAX_ROWS = 2000

    /** The number of columns that are read. */
    private const val MAX_COLUMNS = 128

    private const val TAG = "LegacyWorkbook"

    /**
     * Reads the workbook of a legacy spreadsheet.
     *
     * The records of the file are read first: only the rows and the columns
     * that the reader shows are kept, so a big workbook does not fill the
     * memory of the phone. The model of POI is the way out, when the records
     * cannot be read.
     */
    fun read(file: File): XlsxWorkbook? {
        val started = System.currentTimeMillis()
        val streamed = runCatching { readRecords(file) }.getOrNull()
        if (streamed != null && streamed.hasContent()) {
            Logger.d(TAG, "the workbook was read in ${System.currentTimeMillis() - started} ms")
            return streamed
        }
        Logger.e(TAG, "the records of the workbook could not be read")
        return readWithModel(file)
    }

    /**
     * True when the workbook holds the cells of its sheets.
     *
     * A file whose records cannot be read is read with the model of POI.
     */
    private fun XlsxWorkbook.hasContent(): Boolean =
        sheets.any { sheet -> sheet.rows.any { row -> row.cells.isNotEmpty() } }

    /** Reads the workbook out of the records of the file. */
    private fun readRecords(file: File): XlsxWorkbook? {
        val reader = RecordReader()
        val request = HSSFRequest()
        request.addListenerForAllRecords(reader)
        POIFSFileSystem(FileInputStream(file)).use { storage ->
            HSSFEventFactory().processWorkbookEvents(request, storage)
        }
        return reader.build()
    }

    /** Reads the workbook with the model of POI. */
    private fun readWithModel(file: File): XlsxWorkbook? = runCatching {
        file.inputStream().use { input ->
            val book = org.apache.poi.hssf.usermodel.HSSFWorkbook(input)
            val styles = HashMap<Int, XlsxCellStyle>()
            val sheets = (0 until book.numberOfSheets).map { index -> sheetOf(book, index, styles) }
            XlsxWorkbook(sheets = sheets, activeSheetIndex = book.activeSheetIndex)
        }
    }.onFailure {
        Logger.e(TAG, "the legacy workbook could not be read", it)
    }.getOrNull()

    /** Reads one sheet of the workbook with the model of POI. */
    private fun sheetOf(
        book: org.apache.poi.hssf.usermodel.HSSFWorkbook,
        index: Int,
        styles: HashMap<Int, XlsxCellStyle>,
    ): XlsxSheet {
        val sheet = book.getSheetAt(index)
        val rows = ArrayList<XlsxRow>()
        val first = sheet.firstRowNum
        val last = minOf(sheet.lastRowNum, first + MAX_ROWS)
        for (number in first..last) {
            val row = sheet.getRow(number) ?: continue
            val cells = cellsOf(book, row, styles)
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

    /** The cells of a row, read with the model of POI. */
    private fun cellsOf(
        book: org.apache.poi.hssf.usermodel.HSSFWorkbook,
        row: org.apache.poi.ss.usermodel.Row,
        styles: HashMap<Int, XlsxCellStyle>,
    ): List<XlsxCell> {
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
                style = styleOf(book, cell, styles),
                columnSpan = 1,
                rowSpan = 1,
                isCovered = false,
            )
        }
        return cells
    }

    /** The text of a cell. */
    private fun valueOf(cell: org.apache.poi.ss.usermodel.Cell): String {
        val type = if (cell.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            cell.cachedFormulaResultType
        } else {
            cell.cellType
        }
        return when (type) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.ERROR -> "#error"
            else -> ""
        }
    }

    /** The formula of a cell. */
    private fun formulaOf(cell: org.apache.poi.ss.usermodel.Cell): String? {
        if (cell.cellType != org.apache.poi.ss.usermodel.CellType.FORMULA) return null
        return runCatching { cell.cellFormula }.getOrNull()
    }

    /** The style of a cell, read with the model of POI. */
    private fun styleOf(
        book: org.apache.poi.hssf.usermodel.HSSFWorkbook,
        cell: org.apache.poi.ss.usermodel.Cell,
        styles: HashMap<Int, XlsxCellStyle>,
    ): XlsxCellStyle {
        val style = cell.cellStyle as? org.apache.poi.hssf.usermodel.HSSFCellStyle
        // The style of a cell is asked for every cell of the sheet, the same
        // style is used again and again.
        val key = style?.index?.toInt() ?: 0
        styles[key]?.let { return it }
        val result = styleOfSlow(book, cell)
        styles[key] = result
        return result
    }

    /** The style of a cell, read out of the workbook. */
    private fun styleOfSlow(
        book: org.apache.poi.hssf.usermodel.HSSFWorkbook,
        cell: org.apache.poi.ss.usermodel.Cell,
    ): XlsxCellStyle {
        val style = cell.cellStyle as? org.apache.poi.hssf.usermodel.HSSFCellStyle
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

    /**
     * Reads the records of the workbook.
     *
     * The listener gets every record of the file: the names of the sheets and
     * the texts of the cells. The rows that the reader shows are built from
     * them.
     */
    private class RecordReader : HSSFListener {

        private val sheetNames = ArrayList<String>()

        /** The width of the columns of the sheets. */
        private val columnWidths = HashMap<Int, Float>()

        /**
         * The texts of the file.
         *
         * A big workbook has hundreds of thousands of texts, the reader only
         * needs the ones of the cells that it shows. So the texts are read
         * when a cell needs them.
         */
        private var sharedStrings: SSTRecord? = null
        private val sheets = ArrayList<XlsxSheet>()
        private var current: SheetBuilder? = null

        /** The records of the file, they tell how the file is built. */
        private var begins = 0
        private var ends = 0
        private var rowRecords = 0
        private var cellRecords = 0

        /** The number of the records that open a sheet, the first one is the workbook. */
        private var beginCount = 0

        override fun processRecord(record: Record) {
            when (record) {
                is BOFRecord -> {
                    beginCount++
                    begins++
                    if (beginCount > 1) current = SheetBuilder()
                }

                is EOFRecord -> {
                    ends++
                    current?.let { builder ->
                        sheets += builder.build(
                            sheetNames.getOrNull(sheets.size) ?: "",
                            columnWidths,
                        )
                    }
                    current = null
                }

                is BoundSheetRecord -> sheetNames += record.sheetname

                is SSTRecord -> sharedStrings = record

                is ColumnInfoRecord -> {
                    val width = record.columnWidth / 256f * DP_PER_CHARACTER
                    for (column in record.firstColumn..record.lastColumn) {
                        columnWidths[column] = width
                    }
                }

                is RowRecord -> {
                    rowRecords++
                    current?.startRow(record.rowNumber, record.height / 20f)
                }

                is CellRecord -> {
                    cellRecords++
                    current?.cell(record, sharedStrings)
                }

                is StringRecord -> current?.formulaText(record.string)
            }
        }

        /** The workbook of the records. */
        fun build(): XlsxWorkbook {
            Logger.d(
                TAG,
                "the file holds ${sheets.size} sheets, ${rowRecords} rows, ${cellRecords} cells, " +
                    "$begins begins and $ends ends",
            )
            return XlsxWorkbook(sheets = sheets, activeSheetIndex = 0)
        }
    }

    /**
     * The rows of one sheet of the file.
     *
     * The rows and the columns that the reader does not show are let go, so
     * a big workbook does not fill the memory of the phone.
     */
    private class SheetBuilder {

        private val rows = ArrayList<XlsxRow>()
        private val cells = ArrayList<XlsxCell>()
        private var rowIndex = -1
        private var rowHeight = 0f
        private var formulaCell = -1

        /** A row of the sheet begins. */
        fun startRow(index: Int, heightDp: Float) {
            flush()
            rowIndex = index
            rowHeight = heightDp
        }

        /** A cell of the sheet. */
        fun cell(record: CellRecord, strings: SSTRecord?) {
            if (rowIndex < 0 || rowIndex >= MAX_ROWS) return
            val column = record.column.toInt()
            if (column >= MAX_COLUMNS) return
            val value = valueOf(record, strings) ?: return
            cells += XlsxCell(
                column = column,
                row = rowIndex + 1,
                value = value,
                rawValue = value,
                formula = null,
                style = PLAIN_STYLE,
                columnSpan = 1,
                rowSpan = 1,
                isCovered = false,
            )
            if (record is FormulaRecord) formulaCell = cells.size - 1
        }

        /** The text of a formula, it follows the formula itself. */
        fun formulaText(text: String) {
            val index = formulaCell
            if (index < 0) return
            formulaCell = -1
            val cell = cells[index]
            if (cell.value.isEmpty()) {
                cells[index] = cell.copy(value = text, rawValue = text)
            }
        }

        /** The value of a cell, null when the cell is empty. */
        private fun valueOf(record: CellRecord, strings: SSTRecord?): String? = when (record) {
            is NumberRecord -> record.value.toString()
            is LabelSSTRecord -> runCatching {
                strings?.getString(record.sstIndex)?.string
            }.getOrNull()
            is FormulaRecord -> when (record.cachedResultTypeEnum) {
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> record.value.toString()
                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> record.cachedBooleanValue.toString()
                org.apache.poi.ss.usermodel.CellType.ERROR -> "#error"
                else -> ""
            }

            else -> null
        }

        /** The row is finished. */
        private fun flush() {
            if (cells.isNotEmpty()) {
                // The cells of the row are copied, the list is emptied for the
                // next row.
                rows += XlsxRow(
                    index = rowIndex + 1,
                    heightDp = rowHeight,
                    cells = ArrayList(cells),
                )
            }
            cells.clear()
        }

        /** The sheet of the records. */
        fun build(name: String, columnWidths: Map<Int, Float>): XlsxSheet {
            flush()
            val columns = (0 until MAX_COLUMNS).map { column ->
                columnWidths[column] ?: DEFAULT_COLUMN_DP
            }
            return XlsxSheet(
                name = name,
                rows = rows,
                columnWidthsDp = columns,
                defaultColumnWidthDp = DEFAULT_COLUMN_DP,
                frozenRows = 0,
                frozenColumns = 0,
                tabColor = null,
            )
        }

        private companion object {
            /** The number of rows that are read, a spreadsheet is endless. */
            const val MAX_ROWS = 2000

            /** The number of columns that are read. */
            const val MAX_COLUMNS = 128

            /** The dp a column of the default width is wide. */
            const val DEFAULT_COLUMN_DP = 48f

            /** The style of a cell, before the styles of the file are known. */
            val PLAIN_STYLE = XlsxCellStyle(
                bold = false,
                italic = false,
                underline = false,
                fontColor = null,
                background = null,
                horizontal = null,
                vertical = null,
                wrapText = false,
                numberFormat = null,
                borders = emptySet(),
            )
        }
    }
}
