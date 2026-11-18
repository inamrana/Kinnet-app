package com.kinnect.i230577_i230839

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Photos. Media lives on the Kinnect server and arrives as URLs; each one is downloaded once and
 * kept in memory and in the app's cache folder, so it still shows offline. (Older content stored
 * as Base64 text is still understood.)
 */
object Images {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val loader = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var diskDir: File

    fun init(context: Context) {
        diskDir = File(context.cacheDir, "images").apply { mkdirs() }
    }

    fun isUrl(source: String) = source.startsWith("http://") || source.startsWith("https://")

    /** A photo still waiting in the [Outbox] (shown while its post or message is pending). */
    fun isLocalFile(source: String) = source.startsWith("/")

    /** Reads [uri], scales its longest side down to [maxSide] px and returns Base64 JPEG text. */
    fun encode(context: Context, uri: Uri, maxSide: Int = 900): String? =
        scaled(context, uri, maxSide)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    /** Same scaling, written to a temporary JPEG file ready to upload. */
    fun compressToFile(context: Context, uri: Uri, maxSide: Int = 1280): File? {
        val bytes = scaled(context, uri, maxSide) ?: return null
        return File.createTempFile("upload", ".jpg", context.cacheDir).apply { writeBytes(bytes) }
    }

    private fun scaled(context: Context, uri: Uri, maxSide: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded

        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
        return bytes.toByteArray()
    }

    /** Decodes Base64 photo text (legacy) or an outbox file. URLs go through [load]. */
    fun decode(source: String): Bitmap? {
        if (source.isEmpty() || isUrl(source)) return if (isUrl(source)) cache.get(source) else null
        if (isLocalFile(source)) {
            cache.get(source)?.let { return it }
            return BitmapFactory.decodeFile(source)?.also { cache.put(source, it) }
        }
        val key = source.hashCode().toString()
        cache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(source, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /** Gets the bitmap for a URL from memory, the disk cache or the network, then calls [done] on the main thread. */
    fun load(url: String, done: (Bitmap?) -> Unit) {
        cache.get(url)?.let { done(it); return }
        loader.execute {
            val file = File(diskDir, sha1(url))
            var bitmap = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
            if (bitmap == null) {
                bitmap = runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 20_000
                    val bytes = connection.inputStream.use { it.readBytes() }
                    connection.disconnect()
                    file.writeBytes(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            bitmap?.let { cache.put(url, it) }
            main.post { done(bitmap) }
        }
    }

    /**
     * Shows a photo (URL or Base64) in [view], or leaves the view's design placeholder when
     * there is none. Recycled views are guarded with a tag so a late download can't land on the wrong row.
     */
    fun show(view: ImageView, source: String) {
        if (!isUrl(source)) {
            decode(source)?.let(view::setImageBitmap)
            return
        }
        view.setTag(R.id.image_source, source)
        load(source) { bitmap ->
            if (bitmap != null && view.getTag(R.id.image_source) == source) view.setImageBitmap(bitmap)
        }
    }

    private fun sha1(text: String) =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Letter avatars (TextView with the Avatar style) that switch to the user's photo when they have one. */
object Avatars {

    private val COLORS = intArrayOf(
        R.color.av_crimson, R.color.av_green, R.color.av_indigo, R.color.av_purple,
        R.color.av_brown, R.color.av_steel, R.color.av_olive, R.color.teal,
    )

    fun colorFor(context: Context, uid: String): Int =
        ContextCompat.getColor(context, COLORS[Math.floorMod(uid.hashCode(), COLORS.size)])

    /**
     * [keepShape] keeps the view's own design background (rings, rounded tiles) when there is no photo,
     * only filling in the initials and colour.
     */
    fun show(view: TextView, user: User, keepShape: Boolean = false) {
        val source = user.profileImage
        view.setTag(R.id.image_source, source)
        val ready = Images.decode(source)
        when {
            ready != null -> showPhoto(view, ready)
            else -> {
                showInitials(view, user, keepShape)
                if (Images.isUrl(source)) Images.load(source) { bitmap ->
                    if (bitmap != null && view.getTag(R.id.image_source) == source) showPhoto(view, bitmap)
                }
            }
        }
    }

    private fun showPhoto(view: TextView, photo: Bitmap) {
        view.text = ""
        view.backgroundTintList = null
        view.background = RoundedBitmapDrawableFactory.create(view.resources, photo).apply { isCircular = true }
    }

    private fun showInitials(view: TextView, user: User, keepShape: Boolean) {
        view.text = user.initials
        if (keepShape) return
        view.setBackgroundResource(R.drawable.bg_circle)
        view.backgroundTintList = android.content.res.ColorStateList.valueOf(colorFor(view.context, user.uid))
    }
}
