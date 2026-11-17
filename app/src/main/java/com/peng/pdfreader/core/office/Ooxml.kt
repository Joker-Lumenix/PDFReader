package com.peng.pdfreader.core.office

import android.graphics.Color as AndroidColor
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal OOXML (docx / xlsx / pptx) package reader.
 *
 * Everything is parsed on device with the standard library only:
 * the file is a ZIP container, every part is XML and is read namespace agnostic
 * so the WordprocessingML / SpreadsheetML / DrawingML dialects do not need
 * separate code paths.
 */
class OoxmlPackage private constructor(private val zip: ZipFile) : AutoCloseable {

    private val builder: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Some Android releases do not know every hardening switch - ignore the ones they reject.
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }

    private val cache = HashMap<String, OoxmlNode?>()
    private val textCache = HashMap<String, String?>()

    companion object {
        /**
         * Parts bigger than this are not kept in the cache.
         *
         * The tree of a big slide or of the body of a Word document is big as
         * well, a whole document would not fit into the memory of the phone.
         */
        private const val CACHE_LIMIT = 2 * 1024 * 1024

        fun open(file: File): OoxmlPackage? = runCatching {
            OoxmlPackage(ZipFile(file))
        }.onFailure {
            android.util.Log.e("OoxmlPackage", "cannot open ${file.absolutePath}", it)
        }.getOrNull()
    }

    fun partNames(): List<String> = zip.entries().toList().map { it.name }

    fun hasPart(name: String): Boolean = runCatching {
        zip.getEntry(name) != null
    }.getOrDefault(false)

    fun bytes(name: String): ByteArray? = runCatching {
        zip.getEntry(name)?.let { entry ->
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }.getOrNull()

    /**
     * The stream of a part.
     *
     * The caller has to close the stream. A streaming parser reads the big
     * parts of a document through it, so the part never sits in the memory
     * of the phone as a whole.
     */
    fun stream(name: String): InputStream? = runCatching {
        zip.getEntry(name)?.let { entry -> zip.getInputStream(entry) }
    }.getOrNull()

    fun text(name: String): String? = textCache.getOrPut(name) {
        bytes(name)?.toString(Charsets.UTF_8)
    }

    /** Parses a part into a namespace agnostic node tree. */
    fun node(name: String): OoxmlNode? {
        val cached = cache[name]
        if (cached != null) return cached
        val raw = bytes(name) ?: return null
        val parsed = runCatching {
            val document = builder.newDocumentBuilder().parse(ByteArrayInputStream(raw))
            OoxmlNode(document.documentElement)
        }.getOrNull()
        // The content of a document (the slides, the body, the worksheets) is
        // not kept: the tree of a whole document would fill the memory of the
        // phone. The small parts (the theme, the styles) are used again and
        // again, they stay in the cache.
        if (!isContentPart(name) && raw.size <= CACHE_LIMIT) cache[name] = parsed
        return parsed
    }

    /** The parts that carry the content of the document. */
    private fun isContentPart(name: String): Boolean =
        name.startsWith("ppt/slides/") ||
            name.startsWith("ppt/notesSlides/") ||
            name.startsWith("word/document") ||
            name.startsWith("xl/worksheets/") ||
            name.startsWith("xl/sharedStrings")

    /** Resolves a relationship id to the part it points at (relative to [base]). */
    fun resolveRelationship(base: String, relationshipId: String): String? {
        val relsPath = relationshipPartFor(base)
        val rels = node(relsPath) ?: return null
        val rel = rels.children("Relationship")
            .firstOrNull { it.attr("Id") == relationshipId }
            ?: return null
        val target = rel.attr("Target") ?: return null
        return normalizePart(base, target)
    }

    fun relationships(base: String): List<OoxmlNode> {
        val rels = node(relationshipPartFor(base)) ?: return emptyList()
        return rels.children("Relationship")
    }

    /** All relationships of [base] pointing at a given relationship type suffix. */
    fun relationshipsOfType(base: String, typeSuffix: String): List<OoxmlNode> =
        relationships(base).filter { it.attr("Type")?.endsWith(typeSuffix) == true }

    override fun close() {
        runCatching { zip.close() }
    }

    private fun relationshipPartFor(base: String): String {
        val index = base.lastIndexOf('/')
        val directory = if (index < 0) "" else base.substring(0, index + 1)
        return "${directory}_rels/${base.substringAfterLast('/')}.rels"
    }

    private fun normalizePart(base: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val directory = base.substringBeforeLast('/', "")
        val segments = ArrayList<String>()
        for (segment in "$directory/$target".split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(segment)
            }
        }
        return segments.joinToString("/")
    }
}

