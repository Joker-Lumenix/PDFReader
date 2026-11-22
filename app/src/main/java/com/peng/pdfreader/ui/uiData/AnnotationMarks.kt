package com.peng.pdfreader.ui.uiData

import android.graphics.Path
import android.graphics.RectF
import com.peng.pdfreader.core.pdf.PdfAnnotation
import com.peng.pdfreader.core.pdf.PdfAnnotationKind
import com.peng.pdfreader.core.widget.PdfMark
import com.peng.pdfreader.core.widget.PdfMarkKind
import com.peng.pdfreader.data.Annotation
import com.peng.pdfreader.data.AnnotationType

/**
 * Turns the stored annotations of a page into the marks the page view paints.
 */
object AnnotationMarks {

    /** The marks of one page. */
    fun marksOf(annotations: List<Annotation>, page: Int): List<PdfMark> {
        val result = ArrayList<PdfMark>()
        annotations.filter { it.page == page }.forEach annotation@{ annotation ->
            when (annotation.type) {
                AnnotationType.HIGHLIGHT,
                AnnotationType.UNDERLINE,
                AnnotationType.STRIKETHROUGH,
                -> {
                    val rect = AnnotationPayload.decodeRect(annotation.text) ?: return@annotation
                    result += PdfMark(kindOf(annotation.type), annotation.color, rect)
                }

                AnnotationType.DRAW,
                AnnotationType.SIGNATURE,
                -> {
                    AnnotationPayload.decodeInk(annotation.text).forEach stroke@{ stroke ->
                        if (stroke.isEmpty) return@stroke
                        val path = Path()
                        var first = true
                        var index = 0
                        while (index + 1 < stroke.points.size) {
                            val x = stroke.points[index]
                            val y = stroke.points[index + 1]
                            if (first) {
                                path.moveTo(x, y)
                                first = false
                            } else {
                                path.lineTo(x, y)
                            }
                            index += 2
                        }
                        result += PdfMark(
                            kind = kindOf(annotation.type),
                            colour = stroke.color,
                            rect = RectF(0f, 0f, 1f, 1f),
                            ink = path,
                            width = stroke.width,
                        )
                    }
                }

                AnnotationType.NOTE -> {
                    result += PdfMark(
                        kind = PdfMarkKind.NOTE,
                        colour = annotation.color,
                        rect = RectF(0.88f, 0.02f, 0.94f, 0.06f),
                    )
                }
            }
        }
        return result
    }

    private fun kindOf(type: AnnotationType): PdfMarkKind = when (type) {
        AnnotationType.HIGHLIGHT -> PdfMarkKind.HIGHLIGHT
        AnnotationType.UNDERLINE -> PdfMarkKind.UNDERLINE
        AnnotationType.STRIKETHROUGH -> PdfMarkKind.STRIKETHROUGH
        AnnotationType.DRAW -> PdfMarkKind.DRAW
        AnnotationType.SIGNATURE -> PdfMarkKind.SIGNATURE
        AnnotationType.NOTE -> PdfMarkKind.NOTE
    }

    /** The kind of an annotation that is written into the file. */
    private fun annotationKindOf(type: AnnotationType): PdfAnnotationKind = when (type) {
        AnnotationType.HIGHLIGHT -> PdfAnnotationKind.HIGHLIGHT
        AnnotationType.UNDERLINE -> PdfAnnotationKind.UNDERLINE
        AnnotationType.STRIKETHROUGH -> PdfAnnotationKind.STRIKETHROUGH
        AnnotationType.DRAW -> PdfAnnotationKind.DRAW
        AnnotationType.SIGNATURE -> PdfAnnotationKind.SIGNATURE
        AnnotationType.NOTE -> PdfAnnotationKind.NOTE
    }

    /**
     * The annotations of a document, ready to be written into the file.
     *
     * The key of the map is the number of the page, it starts at one - the
     * pages of a pdf are counted that way.
     */
    fun pdfAnnotationsOf(annotations: List<Annotation>): Map<Int, List<PdfAnnotation>> {
        val result = LinkedHashMap<Int, MutableList<PdfAnnotation>>()
        annotations.forEach { annotation ->
            val page = annotation.page + 1
            val list = result.getOrPut(page) { ArrayList() }
            when (annotation.type) {
                AnnotationType.HIGHLIGHT,
                AnnotationType.UNDERLINE,
                AnnotationType.STRIKETHROUGH,
                -> {
                    val rect = AnnotationPayload.decodeRect(annotation.text) ?: return@forEach
                    list += PdfAnnotation(
                        kind = annotationKindOf(annotation.type),
                        colour = annotation.color,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                    )
                }

                AnnotationType.DRAW,
                AnnotationType.SIGNATURE,
                -> {
                    AnnotationPayload.decodeInk(annotation.text).forEach { stroke ->
                        if (stroke.isEmpty) return@forEach
                        list += PdfAnnotation(
                            kind = annotationKindOf(annotation.type),
                            colour = stroke.color,
                            left = 0f,
                            top = 0f,
                            right = 1f,
                            bottom = 1f,
                            strokes = listOf(stroke.points),
                            width = stroke.width,
                        )
                    }
                }

                AnnotationType.NOTE -> {
                    list += PdfAnnotation(
                        kind = PdfAnnotationKind.NOTE,
                        colour = annotation.color,
                        left = NOTE_LEFT,
                        top = NOTE_TOP,
                        right = NOTE_RIGHT,
                        bottom = NOTE_BOTTOM,
                        text = annotation.text,
                    )
                }
            }
        }
        return result
    }

    /** The place of the note on the page, the fractions of the page. */
    private const val NOTE_LEFT = 0.88f
    private const val NOTE_TOP = 0.02f
    private const val NOTE_RIGHT = 0.94f
    private const val NOTE_BOTTOM = 0.06f
}
