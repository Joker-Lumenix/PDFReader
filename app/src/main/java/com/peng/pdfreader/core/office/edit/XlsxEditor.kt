package com.peng.pdfreader.core.office.edit

import com.peng.pdfreader.core.util.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

/**
 * Writes the value of a cell of a spreadsheet back into the file.
 *
 * The text of a cell is written as an inline string, so the shared strings of
 * the workbook do not have to be changed. A number is written as a number, so
 * the format of the cell still applies to it.
 */
object XlsxEditor {

    private const val TAG = "XlsxEditor"

    /** A value that excel reads as a number. */
    private val NUMBER = Regex("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")

    /**
     * Replaces the value of a cell.
     *
     * @param archive the document that is edited
     * @param part the part of the sheet that holds the cell
     * @param row the number of the row, excel counts them from one
     * @param column the number of the column, it starts at zero
     * @param text the new value of the cell
     * @return true when the cell was written
     */
    fun setCellValue(
        archive: OoxmlArchive,
        part: String,
        row: Int,
        column: Int,
        text: String,
    ): Boolean {
        val bytes = archive.part(part) ?: return false
        val document = XmlParts.parse(bytes) ?: return false
        val root = document.documentElement ?: return false
        val sheet = XmlParts.child(root, "sheetData") ?: return false
        val cell = findCell(sheet, row, column) ?: createCell(document, sheet, row, column)
        applyValue(document, cell, text)
        archive.replacePart(part, XmlParts.serialize(document))
        return true
    }

    /**
     * Replaces the value of a cell of a document on the disk.
     */
    fun setCellValue(file: File, part: String, row: Int, column: Int, text: String): Boolean {
        val archive = OoxmlArchive.open(file) ?: return false
        if (!setCellValue(archive, part, row, column, text)) return false
        return archive.writeTo(file)
    }

    /** The name of a cell, the way excel writes it: the letters and the row. */
    fun referenceOf(column: Int, row: Int): String {
        var value = column
        val builder = StringBuilder()
        while (value >= 0) {
            builder.insert(0, ('A' + value % 26))
            value = value / 26 - 1
        }
        return "$builder$row"
    }

    // ------------------------------------------------------------------ cells

    /** The cell of the sheet, null when the sheet does not have it. */
    private fun findCell(sheet: Element, row: Int, column: Int): Element? {
        val reference = referenceOf(column, row)
        XmlParts.children(sheet, "row").forEach { rowNode ->
            if (rowNode.getAttribute("r").trim() != row.toString()) return@forEach
            XmlParts.children(rowNode, "c").forEach { cell ->
                if (cell.getAttribute("r").trim() == reference) return cell
            }
        }
        return null
    }

    /**
     * Creates the cell of the sheet.
     *
     * A cell that was empty is not in the file, so it has to be written. The
     * row of the cell is created as well when the sheet does not have it.
     */
    private fun createCell(document: Document, sheet: Element, row: Int, column: Int): Element {
        val cell = document.createElementNS(OfficeNamespaces.SHEET, "c")
        cell.setAttribute("r", referenceOf(column, row))
        var rowNode = XmlParts.children(sheet, "row")
            .firstOrNull { it.getAttribute("r").trim() == row.toString() }
        if (rowNode == null) {
            rowNode = document.createElementNS(OfficeNamespaces.SHEET, "row")
            rowNode.setAttribute("r", row.toString())
            // The rows of a sheet are sorted by their number.
            var inserted = false
            val children = XmlParts.children(sheet, "row")
            for (candidate in children) {
                val number = candidate.getAttribute("r").trim().toIntOrNull() ?: continue
                if (number > row) {
                    sheet.insertBefore(rowNode, candidate)
                    inserted = true
                    break
                }
            }
            if (!inserted) sheet.appendChild(rowNode)
        }
        // The cells of a row are sorted by their column.
        var inserted = false
        for (candidate in XmlParts.children(rowNode, "c")) {
            val candidateColumn = columnOf(candidate.getAttribute("r").trim())
            if (candidateColumn > column) {
                rowNode.insertBefore(cell, candidate)
                inserted = true
                break
            }
        }
        if (!inserted) rowNode.appendChild(cell)
        return cell
    }

    /** The column of a cell reference, the row number is ignored. */
    private fun columnOf(reference: String): Int {
        var value = 0
        for (character in reference) {
            if (!character.isLetter()) break
            value = value * 26 + (character.uppercaseChar() - 'A' + 1)
        }
        return value - 1
    }

    /** Writes the value into the cell. */
    private fun applyValue(document: Document, cell: Element, text: String) {
        // The old value and the old formula are replaced.
        listOf("v", "f", "is").forEach { name ->
            XmlParts.children(cell, name).forEach { cell.removeChild(it) }
        }
        if (text.isEmpty()) {
            cell.removeAttribute("t")
            return
        }
        if (NUMBER.matches(text.trim())) {
            cell.removeAttribute("t")
            val value = document.createElementNS(OfficeNamespaces.SHEET, "v")
            value.textContent = text.trim()
            cell.appendChild(value)
            return
        }
        cell.setAttribute("t", "inlineStr")
        val inline = document.createElementNS(OfficeNamespaces.SHEET, "is")
        val node = document.createElementNS(OfficeNamespaces.SHEET, "t")
        node.setAttributeNS(OfficeNamespaces.XML, "xml:space", "preserve")
        node.textContent = text
        inline.appendChild(node)
        cell.appendChild(inline)
    }

    /** Reports that the sheet was not found. */
    internal fun reportMissing(part: String) {
        Logger.e(TAG, "the sheet $part is not in the document")
    }
}