/**
 * A node of an XML tree.
 *
 * The parsers of the app work with two kinds of trees: the small parts of a
 * document are read into a DOM tree, the big parts are read with a streaming
 * parser, which never builds a tree at all. Both are handled through this
 * interface, so the code that reads the properties of a paragraph or of a run
 * does not care where the tree comes from.
 */
interface XmlNode {

    /** The name of the node, the namespace prefix is left out. */
    val localName: String

    /** The text of the node and of all of its descendants. */
    val text: String

    /** The child nodes of the node. */
    fun nodes(): List<XmlNode>

    /** The child nodes with the given name. */
    fun children(name: String): List<XmlNode>

    /** The first child node with one of the given names. */
    fun child(vararg names: String): XmlNode?

    /** The attribute with one of the given names, the prefix is left out. */
    fun attr(vararg names: String): String?

    /** All the descendants of the node with the given name. */
    fun descendants(name: String): List<XmlNode>

    /** All the descendants of the node with one of the given names. */
    fun descendants(vararg names: String): List<XmlNode> =
        names.flatMap { descendants(it) }

    /** The attribute with one of the given names as a number. */
    fun attrInt(vararg names: String): Int? = attr(*names)?.trim()?.toDoubleOrNull()?.toInt()

    /** The attribute with one of the given names as a number. */
    fun attrLong(vararg names: String): Long? = attr(*names)?.trim()?.toDoubleOrNull()?.toLong()

    /** The attribute with one of the given names as a number. */
    fun attrFloat(vararg names: String): Float? = attr(*names)?.trim()?.toFloatOrNull()

    /** The attribute with one of the given names as a flag. */
    fun attrBool(vararg names: String): Boolean? = when (attr(*names)?.trim()?.lowercase()) {
        "1", "true", "on" -> true
        "0", "false", "off" -> false
        else -> null
    }

    /** True when the node carries the given flag attribute. */
    fun flag(vararg names: String): Boolean {
        val value = attr(*names) ?: return false
        return value.trim().lowercase() !in setOf("0", "false", "off")
    }
}

/** Namespace agnostic wrapper around a DOM element. */
class OoxmlNode(val element: Element) : XmlNode {

    override val localName: String
        get() = element.localName ?: element.tagName.substringAfterLast(':')

    /** Concatenated text of all descendants. */
    override val text: String
        get() = element.textContent ?: ""

    override fun nodes(): List<OoxmlNode> {
        val result = ArrayList<OoxmlNode>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                result += OoxmlNode(child as Element)
            }
        }
        return result
    }

    override fun children(name: String): List<OoxmlNode> = nodes().filter { it.localName == name }

    override fun child(vararg names: String): OoxmlNode? {
        for (name in names) {
            nodes().firstOrNull { it.localName == name }?.let { return it }
        }
        return null
    }

    override fun descendants(name: String): List<OoxmlNode> {
        val result = ArrayList<OoxmlNode>()
        fun walk(node: OoxmlNode) {
            node.nodes().forEach {
                if (it.localName == name) result += it
                walk(it)
            }
        }
        walk(this)
        return result
    }

    /** Attribute lookup by local name (namespace prefixes are ignored). */
    override fun attr(vararg names: String): String? {
        for (name in names) {
            val attributes = element.attributes
            for (index in 0 until attributes.length) {
                val attribute = attributes.item(index)
                val attributeName = (attribute.localName ?: attribute.nodeName.substringAfterLast(':'))
                if (attributeName == name) {
                    return attribute.nodeValue
                }
            }
        }
        return null
    }
}

