package com.peng.pdfreader.ui.activities

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.FeedbackActivityBinding

/**
 * Feedback page: the user describes the problem and the text is handed over to
 * a mail client.
 */
class FeedbackActivity : BaseActivity() {

    private lateinit var binding: FeedbackActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The keyboard shrinks the card of the design, so the input and the
        // submit button move up with it.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = FeedbackActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.feedback)
        binding.ivBack.setOnClickListener { finish() }

        binding.tvHeading.text = getString(R.string.feedback_heading)
        applyInputSurface()

        binding.btnSubmit.setOnClickListener { submit() }
    }

    /** Input surface of the design: #f5f5f5 with a 6.4dp radius. */
    private fun applyInputSurface() {
        val density = resources.displayMetrics.density
        binding.etFeedback.background = GradientDrawable().apply {
            cornerRadius = 6.4f * density
            setColor(ContextCompat.getColor(this@FeedbackActivity, R.color.surface_disabled))
        }
    }

    private fun submit() {
        val message = binding.etFeedback.text?.toString()?.trim().orEmpty()
        if (message.isEmpty()) {
            binding.etFeedback.error = getString(R.string.feedback_input_hint)
            return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            data = Uri.parse("mailto:$FEEDBACK_MAIL")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_MAIL))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_mail_subject))
            putExtra(Intent.EXTRA_TEXT, message)
        }

        if (send.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(send, getString(R.string.feedback)))
            Toast.makeText(this, R.string.feedback_mail_opened, Toast.LENGTH_SHORT).show()
            return
        }

        // Some devices only expose the mail client through ACTION_SENDTO.
        val sendTo = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$FEEDBACK_MAIL")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_mail_subject))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        if (sendTo.resolveActivity(packageManager) != null) {
            startActivity(sendTo)
            Toast.makeText(this, R.string.feedback_mail_opened, Toast.LENGTH_SHORT).show()
            return
        }

        Logger.w(TAG, "no mail client installed")
        Toast.makeText(this, R.string.feedback_no_mail_app, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "FeedbackActivity"
        private const val FEEDBACK_MAIL = "m18380449235@gmail.com"
    }
}
