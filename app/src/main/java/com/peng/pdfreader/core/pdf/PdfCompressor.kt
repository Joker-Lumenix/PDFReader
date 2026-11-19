package com.peng.pdfreader.core.pdf

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Locale
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
 *
 * The compressor streams the file: it reads one object after the other and
 * writes it to the result right away, so a document of any size fits into the
 * memory of a phone.
 */
internal object PdfCompressor {

    /**
     * How much of the file is read at once while the objects are looked for.
     *
     * The buffer is used for every object, so it stays small: a big one would
     * fill the heap with the copies of a document with many objects.
     */
    private const val SCAN_CHUNK = 32 * 1024

    /** How much is read to find the dictionary of an object. */
    private const val DICT_CHUNK = 64 * 1024

    /**
     * The most pixels a picture may have before it is scaled down.
     *
     * A picture of that size needs 6MB of memory as a bitmap, more than a
     * phone can spare for a single picture of a document.
     */
    private const val MAX_PIXELS = 1_500_000L

    /** The most bytes a stream may have after it was unpacked. */
    private const val MAX_INFLATED = 32 * 1024 * 1024

    /**
     * The most bytes a stream may have to be re-encoded.
     *
     * A bigger stream is copied over as it is, so it never sits in the memory
     * as a whole.
     */
    private const val MAX_STREAM = 24 * 1024 * 1024

    /**
     * The most bytes a stream may have to be packed again.
     *
     * The content of a document rarely gets much smaller, so the big streams
     * are left alone - unpacking them would fill the heap with copies.
     */
    private const val MAX_REDEFLATE = 2 * 1024 * 1024

    /** The pattern of the header of an object. */
    private val HEADER = Regex("""(\d+)\s+(\d+)\s+obj\b""")

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
        val quality = jpegQuality.coerceIn(1, 100)
        out.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val temporary = File(out.absolutePath + ".tmp")
        val result = rewrite(source, temporary, quality, codec, onProgress)

