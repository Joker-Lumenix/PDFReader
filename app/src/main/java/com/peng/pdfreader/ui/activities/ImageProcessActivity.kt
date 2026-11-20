package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.widget.Toast
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.core.widget.AdSlotView
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ActivityImageProcessBinding
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolFlow
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.ui.uiData.AdSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * The image processing page of the "image to PDF" flow (designs "img-only1",
 * "img-page" and "img-page2").
 *
 * The page shows the picked pictures one after the other. A single picture can
 * only be turned, with more than one picture it can also be deleted - the
 * design keeps the place of the delete action empty then.
 *
 * The button in the top right corner of the design finishes the flow: the PDF
 * is built behind the shared progress page and the result page takes over.
 */
class ImageProcessActivity : BaseActivity() {

    private lateinit var binding: ActivityImageProcessBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    /** The pictures of the selection. */
    private val files = mutableListOf<File>()

    /** The rotation of every picture, in degrees. */
    private val rotations = mutableListOf<Int>()

    /** The picture that is on the screen. */
    private var index = 0

    /** The picture that is on the screen, already turned. */
    private var current: Bitmap? = null

    /** The pictures that were decoded already, so the pager stays quick. */
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paths = intent.getStringArrayListExtra(EXTRA_FILES).orEmpty()
        files.addAll(paths.map { File(it) })
        val angles = intent.getIntArrayExtra(EXTRA_ROTATIONS)
        rotations.addAll(List(files.size) { index -> angles?.getOrNull(index) ?: 0 })

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnDone.setOnClickListener { finishFlow() }
        binding.ivPrev.setOnClickListener { move(-1) }
        binding.ivNext.setOnClickListener { move(1) }
        binding.colRotateLeft.setOnClickListener { turn(-90) }
        binding.colRotateRight.setOnClickListener { turn(90) }
        binding.colDelete.setOnClickListener { delete() }

        binding.content.doOnLayout { applyImageHeight() }

        // the ad slot of the design, between the pager and the bottom bar
        binding.adBanner.style = AdSlotView.Style.BANNER
        binding.adBanner.content = AdSamples.banner(this)
        Ads.loadBanner(binding.adBanner)

