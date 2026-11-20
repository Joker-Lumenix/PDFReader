package com.peng.pdfreader.core.office.legacy

import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxCell
import com.peng.pdfreader.core.office.docx.DocxDocument
import com.peng.pdfreader.core.office.docx.DocxRun
import com.peng.pdfreader.core.util.Logger
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.usermodel.CharacterRun
import org.apache.poi.hwpf.usermodel.Paragraph
import org.apache.poi.hwpf.usermodel.Range
import org.apache.poi.hwpf.usermodel.Table
import java.io.File

/**
 * Reads the legacy binary format of the documents (`.doc`).
 *
 * The format is the compound file of Office 97, the reader of Apache POI
 * opens it. The paragraphs, the runs and the tables of the document are taken
 * out of the records of the file.
 */
internal object LegacyDocument {

    /** The number of blocks that are read, a document is endless. */
    private const val MAX_BLOCKS = 4000

    /** Reads the document of a legacy Word file. */
    fun read(file: File): DocxDocument? = runCatching {
        file.inputStream().use { input ->
            val word = HWPFDocument(input)
            val range = word.range
            val reader = Reader(word, range)
            reader.read()
        }
    }.onFailure {
        Logger.e(TAG, "the legacy document could not be read", it)
    }.getOrNull()

    /** Reads the blocks of a legacy document. */
    private class Reader(private val word: HWPFDocument, private val range: Range) {

        val blocks = ArrayList<DocxBlock>()

        /** The tables that were read already. */
        private val tables = HashSet<Table>()

        fun read(): DocxDocument {
            val count = range.numParagraphs()
            for (index in 0 until count) {
                if (blocks.size >= MAX_BLOCKS) break
                val paragraph = range.getParagraph(index)
                if (paragraph.isInTable) {
                    val table = runCatching { range.getTable(paragraph) }.getOrNull()
                    if (table != null && tables.add(table)) {
                        blocks += tableOf(table)
                    }
                    continue
                }
                blocks += paragraphOf(paragraph)
            }
            return DocxDocument(
                title = "",
                pageWidthPt = 595f,
                pageHeightPt = 842f,
                marginTopPt = 72f,
                marginBottomPt = 72f,
                marginLeftPt = 72f,
                marginRightPt = 72f,
                defaultFontPt = 11f,
                blocks = blocks,
                truncated = blocks.size >= MAX_BLOCKS,
            )
        }

        /** Reads one paragraph of the document. */
        private fun paragraphOf(paragraph: Paragraph): DocxBlock.Paragraph {
            val runs = ArrayList<DocxRun>()
            for (index in 0 until paragraph.numCharacterRuns()) {
                val run = paragraph.getCharacterRun(index)
                val text = run.text()
                if (text.isEmpty()) continue
                runs += DocxRun(
                    text = text,
                    bold = run.isBold(),
                    italic = run.isItalic(),
                    underline = run.underlineCode != 0,
                    strike = false,
                    superscript = run.subSuperScriptIndex > 0,
                    color = null,
                    sizePt = run.fontSizeAsDouble?.toFloat(),
                    fontFamily = run.fontName,
                )
            }
            val styleName = styleNameOf(paragraph)
            val heading = headingLevelOf(styleName)
            return DocxBlock.Paragraph(
                runs = runs,
                styleId = styleName,
                headingLevel = heading,
                alignment = "left",
                indentLevel = 0,
                listLabel = null,
                spaceBeforePt = 0f,
                spaceAfterPt = 0f,
                lineSpacing = 1f,
                pageBreakBefore = false,
            )
        }

        /** Reads one table of the document. */
        private fun tableOf(table: Table): DocxBlock.Table {
            val rows = ArrayList<List<DocxCell>>()
            for (rowIndex in 0 until table.numRows()) {
                val row = table.getRow(rowIndex)
                val cells = ArrayList<DocxCell>()
                for (cellIndex in 0 until row.numCells()) {
                    val cell = row.getCell(cellIndex)
                    val blocks = ArrayList<DocxBlock>()
                    for (index in 0 until cell.numParagraphs()) {
                        blocks += paragraphOf(cell.getParagraph(index))
                    }
                    cells += DocxCell(
                        blocks = blocks,
                        widthPt = 0f,
                        background = null,
                        verticalAlignment = "top",
                        columnSpan = 1,
                    )
                }
                rows += cells
            }
            return DocxBlock.Table(rows = rows, columnWidthsPt = emptyList())
        }

        /** The name of the style of a paragraph. */
        private fun styleNameOf(paragraph: Paragraph): String? {
            val index = paragraph.styleIndex.toInt()
            if (index < 0) return null
            val name = runCatching {
                word.styleSheet.getStyleDescription(index).name
            }.getOrNull()
            return name?.trim()?.takeIf { it.isNotEmpty() }
        }

        /** The level of the heading of a paragraph, taken from its style. */
        private fun headingLevelOf(styleName: String?): Int {
            val name = styleName?.lowercase()?.trim() ?: return 0
            val match = Regex("(heading|标题)\\s*([1-9])").find(name) ?: return 0
            return match.groupValues[2].toIntOrNull()?.coerceIn(1, 6) ?: 0
        }
    }

    private const val TAG = "LegacyDocument"
}
