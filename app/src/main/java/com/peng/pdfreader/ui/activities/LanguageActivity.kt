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

/**
 * Language selection page.
 *
 * The choice is written to [AppPrefs.language] and applied right away through
 * [AppCompatDelegate.setApplicationLocales]; the activity is recreated by the
 * framework afterwards, so the whole app switches to the new language.
 *
 * The design starts with nothing selected - the "Next" button stays disabled
 * until the user picks a language - and it marks the selected row with the red
 * gradient pill.
 */
class LanguageActivity : BaseActivity() {

    private lateinit var binding: LanguageActivityBinding

    private val adapter = LanguageAdapter { option -> onOptionClicked(option) }

    private var selected: LanguageOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LanguageActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.language_selection)
        binding.ivBack.setOnClickListener { finish() }

        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter

        binding.btnNext.setOnClickListener { applySelection() }

        restoreSelection()
    }

    /** Pre-selects the language the app currently runs in. */
    private fun restoreSelection() {
        val applied = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
        val option = LanguageCatalog.find(applied)
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
            LocaleListCompat.forLanguageTags(LanguageCatalog.toLanguageTag(option.code)),
        )
    }
}
