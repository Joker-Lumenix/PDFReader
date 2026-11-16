package com.peng.pdfreader.feature.language

import com.peng.pdfreader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The language list of the design: ten languages plus Simplified and
 * Traditional Chinese.
 */
class LanguageCatalogTest {

    @Test
    fun `the catalog holds the twelve languages of the design`() {
        assertEquals(12, LanguageCatalog.options.size)
        val codes = LanguageCatalog.options.map { it.code }
        listOf(
            "en", "fr", "ms", "es", "pt", "de", "ja", "ko", "ru", "ar", "zh-CN", "zh-rTW",
        ).forEach { assertTrue("missing $it", codes.contains(it)) }
    }

    @Test
    fun `a language is found by its code`() {
        assertNotNull(LanguageCatalog.find("en"))
        assertEquals(R.string.language_en, LanguageCatalog.find("en")?.labelRes)
        assertEquals(R.string.language_zh_cn, LanguageCatalog.find("zh-CN")?.labelRes)
        assertEquals(R.string.language_zh_tw, LanguageCatalog.find("zh-rTW")?.labelRes)
    }

    @Test
    fun `an unknown code falls back to nothing`() {
        assertNull(LanguageCatalog.find("xx"))
        assertNull(LanguageCatalog.find(null))
        assertNull(LanguageCatalog.find("  "))
    }

    @Test
    fun `the codes are turned into language tags`() {
        assertEquals("zh-TW", LanguageCatalog.toLanguageTag("zh-rTW"))
        assertEquals("en", LanguageCatalog.toLanguageTag("en"))
        assertEquals("pt-BR", LanguageCatalog.toLanguageTag("pt-BR"))
    }
}
