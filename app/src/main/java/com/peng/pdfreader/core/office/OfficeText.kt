package com.peng.pdfreader.core.office

import java.io.File

/**
 * The text of an OOXML document.
 *
 * The parsers of the core package read a document into a tree of XML nodes.
 * A big document does not fit into the memory of the phone that way, so its
 * text is read out of the raw XML: the parts are read as text, no tree is
 * built for them.
 */
object OfficeText {

    /**
     * The text runs of an OOXML part.
     *
     * The text of a document lives in the elements `w:t` (Word), `a:t` (the
     * shapes of a slide) and `t` (the cells of a worksheet).
     */
    private val TEXT_RUN = Regex(
        "<(?:[A-Za-z0-9_]+:)?t(?:\\s[^>]*)?>([^<]*)</(?:[A-Za-z0-9_]+:)?t(?:\\s[^>]*)?>",
    )

    /** The number of lines that are collected. */
    private const val MAX_LINES = 4000

    /**
     * The text of the document, line by line.
     *
     * Every text run of the document is a line: the paragraphs of a Word
     * document, the shapes of a slide and the cells of a worksheet.
     */
    fun lines(file: File): List<String> {
        val pkg = OoxmlPackage.open(file) ?: return emptyList()
        return try {
            val result = mutableListOf<String>()
            pkg.partNames()
                .filter { part -> part.endsWith(".xml") && isContentPart(part) }
                .sorted()
                .forEach { part ->
                    val raw = pkg.text(part) ?: return@forEach
                    collect(raw, result)
                }
            result
        } finally {
            pkg.close()
        }
    }

    /** Collects the text runs of one part. */
    fun collect(raw: String, into: MutableList<String>, limit: Int = MAX_LINES) {
        if (into.size >= limit) return
        TEXT_RUN.findAll(raw).forEach { match ->
            if (into.size >= limit) return
            val text = match.groupValues[1]
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .trim()
            if (text.isNotEmpty()) into += text
        }
    }

    /** The parts of a package that carry the text of the document. */
    private fun isContentPart(part: String): Boolean =
        part.startsWith("word/document") ||
            part.startsWith("ppt/slides/") ||
            part.startsWith("ppt/notesSlides/") ||
            part.startsWith("xl/sharedStrings") ||
            part.startsWith("xl/worksheets/")
}
