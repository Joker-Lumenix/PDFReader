package com.peng.pdfreader.feature.tools.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivityImageToPdfBinding
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.tools.adapter.EdgeGapDecoration
import com.peng.pdfreader.feature.tools.adapter.ImageRowAdapter
import com.peng.pdfreader.feature.tools.adapter.ImageTileAdapter
import com.peng.pdfreader.feature.tools.adapter.PitchGridDecoration
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolFormatters
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.feature.tools.util.ToolShapes
import com.peng.pdfreader.feature.tools.view.ImageGlyphDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Image to PDF" flow (designs "Image to PDF", "Image to PDF-loading",
 * "Image to PDF空状态", "Image to PDF下拉").
 *
 * The images are picked with the system photo picker, the grid shows them with
 * their index and the bottom sheet turns them into a document.
 */
class ImageToPdfActivity : BaseActivity() {

    private lateinit var binding: ToolsActivityImageToPdfBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private val images = mutableListOf<File>()
    private val thumbnails = mutableListOf<Bitmap>()

    /** The design shows the pictures as a grid, the list is the second view. */
    private var listMode = false

    private val adapter by lazy {
        ImageTileAdapter(
            onAddClick = { pickImages() },
            onImageClick = { index -> removeImage(index) },
        )
    }

    private val rowAdapter by lazy {
        ImageRowAdapter { index ->
            // the first row is the camera, it opens the picker
            if (index == 0) pickImages() else removeImage(index - 1)
        }
    }

    private val gridDecoration by lazy {
        // the tiles of the design sit at x = 12 / 128 / 244, 12 apart
        PitchGridDecoration(
            cellWidthPx = dp(104),
            pitchPx = dp(116),
            startMarginPx = dp(12),
            rowSpacingPx = dp(12),
        )
    }

    private val listDecoration by lazy { EdgeGapDecoration(dp(12), dp(12)) }

    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivityImageToPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleBar.showBack = true
        binding.titleBar.title = getString(R.string.image_to_pdf)
        binding.titleBar.onBackClick = { onBackPressedDispatcher.onBackPressed() }
        // the design puts the picture action next to the title, it switches
        // between the grid and the list of the picked pictures
        binding.ivHeaderIcon.setImageDrawable(
            ImageGlyphDrawable(resources.displayMetrics.density),
        )
        binding.ivHeaderIcon.setOnClickListener { toggleViewMode() }

        // the running card of the design is a white surface with a radius of 20
        binding.loadingCard.background =
            ToolShapes.roundRect(this, ToolShapes.SURFACE_SHEET, 20f)

        applyViewMode()

        binding.btnImport.setOnClickListener { importToPdf() }
        binding.btnGoToSet.setOnClickListener { openAppSettings() }

        // The import button of the design is a pill, the shared gradient is a
        // rounded rectangle.
        binding.btnImport.background =
            ToolShapes.gradientPill(this, R.drawable.gradient_red, 20f)