        show()
    }

    // ------------------------------------------------------------------ pictures

    /** Shows the picture of the current place of the selection. */
    private fun show() {
        if (files.isEmpty()) {
            finish()
            return
        }
        index = index.coerceIn(0, files.lastIndex)
        binding.tvCounter.text = getString(R.string.tools_image_counter, index + 1, files.size)
        // The delete action of the design is only there with more than one
        // picture, the place of the action stays empty otherwise.
        binding.colDelete.visibility = if (files.size > 1) View.VISIBLE else View.GONE
        val hasPrevious = index > 0
        val hasNext = index < files.lastIndex
        binding.ivPrev.setImageResource(
            if (hasPrevious) R.mipmap.ic_arrow_left_enable else R.mipmap.ic_left_arrow_unable,
        )
        // The red arrow of the design points to the right, the round arrow of
        // the library points to the left - it is mirrored.
        binding.ivNext.setImageResource(
            if (hasNext) R.mipmap.ic_arrow_left_enable else R.mipmap.ic_arrow_right_unable,
        )
        binding.ivNext.scaleX = if (hasNext) -1f else 1f
        binding.ivPrev.isEnabled = hasPrevious
        binding.ivNext.isEnabled = hasNext
        load()
    }

    /** Decodes the picture of the current place and paints it. */
    private fun load() {
        val file = files.getOrNull(index) ?: return
        val key = cacheKey(file, rotations.getOrElse(index) { 0 })
        val cached = cache.get(key)
        if (cached != null) {
            paint(cached)
            return
        }
        val width = binding.ivImage.width.takeIf { it > 0 } ?: dp(360)
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decode(file, width) }
            if (bitmap == null) {
                toast(R.string.tools_image_failed)
                return@launch
            }
            cache.put(key, bitmap)
            paint(bitmap)
        }
    }

    /** Paints the picture and fits its height into the page. */
    private fun paint(bitmap: Bitmap) {
        current = bitmap
        binding.ivImage.setImageBitmap(bitmap)
        applyImageHeight()
    }

    /**
     * The picture of the design fills the width of the page, its height comes
     * from the picture itself. A picture that is taller than the page is
     * scaled down, so the pager below it stays visible.
     */
    private fun applyImageHeight() {
        val bitmap = current ?: return
        val content = binding.content
        if (content.width <= 0) return
        val width = content.width - dp(24)
        val height = (width.toFloat() * bitmap.height / bitmap.width).toInt()
        val maxHeight = content.height - dp(120)
        val params = binding.ivImage.layoutParams
        params.height = min(height, maxHeight.coerceAtLeast(dp(120)))
        binding.ivImage.layoutParams = params
    }

    /** Moves through the selection. */
    private fun move(delta: Int) {
        val next = index + delta
        if (next < 0 || next >= files.size) return
        index = next
        show()
    }

    /** Turns the picture of the current place. */
    private fun turn(degrees: Int) {
        val current = rotations.getOrNull(index) ?: return
        rotations[index] = ((current + degrees) % 360 + 360) % 360
        load()
    }

    /** Removes the picture of the current place from the selection. */
    private fun delete() {
        if (files.size <= 1) return
        files.removeAt(index)
        rotations.removeAt(index)
        if (index >= files.size) index = files.lastIndex
        show()
    }

    // ------------------------------------------------------------------ pdf

    /**
     * Builds the PDF of the selection.
     *
     * The job runs behind the shared progress page, the result page takes over
     * as soon as the document is ready.
     */
    private fun finishFlow() {
        if (files.isEmpty()) {
            toast(R.string.tools_image_failed)
            return
        }
        val out = ToolOutput.file(this, "images.pdf")
        val angles = rotations.toList()
        lifecycleScope.launch {
            ToolFlow.run(this@ImageProcessActivity, getString(R.string.tools_image_running)) { report ->
                val outcome = engine.imagesToPdf(
                    images = files.toList(),
                    out = out,
                    rotations = angles,
                ) { current, total ->
                    report(if (total <= 0) 0 else current * 100 / total)
                }
                outcome.getOrThrow()
                val document = repository.addExisting(
                    ToolOutput.uniqueName("images", ".pdf"),
                    DocType.PDF,
                    out,
                )
                ToolFlow.success(document.id, getString(R.string.tools_image_done))
            }
            // The job is done, the progress page takes over with the result.
            // The page tells the flow that it is finished, so the flow can
            // leave the selection behind.
            setResult(RESULT_OK)
            finish()
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Decodes a picture for the screen.
     *
     * The picture is decoded with the width of the page in mind, so the
     * picture on the screen is sharp even though the file behind it may be
     * much bigger.
     */
    private fun decode(file: File, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > targetWidth * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return turn(bitmap, rotations.getOrElse(index) { 0 })
    }

    /** Turns a picture by a multiple of 90 degrees. */
    private fun turn(bitmap: Bitmap, degrees: Int): Bitmap {
        val angle = ((degrees % 360) + 360) % 360
        if (angle == 0) return bitmap
        val matrix = Matrix().apply { postRotate(angle.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrElse { bitmap }
    }

    /** The key of a picture in the cache, the rotation is part of it. */
    private fun cacheKey(file: File, degrees: Int): String = "${file.absolutePath}@$degrees"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_FILES = "files"
        private const val EXTRA_ROTATIONS = "rotations"

        /** How much memory the cache of the decoded pictures may use. */
        private const val CACHE_BYTES = 24 * 1024 * 1024

        /**
         * The intent that opens the processing page for the given pictures.
         *
         * @param files the pictures of the selection
         * @param rotations the rotation of every picture in degrees
         */
        fun intent(context: Context, files: List<File>, rotations: List<Int> = emptyList()): Intent {
            return Intent(context, ImageProcessActivity::class.java).apply {
                putStringArrayListExtra(
                    EXTRA_FILES,
                    ArrayList(files.map { it.absolutePath }),
                )
                putExtra(EXTRA_ROTATIONS, rotations.toIntArray())
            }
        }
    }
}
