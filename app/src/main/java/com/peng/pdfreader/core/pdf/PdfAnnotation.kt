package com.peng.pdfreader.core.pdf

/**
 * The kind of an annotation that is written into a PDF.
 */
enum class PdfAnnotationKind {

    /** A text that is marked with a colour. */
    HIGHLIGHT,

    /** A text with a line under it. */
    UNDERLINE,

    /** A text with a line through it. */
    STRIKETHROUGH,

    /** A free hand mark. */
    DRAW,

    /** A note that is pinned to the page. */
    NOTE,

    /** A signature that is placed on the page. */
    SIGNATURE,
}

/**
 * An annotation of a page of a PDF.
 *
 * The area and the strokes are expressed in the fractions of the page, the
 * same way the reader draws them: the origin is the top left corner of the
 * page and both axes run from zero to one.
 */
data class PdfAnnotation(
    val kind: PdfAnnotationKind,
    val colour: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** The strokes of a free hand mark, x and y in the fractions of the page. */
    val strokes: List<FloatArray> = emptyList(),
    /** The width of a free hand mark, as a fraction of the width of the page. */
    val width: Float = 0f,
    /** The text of a note. */
    val text: String = "",
)
