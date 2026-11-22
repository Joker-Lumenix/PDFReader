package com.peng.pdfreader.core.office.edit

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** The namespaces of the office formats. */
object OfficeNamespaces {

    /** WordprocessingML. */
    const val WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    /** SpreadsheetML. */
    const val SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** DrawingML, the text of the shapes of a presentation. */
    const val DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main"

    /** PresentationML. */
    const val PRESENTATION = "http://schemas.openxmlformats.org/presentationml/2006/main"

    /** The namespace of the `xml` prefix, it is bound everywhere. */
    const val XML = "http://www.w3.org/XML/1998/namespace"
}

/**
 * The tree of a part of a document, read for an edit.
 *
 * The reader of the app parses the parts without the namespaces, it only
 * compares the local names. The editor has to write the parts back, so it
 * needs the namespaces: the tree keeps them and the serializer writes them
 * again.
 */
internal object XmlParts {

    /** Parses a part of a document into a tree. */
    fun parse(bytes: ByteArray): Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Some android releases do not know every hardening switch.
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }.getOrNull()

    /** Writes a tree back into the bytes of a part. */
    fun serialize(document: Document): ByteArray {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(document), StreamResult(out))
        return out.toByteArray()
    }

    /** The name of an element, the namespace prefix is left out. */
    fun localName(element: Element): String =
        element.localName ?: element.tagName.substringAfterLast(':')

    /** The child elements with the given name. */
    fun children(parent: Element, name: String): List<Element> {
        val result = ArrayList<Element>()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (localName(element) == name) result += element
        }
        return result
    }

    /** The first child element with one of the given names. */
    fun child(parent: Element, vararg names: String): Element? {
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (localName(element) in names) return element
        }
        return null
    }

    /** All the descendant elements with the given name, in the order of the document. */
    fun descendants(parent: Element, name: String): List<Element> {
        val result = ArrayList<Element>()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (localName(element) == name) result += element
            result += descendants(element, name)
        }
        return result
    }
}
