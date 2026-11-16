package com.peng.pdfreader.appcore.pdf

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Inflater

/**
 * Best-effort, dependency-free PDF metadata reader.
 *
 * Extracts the document outline (bookmarks) and the raw text of each page by scanning
 * the raw PDF objects. Real world PDFs vary wildly, so every step is defensive: when
 * something cannot be parsed the caller simply gets an empty list.
 *
 * The file is never loaded into the memory as a whole: the objects are indexed in
 * chunks and their bodies are read on demand, so a big document costs only the
 * bytes of the objects that are really needed.
 */
object PdfMeta {

    /**
     * The biggest file the text search reads.
     *
     * The search needs the text of every page, that does not fit into the memory
     * of a phone for a huge document.
     */
    private const val MAX_TEXT_BYTES = 24L * 1024 * 1024

    /** The most objects the fallback scan reads, it looks at every object. */
    private const val MAX_FALLBACK_OBJECTS = 5_000

    private class PdfObjects(private val index: PdfObjectIndex) {
        val pageOrder = ArrayList<Int>()

        fun body(id: Int): String? = index.body(id)

        fun ids(): List<Int> = index.ids()
    }

    fun outline(file: File): List<OutlineNode> {
        val parsed = parse(file) ?: return emptyList()
        val catalogId = parsed.ids().firstOrNull { id ->
            parsed.body(id)?.contains("/Catalog") == true
        } ?: return emptyList()
        val outlinesRef = reference(parsed.body(catalogId) ?: return emptyList(), "Outlines")
            ?: return emptyList()
        val outlines = parsed.body(outlinesRef) ?: return emptyList()
        val firstRef = reference(outlines, "First") ?: return emptyList()

        val result = mutableListOf<OutlineNode>()
        var current: Int? = firstRef
        var guard = 0
        while (current != null && guard++ < 500) {
            val id = current ?: break
            val body = parsed.body(id) ?: break
            val title = stringValue(body, "Title") ?: ""
            val page = destinationPage(parsed, body)
            val level = depthOf(parsed, id)
            result += OutlineNode(title = title, page = page, level = level)
            current = reference(body, "Next")
        }
        return result
    }

    fun textByPage(file: File): List<String> {
        // A huge document does not fit into the memory, the search stays empty.
        if (file.length() > MAX_TEXT_BYTES) return emptyList()
        val parsed = parse(file) ?: return emptyList()
        val result = ArrayList<String>()
        parsed.pageOrder.forEach { pageId ->
            val body = parsed.body(pageId) ?: return@forEach
            val contentRefs = contentReferences(body)
            val builder = StringBuilder()
            contentRefs.forEach { ref ->
                streamData(parsed.body(ref) ?: return@forEach)?.let { raw ->
                    builder.append(extractText(raw))
                }
            }
            result += builder.toString().replace(Regex("\\s+"), " ").trim()
        }
        return result
    }

    // ------------------------------------------------------------------ parsing

    private fun parse(file: File): PdfObjects? = runCatching {
        val index = PdfObjectIndex(file).apply { scan() }
        val parsed = PdfObjects(index)

        val catalogId = index.ids().firstOrNull { id ->
            index.body(id)?.contains("/Catalog") == true
        }
        val pagesRef = catalogId?.let { reference(index.body(it) ?: "", "Pages") }
        if (pagesRef != null) {
            collectPages(parsed, pagesRef, parsed.pageOrder, HashSet())
        }
        if (parsed.pageOrder.isEmpty() && index.ids().size <= MAX_FALLBACK_OBJECTS) {
            index.ids()
                .filter { id ->
                    val body = index.body(id) ?: return@filter false
                    body.contains("/Type") && body.contains("/Page") && !body.contains("/Pages")
                }
                .sorted()
                .forEach { parsed.pageOrder.add(it) }
        }
        parsed
    }.getOrNull()

