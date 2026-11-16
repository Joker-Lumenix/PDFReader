package com.peng.pdfreader.appcore.pdf

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/**
 * Stream level PDF compressor.
 *
 * The framework renderer can only draw a PDF, it cannot rewrite one, so the
 * compressor works on the raw file:
 *
 * - every embedded JPEG is decoded and re-encoded at the requested quality
 * - every image stored as raw RGB inside a flate stream is converted to JPEG
 * - every other deflated stream is re-deflated with the best compression level
 *
 * The document is then written back with a freshly generated cross reference
 * table, so the result is a plain (non incremental) PDF that every reader can
 * open. The whole class is free of any Android dependency, which keeps the
 * algorithm testable off device.
 */
internal object PdfCompressor {

    /**
     * The biggest document the compressor works on.
     *
     * The compressor rewrites the file, so it holds the source and the result
     * in the memory. A bigger document does not fit into the heap of a phone.
     */
    private const val MAX_SOURCE_BYTES = 32L * 1024 * 1024

    fun compress(
        source: File,
        out: File,
        jpegQuality: Int,
        codec: ImageCodec,
        onProgress: (PdfProgress) -> Unit = {},
    ): Result<CompressResult> = runCatching {
        if (!source.exists() || !source.isFile) {
            throw PdfError.UnreadableSource(source.absolutePath)
        }
        // The compressor rewrites the document, so it needs the whole file in
        // the memory. A document that does not fit into it is refused with a
        // clear message instead of killing the app.
        if (source.length() > MAX_SOURCE_BYTES) {
            throw PdfError.Unsupported(PdfError.CODE_TOO_LARGE)
        }
        val quality = jpegQuality.coerceIn(1, 100)
        val original = source.readBytes()
        val parsed = parse(original) ?: throw PdfError.Unsupported(PdfError.CODE_STRUCTURE)
        if (parsed.encrypted) throw PdfError.Unsupported(PdfError.CODE_ENCRYPTED)

        val objects = parsed.objects.values.toMutableList()
        val total = objects.size
        var recompressedImages = 0

        objects.forEachIndexed { index, obj ->
            val updated = recompress(obj, quality, codec)
            if (updated !== obj) {
                objects[index] = updated
                if (updated.isJpegImage) recompressedImages++
            }
            onProgress(PdfProgress(PdfStage.COMPRESS, index + 1, total))
        }

        val rebuilt = build(objects, parsed)
        val pages = parsed.pageCount()

        // A compressor must never make a document bigger: when the rebuilt file
        // grew, the original bytes are kept.
        val keepOriginal = rebuilt.size >= original.size
        val payload = if (keepOriginal) original else rebuilt

        out.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val temporary = File(out.absolutePath + ".tmp")
        temporary.outputStream().use { it.write(payload) }
        if (!temporary.renameTo(out)) {
            out.outputStream().use { it.write(payload) }
            temporary.delete()
        }

        CompressResult(
            originalSize = original.size.toLong(),
            newSize = payload.size.toLong(),
            pages = pages,
            recompressedImages = if (keepOriginal) 0 else recompressedImages,
            keptOriginal = keepOriginal,
        )
    }

    // ------------------------------------------------------------------ object model

    private class Obj(
        val number: Int,
        val generation: Int,
        val dict: PdfDict,
        val stream: ByteArray?,
    ) {
        /** True when the object is a JPEG image XObject. */
        val isJpegImage: Boolean
            get() = stream != null && dict.isImage && dict.filters() == listOf("DCTDecode")
    }

    private class ParsedPdf(
        val objects: LinkedHashMap<Int, Obj>,
        val rootRef: String?,
        val infoRef: String?,
        val idEntry: String?,
        val encrypted: Boolean,
        val version: String,
    ) {
        fun pageCount(): Int = objects.values.count { it.dict.isPage }
    }

    // ------------------------------------------------------------------ parsing

