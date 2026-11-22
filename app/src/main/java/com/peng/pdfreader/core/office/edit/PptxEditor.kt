package com.peng.pdfreader.core.office.edit

import com.peng.pdfreader.core.util.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

/**
 * Writes the text of a shape of a slide back into the file.
 *
 * The reader of the app numbers the shapes of a slide in the order it reads
 * them. The editor uses the same number to find the shape in the tree of the
 * slide, so the text the user changes on the screen is the text that is
 * written into the file.
 */
object PptxEditor {

    private const val TAG = "PptxEditor"

    /**
     * Replaces the text of a shape of a slide.
     *
     * @param archive the document that is edited
     * @param part the part of the slide that holds the shape
     * @param index the number of the shape, the number the reader gives it
     * @param text the new text of the shape
     * @return true when the shape was found and written
     */
    fun setShapeText(
        archive: OoxmlArchive,
        part: String,
        index: Int,
        text: String,
    ): Boolean {
        val bytes = archive.part(part) ?: return false
        val document = XmlParts.parse(bytes) ?: return false
        val root = document.documentElement ?: return false
        val tree = XmlParts.descendants(root, "spTree").firstOrNull() ?: return false
        val shape = shapes(tree).getOrNull(index) ?: return false
        applyText(document, shape, text)
        archive.replacePart(part, XmlParts.serialize(document))
        return true
    }

    /**
     * Replaces the text of a shape of a document on the disk.
     */
    fun setShapeText(file: File, part: String, index: Int, text: String): Boolean {
        val archive = OoxmlArchive.open(file) ?: return false
        if (!setShapeText(archive, part, index, text)) return false
        return archive.writeTo(file)
    }

    /**
     * The shapes of a slide, in the order the reader reads them.
     *
     * The reader walks the shape tree of the slide and the groups inside of
     * it, the pictures and the tables are not shapes with a text.
     */
    fun shapes(tree: Element): List<Element> {
        val result = ArrayList<Element>()
        walk(tree, result)
        return result
    }

    private fun walk(parent: Element, result: MutableList<Element>) {
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            when (XmlParts.localName(element)) {
                "sp" -> result += element
                "grpSp" -> walk(element, result)
                else -> Unit
            }
        }
    }

    /**
     * Writes the text into the shape.
     *
     * The properties of the paragraph and of its first run are kept, so the
     * edited text looks the way the text of the slide did before.
     */
    private fun applyText(document: Document, shape: Element, text: String) {
        val body = XmlParts.children(shape, "txBody").firstOrNull() ?: return
        val paragraphs = XmlParts.children(body, "p")
        val first = paragraphs.firstOrNull()
        val paragraphProperties = first?.let { XmlParts.child(it, "pPr") }
        val run = first?.let { XmlParts.children(it, "r").firstOrNull() }
        val runProperties = run?.let { XmlParts.child(it, "rPr") }
        val endProperties = first?.let { XmlParts.child(it, "endParaRPr") }

        paragraphs.forEach { body.removeChild(it) }

        val paragraph = document.createElementNS(OfficeNamespaces.DRAWING, "a:p")
        paragraphProperties?.let { paragraph.appendChild(it.cloneNode(true)) }
        if (text.isNotEmpty()) {
            val textRun = document.createElementNS(OfficeNamespaces.DRAWING, "a:r")
            runProperties?.let { textRun.appendChild(it.cloneNode(true)) }
            val node = document.createElementNS(OfficeNamespaces.DRAWING, "a:t")
            node.textContent = text
            textRun.appendChild(node)
            paragraph.appendChild(textRun)
        }
        endProperties?.let { paragraph.appendChild(it.cloneNode(true)) }
        body.appendChild(paragraph)
    }

    /** Reports that the shape was not found. */
    internal fun reportMissing(index: Int) {
        Logger.e(TAG, "the shape $index is not in the slide")
    }
}