    private fun collectPages(
        parsed: PdfObjects,
        pagesId: Int,
        out: MutableList<Int>,
        visited: MutableSet<Int>,
    ) {
        if (!visited.add(pagesId)) return
        val body = parsed.body(pagesId) ?: return
        val kidsArray = kidsOf(body) ?: return
        kidsArray.forEach { kidId ->
            val kidBody = parsed.body(kidId) ?: return@forEach
            if (kidBody.contains("/Pages")) {
                collectPages(parsed, kidId, out, visited)
            } else {
                out.add(kidId)
            }
        }
    }

    private fun kidsOf(body: String): List<Int>? {
    val index = body.indexOf("/Kids")
    if (index < 0) return null
    val open = body.indexOf('[', index)
    val close = body.indexOf(']', open)
    if (open < 0 || close < 0) return null
    return Regex("(\\d+)\\s+\\d+\\s+R").findAll(body.substring(open, close))
        .map { it.groupValues[1].toInt() }
        .toList()
}

private fun contentReferences(body: String): List<Int> {
    val index = body.indexOf("/Contents")
    if (index < 0) return emptyList()
    val open = body.indexOf('[', index)
    if (open in 0 until index + 40) {
        val close = body.indexOf(']', open)
        if (open >= 0 && close > open) {
            return Regex("(\\d+)\\s+\\d+\\s+R").findAll(body.substring(open, close))
                .map { it.groupValues[1].toInt() }
                .toList()
        }
    }
    return reference(body, "Contents")?.let { listOf(it) } ?: emptyList()
}

private fun streamData(body: String): String? {
    val marker = body.indexOf("stream")
    if (marker < 0) return null
    var start = marker + "stream".length
    if (body.getOrNull(start) == '\r') start++
    if (body.getOrNull(start) == '\n') start++
    // The parser cuts the object off in front of "endstream", so the
    // terminator is not part of the body any more - the rest of the body
    // is the payload.
    val end = body.indexOf("endstream", start).let { if (it < 0) body.length else it }
    val payload = body.substring(start, end)
    return if (body.contains("/FlateDecode")) inflate(payload) else payload
}

private fun inflate(payload: String): String? = runCatching {
    val data = payload.toByteArray(Charsets.ISO_8859_1)
    val inflater = Inflater()
    inflater.setInput(data)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count <= 0) break
        output.write(buffer, 0, count)
    }
    inflater.end()
    String(output.toByteArray(), Charsets.ISO_8859_1)
}.getOrNull()

private fun extractText(content: String): String {
    val builder = StringBuilder()
    val pattern = Regex("""\(((?:\\.|[^\\)])*)\)\s*Tj|\[(.*?)\]\s*TJ|<([0-9A-Fa-f\s]+)>\s*Tj""")
    pattern.findAll(content).forEach { match ->
        val literal = match.groupValues[1]
        if (literal.isNotEmpty()) {
            builder.append(unescape(literal)).append(' ')
        } else {
            val array = match.groupValues[2]
            if (array.isNotEmpty()) {
                Regex("""\(((?:\\.|[^\\)])*)\)""").findAll(array).forEach { item ->
                    builder.append(unescape(item.groupValues[1]))
                }
                builder.append(' ')
            } else {
                val hex = match.groupValues[3].replace(Regex("\\s"), "")
                if (hex.length >= 4) {
                    builder.append(decodeHexString(hex)).append(' ')
                }
            }
        }
    }
    return builder.toString()
}

private fun decodeHexString(hex: String): String {
    val bytes = ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    return String(bytes, Charsets.ISO_8859_1)
}