/**
 * A node of a tree that was read with a streaming parser.
 *
 * The tree is only built for the small parts of a document - the properties of
 * a paragraph, of a run or of a section - the body of the document is never
 * held as a tree.
 */
class SaxNode internal constructor(
    override val localName: String,
    private val attributes: Map<String, String>,
) : XmlNode {

    private val children = ArrayList<SaxNode>()
    private val textBuilder = StringBuilder()

    override val text: String
        get() = textBuilder.toString() + children.joinToString("") { it.text }

    override fun nodes(): List<XmlNode> = children

    override fun children(name: String): List<XmlNode> = children.filter { it.localName == name }

    override fun child(vararg names: String): XmlNode? {
        for (name in names) {
            children.firstOrNull { it.localName == name }?.let { return it }
        }
        return null
    }

    override fun descendants(name: String): List<XmlNode> {
        val result = ArrayList<XmlNode>()
        fun walk(node: SaxNode) {
            node.children.forEach {
                if (it.localName == name) result += it
                walk(it)
            }
        }
        walk(this)
        return result
    }

    override fun attr(vararg names: String): String? {
        for (name in names) {
            attributes[name]?.let { return it }
        }
        return null
    }

    internal fun addChild(node: SaxNode) {
        children += node
    }

    internal fun appendText(value: String) {
        textBuilder.append(value)
    }
}

/**
 * Collects a small part of a document while the parser walks over it.
 *
 * The collector is used for the parts that are needed as a tree: the
 * properties of a paragraph, of a run and of a section.
 */
class SaxCollector {

    private val stack = ArrayList<SaxNode>()

    /** The tree that was collected. */
    var root: SaxNode? = null
        private set

    /** True when the collected element is closed. */
    val isComplete: Boolean get() = stack.isEmpty()

    /** Opens an element of the collected tree. */
    fun start(localName: String, attributes: Map<String, String>) {
        val node = SaxNode(localName, attributes)
        val parent = stack.lastOrNull()
        if (parent == null) root = node else parent.addChild(node)
        stack += node
    }

    /** Closes an element of the collected tree. */
    fun end() {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
    }

    /** Adds text to the element that is open. */
    fun text(value: String) {
        stack.lastOrNull()?.appendText(value)
    }
}

/** Theme + DrawingML colour helpers shared by the Word and PowerPoint renderers. */
object OfficeColors {

    private const val EMU_PER_DP = 9525f

    /**
     * The presentation colours and the theme slots they point at.
     *
     * A theme stores its colours under the slots `dk1` / `lt1` / `dk2` / `lt2`,
     * the drawings refer to them with the names `tx1` / `bg1` / `tx2` / `bg2`.
     */
    private val PRESENTATION_COLORS = mapOf(
        "tx1" to "dk1",
        "bg1" to "lt1",
        "tx2" to "dk2",
        "bg2" to "lt2",
    )

    fun emuToDp(value: Int): Float = value / EMU_PER_DP

    fun emuToPt(value: Int): Float = value / 12700f

    /** Reads ppt/theme/theme1.xml and returns the colour scheme by slot name. */
    fun readTheme(pkg: OoxmlPackage): Map<String, OfficeColor> {
        val result = HashMap<String, OfficeColor>()
        val theme = pkg.node("ppt/theme/theme1.xml") ?: pkg.node("word/theme/theme1.xml") ?: return result
        val scheme = theme.descendants("clrScheme").firstOrNull() ?: return result
        scheme.nodes().forEach { slot ->
            val color = colorOf(slot, emptyMap())
            if (color != null) result[slot.localName] = color
        }
        // The presentation colours are aliases of the theme slots, a shape that
        // paints itself with "tx1" needs the "dk1" colour of the theme.
        PRESENTATION_COLORS.forEach { (presentation, slot) ->
            val color = result[slot] ?: result[presentation] ?: return@forEach
            result[presentation] = color
        }
        return result
    }