    private fun parse(bytes: ByteArray): ParsedPdf? {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val objects = LinkedHashMap<Int, Obj>()

        val header = Regex("""(\d+)\s+(\d+)\s+obj\b""")
        var searchFrom = 0
        while (searchFrom < raw.length) {
            val match = header.find(raw, searchFrom) ?: break
            val number = match.groupValues[1].toInt()
            val generation = match.groupValues[2].toInt()
            val bodyStart = match.range.last + 1
            val endObj = raw.indexOf("endobj", bodyStart).let { if (it < 0) raw.length else it }

            val streamKeyword = raw.indexOf("stream", bodyStart)
            val hasStream = streamKeyword in bodyStart until endObj
            val dictEnd = if (hasStream) streamKeyword else endObj
            val dict = PdfDict(raw.substring(bodyStart, dictEnd).trim())

            var stream: ByteArray? = null
            var nextFrom = endObj
            if (hasStream) {
                var dataStart = streamKeyword + "stream".length
                if (raw.getOrNull(dataStart) == '\r') dataStart++
                if (raw.getOrNull(dataStart) == '\n') dataStart++
                val length = dict.intValue("Length")
                var dataEnd = -1
                if (length != null && length >= 0 && dataStart + length <= bytes.size) {
                    val candidate = dataStart + length
                    val tail = raw.substring(candidate, minOf(raw.length, candidate + 16)).trimStart()
                    if (tail.startsWith("endstream")) dataEnd = candidate
                }
                if (dataEnd < 0) {
                    val endStream = raw.indexOf("endstream", dataStart)
                    dataEnd = if (endStream < 0) bytes.size else endStream
                    while (dataEnd > dataStart &&
                        (bytes[dataEnd - 1] == '\n'.code.toByte() || bytes[dataEnd - 1] == '\r'.code.toByte())
                    ) {
                        dataEnd--
                    }
                }
                stream = bytes.copyOfRange(dataStart, minOf(dataEnd, bytes.size))
                val endStreamIndex = raw.indexOf("endstream", dataStart)
                nextFrom = if (endStreamIndex < 0) endObj else endStreamIndex + "endstream".length
            }

            objects[number] = Obj(number, generation, dict, stream)
            searchFrom = maxOf(nextFrom, match.range.last + 1)
        }

        if (objects.isEmpty()) return null

        // Expand compressed object streams: their members become regular
        // objects so the rewritten file can use a classic cross reference table.
        val expanded = expandObjectStreams(objects)
        expanded.forEach { (number, obj) -> if (!objects.containsKey(number)) objects[number] = obj }

        // The cross reference stream carries the trailer of a PDF 1.5 file and
        // the linearisation hint describes the old byte layout - both are
        // rebuilt from scratch.
        val xrefTrailer = objects.values.firstOrNull { it.dict.isXrefStream }?.dict
        val obsolete = objects.entries
            .filter { it.value.dict.isXrefStream || it.value.dict.isLinearized }
            .map { it.key }
        obsolete.forEach { objects.remove(it) }

        val trailer = readTrailer(raw)
        val rootRef = trailer.rootRef
            ?: xrefTrailer?.referenceValue("Root")
            ?: objects.values.firstOrNull { it.dict.isCatalog }?.let { "${it.number} 0 R" }
            ?: return null
        val infoRef = trailer.infoRef ?: xrefTrailer?.referenceValue("Info")
        val idEntry = trailer.idEntry ?: xrefTrailer?.rawIdEntry()
        val encrypted = trailer.encrypted || xrefTrailer?.hasKey("Encrypt") == true

        return ParsedPdf(objects, rootRef, infoRef, idEntry, encrypted, headerVersion(raw))
    }

    /** The PDF version of the source document, for example "1.7". */
    private fun headerVersion(raw: String): String {
        val match = Regex("""%PDF-(\d\.\d)""").find(raw) ?: return "1.4"
        val version = match.groupValues[1]
        return if (version.toFloat() >= 1.7f) "1.7" else version
    }

    private class Trailer(
        val rootRef: String?,
        val infoRef: String?,
        val idEntry: String?,
        val encrypted: Boolean,
    )

    private fun readTrailer(raw: String): Trailer {
        val index = raw.lastIndexOf("trailer")
        if (index < 0) return Trailer(null, null, null, false)
        val dict = PdfDict(raw.substring(index + "trailer".length, minOf(raw.length, index + 4096)))
        return Trailer(
            rootRef = dict.referenceValue("Root"),
            infoRef = dict.referenceValue("Info"),
            idEntry = dict.rawIdEntry(),
            encrypted = dict.hasKey("Encrypt"),
        )
    }

