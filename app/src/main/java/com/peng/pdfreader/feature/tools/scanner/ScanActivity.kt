package com.peng.pdfreader.feature.tools.scanner

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a photo of the camera into a PDF.
 *
 * The camera is the system one: no document scanner is bundled, the photo is
 * simply placed into a new document.
 */
class ScanActivity : BaseActivity() {

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private var pendingUri: Uri? = null

    /**
     * Full resolution capture, the photo is written into the media store and
     * copied into the scratch directory afterwards.
     */
    private val capture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = pendingUri
        pendingUri = null
        if (result.resultCode != RESULT_OK || uri == null) {
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { importCapture(uri) }
            if (file == null) {
                Toast.makeText(this@ScanActivity, R.string.tools_scan_failed, Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                buildPdf(file)
            }
        }
    }

    /**
     * Fallback for the devices without a writable media entry, the camera
     * hands over a small preview of the photo.
     */
    private val capturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) {
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { importBitmap(bitmap) }
            if (file == null) {
                Toast.makeText(this@ScanActivity, R.string.tools_scan_failed, Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                buildPdf(file)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCamera()
    }

    private fun launchCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = createMediaEntry()
            if (uri != null) {
                pendingUri = uri
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                try {
                    capture.launch(intent)
                    return
                } catch (error: ActivityNotFoundException) {
                    pendingUri = null
                    discardMediaEntry(uri)
                }
            }
        }
        try {
            capturePreview.launch(null)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, R.string.tools_scan_no_camera, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createMediaEntry(): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "scan_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Let's PDF",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()

    private fun discardMediaEntry(uri: Uri) {
        runCatching { contentResolver.delete(uri, null, null) }
    }

    private suspend fun importCapture(uri: Uri): File? = withContext(Dispatchers.IO) {
        val target = ToolOutput.file(this@ScanActivity, "scan_${System.currentTimeMillis()}.jpg")
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrNull()
        // The photo was only a transport, it does not belong into the gallery:
        // the pending entry is dropped again right after the copy.
        discardMediaEntry(uri)
        if (copied != null && target.length() > 0) target else null
    }

    private suspend fun importBitmap(bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        val target = ToolOutput.file(this@ScanActivity, "scan_${System.currentTimeMillis()}.jpg")
        val written = runCatching {
            target.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output) }
        }.isSuccess
        if (written) target else null
    }

    private fun buildPdf(image: File) {
        val out = ToolOutput.file(this, "scan.pdf")
        lifecycleScope.launch {
            val outcome = engine.imagesToPdf(listOf(image), out)
            outcome.fold(
                onSuccess = {
                    val document = repository.addExisting(
                        ToolOutput.uniqueName("scan", ".pdf"),
                        DocType.PDF,
                        out,
                    )
                    Toast.makeText(
                        this@ScanActivity,
                        R.string.tools_scan_done,
                        Toast.LENGTH_SHORT,
                    ).show()
                    PreviewActivity.start(this@ScanActivity, document.id)
                    finish()
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@ScanActivity,
                        getString(R.string.tools_failed, error.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                },
            )
        }
    }
}
