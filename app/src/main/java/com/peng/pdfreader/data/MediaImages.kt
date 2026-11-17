package com.peng.pdfreader.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/**
 * 系统相册里的图片和相册（分组）。
 *
 * 应用有"访问所有文件"的权限时，可以直接读到系统相册里的图片，"图片转 PDF"
 * 的页面用它们来选图：页面显示所有图片，标题旁边的箭头打开相册分组，
 * 选一个分组之后页面只显示这个分组的图片。
 */
object MediaImages {

    /** 系统里的一张图片。 */
    data class Image(
        /** 图片在 MediaStore 里的 id。 */
        val id: Long,
        /** 图片的地址。 */
        val uri: Uri,
        /** 图片的名字。 */
        val name: String,
        /** 相册的 id，也就是 MediaStore 里的 bucket。 */
        val albumId: String,
        /** 相册的名字。 */
        val albumName: String,
        /** 图片的大小，单位字节。 */
        val size: Long,
        /** 拍摄时间，用于排序。 */
        val date: Long,
    )

    /** 系统里的一个相册，也就是设计稿里的分组。 */
    data class Album(
        /** 相册的 id。 */
        val id: String,
        /** 相册的名字。 */
        val name: String,
        /** 相册里的图片数量。 */
        val count: Int,
        /** 相册的封面图片。 */
        val cover: Image?,
    )

    /**
     * 系统里的所有图片。
     *
     * @param albumId 相册的 id，null 表示所有相册。
     */
    fun images(context: Context, albumId: String? = null): List<Image> {
        val result = mutableListOf<Image>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val selection = if (albumId.isNullOrEmpty()) {
            null
        } else {
            "${MediaStore.Images.Media.BUCKET_ID} = ?"
        }
        val args = if (albumId.isNullOrEmpty()) null else arrayOf(albumId)
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val albumNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    result += Image(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                        name = cursor.getString(nameColumn).orEmpty(),
                        albumId = cursor.getString(albumIdColumn).orEmpty(),
                        albumName = cursor.getString(albumNameColumn).orEmpty(),
                        size = cursor.getLong(sizeColumn),
                        date = cursor.getLong(dateColumn),
                    )
                }
            }
        }
        return result
    }

    /** 系统里的所有相册，也就是设计稿里的分组。 */
    fun albums(context: Context): List<Album> {
        val groups = LinkedHashMap<String, Album>()
        images(context).forEach { image ->
            val album = groups[image.albumId]
            if (album == null) {
                groups[image.albumId] = Album(
                    id = image.albumId,
                    // 有些图片没有相册的名字（比如 adb 放进去的文件）。
                    name = image.albumName.ifBlank {
                        context.getString(com.peng.pdfreader.R.string.tools_image_album_other)
                    },
                    count = 1,
                    cover = image,
                )
            } else {
                groups[image.albumId] = album.copy(count = album.count + 1)
            }
        }
        return groups.values.sortedBy { it.name.lowercase() }
    }

    /**
     * 图片的缩略图。
     *
     * 缩略图是正方的（图片本身的长宽比会被压进来），列表和网格里显示的都是
     * 正方的格子。
     *
     * @param size 缩略图的边长，单位像素。
     */
    fun thumbnail(context: Context, image: Image, size: Int): Bitmap? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(image.uri, Size(size, size), null)
            }.getOrNull()
        } else {
            // Android 10 以前没有 loadThumbnail，只能把原图缩下来。
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(image.uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > size * 2) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(image.uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }.getOrNull()
        }
        if (bitmap == null) return null
        if (bitmap.width == size && bitmap.height == size) return bitmap
        return runCatching {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }.getOrElse { bitmap }
    }
}