    private fun expandObjectStreams(objects: LinkedHashMap<Int, Obj>): Map<Int, Obj> {
        val result = LinkedHashMap<Int, Obj>()
        objects.values.forEach { obj ->
            if (!obj.dict.isObjStm || obj.stream == null) return@forEach
            val inflated = inflate(obj.stream) ?: return@forEach
            val count = obj.dict.intValue("N") ?: return@forEach
            val first = obj.dict.intValue("First") ?: return@forEach
            if (first <= 0 || first > inflated.size) return@forEach
            val header = String(inflated, 0, first, Charsets.ISO_8859_1)
            val pairs = Regex("""(\d+)\s+(\d+)""").findAll(header)
                .take(count.coerceAtLeast(0))
                .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
                .toList()
            if (pairs.isEmpty()) return@forEach
            val ordered = pairs.sortedBy { it.second }
            ordered.forEachIndexed { position, (memberNumber, offset) ->
                val start = first + offset
                if (start < 0 || start >= inflated.size) return@forEachIndexed
                val end = if (position + 1 < ordered.size) {
                    first + ordered[position + 1].second
                } else {
                    inflated.size
                }
                if (end <= start) return@forEachIndexed
                val body = String(inflated, start, minOf(end, inflated.size), Charsets.ISO_8859_1).trim()
                result[memberNumber] = Obj(memberNumber, 0, PdfDict(body), null)
            }
        }
        return result
    }

    // ------------------------------------------------------------------ recompression

    private fun recompress(obj: Obj, quality: Int, codec: ImageCodec): Obj {
        val stream = obj.stream ?: return obj
        val filters = obj.dict.filters()

        if (obj.dict.isImage) {
            val jpeg = recompressImage(obj, stream, filters, quality, codec)
            if (jpeg != null) return jpeg
        }

        if (filters == listOf("FlateDecode")) {
            val raw = inflate(stream) ?: return obj
            val deflated = deflate(raw)
            if (deflated.size < stream.size) {
                val dict = obj.dict.replace("Length", deflated.size.toString())
                return Obj(obj.number, obj.generation, dict, deflated)
            }
        }
        return obj
    }

    private fun recompressImage(
        obj: Obj,
        stream: ByteArray,
        filters: List<String>,
        quality: Int,
        codec: ImageCodec,
    ): Obj? {
        // 1) A plain JPEG image: decode and re-encode it.
        if (filters == listOf("DCTDecode")) {
            val space = obj.dict.colorSpace()
            // Only the two simple colour spaces are safe to re-encode, an
            // embedded ICC profile would no longer match the new pixels.
            if (space != null && space != "/DeviceRGB" && space != "/DeviceGray") return null
            if (obj.dict.hasDecodeArray()) return null
            val decoded = codec.decodeJpeg(stream) ?: return null
            val encoded = codec.encodeJpeg(decoded, quality)
            if (encoded.size >= stream.size) return null
            var dict = obj.dict.replace("Length", encoded.size.toString())
            // A grayscale JPEG is re-encoded as YCbCr, which is only valid when
            // the image is described as RGB.
            if (space == null || space == "/DeviceGray") {
                dict = dict.ensure("ColorSpace", "/DeviceRGB")
            }
            return Obj(obj.number, obj.generation, dict, encoded)
        }

        // 2) A raw RGB image inside a flate stream: turn it into a JPEG.
        if (filters == listOf("FlateDecode")) {
            if (obj.dict.hasDecodeArray()) return null
            if (obj.dict.hasPredictor()) return null
            if (obj.dict.colorSpace() != "/DeviceRGB") return null
            if (obj.dict.intValue("BitsPerComponent") != 8) return null
            val width = obj.dict.intValue("Width") ?: return null
            val height = obj.dict.intValue("Height") ?: return null
            if (width <= 0 || height <= 0) return null
            val raw = inflate(stream) ?: return null
            if (raw.size != width * height * 3) return null
            val decoded = codec.decodeRgb(width, height, raw) ?: return null
            val encoded = codec.encodeJpeg(decoded, quality)
            if (encoded.size >= raw.size) return null
            val dict = obj.dict
                .replace("Length", encoded.size.toString())
                .replace("Filter", "/DCTDecode")
                .ensure("ColorSpace", "/DeviceRGB")
                .remove("DecodeParms")
            return Obj(obj.number, obj.generation, dict, encoded)
        }
        return null
    }

    // ------------------------------------------------------------------ writing

