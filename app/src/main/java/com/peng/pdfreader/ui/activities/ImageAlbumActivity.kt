package com.peng.pdfreader.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.MediaImages
import com.peng.pdfreader.databinding.ActivityImageAlbumBinding
import com.peng.pdfreader.ui.adapter.decoration.EdgeGapDecoration
import com.peng.pdfreader.ui.adapter.listAdapter.ImageRowAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The albums of the device (the groups of the design).
 *
 * The page lists every album of the system with its cover, its name and the
 * number of the pictures in it - the rows of the design "Image to PDF下拉".
 * The first entry is "all photos", it shows the pictures of every album.
 *
 * Clicking an album hands it back to the page that opened it and closes the
 * page. The back button closes the page without a choice.
 */
class ImageAlbumActivity : BaseActivity() {

    private lateinit var binding: ActivityImageAlbumBinding

    private val adapter by lazy {
        ImageRowAdapter { index ->
            val album = albums.getOrNull(index) ?: return@ImageRowAdapter
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_ALBUM_ID, album.id)
                    .putExtra(EXTRA_ALBUM_NAME, album.name),
            )
            finish()
        }
    }

    /** The albums of the list, the first one is "all photos". */
    private val albums = mutableListOf<MediaImages.Album>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.tools_image_albums)
        // The back button closes the page without a choice.
        binding.ivBack.setOnClickListener { finish() }

        binding.rvAlbums.layoutManager = LinearLayoutManager(this)
        binding.rvAlbums.adapter = adapter
        // the rows of the design sit 12dp from the edges, 12dp apart
        binding.rvAlbums.addItemDecoration(
            EdgeGapDecoration(dp(12), dp(12)),
        )

        loadAlbums()
    }

    /** Reads the albums of the device and decodes their covers. */
    private fun loadAlbums() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val found = MediaImages.albums(this@ImageAlbumActivity)
                val all = found.sumOf { it.count }
                val list = mutableListOf(
                    MediaImages.Album(
                        id = "",
                        name = getString(R.string.tools_image_all_albums),
                        count = all,
                        cover = null,
                    ),
                )
                list += found
                list
            }
            albums.clear()
            albums.addAll(list)

            val rows = withContext(Dispatchers.IO) {
                list.map { album ->
                    ImageRowAdapter.Row(
                        name = album.name,
                        meta = album.count.toString(),
                        thumbnail = album.cover?.let {
                            MediaImages.thumbnail(this@ImageAlbumActivity, it, dp(64))
                        },
                    )
                }
            }
            adapter.submit(rows)
        }
    }

    companion object {
        /** 相册的 id，空的表示"所有照片"。 */
        const val EXTRA_ALBUM_ID = "albumId"

        /** 相册的名字。 */
        const val EXTRA_ALBUM_NAME = "albumName"

        /** Opens the page of the albums. */
        fun start(context: Context) {
            context.startActivity(Intent(context, ImageAlbumActivity::class.java))
        }
    }

    /** Converts dp into pixels. */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
