package com.peng.pdfreader.feature.settings.notice

import android.os.Bundle
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.SettingsNoticeActivityBinding

/**
 * In app notice screen, used for the privacy policy and the terms of use.
 *
 * The copy is a short placeholder - the final legal text has to be supplied
 * by the app owner (see the report).
 */
class NoticeActivity : BaseActivity() {

    private lateinit var binding: SettingsNoticeActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsNoticeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_PRIVACY
        val titleRes: Int
        val bodyRes: Int
        if (type == TYPE_TERMS) {
            titleRes = R.string.settings_terms_of_use
            bodyRes = R.string.notice_terms_body
        } else {
            titleRes = R.string.settings_privacy_policy
            bodyRes = R.string.notice_privacy_body
        }

        binding.titleBar.title = getString(titleRes)
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = { finish() }

        binding.tvNoticeTitle.text = getString(titleRes)
        binding.tvNoticeBody.text = getString(bodyRes)
    }

    companion object {
        const val EXTRA_TYPE = "notice_type"
        const val TYPE_PRIVACY = "privacy"
        const val TYPE_TERMS = "terms"
    }
}
