package com.peng.pdfreader.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.peng.pdfreader.BuildConfig
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.databinding.SettingsFragmentBinding
import com.peng.pdfreader.databinding.SettingsRowBinding
import com.peng.pdfreader.ui.activities.FeedbackActivity
import com.peng.pdfreader.ui.activities.LanguageActivity
import com.peng.pdfreader.ui.uiData.language.LanguageCatalog
import com.peng.pdfreader.ui.activities.NoticeActivity
import com.peng.pdfreader.ui.activities.RatingActivity
import com.peng.pdfreader.utils.SettingsScreen

/**
 * Settings tab: sharing, language, feedback, privacy policy and terms of use.
 *
 * The app bar carries the red thumbs up of the design, it opens the rating
 * sheet.
 */
class SettingsFragment : BaseFragment() {

    private var _binding: SettingsFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.titleBar.title = getString(R.string.nav_settings)
        binding.titleBar.addAction(buildRateAction())

        bindRows()

        binding.tvVersion.text = getString(R.string.settings_version_format, BuildConfig.VERSION_NAME)
    }

    override fun onResume() {
        super.onResume()
        updateLanguageSubtitle()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindRows() {
        setupRow(
            row = binding.rowShare,
            iconRes = R.mipmap.ic_share_app,
            title = getString(R.string.settings_share_app),
        ) { shareApp() }

        setupRow(
            row = binding.rowLanguage,
            iconRes = R.mipmap.ic_language,
            title = getString(R.string.language),
        ) { openLanguage() }

        setupRow(
            row = binding.rowFeedback,
            iconRes = R.mipmap.ic_feedback,
            title = getString(R.string.feedback),
        ) { openFeedback() }

        setupRow(
            row = binding.rowPrivacy,
            iconRes = R.mipmap.ic_privacy,
            title = getString(R.string.settings_privacy_policy),
        ) { openNotice(NoticeActivity.TYPE_PRIVACY) }

        setupRow(
            row = binding.rowTerms,
            iconRes = R.mipmap.ic_term_of_use,
            title = getString(R.string.settings_terms_of_use),
        ) { openNotice(NoticeActivity.TYPE_TERMS) }
    }

    private fun setupRow(
        row: SettingsRowBinding,
        iconRes: Int,
        title: String,
        onClick: () -> Unit,
    ) {
        row.ivRowIcon.setImageResource(iconRes)
        row.tvRowTitle.text = title
        row.root.setOnClickListener { onClick() }
    }

    /** The language row carries the current selection as its subtitle. */
    private fun updateLanguageSubtitle() {
        val applied = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
        val option = LanguageCatalog.find(applied)
        val subtitle = binding.rowLanguage.tvRowSubtitle
        subtitle.visibility = View.VISIBLE
        subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_error_pink))
        subtitle.text = if (option == null) {
            getString(R.string.settings_follow_system)
        } else {
            getString(option.labelRes)
        }
    }

    /** The red thumbs up of the design opens the rating sheet. */
    private fun buildRateAction(): View {
        val action = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24))
            setImageResource(R.mipmap.ic_zan)
            contentDescription = getString(R.string.rate)
            setOnClickListener { openRating() }
        }
        return action
    }

    private fun shareApp() {
        val shareText = getString(R.string.settings_share_message, getString(R.string.app_name))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(send, getString(R.string.system_share))
        SettingsScreen.start(requireContext(), chooser)
    }

    private fun openLanguage() {
        SettingsScreen.start(requireContext(), Intent(requireContext(), LanguageActivity::class.java))
    }

    private fun openFeedback() {
        SettingsScreen.start(requireContext(), Intent(requireContext(), FeedbackActivity::class.java))
    }

    private fun openRating() {
        SettingsScreen.start(requireContext(), Intent(requireContext(), RatingActivity::class.java))
    }

    private fun openNotice(type: String) {
        SettingsScreen.start(
            requireContext(),
            Intent(requireContext(), NoticeActivity::class.java).putExtra(NoticeActivity.EXTRA_TYPE, type),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
