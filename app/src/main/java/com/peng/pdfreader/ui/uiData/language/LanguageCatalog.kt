package com.peng.pdfreader.ui.uiData.language

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.peng.pdfreader.R
import java.util.Locale

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

    /** The code of the entry that keeps the language of the system. */
    const val FOLLOW_SYSTEM = "system"

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

    /**
     * The list of the language page.
     *
     * The app speaks the languages of [options]. When the system speaks another
     * language, the list carries the entry that follows the system, so the user
     * can go back to the language of the device.
     */
    fun optionsFor(locale: Locale): List<LanguageOption> {
        if (optionFor(locale) != null) return options
        return listOf(followSystemOption()) + options
    }

    /**
     * The option of the given locale, `null` when the app has no language for it.
     *
     * The region of the locale falls back to the language: `en-US` is served by
     * the English of the app - as long as the app has one variant of it only.
     */
    fun optionFor(locale: Locale): LanguageOption? {
        val tag = locale.toLanguageTag()
        options.firstOrNull { toLanguageTag(it.code).equals(tag, ignoreCase = true) }?.let { return it }
        val language = locale.language.lowercase()
        val same = options.filter {
            toLanguageTag(it.code).substringBefore('-').lowercase() == language
        }
        return same.singleOrNull()
    }

    /** The entry that follows the language of the system. */
    fun followSystemOption(): LanguageOption =
        LanguageOption(FOLLOW_SYSTEM, R.string.settings_follow_system)

    /**
     * The locale of the system.
     *
     * The language the user has set for the app does not change the language
     * of the device: the system locales carry it (Android 13 and later), on
     * the older systems the default list of the process is the one of the
     * device.
     */
    fun systemLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager?.systemLocales?.takeIf { !it.isEmpty }?.let { return it[0] }
        }
        return LocaleList.getDefault()[0] ?: Locale.getDefault()
    }

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
