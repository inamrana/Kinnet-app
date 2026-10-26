package com.inamulhaq.i230839

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Photos are stored in the Realtime Database as small Base64 JPEGs
 * (profile pictures, covers and chat images), so no extra storage service is needed.
 */
object Images {

    private val cache = object : LruCache<Int, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    /** Reads [uri], scales its longest side down to [maxSide] px and returns Base64 JPEG text. */
    fun encode(context: Context, uri: Uri, maxSide: Int = 900): String? {
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes)
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(base64: String): Bitmap? {
        if (base64.isEmpty()) return null
        val key = base64.hashCode()
        cache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /** Shows [base64] in [view], or leaves the view's design placeholder when there is no photo. */
    fun show(view: ImageView, base64: String) {
        decode(base64)?.let(view::setImageBitmap)
    }
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
        val photo = Images.decode(user.profileImage)
        if (photo != null) {
            view.text = ""
            view.backgroundTintList = null
            view.background = RoundedBitmapDrawableFactory.create(view.resources, photo).apply { isCircular = true }
        } else if (keepShape) {
            view.text = user.initials
        } else {
            view.text = user.initials
            view.setBackgroundResource(R.drawable.bg_circle)
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(colorFor(view.context, user.uid))
        }
    }
}
