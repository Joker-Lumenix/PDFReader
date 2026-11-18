package com.peng.pdfreader.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.databinding.LanguageActivityBinding
import com.peng.pdfreader.ui.adapter.listAdapter.LanguageAdapter
import com.peng.pdfreader.ui.uiData.language.LanguageCatalog
import com.peng.pdfreader.ui.uiData.language.LanguageOption
import java.util.Locale

/**
 * Language selection page.
 *
 * The choice is written to [AppPrefs.language] and applied right away through
 * [AppCompatDelegate.setApplicationLocales]; the activity is recreated by the
 * framework afterwards, so the whole app switches to the new language.
 *
 * The page shows the language of the system when the app speaks it - that one
 * is marked right away. When the system speaks another language, the list
 * carries the entry that follows the system.
 */
class LanguageActivity : BaseActivity() {

    private lateinit var binding: LanguageActivityBinding

    private lateinit var adapter: LanguageAdapter

    private var selected: LanguageOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LanguageActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.language)
        binding.ivBack.setOnClickListener { finish() }

        // The language of the system decides what the page shows: when the app
        // speaks it, it is marked right away, otherwise the list carries the
        // entry that follows the system.
        val systemLocale = LanguageCatalog.systemLocale(this)
        adapter = LanguageAdapter(LanguageCatalog.optionsFor(systemLocale)) { option ->
            onOptionClicked(option)
        }
        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter

        binding.btnNext.setOnClickListener { applySelection() }

        restoreSelection(systemLocale)
    }

    /**
     * Marks the language of the system, or the entry that follows it when the
     * app does not speak the language of the system.
     */
    private fun restoreSelection(systemLocale: Locale) {
        val option = LanguageCatalog.optionFor(systemLocale)
            ?: LanguageCatalog.followSystemOption()
        selected = option
        adapter.select(option)
        updateNextState()
    }

    private fun onOptionClicked(option: LanguageOption) {
        selected = option
        adapter.select(option)
        updateNextState()
    }

    private fun updateNextState() {
        val hasSelection = selected != null
        binding.btnNext.isEnabled = hasSelection
        binding.btnNext.setBackgroundResource(
            if (hasSelection) R.drawable.gradient_red else R.drawable.bg_btn_unable_r16,
        )
    }

    private fun applySelection() {
        val option = selected ?: return
        AppPrefs.get(this).language = option.code
        AppCompatDelegate.setApplicationLocales(
            if (option.code == LanguageCatalog.FOLLOW_SYSTEM) {
                // An empty list hands the language back to the system.
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(LanguageCatalog.toLanguageTag(option.code))
            },
        )
    }
}
