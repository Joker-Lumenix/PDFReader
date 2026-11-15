package com.peng.pdfreader.feature.tools.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivityImageToPdfBinding
import com.peng.pdfreader.databinding.ToolsDialogProgressBinding
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.tools.adapter.ImageTileAdapter
import com.peng.pdfreader.feature.tools.adapter.ToolGridDecoration
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.feature.tools.util.ToolShapes
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

    private val adapter by lazy {
        ImageTileAdapter(
            onAddClick = { pickImages() },
            onImageClick = { index -> removeImage(index) },
        )
    }

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
        binding.titleBar.title = getString(R.string.tools_image_to_pdf_title)
        binding.titleBar.onBackClick = { onBackPressedDispatcher.onBackPressed() }
        binding.titleBar.addAction(buildPickAction())

        binding.rvImages.layoutManager = GridLayoutManager(this, 3)
        binding.rvImages.adapter = adapter
        binding.rvImages.addItemDecoration(
            ToolGridDecoration(
                spanCount = 3,
                cellWidthPx = dp(104),
                rowSpacingPx = dp(12),
                itemCount = { adapter.itemCount },
            ),
        )

        binding.btnImport.setOnClickListener { importToPdf() }
        binding.btnGoToSet.setOnClickListener { openAppSettings() }

        // The import button of the design is a pill, the shared gradient is a
        // rounded rectangle.
        binding.btnImport.background =
            ToolShapes.gradientPill(this, R.drawable.gradient_red, 20f)

        refresh()
    }

    // ------------------------------------------------------------------ picking

    private fun buildPickAction(): ImageView = ImageView(this).apply {
        layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply {
            marginStart = dp(8)
        }
        setImageResource(R.mipmap.ic_file_choose)
        imageTintList = ColorStateList.valueOf(getColor(R.color.text_placeholder))
        setOnClickListener { pickImages() }
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
        val dialog = showProgress()
        val out = ToolOutput.file(this, "images.pdf")
        lifecycleScope.launch {
            val outcome = engine.imagesToPdf(images.toList(), out)
            dialog.dismiss()
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

    private fun showProgress(): AlertDialog {
        val view = ToolsDialogProgressBinding.inflate(LayoutInflater.from(this))
        view.tvMessage.setText(R.string.tools_image_running)
        return MaterialAlertDialogBuilder(this)
            .setView(view.root)
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------------------------ state

    private fun refresh() {
        adapter.submit(thumbnails.toList())
        binding.btnImport.text = getString(R.string.import_count, images.size)
        binding.btnImport.isEnabled = images.isNotEmpty()
        binding.emptyState.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
        rebuildStrip()
    }

    /**
     * The strip of the design shows the picked images as small thumbnails.
     */
    private fun rebuildStrip() {
        binding.strip.removeAllViews()
        val size = dp(56)
        thumbnails.forEach { bitmap ->
            val image = ImageView(this)
            image.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(10)
            }
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setImageBitmap(bitmap)
            binding.strip.addView(image)
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
