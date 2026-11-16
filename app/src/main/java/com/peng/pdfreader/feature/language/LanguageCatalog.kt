package com.peng.pdfreader.feature.language

import androidx.annotation.StringRes
import com.peng.pdfreader.R

/** One entry of the language list. */
data class LanguageOption(
    /** Locale tag as it is stored in the preferences, e.g. `en` or `zh-rTW`. */
    val code: String,
    @StringRes val labelRes: Int,
)

/**
 * The languages the app can be switched to.
 *
 * The list is the one of the design (ten languages) plus the two Chinese
 * variants the app ships, so the user can always switch back to Chinese.
 */
object LanguageCatalog {

    val options: List<LanguageOption> = listOf(
        LanguageOption("en", R.string.language_en),
        LanguageOption("fr", R.string.language_fr),
        LanguageOption("ms", R.string.language_ms),
        LanguageOption("es", R.string.language_es),
        LanguageOption("pt", R.string.language_pt),
        LanguageOption("de", R.string.language_de),
        LanguageOption("ja", R.string.language_ja),
        LanguageOption("ko", R.string.language_ko),
        LanguageOption("ru", R.string.language_ru),
        LanguageOption("ar", R.string.language_ar),
        LanguageOption("zh-CN", R.string.language_zh_cn),
        LanguageOption("zh-rTW", R.string.language_zh_tw),
    )

    /** The option of the given tag, `null` when the tag is unknown. */
    fun find(code: String?): LanguageOption? {
        if (code.isNullOrBlank()) return null
        val wanted = toLanguageTag(code)
        return options.firstOrNull { toLanguageTag(it.code).equals(wanted, ignoreCase = true) }
    }

    /**
     * Converts a stored tag into a BCP 47 tag.
     *
     * The preferences use the resource qualifier form (`zh-rTW`) while the
     * locale API only understands BCP 47 (`zh-TW`).
     */
    fun toLanguageTag(code: String): String {
        val parts = code.split('-')
        // `zh-rTW` is the resource qualifier form, BCP 47 wants `zh-TW`.
        if (parts.size >= 2 && parts[1].length > 1 && parts[1][0].equals('r', ignoreCase = true)) {
            return (listOf(parts[0], parts[1].drop(1)) + parts.drop(2)).joinToString("-")
        }
        return code
    }
}
