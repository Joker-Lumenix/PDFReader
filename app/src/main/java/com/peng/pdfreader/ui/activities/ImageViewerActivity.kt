package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.ActivityImageViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The image viewer of the "image to PDF" flow.
 *
 * The badge on the bottom right of a tile of the grid opens the picture in its
 * full size. The page only shows the picture: turning and deleting a picture
 * belongs to the processing page.
 */
class ImageViewerActivity : BaseActivity() {

    private lateinit var binding: ActivityImageViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            finish()
            return
        }

        binding.ivImage.doOnLayout { view ->
            val width = max(view.width, dp(360))
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { decode(uri, width) }
                if (bitmap == null) {
                    finish()
                    return@launch
                }
                binding.ivImage.setImageBitmap(bitmap)
                applyHeight(bitmap)
            }
        }
    }

    /**
     * The picture fills the width of the page, its height comes from the
     * picture itself. A picture that is taller than the page is scaled down.
     */
    private fun applyHeight(bitmap: Bitmap) {
        val view = binding.ivImage
        val width = view.width - dp(24)
        val height = (width.toFloat() * bitmap.height / bitmap.width).toInt()
        val maxHeight = view.height - dp(24)
        val params = view.layoutParams
        params.height = maxHeight.coerceAtMost(height).coerceAtLeast(dp(120))
        view.layoutParams = params
    }

    /** Decodes the picture with the width of the page in mind. */
    private fun decode(uri: Uri, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > targetWidth * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"

        /**
         * Opens the viewer for the picture behind the given address.
         */
        fun start(context: Context, uri: Uri, title: String) {
            context.startActivity(
                Intent(context, ImageViewerActivity::class.java)
                    .putExtra(EXTRA_URI, uri.toString())
                    .putExtra(EXTRA_TITLE, title),
            )
        }
    }
}
