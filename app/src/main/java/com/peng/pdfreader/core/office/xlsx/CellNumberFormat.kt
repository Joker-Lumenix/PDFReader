package com.peng.pdfreader.core.office.xlsx

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

/**
 * The number formats of a spreadsheet.
 *
 * A spreadsheet shows the value of a cell the way the format of the cell
 * tells it: a date as a date, a percentage as a percentage, a number with the
 * thousands separator. The reader shows the value as it is written in the
 * file, so the format is applied here.
 */
internal object CellNumberFormat {

    /** The days between the day the spreadsheet counts from and the epoch. */
    private const val EPOCH_DAYS = 25569L

    /**
     * The text of a cell, with the format of the file.
     *
     * Null is returned when the value is not a number or when the format does
     * not change the way the value is written.
     */
    fun format(raw: String, code: String?): String? {
        if (code.isNullOrBlank()) return null
        val trimmed = code.trim()
        if (trimmed.isEmpty() || trimmed.equals("General", true) || trimmed == "@") return null
        val number = raw.trim().toDoubleOrNull() ?: return null

        val sections = trimmed.split(';')
        val section = if (number < 0 && sections.size > 1) sections[1] else sections[0]
        val pattern = section.replace(Regex("\\[[^]]*]"), "")
        if (pattern.isBlank()) return null

        val hasDate = pattern.contains('y') || pattern.contains('d')
        val hasTime = pattern.contains('h') || pattern.contains('s')
        val isDate = hasDate || (pattern.contains('m') && !hasTime)
        if (isDate || hasTime) return dateOf(number, pattern)

        val percent = pattern.contains('%')
        val decimals = decimalsOf(pattern)
        val grouping = groupingOf(pattern)
        val prefix = literalBefore(pattern)
        val suffix = literalAfter(pattern)
        val value = if (percent) number * 100 else number
        val patternText = if (grouping) "%,." + decimals + "f" else "%." + decimals + "f"
        val body = String.format(Locale.US, patternText, value)
        return prefix + body + suffix + if (percent) "%" else ""
    }

    /**
     * The date of a number.
     *
     * The spreadsheet counts the days from the 30th of December 1899, the
     * part behind the point is the time of the day.
     */
    private fun dateOf(number: Double, pattern: String): String? {
        val days = floor(number).toLong()
        val millisOfDay = ((number - days) * 86400_000L).toLong()
        val date = Date((days + EPOCH_DAYS) * 86400_000L + millisOfDay)
        val format = dateFormatOf(pattern) ?: return null
        return runCatching { SimpleDateFormat(format, Locale.getDefault()).format(date) }.getOrNull()
    }

    /** The format of a date, as Java knows it. */
    private fun dateFormatOf(pattern: String): String? {
        val builder = StringBuilder()
        var index = 0
        var quoted = false
        while (index < pattern.length) {
            val character = pattern[index]
            if (character == '"') {
                quoted = !quoted
                index++
                continue
            }
            if (quoted) {
                builder.append(character)
                index++
                continue
            }
            when (character) {
                'y' -> {
                    val long = pattern.startsWith("yyyy", index)
                    builder.append(if (long) "yyyy" else "yy")
                    index += if (long) 4 else 2
                }

                'd' -> {
                    builder.append("dd")
                    index += 2
                }

                'h' -> {
                    builder.append("HH")
                    index++
                }

                's' -> {
                    builder.append("ss")
                    index++
                }

                'm' -> {
                    // the minutes of a time, the months of a date
                    val isMinute = index > 0 && (pattern[index - 1] == 'h' || pattern[index - 1] == 'H')
                    builder.append(if (isMinute) "mm" else "MM")
                    index++
                }

                'a' -> {
                    if (pattern.startsWith("am/pm", index, true)) {
                        builder.append("a")
                        index += 5
                    } else {
                        builder.append(character)
                        index++
                    }
                }

                else -> {
                    builder.append(character)
                    index++
                }
            }
        }
        return builder.toString().ifBlank { null }
    }

    /** The number of the decimals of a format code. */
    private fun decimalsOf(pattern: String): Int {
        val point = pattern.indexOf('.')
        if (point < 0) return 0
        var count = 0
        for (index in point + 1 until pattern.length) {
            val character = pattern[index]
            if (character == '0' || character == '#') count++ else break
        }
        return count
    }

    /** True when the format groups the thousands. */
    private fun groupingOf(pattern: String): Boolean {
        val point = pattern.indexOf('.')
        val head = if (point < 0) pattern else pattern.substring(0, point)
        return head.contains(',')
    }

    /** The text in front of the number of a format code. */
    private fun literalBefore(pattern: String): String {
        val builder = StringBuilder()
        var quoted = false
        for (character in pattern) {
            if (character == '"') {
                quoted = !quoted
                continue
            }
            if (!quoted && (character.isDigit() || character == '.' || character == ',' ||
                character == '%' || character == '#' || character == '0' || character == 'E' ||
                character == 'e' || character == '+' || character == '-')
            ) {
                break
            }
            builder.append(character)
        }
        return builder.toString().trim()
    }

    /** The text behind the number of a format code. */
    private fun literalAfter(pattern: String): String {
        val last = pattern.indexOfLast { it.isDigit() || it == '.' || it == '%' || it == '#' || it == '0' }
        if (last < 0) return ""
        val builder = StringBuilder()
        var quoted = false
        for (index in last + 1 until pattern.length) {
            val character = pattern[index]
            if (character == '"') {
                quoted = !quoted
                continue
            }
            if (!quoted && (character.isDigit() || character == '#' || character == '0')) continue
            builder.append(character)
        }
        return builder.toString().trim()
    }
}