private fun unescape(value: String): String {
    val builder = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\' && index + 1 < value.length) {
            when (val next = value[index + 1]) {
                'n' -> { builder.append('\n'); index += 2 }
                'r' -> { builder.append('\r'); index += 2 }
                't' -> { builder.append('\t'); index += 2 }
                'b' -> { builder.append('\b'); index += 2 }
                'f' -> { builder.append('\u000C'); index += 2 }
                '(' -> { builder.append('('); index += 2 }
                ')' -> { builder.append(')'); index += 2 }
                '\\' -> { builder.append('\\'); index += 2 }
                '\n' -> { index += 2 }
                else -> {
                    if (next.isDigit() && next.code - '0'.code in 0..7) {
                        var octal = ""
                        var scan = index + 1
                        while (scan < value.length && octal.length < 3 && value[scan].isDigit()) {
                            octal += value[scan]
                            scan++
                        }
                        builder.append((octal.toIntOrNull(8) ?: 0).toChar())
                        index = scan
                    } else {
                        builder.append(next)
                        index += 2
                    }
                }
            }
        } else {
            builder.append(char)
            index++
        }
    }
    return builder.toString()
}

private fun reference(body: String, key: String): Int? {
    val index = body.indexOf("/$key")
    if (index < 0) return null
    val match = Regex("(\\d+)\\s+\\d+\\s+R").find(body.substring(index))
    return match?.groupValues?.get(1)?.toIntOrNull()
}

private fun stringValue(body: String, key: String): String? {
    val index = body.indexOf("/$key")
    if (index < 0) return null
    val tail = body.substring(index)
    val hex = Regex("<([0-9A-Fa-f\\s]+)>").find(tail)
    if (hex != null && hex.range.first < 12) {
        return decodeHexString(hex.groupValues[1].replace(Regex("\\s"), ""))
    }
    val literal = Regex("\\(((?:\\\\.|[^\\\\)])*)\\)").find(tail)
    if (literal != null && literal.range.first < 12) {
        return unescape(literal.groupValues[1])
    }
    return null
}

private fun destinationPage(parsed: PdfObjects, body: String): Int {
    val destArray = Regex("""\[(\s*\d+\s+\d+\s+R)""").find(body)
    val pageRef = destArray?.groupValues?.get(1)?.split("""\s+""")?.get(0)?.toIntOrNull()
        ?: Regex("""/D\s*\[\s*(\d+)\s+\d+\s+R""").find(body)?.groupValues?.get(1)?.toIntOrNull()
    if (pageRef == null) return 1
    val index = parsed.pageOrder.indexOf(pageRef)
    return if (index >= 0) index + 1 else 1
}