        // A compressor must never make a document bigger: when the rebuilt file
        // grew, the original bytes are kept.
        if (temporary.length() >= source.length()) {
            temporary.delete()
            source.copyTo(out, overwrite = true)
            result.copy(
                newSize = source.length(),
                recompressedImages = 0,
                keptOriginal = true,
            )
        } else {
            if (!temporary.renameTo(out)) {
                temporary.copyTo(out, overwrite = true)
                temporary.delete()
            }
            result
        }
    }

    // ------------------------------------------------------------------ rewriting

    /**
     * Writes the compressed document into [out].
     *
     * The file is read object by object, so only one of them is held in the
     * memory at a time.
     */
    private fun rewrite(
        source: File,
        out: File,
        quality: Int,
        codec: ImageCodec,
        onProgress: (PdfProgress) -> Unit,
    ): CompressResult {
        RandomAccessFile(source, "r").use { raf ->
            val length = raf.length()
            val version = headerVersion(raf)
            val writer = PdfWriter(out)

            var rootRef: String? = null
            var infoRef: String? = null
            var idEntry: String? = null
            var encrypted = false
            var pages = 0
            var recompressed = 0

            writer.text("%PDF-$version\n")
            writer.bytes(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

            var searchFrom = 0L
            var index = 0
            while (searchFrom < length) {
                val found = findHeader(raf, searchFrom, length) ?: break
                val obj = readObject(raf, found.offset, length) ?: break
                searchFrom = obj.end

                when {
                    // The cross reference stream carries the trailer of a PDF 1.5
                    // file - the values are read from it and the object itself
                    // is dropped, the new file has a classic table.
                    obj.dict.isXrefStream -> {
                        val dict = PdfDict(
                            String(inflate(obj.stream ?: ByteArray(0)) ?: ByteArray(0), Charsets.ISO_8859_1),
                        )
                        rootRef = dict.referenceValue("Root") ?: rootRef
                        infoRef = dict.referenceValue("Info") ?: infoRef
                        idEntry = dict.rawIdEntry() ?: idEntry
                        encrypted = encrypted || dict.hasKey("Encrypt")
                    }

                    // The linearisation hint describes the old byte layout, it
                    // is rebuilt from scratch.
                    obj.dict.isLinearized -> Unit

                    // The members of an object stream become regular objects,
                    // so the new file can use a classic cross reference table.
                    obj.dict.isObjStm -> {
                        expandObjectStream(obj)?.forEach { member ->
                            if (member.dict.isPage) pages++
                            writer.writeObject(member, raf)
                        }
                    }

                    else -> {
                        val updated = recompress(obj, quality, codec)
                        if (updated.dict.isPage) pages++
                        if (updated.isJpegImage) recompressed++
                        writer.writeObject(updated, raf)
                    }
                }

                if (length > 0) {
                    val percent = (searchFrom * 100 / length).toInt().coerceIn(0, 100)
                    onProgress(PdfProgress(PdfStage.COMPRESS, percent, 100))
                }
                index++
            }

            // The classic trailer of the file, if it has one.
            val trailer = readTrailer(raf)
            rootRef = trailer.rootRef ?: rootRef
            infoRef = trailer.infoRef ?: infoRef
            idEntry = trailer.idEntry ?: idEntry
            encrypted = encrypted || trailer.encrypted
            if (rootRef == null) throw PdfError.Unsupported(PdfError.CODE_STRUCTURE)

            writer.finish(rootRef, infoRef, idEntry)
            writer.close()

            return CompressResult(
                originalSize = source.length(),
                newSize = out.length(),
                pages = pages,
                recompressedImages = recompressed,
                keptOriginal = false,
            )
        }
    }

    /** One object of the file. */
    private class Obj(
        val number: Int,
        val generation: Int,
        val dict: PdfDict,
        val stream: ByteArray?,
        val end: Long,
        /** The place of the data in the source file, when it is too big for the memory. */
        val streamRange: LongArray? = null,
    ) {
        /** True when the object is a JPEG image XObject. */
        val isJpegImage: Boolean
            get() = stream != null && dict.isImage && dict.filters() == listOf("DCTDecode")
    }

    /** The header of an object and where it starts. */
    private class Found(val number: Int, val generation: Int, val offset: Long)

    /** The trailer of a file with a classic cross reference table. */
    private class Trailer(
        val rootRef: String?,
        val infoRef: String?,
        val idEntry: String?,
        val encrypted: Boolean,
    )

    // ------------------------------------------------------------------ reading

    /** Looks for the next object header at or after [from]. */
    private fun findHeader(raf: RandomAccessFile, from: Long, length: Long): Found? {
        var position = from
        var carry = ""
        while (position < length) {
            raf.seek(position)
            val read = minOf(SCAN_CHUNK.toLong(), length - position).toInt()
            val chunk = ByteArray(read)
            raf.readFully(chunk)
            val text = carry + String(chunk, 0, read, Charsets.ISO_8859_1)
            val match = HEADER.find(text)
            if (match != null) {
                val offset = position - carry.length + match.range.first
                return Found(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    offset,
                )
            }
            // The pattern may be cut in half at the end of the chunk, so the
            // tail is kept for the next round.
            val keep = minOf(text.length, 64)
            carry = text.substring(text.length - keep)
            position += (text.length - keep).toLong()
        }
        return null
    }

    /** Reads the object that starts at [offset]. */
    private fun readObject(raf: RandomAccessFile, offset: Long, length: Long): Obj? {
        raf.seek(offset)
        val window = ByteArray(DICT_CHUNK)
        val read = raf.read(window)
        if (read <= 0) return null
        val text = String(window, 0, read, Charsets.ISO_8859_1)
        val header = HEADER.find(text) ?: return null
        val number = header.groupValues[1].toInt()
        val generation = header.groupValues[2].toInt()
        val bodyStart = header.range.last + 1
        val endObj = text.indexOf("endobj", bodyStart).let { if (it < 0) text.length else it }
        val streamKeyword = text.indexOf("stream", bodyStart)
        val hasStream = streamKeyword in bodyStart until endObj

        if (!hasStream) {
            val dict = PdfDict(text.substring(bodyStart, endObj).trim())
            return Obj(number, generation, dict, null, offset + endObj + 6)
        }
        val dict = PdfDict(text.substring(bodyStart, streamKeyword).trim())
        var dataStart = offset + streamKeyword + "stream".length
        if (readByte(raf, dataStart) == '\r'.code) dataStart++
        if (readByte(raf, dataStart) == '\n'.code) dataStart++

        var dataEnd = -1L
        val declared = dict.intValue("Length")
        if (declared != null && declared >= 0 && dataStart + declared <= length) {
            // The keyword behind the data tells whether the length of the
            // dictionary is right.
            val tail = readText(raf, dataStart + declared, 24)
            if (tail.trimStart().startsWith("endstream")) dataEnd = dataStart + declared
        }
        if (dataEnd < 0) dataEnd = findEndStream(raf, dataStart, length)

        // Only the pictures are unpacked, they are re-encoded. Every other
        // stream is copied over in pieces, so it never sits in the memory.
        if (!dict.isImage && dataEnd - dataStart > MAX_REDEFLATE) {
            return Obj(number, generation, dict, null, dataEnd, longArrayOf(dataStart, dataEnd))
        }
        val stream = readBytes(raf, dataStart, dataEnd - dataStart)
        return Obj(number, generation, dict, stream, dataEnd)
    }

    /** Reads the byte at the given position, -1 when the file ends. */
    private fun readByte(raf: RandomAccessFile, position: Long): Int {
        if (position < 0 || position >= raf.length()) return -1
        raf.seek(position)
        return raf.read()
    }

    /** Reads [count] bytes at the given position. */
    private fun readBytes(raf: RandomAccessFile, position: Long, count: Long): ByteArray {
        val size = count.coerceAtLeast(0).coerceAtMost(raf.length() - position)
        if (size <= 0) return ByteArray(0)
        raf.seek(position)
        val data = ByteArray(size.toInt())
        raf.readFully(data)
        return data
    }

    /** Reads [count] characters at the given position. */
    private fun readText(raf: RandomAccessFile, position: Long, count: Int): String {
        if (position < 0 || position >= raf.length()) return ""
        raf.seek(position)
        val data = ByteArray(minOf(count.toLong(), raf.length() - position).toInt())
        raf.readFully(data)
        return String(data, Charsets.ISO_8859_1)
    }

    /** Looks for the end of the stream data. */
    private fun findEndStream(raf: RandomAccessFile, from: Long, length: Long): Long {
        val pattern = "endstream"
        var position = from
        var carry = ""
        while (position < length) {
            raf.seek(position)
            val read = minOf(SCAN_CHUNK.toLong(), length - position).toInt()
            val chunk = ByteArray(read)
            raf.readFully(chunk)
            val text = carry + String(chunk, 0, read, Charsets.ISO_8859_1)
            val index = text.indexOf(pattern)
            if (index >= 0) {
                var end = position - carry.length + index
                // The line break in front of the keyword belongs to the file.
                if (end > from && readByte(raf, end - 1) == '\n'.code) end--
                if (end > from && readByte(raf, end - 1) == '\r'.code) end--
                return end
            }
            val keep = minOf(text.length, 32)
            carry = text.substring(text.length - keep)
            position += (text.length - keep).toLong()
        }
        return length
    }

    /** The PDF version of the file, for example "1.7". */
    private fun headerVersion(raf: RandomAccessFile): String {
        val head = readText(raf, 0, 64)
        val match = Regex("""%PDF-(\d\.\d)""").find(head) ?: return "1.4"
        val version = match.groupValues[1]
        return if (version.toFloat() >= 1.7f) "1.7" else version
    }

    /** The classic trailer of the file. */
    private fun readTrailer(raf: RandomAccessFile): Trailer {
        val length = raf.length()
        val tail = readText(raf, maxOf(0, length - 8192), 8192)
        val index = tail.lastIndexOf("trailer")
        if (index < 0) return Trailer(null, null, null, false)
        val dict = PdfDict(tail.substring(index + "trailer".length).trim())
        return Trailer(
            rootRef = dict.referenceValue("Root"),
            infoRef = dict.referenceValue("Info"),
            idEntry = dict.rawIdEntry(),
            encrypted = dict.hasKey("Encrypt"),
        )
    }

    /** The members of an object stream as regular objects. */
    private fun expandObjectStream(obj: Obj): List<Obj>? {
        val stream = obj.stream ?: return null
        val inflated = inflate(stream) ?: return null
        val count = obj.dict.intValue("N") ?: return null
        val first = obj.dict.intValue("First") ?: return null
        if (first <= 0 || first > inflated.size) return null
        val header = String(inflated, 0, first, Charsets.ISO_8859_1)
        val pairs = Regex("""(\d+)\s+(\d+)""").findAll(header)
            .take(count.coerceAtLeast(0))
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toList()
        if (pairs.isEmpty()) return null
        val ordered = pairs.sortedBy { it.second }
        val result = mutableListOf<Obj>()
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
            result += Obj(memberNumber, 0, PdfDict(body), null, 0)
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
            // Only the small streams are unpacked and packed again: a big one
            // would fill the heap with the copies of the buffer, and the
            // content of a document rarely gets much smaller.
            if (stream.size > MAX_REDEFLATE) return obj
            val raw = inflate(stream) ?: return obj
            val deflated = deflate(raw)
            if (deflated.size < stream.size) {
                val dict = obj.dict.replace("Length", deflated.size.toString())
                return Obj(obj.number, obj.generation, dict, deflated, obj.end)
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
            // A picture that is too big is scaled down while it is decoded, so
            // the memory of the compressor stays small.
            val decoded = codec.decodeJpeg(stream, MAX_PIXELS.toInt()) ?: return null
            val encoded = codec.encodeJpeg(decoded, quality)
            if (encoded.size >= stream.size) return null
            var dict = obj.dict.replace("Length", encoded.size.toString())
            // The picture may have been scaled down, the dictionary has to
            // follow - the page still shows it at the same place.
            dict = dict.replace("Width", decoded.width.toString())
            dict = dict.replace("Height", decoded.height.toString())
            // A grayscale JPEG is re-encoded as YCbCr, which is only valid when
            // the image is described as RGB.
            if (space == null || space == "/DeviceGray") {
                dict = dict.ensure("ColorSpace", "/DeviceRGB")
            }
            return Obj(obj.number, obj.generation, dict, encoded, obj.end)
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
            // A picture that is too big for the memory is scaled down, so the
            // decoder only holds a part of it.
            val step = sampleStep(width, height)
            // The rows are unpacked one after the other, the picture never
            // sits in the memory as a whole.
            val reader = RowReader(stream, width * 3, height)
            val decoded = codec.decodeRgbRows(width, height, step) { reader.next() } ?: return null
            val encoded = codec.encodeJpeg(decoded, quality)
            if (encoded.size >= stream.size) return null
            val dict = obj.dict
                .replace("Length", encoded.size.toString())
                .replace("Filter", "/DCTDecode")
                .replace("Width", decoded.width.toString())
                .replace("Height", decoded.height.toString())
                .ensure("ColorSpace", "/DeviceRGB")
                .remove("DecodeParms")
            return Obj(obj.number, obj.generation, dict, encoded, obj.end)
        }
        return null
    }

    /**
     * Reads the rows of a raw picture one after the other.
     *
     * The rows are unpacked as they are needed, so the memory of the reader
     * stays at one row.
     */
    private class RowReader(
        private val stream: ByteArray,
        private val rowBytes: Int,
        private val rows: Int,
    ) {
        private val inflater = Inflater()
        private val buffer = ByteArray(rowBytes)
        private var filled = 0
        private var read = 0

        init {
            inflater.setInput(stream)
        }

        /** The next row of the picture, null at the end. */
        fun next(): ByteArray? {
            if (read >= rows) return null
            while (filled < rowBytes) {
                val count = inflater.inflate(buffer, filled, rowBytes - filled)
                if (count <= 0) {
                    inflater.end()
                    return null
                }
                filled += count
            }
            filled = 0
            read++
            // The buffer is reused, the reader of the row has to be done with
            // it before the next row is asked for.
            return buffer
        }
    }

    /**
     * The step between two pixels of a picture that is too big.
     *
     * A picture that is scaled down by the step keeps its place on the page -
     * the dictionary of the picture follows.
     */
    private fun sampleStep(width: Int, height: Int): Int {
        var step = 1
        val pixels = width.toLong() * height.toLong()
        while (pixels / (step.toLong() * step.toLong()) > MAX_PIXELS) step *= 2
        return step
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes the new document.
     *
     * The objects are written one after the other, the cross reference table
     * is built from the places they landed on.
     */
    private class PdfWriter(out: File) {

        private val stream: OutputStream = BufferedOutputStream(FileOutputStream(out), 64 * 1024)
        private var position = 0L
        private val offsets = HashMap<Int, Long>()

        /** Writes text into the file. */
        fun text(value: String) {
            val data = value.toByteArray(Charsets.ISO_8859_1)
            stream.write(data)
            position += data.size
        }

        /** Writes bytes into the file. */
        fun bytes(value: ByteArray) {
            stream.write(value)
            position += value.size
        }

        /** Writes one object. */
        fun writeObject(obj: Obj, source: RandomAccessFile? = null) {
            offsets[obj.number] = position
            text("${obj.number} ${obj.generation} obj\n")
            text(obj.dict.text)
            val data = obj.stream
            if (data != null) {
                text("\nstream\n")
                bytes(data)
                text("\nendstream")
            } else if (obj.streamRange != null && source != null) {
                // A stream that is too big for the memory is copied over in
                // pieces, so it never sits in the memory as a whole.
                text("\nstream\n")
                copy(source, obj.streamRange[0], obj.streamRange[1])
                text("\nendstream")
            }
            text("\nendobj\n")
        }

        /** Copies a part of the source file into the result. */
        private fun copy(source: RandomAccessFile, from: Long, to: Long) {
            val chunk = ByteArray(64 * 1024)
            var position = from
            source.seek(position)
            while (position < to) {
                val read = source.read(chunk, 0, minOf(chunk.size.toLong(), to - position).toInt())
                if (read <= 0) break
                bytes(chunk.copyOf(read))
                position += read
            }
        }

        /** Writes the cross reference table and the trailer. */
        fun finish(rootRef: String?, infoRef: String?, idEntry: String?) {
            val xrefPosition = position
            text("xref\n")

            // The cross reference table is written in subsections so that every
            // entry keeps its natural generation.
            val numbers = offsets.keys.sorted()
            var index = 0
            while (index < numbers.size) {
                var end = index
                while (end + 1 < numbers.size && numbers[end + 1] == numbers[end] + 1) end++
                text("${numbers[index]} ${end - index + 1}\n")
                for (i in index..end) {
                    val number = numbers[i]
                    text(String.format(Locale.US, "%010d %05d n \n", offsets[number], 0))
                }
                index = end + 1
            }

            val trailer = buildString {
                append("trailer\n<< /Size ").append(numbers.lastOrNull()?.plus(1) ?: 1)
                rootRef?.let { append(" /Root ").append(it) }
                infoRef?.let { append(" /Info ").append(it) }
                idEntry?.let { append(" /ID ").append(it) }
                append(" >>\n")
                append("startxref\n").append(xrefPosition).append("\n%%EOF\n")
            }
            text(trailer)
        }

        /** Closes the file. */
        fun close() {
            stream.flush()
            stream.close()
        }
    }

    // ------------------------------------------------------------------ codec helpers

    /**
     * Unpacks a stream.
     *
     * @param expectedSize the size of the result, when it is known. The buffer
     *   is then filled once instead of growing with every piece - a picture of
     *   a document is megabytes big and the copy would fill the heap.
     */
    private fun inflate(data: ByteArray, expectedSize: Long = 0L): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        if (expectedSize in 1..MAX_INFLATED) {
            val size = expectedSize.toInt()
            val output = ByteArray(size)
            var written = 0
            while (!inflater.finished() && written < size) {
                val count = inflater.inflate(output, written, size - written)
                if (count <= 0) break
                written += count
            }
            inflater.end()
            return@runCatching if (written == size) output else output.copyOf(written)
        }
        val output = ByteArrayOutputStream(minOf(data.size * 2, MAX_INFLATED))
        val chunk = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(chunk)
            if (count <= 0) break
            output.write(chunk, 0, count)
            // A stream that does not fit into the memory is left alone.
            if (output.size() > MAX_INFLATED) {
                inflater.end()
                return null
            }
        }
        inflater.end()
        output.toByteArray()
    }.getOrNull()

    /**
     * The number of pixels of a JPEG, read from its header.
     *
     * The header is enough to know how big the picture is, so a picture that
     * would not fit into the memory is skipped before it is decoded.
     */
    private fun jpegPixels(data: ByteArray): Long? {
        if (data.size < 4) return null
        if ((data[0].toInt() and 0xFF) != 0xFF || (data[1].toInt() and 0xFF) != 0xD8) return null
        var index = 2
        while (index + 9 < data.size) {
            if ((data[index].toInt() and 0xFF) != 0xFF) {
                index++
                continue
            }
            val marker = data[index + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                index += 2
                continue
            }
            val length = ((data[index + 2].toInt() and 0xFF) shl 8) or (data[index + 3].toInt() and 0xFF)
            // The start of frame markers carry the size of the picture.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val height = ((data[index + 5].toInt() and 0xFF) shl 8) or (data[index + 6].toInt() and 0xFF)
                val width = ((data[index + 7].toInt() and 0xFF) shl 8) or (data[index + 8].toInt() and 0xFF)
                return width.toLong() * height.toLong()
            }
            index += 2 + length
        }
        return null
    }

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