    /** Resolves a colour node (srgbClr / schemeClr / sysClr) against the theme. */
    fun colorOf(node: OoxmlNode, theme: Map<String, OfficeColor>): OfficeColor? {
        val colorNode = node.nodes().firstOrNull {
            it.localName == "srgbClr" || it.localName == "schemeClr" || it.localName == "sysClr" || it.localName == "prstClr"
        } ?: if (node.localName == "srgbClr" || node.localName == "schemeClr" || node.localName == "sysClr") {
            node
        } else {
            null
        } ?: return null

        val base = when (colorNode.localName) {
            "srgbClr" -> colorNode.attr("val")?.let { parseHex(it) }
            "sysClr" -> colorNode.attr("lastClr")?.let { parseHex(it) }
                ?: colorNode.attr("val")?.let { parseHex(it) }
            "schemeClr" -> {
                val slot = colorNode.attr("val") ?: return null
                val themeSlot = PRESENTATION_COLORS[slot] ?: slot
                theme[themeSlot] ?: theme[slot] ?: theme[slot.replaceFirstChar { it.uppercase() }]
            }
            "prstClr" -> null
            else -> null
        } ?: return null

        var result = base
        val transforms = colorNode.nodes().filter { it.localName.endsWith("Lum") || it.localName == "alpha" || it.localName == "shade" || it.localName == "tint" }
        for (transform in transforms) {
            val amount = transform.attr("val")?.trim()?.toIntOrNull() ?: continue
            result = when (transform.localName) {
                "alpha" -> result.copy(alpha = (amount.coerceIn(0, 100_000)) / 100_000f)
                "lumMod" -> result.luminanceMod(amount / 100_000f)
                "lumOff" -> result.luminanceOffset(amount / 100_000f)
                "shade" -> result.scale(amount / 100_000f)
                "tint" -> result.tint(amount / 100_000f)
                else -> result
            }
        }
        return result
    }

    fun parseHex(value: String): OfficeColor? {
        val hex = value.removePrefix("#").trim()
        return when (hex.length) {
            6 -> OfficeColor(0xFF000000L or hex.toLong(16) or 0x00000000L)
            8 -> {
                // SpreadsheetML writes AARRGGBB but Excel ignores the alpha byte,
                // so the colour is always fully opaque.
                val red = hex.substring(2, 4).toInt(16)
                val green = hex.substring(4, 6).toInt(16)
                val blue = hex.substring(6, 8).toInt(16)
                OfficeColor(red / 255f, green / 255f, blue / 255f, 1f)
            }
            else -> null
        }
    }

    fun toAndroid(color: OfficeColor): Int = AndroidColor.argb(
        (color.alpha * 255).toInt().coerceIn(0, 255),
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
    )
}

private fun OfficeColor.luminanceMod(factor: Float): OfficeColor =
    OfficeColor(red * factor, green * factor, blue * factor, alpha)

private fun OfficeColor.luminanceOffset(factor: Float): OfficeColor =
    OfficeColor(
        (red + (1f - red) * factor).coerceIn(0f, 1f),
        (green + (1f - green) * factor).coerceIn(0f, 1f),
        (blue + (1f - blue) * factor).coerceIn(0f, 1f),
        alpha,
    )

private fun OfficeColor.scale(factor: Float): OfficeColor {
    return OfficeColor(red * factor, green * factor, blue * factor, alpha)
}

private fun OfficeColor.tint(factor: Float): OfficeColor {
    return OfficeColor(
        red * factor + (1 - factor),
        green * factor + (1 - factor),
        blue * factor + (1 - factor),
        alpha,
    )
}
