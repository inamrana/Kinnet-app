package com.inamulhaq.i230839

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.concurrent.Executors

/** A photo or video on the device, read from MediaStore for the Photo picker (08). */
data class GalleryItem(val uri: Uri, val isVideo: Boolean, val durationMs: Long)

/** Reads the device gallery and loads small thumbnails off the main thread. */
object Gallery {

    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = object : LruCache<Uri, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Uri, value: Bitmap) = value.byteCount
    }

    /** Runtime permissions needed to read photos (and videos) on this Android version. */
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Newest photos and videos first. Runs [done] on the main thread. */
    fun load(context: Context, includeVideos: Boolean, done: (List<GalleryItem>) -> Unit) {
        io.execute {
            val items = mutableListOf<GalleryItem>()
            val files = MediaStore.Files.getContentUri("external")
            val types = if (includeVideos) {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            } else {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            }
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Video.VideoColumns.DURATION,
            )
            runCatching {
                context.contentResolver.query(
                    files, projection, types, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                    while (cursor.moveToNext() && items.size < MAX_ITEMS) {
                        val id = cursor.getLong(idCol)
                        val video = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        val duration = if (video && durationCol >= 0) cursor.getLong(durationCol) else 0L
                        items += GalleryItem(ContentUris.withAppendedId(base, id), video, duration)
                    }
                }
            }
            main.post { done(items) }
        }
    }

    /** Shows a thumbnail of [item] in [view]; [size] is the longest side in pixels. */
    fun thumbnail(view: ImageView, item: GalleryItem, size: Int) {
        view.tag = item.uri
        thumbs.get(item.uri)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        val context = view.context.applicationContext
        io.execute {
            val bitmap = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(item.uri, Size(size, size), null)
                } else {
                    @Suppress("DEPRECATION")
                    val id = ContentUris.parseId(item.uri)
                    @Suppress("DEPRECATION")
                    if (item.isVideo) MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                    ) else MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                    )
                }
            }.getOrNull() ?: return@execute
            thumbs.put(item.uri, bitmap)
            main.post { if (view.tag == item.uri) view.setImageBitmap(bitmap) }
        }
    }

    /** "0:23" */
    fun duration(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private const val MAX_ITEMS = 400
}

/** A FrameLayout that is always as tall as it is wide (gallery grid cells). */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