        refresh()
    }

    // ------------------------------------------------------------------ picking

    /**
     * Switches between the grid of the design "Image to PDF" and the list of
     * the design "Image to PDF下拉".
     */
    private fun toggleViewMode() {
        listMode = !listMode
        applyViewMode()
    }

    private fun applyViewMode() {
        binding.rvImages.removeItemDecoration(gridDecoration)
        binding.rvImages.removeItemDecoration(listDecoration)
        if (listMode) {
            binding.rvImages.layoutManager = LinearLayoutManager(this)
            binding.rvImages.adapter = rowAdapter
            binding.rvImages.addItemDecoration(listDecoration)
        } else {
            binding.rvImages.layoutManager = GridLayoutManager(this, 3)
            binding.rvImages.adapter = adapter
            binding.rvImages.addItemDecoration(gridDecoration)
        }
        refresh()
    }

    private fun pickImages() {
        picker.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build(),
        )
    }

    private fun importImages(uris: List<Uri>) {
        lifecycleScope.launch {
            uris.forEach { uri ->
                val loaded = withContext(Dispatchers.IO) { copyImage(uri) }
                if (loaded != null) {
                    images += loaded.first
                    thumbnails += loaded.second
                }
            }
            refresh()
        }
    }

    /**
     * Copies a picked image into the scratch directory and decodes a thumbnail.
     */
    private fun copyImage(uri: Uri): Pair<File, Bitmap>? = runCatching {
        val target = ToolOutput.file(this, "image_${System.currentTimeMillis()}_${images.size}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val bitmap = decodeThumbnail(target) ?: return null
        target to bitmap
    }.getOrNull()

    private fun decodeThumbnail(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > THUMBNAIL_SIZE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun removeImage(index: Int) {
        if (index !in images.indices) return
        images.removeAt(index).delete()
        thumbnails.removeAt(index).recycle()
        refresh()
    }

    // ------------------------------------------------------------------ pdf

    private fun importToPdf() {
        if (images.isEmpty()) {
            Toast.makeText(this, R.string.tools_image_failed, Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true)
        val out = ToolOutput.file(this, "images.pdf")
        lifecycleScope.launch {
            val outcome = engine.imagesToPdf(images.toList(), out)
            showLoading(false)
            outcome.fold(
                onSuccess = {
                    val document = repository.addExisting(
                        ToolOutput.uniqueName("images", ".pdf"),
                        DocType.PDF,
                        out,
                    )
                    Toast.makeText(
                        this@ImageToPdfActivity,
                        getString(R.string.tools_image_added, images.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                    PreviewActivity.start(this@ImageToPdfActivity, document.id)
                    finish()
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@ImageToPdfActivity,
                        getString(R.string.tools_failed, error.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    /** The running state of the design "Image to PDF-loading". */
    private fun showLoading(visible: Boolean) {
        binding.loadingState.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnImport.isEnabled = !visible && images.isNotEmpty()
    }

    // ------------------------------------------------------------------ state

    private fun refresh() {
        adapter.submit(thumbnails.toList())
        rowAdapter.submit(buildRows())
        binding.btnImport.text = getString(R.string.import_count, images.size)
        binding.btnImport.isEnabled = images.isNotEmpty()
        binding.emptyState.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
        // the empty state of the design carries the import button only
        binding.stripScroll.visibility = if (images.isEmpty()) View.GONE else View.VISIBLE
        rebuildStrip()
    }

    /** The rows of the list view, the camera comes first. */
    private fun buildRows(): List<ImageRowAdapter.Row> {
        val rows = mutableListOf(
            ImageRowAdapter.Row(
                name = getString(R.string.tools_image_add),
                meta = "",
                thumbnail = null,
                isCamera = true,
            ),
        )
        images.forEachIndexed { index, file ->
            rows += ImageRowAdapter.Row(
                name = file.name,
                meta = ToolFormatters.size(file.length()),
                thumbnail = thumbnails.getOrNull(index),
            )
        }
        return rows
    }

    /**
     * The strip of the design shows the picked images as small thumbnails,
     * every one of them carries the mark of the design in its top right corner.
     */
    private fun rebuildStrip() {
        binding.strip.removeAllViews()
        val size = dp(56)
        thumbnails.forEach { bitmap ->
            val holder = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    // the stub of the SDK has no setMarginTop, the fields are public
                    topMargin = dp(4)
                    marginEnd = dp(10)
                }
                // the mark of the design hangs over the top right corner
                clipChildren = false
            }
            val image = ImageView(this)
            image.layoutParams = FrameLayout.LayoutParams(size, size)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setImageBitmap(bitmap)
            // the thumbnails of the design have rounded corners
            image.background = ToolShapes.roundRect(this, ToolShapes.BACKDROP, 4f)
            image.clipToOutline = true
            holder.addView(image)

            val mark = ImageView(this)
            mark.layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = -dp(4)
                marginEnd = -dp(4)
            }
            mark.setImageResource(R.mipmap.ic_ok)
            mark.contentDescription = getString(R.string.tools_image_strip)
            holder.addView(mark)

            binding.strip.addView(holder)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        runCatching { startActivity(intent) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_IMAGES = 30
        private const val THUMBNAIL_SIZE = 104

        /** Opens the "image to PDF" flow. */
        fun start(context: Context) {
            context.startActivity(Intent(context, ImageToPdfActivity::class.java))
        }
    }
}
