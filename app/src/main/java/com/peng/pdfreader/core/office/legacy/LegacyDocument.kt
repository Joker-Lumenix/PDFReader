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
    private const val MAX_BLOCKS = 20000

    /** The twips of a point, the file measures in twips. */
    private const val TWIPS_PER_POINT = 20f

    /** The width of a page, when the file does not name one. */
    private const val DEFAULT_PAGE_WIDTH_PT = 595f

    /** The height of a page, when the file does not name one. */
    private const val DEFAULT_PAGE_HEIGHT_PT = 842f

    /** A margin of a page, when the file does not name one. */
    private const val DEFAULT_MARGIN_PT = 72f

    /** The twips of one level of the indent of a list. */
    private const val INDENT_PER_LEVEL = 360

    /** The property of a section that holds the width of the page. */
    private const val SPRM_PAGE_WIDTH = 0xB01F

    /** The property of a section that holds the height of the page. */
    private const val SPRM_PAGE_HEIGHT = 0xB020

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
                pageWidthPt = pageSize().first,
                pageHeightPt = pageSize().second,
                marginTopPt = marginOf { it.dyaTop },
                marginBottomPt = marginOf { it.dyaBottom },
                marginLeftPt = marginOf { it.dxaLeft },
                marginRightPt = marginOf { it.dxaRight },
                defaultFontPt = 11f,
                blocks = blocks,
                truncated = blocks.size >= MAX_BLOCKS,
            )
        }

        /**
         * The size of the page of the document, in point.
         *
         * The file keeps the size in the properties of its section, they are
         * read out of them.
         */
        private fun pageSize(): Pair<Float, Float> {
            val section = runCatching {
                word.getSectionTable().sections.firstOrNull()
            }.getOrNull() ?: return DEFAULT_PAGE_WIDTH_PT to DEFAULT_PAGE_HEIGHT_PT
            var width = DEFAULT_PAGE_WIDTH_PT
            var height = DEFAULT_PAGE_HEIGHT_PT
            val properties = section.grpprl ?: return width to height
            var index = 0
            while (index + 4 <= properties.size) {
                val opcode = (properties[index].toInt() and 0xff) or
                    ((properties[index + 1].toInt() and 0xff) shl 8)
                val size = (properties[index + 2].toInt() and 0xff) or
                    ((properties[index + 3].toInt() and 0xff) shl 8)
                if (size < 2 || index + 4 + size > properties.size) break
                val operand = properties.copyOfRange(index + 4, index + 4 + size)
                when (opcode) {
                    SPRM_PAGE_WIDTH -> width = readShort(operand) / TWIPS_PER_POINT
                    SPRM_PAGE_HEIGHT -> height = readShort(operand) / TWIPS_PER_POINT
                }
                index += 4 + size
            }
            return width to height
        }

        /** A number of two bytes of the file, it can be negative. */
        private fun readShort(operand: ByteArray): Float {
            if (operand.size < 2) return 0f
            val value = (operand[0].toInt() and 0xff) or ((operand[1].toInt() and 0xff) shl 8)
            // A number that is bigger than the half of its range is negative.
            return if (value > 0x7fff) (value - 0x10000).toFloat() else value.toFloat()
        }

        /** A margin of the page of the document, in point. */
        private fun marginOf(margin: (org.apache.poi.hwpf.usermodel.SectionProperties) -> Int): Float =
            runCatching {
                val properties = word.getSectionTable().sections.firstOrNull()?.sectionProperties
                margin(properties!!) / TWIPS_PER_POINT
            }.getOrDefault(DEFAULT_MARGIN_PT)
                .coerceAtLeast(0f)

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
                alignment = alignmentOf(paragraph),
                indentLevel = indentLevelOf(paragraph),
                listLabel = null,
                spaceBeforePt = spaceOf(paragraph) { it.spacingBefore },
                spaceAfterPt = spaceOf(paragraph) { it.spacingAfter },
                lineSpacing = lineSpacingOf(paragraph),
                pageBreakBefore = false,
            )
        }

        /** The alignment of a paragraph. */
        private fun alignmentOf(paragraph: Paragraph): String =
            when (runCatching { paragraph.justification }.getOrDefault(0)) {
                1 -> "center"
                2 -> "right"
                3, 4 -> "justify"
                else -> "left"
            }

        /** The space above or below a paragraph, in point. */
        private fun spaceOf(
            paragraph: Paragraph,
            space: (Paragraph) -> Int,
        ): Float = runCatching { space(paragraph) / TWIPS_PER_POINT.toFloat() }
            .getOrDefault(0f)
            .coerceAtLeast(0f)

        /**
         * The distance between the lines of a paragraph.
         *
         * The file writes the distance in twips, a negative value means that
         * the distance is exact and not a factor of the height of the text.
         */
        private fun lineSpacingOf(paragraph: Paragraph): Float {
            val descriptor = runCatching { paragraph.getLineSpacing() }.getOrNull() ?: return 1f
            // The file writes the distance of the lines in two ways: as a
            // factor of the height of the text or as an exact distance. Both
            // of them are kept in one number.
            val packed = runCatching { descriptor.toInt() }.getOrDefault(0)
            val lines = (packed shr 16) and 0xffff
            if (lines > 0) return (lines / 240f).coerceIn(0.5f, 5f)
            val exact = packed and 0xffff
            if (exact == 0) return 1f
            // The height of a line of the text is about a fifth more than the
            // size of the text, the factor follows from it.
            val size = runCatching { paragraph.getCharacterRun(0).fontSizeAsDouble }
                .getOrNull()
                ?.toFloat()
                ?.takeIf { it > 0f }
                ?: 12f
            val factor = (exact / TWIPS_PER_POINT) / (size * 1.2f)
            return factor.coerceIn(0.5f, 5f)
        }

        /** The level of the indent of a paragraph. */
        private fun indentLevelOf(paragraph: Paragraph): Int {
            // The file keeps the indent of the paragraph, every level of a
            // list is one indent deeper.
            val left = runCatching { paragraph.getIndentFromLeft() }.getOrDefault(0)
            if (left <= 0) return 0
            return (left / INDENT_PER_LEVEL).coerceIn(0, 8)
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