    private fun build(objects: List<Obj>, parsed: ParsedPdf): ByteArray {
        val buffer = ByteArrayOutputStream()
        val offsets = HashMap<Int, Long>()
        var position = 0L

        fun writeText(text: String) {
            val data = text.toByteArray(Charsets.ISO_8859_1)
            buffer.write(data)
            position += data.size
        }

        fun writeBytes(data: ByteArray) {
            buffer.write(data)
            position += data.size
        }

        val sorted = objects.sortedBy { it.number }
        // Every PDF starts with the version marker and a binary comment, so
        // tools can recognise the file as binary content.
        writeText("%PDF-${parsed.version}\n")
        writeBytes(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
        sorted.forEach { obj ->
            offsets[obj.number] = position
            writeText("${obj.number} ${obj.generation} obj\n")
            val stream = obj.stream
            if (stream == null) {
                writeText(obj.dict.text)
            } else {
                writeText(obj.dict.text)
                writeText("\nstream\n")
                writeBytes(stream)
                writeText("\nendstream")
            }
            writeText("\nendobj\n")
        }

        val size = (offsets.keys.maxOrNull() ?: 0) + 1
        val xrefPosition = position
        writeText("xref\n")

        // The cross reference table is written in subsections so that every
        // entry keeps its natural generation.
        val present = sorted.filter { offsets.containsKey(it.number) }
        var index = 0
        while (index < present.size) {
            var end = index
            while (end + 1 < present.size && present[end + 1].number == present[end].number + 1) end++
            val first = present[index].number
            val count = end - index + 1
            writeText("$first $count\n")
            for (i in index..end) {
                val obj = present[i]
                writeText(String.format("%010d %05d n \n", offsets[obj.number], obj.generation))
            }
            index = end + 1
        }

        val trailer = buildString {
            append("trailer\n<< /Size ").append(size)
            parsed.rootRef?.let { append(" /Root ").append(it) }
            parsed.infoRef?.let { append(" /Info ").append(it) }
            parsed.idEntry?.let { append(" /ID ").append(it) }
            append(" >>\n")
            append("startxref\n").append(xrefPosition).append("\n%%EOF\n")
        }
        writeText(trailer)

        return buffer.toByteArray()
    }

    // ------------------------------------------------------------------ codec helpers

    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = ByteArrayOutputStream(data.size * 2)
        val chunk = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(chunk)
            if (count <= 0) break
            output.write(chunk, 0, count)
        }
        inflater.end()
        output.toByteArray()
    }.getOrNull()

    private fun deflate(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(data.size)
        DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION), 16 * 1024).use { stream ->
            stream.write(data)
            stream.finish()
        }
        return output.toByteArray()
    }
}

/**
 * A thin read only view on a PDF dictionary.
 *
 * The class only offers the handful of lookups the compressor needs and knows
 * how to rewrite a single entry without disturbing the rest of the text.
 */
internal class PdfDict(val text: String) {

    fun hasKey(key: String): Boolean = keyPattern(key).containsMatchIn(text)

    val isImage: Boolean get() = valuePattern("Subtype")?.trim() == "/Image"

    val isPage: Boolean get() = valuePattern("Type")?.trim() == "/Page"

    val isCatalog: Boolean get() = valuePattern("Type")?.trim() == "/Catalog"

    val isObjStm: Boolean get() = valuePattern("Type")?.trim() == "/ObjStm"

    val isXrefStream: Boolean get() = valuePattern("Type")?.trim() == "/XRef"

    val isLinearized: Boolean get() = hasKey("Linearized")

    fun intValue(key: String): Int? {
        val value = valuePattern(key) ?: return null
        return value.trim().toIntOrNull()
    }

    /** The raw colour space entry, for example "/DeviceRGB". */
    fun colorSpace(): String? {
        val value = valuePattern("ColorSpace") ?: return null
        return value.trim()
    }

    /** All filter names of the stream, in the order they are applied. */
    fun filters(): List<String> {
        val value = valuePattern("Filter") ?: return emptyList()
        val trimmed = value.trim()
        if (trimmed.startsWith("[")) {
            return Regex("""/([A-Za-z0-9]+)""").findAll(trimmed).map { it.groupValues[1] }.toList()
        }
        if (!trimmed.startsWith("/")) return emptyList()
        return listOf(trimmed.removePrefix("/"))
    }

    /** True when the image carries a /Decode array, which changes the pixels. */
    fun hasDecodeArray(): Boolean = hasKey("Decode")

