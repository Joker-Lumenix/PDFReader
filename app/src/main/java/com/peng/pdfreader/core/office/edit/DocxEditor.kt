package com.peng.pdfreader.core.office.edit

import com.peng.pdfreader.core.util.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

/**
 * Writes the text of a Word document back into the file.
 *
 * The reader of the app numbers the paragraphs of a document in the order it
 * reads them. The editor uses the same number to find the paragraph in the
 * tree of the document, so the text the user changes on the screen is the
 * text that is written into the file.
 */
object DocxEditor {

    /** The part of a Word document that carries its text. */
    const val DOCUMENT_PART = "word/document.xml"

    private const val TAG = "DocxEditor"

    /**
     * Replaces the text of a paragraph.
     *
     * @param archive the document that is edited
     * @param index the number of the paragraph, the number the reader gives it
     * @param text the new text of the paragraph
     * @return true when the paragraph was found and written
     */
    fun setParagraphText(
        archive: OoxmlArchive,
        index: Int,
        text: String,
        part: String = DOCUMENT_PART,
    ): Boolean {
        val bytes = archive.part(part) ?: return false
        val document = XmlParts.parse(bytes) ?: return false
        val root = document.documentElement ?: return false
        val paragraph = paragraphs(root).getOrNull(index) ?: return false
        applyText(document, paragraph, text)
        archive.replacePart(part, XmlParts.serialize(document))
        return true
    }

    /**
     * Replaces the text of a paragraph of a document on the disk.
     *
     * The document is opened, the paragraph is written and the document is
     * written again. The old file is only replaced when the new one is
     * complete.
     */
    fun setParagraphText(file: File, index: Int, text: String): Boolean {
        val archive = OoxmlArchive.open(file) ?: return false
        if (!setParagraphText(archive, index, text)) return false
        return archive.writeTo(file)
    }

    /**
     * The paragraphs of a document, in the order the reader reads them.
     *
     * A paragraph inside a drawing is not a paragraph of the text, the reader
     * skips the drawings as well. A paragraph inside a paragraph does not
     * count twice either.
     */
    fun paragraphs(root: Element): List<Element> {
        val result = ArrayList<Element>()
        // The depth of the paragraphs: a paragraph inside a paragraph does not
        // count twice.
        var depth = 0

        fun walk(parent: Element) {
            val nodes = parent.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val element = node as Element
                when (XmlParts.localName(element)) {
                    // The reader skips the drawings, the text of a text box of
                    // a drawing is not a paragraph of the document.
                    "drawing", "pict" -> continue
                    "p" -> {
                        if (depth == 0) result += element
                        depth++
                        walk(element)
                        depth--
                    }
                    else -> walk(element)
                }
            }
        }

        walk(root)
        return result
    }

    /**
     * Writes the text into the paragraph.
     *
     * The properties of the paragraph and of its first run are kept, so the
     * edited paragraph looks the way it did before.
     */
    private fun applyText(document: Document, paragraph: Element, text: String) {
        val properties = runProperties(paragraph)
        // The properties of the paragraph stay, everything else is replaced
        // by the new run.
        val kept = ArrayList<Node>()
        val children = paragraph.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            if (XmlParts.localName(node as Element) == "pPr") kept += node
        }
        while (paragraph.firstChild != null) paragraph.removeChild(paragraph.firstChild)
        kept.forEach { paragraph.appendChild(it) }

        val run = document.createElementNS(OfficeNamespaces.WORD, "w:r")
        properties?.let { run.appendChild(it.cloneNode(true)) }
        val node = document.createElementNS(OfficeNamespaces.WORD, "w:t")
        // The spaces at the beginning and at the end of a text are kept only
        // when the element says so.
        node.setAttributeNS(OfficeNamespaces.XML, "xml:space", "preserve")
        node.textContent = text
        run.appendChild(node)
        paragraph.appendChild(run)
    }

    /** The properties of the first run of the paragraph. */
    private fun runProperties(paragraph: Element): Element? {
        val run = XmlParts.children(paragraph, "r").firstOrNull()
            ?: XmlParts.descendants(paragraph, "r").firstOrNull()
            ?: return null
        return XmlParts.child(run, "rPr")
    }

    /** Reports that the paragraph was not found. */
    internal fun reportMissing(index: Int) {
        Logger.e(TAG, "the paragraph $index is not in the document")
    }
}
