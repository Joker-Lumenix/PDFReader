package com.peng.pdfreader.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * A minimal PDF writer.
 *
 * The framework writer ([android.graphics.pdf.PdfDocument]) embeds a subset
 * font with an Identity-H encoding, so the text of such a file cannot be read
 * back - the search of the reader would find nothing in it. This writer
 * produces a plain PDF with the standard Helvetica font and WinAnsi encoding,
 * so the text stays readable.
 */
internal class MiniPdf(private val width: Int = 595, private val height: Int = 842) {

    /** One text line of a page. */
    data class Line(val text: String, val size: Float, val x: Float, val y: Float)

    private val contents = mutableListOf<ByteArray>()

    /** Adds a page with the given text lines. */
    fun addPage(lines: List<Line>) {
        val content = buildString {
            append("BT\n")
            lines.forEach { line ->
                append("/F1 ${line.size} Tf\n")
                append("${line.x} ${line.y} Td\n")
                append("(${escape(line.text)}) Tj\n")
            }
            append("ET\n")
        }
        contents += content.toByteArray(StandardCharsets.ISO_8859_1)
    }

    /** Writes the file with a correct cross reference table. */
    fun writeTo(target: File) {
        val objects = mutableListOf<ByteArray>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(StandardCharsets.ISO_8859_1)
        objects += (
            "<< /Type /Pages /Kids [${contents.indices.joinToString(" ") { "${4 + it * 2} 0 R" }}] " +
                "/Count ${contents.size} >>"
            ).toByteArray(StandardCharsets.ISO_8859_1)
        objects += (
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
            ).toByteArray(StandardCharsets.ISO_8859_1)
        contents.forEachIndexed { index, content ->
            objects += (
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] " +
                    "/Resources << /Font << /F1 3 0 R >> >> /Contents ${5 + index * 2} 0 R >>"
                ).toByteArray(StandardCharsets.ISO_8859_1)
            // the content has to be a stream object, a reader looks for the
            // stream keyword and its length
            objects += (
                "<< /Length ${content.size} >>\nstream\n" +
                    String(content, StandardCharsets.ISO_8859_1) +
                    "\nendstream"
                ).toByteArray(StandardCharsets.ISO_8859_1)
        }

        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(StandardCharsets.ISO_8859_1))
        val offsets = IntArray(objects.size)
        objects.forEachIndexed { index, bytes ->
            offsets[index] = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write(bytes)
            out.write("\nendobj\n".toByteArray(StandardCharsets.ISO_8859_1))
        }
        val xref = out.size()
        out.write("xref\n0 ${objects.size + 1}\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("0000000000 65535 f \n".toByteArray(StandardCharsets.ISO_8859_1))
        offsets.forEach { offset ->
            out.write("%010d 00000 n \n".format(offset).toByteArray(StandardCharsets.ISO_8859_1))
        }
        out.write(
            "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        target.outputStream().use { it.write(out.toByteArray()) }
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
}