    /**
     * True when the stream data is run through a predictor.
     *
     * An unknown predictor is treated as present, so the conservative path is
     * taken and the data is left untouched.
     */
    fun hasPredictor(): Boolean {
        if (!hasKey("DecodeParms")) return hasKey("Predictor")
        val value = valuePattern("DecodeParms") ?: return true
        val trimmed = value.trim()
        if (!trimmed.startsWith("<<")) return true
        val predictor = Regex("""/Predictor\s+(\d+)""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
        return predictor != null && predictor > 1
    }

    /** An indirect reference such as "12 0 R", or null. */
    fun referenceValue(key: String): String? {
        val value = valuePattern(key) ?: return null
        return value.trim().takeIf { Regex("""\d+\s+\d+\s+R""").matches(it) }
    }

    /** The raw /ID entry, for example "[<00><11>]". */
    fun rawIdEntry(): String? {
        val value = valuePattern("ID") ?: return null
        return value.trim().takeIf { it.startsWith("[") }
    }

    /** Replaces the value of [key]; the key itself is kept. */
    fun replace(key: String, value: String): PdfDict = PdfDict(replaceText(key, value))

    /** Inserts [key] when it is missing, otherwise replaces its value. */
    fun ensure(key: String, value: String): PdfDict =
        if (hasKey(key)) replace(key, value) else PdfDict(ensureText(key, value))

    /** Removes [key] together with its value. */
    fun remove(key: String): PdfDict = PdfDict(removeText(key))

    // ------------------------------------------------------------------ internals

    private fun replaceText(key: String, value: String): String {
        val match = keyPattern(key).find(text) ?: return text
        val start = valueStart(text, match.range.last + 1)
        val end = valueEnd(text, start)
        return text.substring(0, match.range.first) + "/" + key + " " + value + text.substring(end)
    }

    private fun ensureText(key: String, value: String): String {
        val close = text.lastIndexOf(">>")
        if (close < 0) return text
        val head = text.substring(0, close).trimEnd()
        val tail = text.substring(close)
        return "$head /$key $value $tail"
    }

    private fun removeText(key: String): String {
        val match = keyPattern(key).find(text) ?: return text
        val start = valueStart(text, match.range.last + 1)
        val end = valueEnd(text, start)
        return text.substring(0, match.range.first) + text.substring(end)
    }

    private fun keyPattern(key: String): Regex =
        Regex("(?<![A-Za-z0-9])/" + Regex.escape(key) + "(?![A-Za-z0-9])")

    private fun valuePattern(key: String): String? {
        val match = keyPattern(key).find(text) ?: return null
        val start = valueStart(text, match.range.last + 1)
        val end = valueEnd(text, start)
        if (end <= start) return null
        return text.substring(start, end)
    }

    private fun valueStart(text: String, from: Int): Int {
        var index = from
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun valueEnd(text: String, start: Int): Int {
        if (start >= text.length) return text.length
        return when (text[start]) {
            '<' -> if (text.startsWith("<<", start)) blockEnd(text, start) else {
                val close = text.indexOf('>', start)
                if (close < 0) text.length else close + 1
            }
            '[' -> blockEnd(text, start)
            '/' -> {
                var index = start + 1
                while (index < text.length && (text[index].isLetterOrDigit() ||
                        text[index] == '#' || text[index] == '.' ||
                        text[index] == '_' || text[index] == '-')
                ) {
                    index++
                }
                index
            }
            else -> {
                var index = start
                while (index < text.length && !text[index].isWhitespace() &&
                    text[index] != '>' && text[index] != ']' && text[index] != '/' &&
                    text[index] != '[' && text[index] != '<'
                ) {
                    index++
                }
                if (index == start) return start + 1
                // A bare number may be the start of an indirect reference.
                if (text.substring(start, index).all { it.isDigit() }) {
                    val reference = Regex("""\s+\d+\s+R""").find(text, index)
                    if (reference != null) return reference.range.last + 1
                }
                index
            }
        }
    }

    /** End of a balanced &lt;&lt; &gt;&gt; or [ ] block. */
    private fun blockEnd(text: String, start: Int): Int {
        val open = text[start]
        val close = if (open == '<') '>' else ']'
        var depth = 0
        var index = start
        while (index < text.length) {
            val char = text[index]
            if (char == open) {
                depth++
                index++
                if (open == '<' && index < text.length && text[index] == '<') index++
                continue
            }
            if (char == close) {
                depth--
                index++
                if (open == '<' && index < text.length && text[index] == '>') index++
                if (depth == 0) return index
                continue
            }
            index++
        }
        return text.length
    }
}
