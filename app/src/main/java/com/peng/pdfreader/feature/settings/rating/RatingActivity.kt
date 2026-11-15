package com.peng.pdfreader.feature.settings.rating

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.peng.pdfreader.BuildConfig
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.SettingsRatingActivityBinding
import com.peng.pdfreader.feature.feedback.FeedbackActivity
import com.peng.pdfreader.feature.settings.util.SettingsScreen

/**
 * Rating page with the three emoji options.
 *
 * A happy or neutral answer sends the user to the store listing, a negative
 * answer opens the feedback page.
 */
class RatingActivity : BaseActivity() {

    private lateinit var binding: SettingsRatingActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsRatingActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleBar.title = getString(R.string.rate)
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = { finish() }

        binding.tvQuestion.text = getString(R.string.rating_question)

        binding.llGood.setOnClickListener { openStore() }
        binding.llNeutral.setOnClickListener { openStore() }
        binding.llBad.setOnClickListener { openFeedback() }

        binding.btnRate.setOnClickListener { openStore() }
        binding.btnLater.setOnClickListener { finish() }
    }

    private fun openStore() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}"),
        )
        try {
            startActivity(marketIntent)
            return
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "no store app installed, falling back to the web listing")
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"),
        )
        try {
            startActivity(webIntent)
        } catch (error: ActivityNotFoundException) {
            Logger.e(TAG, "no browser installed", error)
            Toast.makeText(this, R.string.rating_store_missing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFeedback() {
        SettingsScreen.start(this, Intent(this, FeedbackActivity::class.java))
    }

    companion object {
        private const val TAG = "RatingActivity"
    }
}
