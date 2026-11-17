package com.peng.pdfreader.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.data.MediaImages
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.databinding.ToolsActivityImageToPdfBinding
import com.peng.pdfreader.ui.adapter.decoration.PitchGridDecoration
import com.peng.pdfreader.ui.adapter.listAdapter.ImageTileAdapter
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolFlow
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
 * With the access to the files the page shows the pictures of the device: the
 * arrow next to the title opens the albums of the device (the groups of the
 * design), a chosen album narrows the pictures down. The pictures of the
 * picker are added to the selection the same way.
 */
class ImageToPdfActivity : BaseActivity() {

    private lateinit var binding: ToolsActivityImageToPdfBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    /** 设备里的图片，页面显示的就是它们。 */
    private val deviceImages = mutableListOf<MediaImages.Image>()

    /** 设备里图片的缩略图。 */
    private val thumbnails = mutableMapOf<Long, Bitmap>()

    /** 选中的图片，已经复制到沙盒里。 */
    private val selected = mutableListOf<Selected>()

    /** 当前显示的相册，null 表示所有相册。 */
    private var albumId: String? = null

    /** 选中的图片。 */
    private class Selected(
        /** 来源图片的 id，照片选择器选的是 0。 */
        val sourceId: Long,
        /** 沙盒里的文件。 */
        val file: File,
        val thumbnail: Bitmap?,
    )

    private val adapter by lazy {
        ImageTileAdapter(
            onAddClick = { pickImages() },
            onImageClick = { index -> toggleImage(index) },
        )
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

    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }

    /** 标题旁边的箭头打开相册分组页面。 */
    private val albums = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        // 选中的分组回到这一页，页面只显示这个分组的图片。
        albumId = data.getStringExtra(ImageAlbumActivity.EXTRA_ALBUM_ID)
        loadDeviceImages()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivityImageToPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.image_to_pdf)
        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.ivChangeCameraGroup.setOnClickListener {
            albums.launch(Intent(this, ImageAlbumActivity::class.java))
        }

        // the running card of the design is a white surface with a radius of 20
        binding.loadingCard.background =
            ToolShapes.roundRect(this, ToolShapes.SURFACE_SHEET, 20f)

        binding.rvImages.layoutManager = GridLayoutManager(this, 3)
        binding.rvImages.adapter = adapter
        binding.rvImages.addItemDecoration(gridDecoration)

        binding.btnImport.setOnClickListener { importToPdf() }
        binding.btnGoToSet.setOnClickListener { openAppSettings() }

        // The import button of the design is a pill, the shared gradient is a
        // rounded rectangle.
        binding.btnImport.background =
            ToolShapes.gradientPill(this, R.drawable.gradient_red, 20f)

        loadDeviceImages()
    }

    // ------------------------------------------------------------------ images

    /**
     * Reads the pictures of the device.
     *
     * Without the access to the files the system does not let the app read the
     * pictures, the page shows the hint of the permission then.
     */
    private fun loadDeviceImages() {
        if (!SharedStorage.canRead(this)) {
            deviceImages.clear()
            thumbnails.clear()
            refresh()
            return
        }
        lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) {
                MediaImages.images(this@ImageToPdfActivity, albumId).take(MAX_IMAGES)
            }
            deviceImages.clear()
            deviceImages.addAll(images)
            thumbnails.clear()
            withContext(Dispatchers.IO) {
                images.forEach { image ->
                    MediaImages.thumbnail(this@ImageToPdfActivity, image, THUMBNAIL_SIZE)
                        ?.let { thumbnails[image.id] = it }
                }
            }
            refresh()
        }
    }

    /** Selects a picture of the device or removes it from the selection. */
    private fun toggleImage(index: Int) {
        val image = deviceImages.getOrNull(index) ?: return
        val existing = selected.indexOfFirst { it.sourceId == image.id }
        if (existing >= 0) {
            selected.removeAt(existing).file.delete()
            refresh()
            return
        }
        lifecycleScope.launch {
            // The picture is copied into the scratch directory, the PDF is made
            // from the copies.
            val file = withContext(Dispatchers.IO) { copyToSandbox(image.uri) }
            if (file == null) {
                Toast.makeText(this@ImageToPdfActivity, R.string.tools_image_failed, Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            selected += Selected(image.id, file, thumbnails[image.id])
            refresh()
        }
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
                val file = withContext(Dispatchers.IO) { copyToSandbox(uri) }
                if (file != null) {
                    val bitmap = withContext(Dispatchers.IO) { decodeThumbnail(file) }
                    selected += Selected(0L, file, bitmap)
                }
            }
            refresh()
        }
    }

    /**
     * Copies a picture into the scratch directory.
     */
    private fun copyToSandbox(uri: Uri): File? = runCatching {
        val target = ToolOutput.file(this, "image_${System.currentTimeMillis()}_${selected.size}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    private fun decodeThumbnail(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > THUMBNAIL_SIZE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    // ------------------------------------------------------------------ pdf

    private fun importToPdf() {
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.tools_image_failed, Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true)
        val out = ToolOutput.file(this, "images.pdf")
        lifecycleScope.launch {
            // The job runs behind the shared progress page.
            ToolFlow.run(this@ImageToPdfActivity, getString(R.string.tools_image_running)) { _ ->
                val outcome = engine.imagesToPdf(selected.map { it.file }, out)
                outcome.getOrThrow()
                val document = repository.addExisting(
                    ToolOutput.uniqueName("images", ".pdf"),
                    DocType.PDF,
                    out,
                )
                ToolFlow.success(document.id, getString(R.string.tools_image_done))
            }
            showLoading(false)
            // The job is done, the progress page takes over with the result.
            finish()
        }
    }

    /** The running state of the design "Image to PDF-loading". */
    private fun showLoading(visible: Boolean) {
        binding.loadingState.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnImport.isEnabled = !visible && selected.isNotEmpty()
    }

    // ------------------------------------------------------------------ state

    private fun refresh() {
        adapter.submit(
            deviceImages.map { image ->
                ImageTileAdapter.Item(
                    thumbnail = thumbnails[image.id],
                    index = selected.indexOfFirst { it.sourceId == image.id } + 1,
                )
            },
        )
        binding.btnImport.text = getString(R.string.import_count, selected.size)
        binding.btnImport.isEnabled = selected.isNotEmpty()
        // The empty state of the design is the state without the permission.
        // When the app may read the files the grid with the camera tile is
        // shown instead of the hint of the permission.
        val permissionMissing = !SharedStorage.canRead(this)
        binding.emptyState.visibility = if (permissionMissing) View.VISIBLE else View.GONE
        // the empty state of the design carries the import button only
        binding.stripScroll.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        rebuildStrip()
    }

    override fun onResume() {
        super.onResume()
        // The permission may have been given in the system screen.
        if (SharedStorage.canRead(this)) loadDeviceImages() else refresh()
    }

    /**
     * The strip of the design shows the picked images as small thumbnails,
     * every one of them carries the mark of the design in its top right corner.
     */
    private fun rebuildStrip() {
        binding.strip.removeAllViews()
        val size = dp(56)
        selected.forEach { item ->
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
            image.setImageBitmap(item.thumbnail)
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

    /**
     * Opens the screen of the system for the permission of the files.
     *
     * From Android 11 on that is the "access all files" permission
     * (MANAGE_EXTERNAL_STORAGE), the user switches it there. The app comes
     * back on its own and the page leaves the hint of the permission.
     */
    private fun openAppSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", packageName, null),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
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
