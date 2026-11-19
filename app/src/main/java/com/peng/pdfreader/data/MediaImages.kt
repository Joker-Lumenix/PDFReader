package com.peng.pdfreader.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.peng.pdfreader.core.util.Logger
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 系统相册里的图片和相册（分组）。
 *
 * 应用有"访问所有文件"的权限时，可以直接读到系统相册里的图片，"图片转 PDF"
 * 的页面用它们来选图：页面显示所有图片，标题旁边的箭头打开相册分组，
 * 选一个分组之后页面只显示这个分组的图片。
 *
 * 图片先从 MediaStore 里取。拿不到的时候（有些机型在没有相册索引的目录里
 * 不给 MediaStore 建索引），页面会退回到直接扫描共享存储里的图片文件，
 * 所以"有权限却没有图片"的页面不会出现。
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
        val fromStore = storeImages(context, albumId)
        if (fromStore.isNotEmpty()) return fromStore
        // MediaStore 没有索引的图片由共享存储的扫描补上。
        return scannedImages(albumId)
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
     * 缩略图是正方的（图片本身的长宽比会被裁掉），列表和网格里显示的都是
     * 正方的格子。
     *
     * @param size 缩略图的边长，单位像素。
     */
    fun thumbnail(context: Context, image: Image, size: Int): Bitmap? {
        val bitmap = loadThumbnail(context, image, size) ?: return null
        return cropSquare(bitmap, size)
    }

    /**
     * 把图片裁成正方形。
     *
     * 格子是正方的，图片按原样压进去会变形，所以多余的部分被裁掉了 - 画面
     * 保持自己的比例。
     */
    private fun cropSquare(bitmap: Bitmap, size: Int): Bitmap {
        if (bitmap.width == size && bitmap.height == size) return bitmap
        val scale = max(
            size.toFloat() / bitmap.width.toFloat(),
            size.toFloat() / bitmap.height.toFloat(),
        )
        val scaledWidth = (bitmap.width * scale).roundToInt()
        val scaledHeight = (bitmap.height * scale).roundToInt()
        val scaled = if (scaledWidth == bitmap.width && scaledHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        }
        val left = ((scaledWidth - size) / 2).coerceAtLeast(0)
        val top = ((scaledHeight - size) / 2).coerceAtLeast(0)
        if (left == 0 && top == 0 && scaled.width == size && scaled.height == size) return scaled
        val cropped = Bitmap.createBitmap(scaled, left, top, size, size)
        if (cropped !== scaled) scaled.recycle()
        return cropped
    }

    /**
     * 图片的完整画面。
     *
     * 处理页面的图片是放大的，格子里的缩略图看不清，所以那里用的是按屏幕宽度
     * 解码的画面。
     *
     * @param targetWidth 需要的宽度，单位像素，0 表示原图。
     */
    fun decode(context: Context, image: Image, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(image.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        if (targetWidth > 0) {
            while (bounds.outWidth / sample > targetWidth * 2) sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            context.contentResolver.openInputStream(image.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    // ------------------------------------------------------------------ sources

    /** MediaStore 里的图片。 */
    private fun storeImages(context: Context, albumId: String?): List<Image> {
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
        }.onFailure { Logger.w(TAG, "the images of MediaStore are not readable: ${it.message}") }
        return result
    }

    /** 共享存储里的图片，MediaStore 没有给它们建索引时的后备。 */
    private fun scannedImages(albumId: String?): List<Image> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        val found = mutableListOf<Image>()
        collectImages(root, 1, found)
        val filtered = if (albumId.isNullOrEmpty()) {
            found
        } else {
            found.filter { it.albumId == albumId }
        }
        Logger.i(TAG, "the scan of ${root.path} found ${filtered.size} images")
        return filtered
    }

    /** 走一遍文件夹，把里面的图片收起来。 */
    private fun collectImages(folder: File, depth: Int, found: MutableList<Image>) {
        if (depth > MAX_DEPTH || found.size >= MAX_IMAGES) return
        val children = folder.listFiles() ?: return
        children.forEach { child ->
            if (found.size >= MAX_IMAGES) return@forEach
            when {
                child.isDirectory -> {
                    val name = child.name
                    if (name.startsWith(".")) return@forEach
                    if (name.equals("Android", ignoreCase = true)) return@forEach
                    collectImages(child, depth + 1, found)
                }

                child.isFile -> addImage(child, found)
            }
        }
    }

    /** 把一个图片文件放进结果里，它的相册就是它所在的文件夹。 */
    private fun addImage(file: File, found: MutableList<Image>) {
        if (file.length() <= 0L) return
        if (!isImage(file.name)) return
        val folder = file.parentFile
        val albumId = folder?.absolutePath.orEmpty()
        val albumName = folder?.name.orEmpty()
        found += Image(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            name = file.name,
            albumId = albumId,
            albumName = albumName,
            size = file.length(),
            date = file.lastModified(),
        )
    }

    /** 文件名是不是一张图片。 */
    private fun isImage(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    // ------------------------------------------------------------------ decoding

    /**
     * 缩略图。
     *
     * 系统自带的缩略图最省电，但不是每台设备都给。拿不到的时候图片自己解码，
     * 缩小的步长按屏幕上的格子大小算，所以格子里的图是清楚的。
     */
    private fun loadThumbnail(context: Context, image: Image, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = runCatching {
                context.contentResolver.loadThumbnail(image.uri, Size(size, size), null)
            }.getOrNull()
            if (loaded != null) return loaded
        }
        // Android 10 以前没有 loadThumbnail，只能把原图缩下来。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(image.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > size * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            context.contentResolver.openInputStream(image.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    /** The tag of the log of the scanner. */
    private const val TAG = "MediaImages"

    /** How deep the scan goes into a folder. */
    private const val MAX_DEPTH = 6

    /** The most images one scan collects. */
    private const val MAX_IMAGES = 3_000

    /** The extensions of the files that count as an image. */
    private val IMAGE_EXTENSIONS = setOf(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "gif",
        "bmp",
        "heic",
        "heif",
    )
}
