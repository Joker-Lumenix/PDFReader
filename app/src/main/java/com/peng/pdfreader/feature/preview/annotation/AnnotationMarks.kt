package com.peng.pdfreader.feature.preview.annotation

import android.graphics.Path
import android.graphics.RectF
import com.peng.pdfreader.appcore.widget.PdfMark
import com.peng.pdfreader.appcore.widget.PdfMarkKind
import com.peng.pdfreader.data.repository.Annotation
import com.peng.pdfreader.data.repository.AnnotationType

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
}
