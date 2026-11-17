package com.peng.pdfreader.ui.uiData

import android.graphics.RectF
import com.peng.pdfreader.core.widget.InkStroke
import java.util.Locale

/**
 * The geometry of an annotation.
 *
 * The repository only knows the page, the kind and the colour of an
 * annotation, so the position of a mark is encoded into the free text field:
 *
 * - a rectangle is stored as `r:left,top,right,bottom`
 * - the ink is stored as `i:colour:width:x y x y|colour:width:x y`
 *
 * All numbers are fractions of the page (0..1).
 */
object AnnotationPayload {

    private const val PREFIX_RECT = "r:"
    private const val PREFIX_INK = "i:"

    /** Encodes an area of the page. */
    fun encodeRect(rect: RectF): String {
        val builder = StringBuilder(PREFIX_RECT)
        builder.append(format(rect.left)).append(',')
            .append(format(rect.top)).append(',')
            .append(format(rect.right)).append(',')
            .append(format(rect.bottom))
        return builder.toString()
    }

    /** Decodes an area of the page. */
    fun decodeRect(payload: String): RectF? {
        if (!payload.startsWith(PREFIX_RECT)) return null
        val parts = payload.removePrefix(PREFIX_RECT).split(",")
        if (parts.size != 4) return null
        val values = parts.map { it.toFloatOrNull() ?: return null }
        return RectF(values[0], values[1], values[2], values[3])
    }

    /** Encodes the strokes of a free hand mark. */
    fun encodeInk(strokes: List<InkStroke>): String {
        val builder = StringBuilder(PREFIX_INK)
        strokes.forEachIndexed { index, stroke ->
            if (index > 0) builder.append('|')
            builder.append(Integer.toHexString(stroke.color)).append(':')
                .append(format(stroke.width)).append(':')
            stroke.points.forEachIndexed { pointIndex, value ->
                if (pointIndex > 0) builder.append(' ')
                builder.append(format(value))
            }
        }
        return builder.toString()
    }

    /** Decodes the strokes of a free hand mark. */
    fun decodeInk(payload: String): List<InkStroke> {
        if (!payload.startsWith(PREFIX_INK)) return emptyList()
        return payload.removePrefix(PREFIX_INK)
            .split("|")
            .mapNotNull { segment ->
                val fields = segment.split(":")
                if (fields.size < 3) return@mapNotNull null
                val colour = fields[0].toIntOrNull(16) ?: return@mapNotNull null
                val width = fields[1].toFloatOrNull() ?: 0f
                val values = fields[2].trim().split(' ')
                    .mapNotNull { it.toFloatOrNull() }
                if (values.size < 2) return@mapNotNull null
                InkStroke(colour, width, values.toFloatArray())
            }
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.5f", value)
}