private fun depthOf(parsed: PdfObjects, itemId: Int): Int {
    var depth = 0
    var current = itemId
    var guard = 0
    while (guard++ < 20) {
        val body = parsed.body(current) ?: break
        val parentRef = reference(body, "Parent") ?: break
        val parentBody = parsed.body(parentRef) ?: break
        if (!parentBody.contains("/Outlines")) break
        depth++
        current = parentRef
    }
    return depth
}
}

    /**
     * The index of the objects of a PDF file.
     *
     * The file is read in chunks, so it never has to be held in the memory as a
     * whole. Only the offsets of the objects are kept, their bodies are read
     * when they are really needed.
     */
    private class PdfObjectIndex(private val file: File) {

        /** The object number -> the offset of its body. */
        private val offsets = LinkedHashMap<Int, Long>()

        /** The object number -> the length of its body. */
        private val lengths = LinkedHashMap<Int, Int>()

        /** The bodies that were read already. */
        private val cache = HashMap<Int, String>()

        fun ids(): List<Int> = offsets.keys.toList()

        /** Scans the file once and remembers where every object starts. */
        fun scan() {
            file.inputStream().use { input ->
                val buffer = ByteArray(CHUNK + OVERLAP)
                var carry = 0
                var chunkStart = 0L
                while (true) {
                    val read = input.read(buffer, carry, CHUNK)
                    if (read <= 0) break
                    val total = carry + read
                    var pos = 0
                    while (true) {
                        val hit = indexOf(buffer, total, pos, "obj")
                        if (hit < 0) break
                        val id = objectNumberBefore(buffer, hit)
                        if (id >= 0 && !offsets.containsKey(id)) {
                            offsets[id] = chunkStart + hit + 3
                        }
                        pos = hit + 3
                    }
                    carry = minOf(OVERLAP, total)
                    System.arraycopy(buffer, total - carry, buffer, 0, carry)
                    chunkStart += read
                }
            }
            // The length of every object is only known when it is read.
        }

        /** The body of an object, read from the file on demand. */
        fun body(id: Int): String? {
            cache[id]?.let { return it }
            val offset = offsets[id] ?: return null
            val text = readBody(offset) ?: return null
            if (cache.size < MAX_CACHED_BODIES) cache[id] = text
            return text
        }

        /** Reads the body of an object, it ends at "endobj" or "endstream". */
        private fun readBody(offset: Long): String? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                var read = raf.read(chunk)
                while (read > 0) {
                    out.write(chunk, 0, read)
                    val bytes = out.toByteArray()
                    val end = endOfObject(bytes)
                    if (end >= 0) return String(bytes, 0, end, Charsets.ISO_8859_1)
                    if (out.size() >= MAX_BODY_BYTES) break
                    read = raf.read(chunk)
                }
                String(out.toByteArray(), Charsets.ISO_8859_1)
            }
        }.getOrNull()

        /** The offset of "endobj" or "endstream" in the bytes, -1 when there is none. */
        private fun endOfObject(bytes: ByteArray): Int {
            val endObj = indexOf(bytes, bytes.size, 0, "endobj")
            val endStream = indexOf(bytes, bytes.size, 0, "endstream")
            return when {
                endObj < 0 -> endStream
                endStream < 0 -> endObj
                else -> minOf(endObj, endStream)
            }
        }

        /**
         * The object number in front of the "obj" keyword.
         *
         * The keyword sits behind "12 0 obj", so the digits are read backwards.
         */
        private fun objectNumberBefore(buffer: ByteArray, objAt: Int): Int {
            var index = objAt - 1
            while (index >= 0 && isWhitespace(buffer[index])) index--
            // the generation number
            while (index >= 0 && isDigit(buffer[index])) index--
            while (index >= 0 && isWhitespace(buffer[index])) index--
            val end = index
            while (index >= 0 && isDigit(buffer[index])) index--
            if (index >= end) return -1
            var number = 0
            for (position in index + 1..end) {
                number = number * 10 + (buffer[position] - '0'.code.toByte()).toInt()
                if (number > Int.MAX_VALUE / 10) return -1
            }
            return number
        }

        private fun isDigit(value: Byte): Boolean = value in '0'.code.toByte()..'9'.code.toByte()

        private fun isWhitespace(value: Byte): Boolean =
            value == ' '.code.toByte() || value == '\n'.code.toByte() ||
                value == '\r'.code.toByte() || value == '\t'.code.toByte()

        /** The first position of the keyword in the bytes, -1 when there is none. */
        private fun indexOf(buffer: ByteArray, length: Int, from: Int, keyword: String): Int {
            if (length <= from) return -1
            val first = keyword[0].code.toByte()
            var position = from
            while (position < length) {
                if (buffer[position] == first &&
                    position + keyword.length <= length &&
                    matches(buffer, position, keyword)
                ) {
                    return position
                }
                position++
            }
            return -1
        }

        private fun matches(buffer: ByteArray, position: Int, keyword: String): Boolean {
            for (offset in keyword.indices) {
                if (buffer[position + offset] != keyword[offset].code.toByte()) return false
            }
            return true
        }

        companion object {
            /** The size of one chunk of the scan. */
            private const val CHUNK = 1 shl 20

            /** The overlap of two chunks, an object header may cross the border. */
            private const val OVERLAP = 32

            /** The most bytes one object body may have. */
            private const val MAX_BODY_BYTES = 8 shl 20

            /** The most bodies that are kept in the memory. */
            private const val MAX_CACHED_BODIES = 64
        }
}
