package com.peng.pdfreader.appcore.pdf

import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Best-effort, dependency-free PDF metadata reader.
 *
 * Extracts the document outline (bookmarks) and the raw text of each page by scanning
 * the raw PDF objects. Real world PDFs vary wildly, so every step is defensive: when
 * something cannot be parsed the caller simply gets an empty list.
 */
object PdfMeta {

    private class PdfObjects {
        val objects = HashMap<Int, String>()
        val pageOrder = ArrayList<Int>()

        fun body(id: Int): String? = objects[id]
    }

    fun outline(file: File): List<OutlineNode> {
        val parsed = parse(file) ?: return emptyList()
        val catalogId = parsed.objects.entries.firstOrNull {
            it.value.contains("/Catalog")
        }?.key ?: return emptyList()
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

    fun textByPage(file: File): List<String> {        val parsed = parse(file) ?: return emptyList()
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
        val bytes = file.readBytes()
        val raw = String(bytes, Charsets.ISO_8859_1)
        val objects = HashMap<Int, String>()
        val pattern = Regex("(\\d+)\\s+(\\d+)\\s+obj")
        var match: MatchResult? = pattern.find(raw)
        while (match != null) {
            val id = match.groupValues[1].toInt()
            val start = match.range.last + 1
            var end = raw.indexOf("endobj", start)
            if (end < 0) end = raw.length
            val streamEnd = raw.indexOf("endstream", start)
            if (streamEnd in 0 until end) {
                end = streamEnd
            }
            objects[id] = raw.substring(start, end)
            match = pattern.find(raw, match.range.last + 1)
        }
        val parsed = PdfObjects()
        parsed.objects.putAll(objects)

        val catalogId = objects.entries.firstOrNull { it.value.contains("/Catalog") }?.key
        val pagesRef = catalogId?.let { reference(objects[it] ?: "", "Pages") }
        if (pagesRef != null) {
            collectPages(objects, pagesRef, parsed.pageOrder, HashSet())
        }
        if (parsed.pageOrder.isEmpty()) {
            objects.entries
                .filter { it.value.contains("/Type") && it.value.contains("/Page") && !it.value.contains("/Pages") }
                .map { it.key }
                .sorted()
                .forEach { parsed.pageOrder.add(it) }
        }
        parsed
    }.getOrNull()

    private fun collectPages(
        objects: Map<Int, String>,
        pagesId: Int,
        out: MutableList<Int>,
        visited: MutableSet<Int>,
    ) {
        if (!visited.add(pagesId)) return
        val body = objects[pagesId] ?: return
        val kidsArray = kidsOf(body) ?: return
        kidsArray.forEach { kidId ->
            val kidBody = objects[kidId] ?: return@forEach
            if (kidBody.contains("/Pages")) {
                collectPages(objects, kidId, out, visited)
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
