package com.kinnect.i230577_i230839

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * Files waiting to be uploaded. Anything chosen from the gallery or camera is copied (or
 * compressed) into the app's own folder first, so a queued post, story or message still has its
 * photo, video or file when it is finally sent — even after the app was closed while offline.
 */
object Outbox {

    private lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.filesDir, "outbox").apply { mkdirs() }
    }

    /** A photo, scaled down to [maxSide] and saved as JPEG. */
    fun photo(context: Context, uri: Uri, maxSide: Int = 1280): File? {
        val temp = Images.compressToFile(context, uri, maxSide) ?: return null
        val file = File(dir, "${Sync.newClientId()}.jpg")
        temp.copyTo(file, overwrite = true)
        temp.delete()
        return file
    }

    /** A video or any other file, copied as it is. Returns null if it is bigger than [maxBytes]. */
    fun copy(context: Context, uri: Uri, maxBytes: Long = 38L * 1024 * 1024): File? {
        val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        if (size > maxBytes) return null
        val name = displayName(context, uri)
        val extension = name.substringAfterLast('.', "").take(8).ifEmpty { "bin" }
        val file = File(dir, "${Sync.newClientId()}.$extension")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        }.isSuccess
        return file.takeIf { copied && it.length() in 1..maxBytes }
    }

    /** First frame of a video as a small JPEG (the story rail's preview). */
    fun videoThumb(context: Context, uri: Uri): File? = runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(0)
        retriever.release()
        frame ?: return null
        val scale = 480f / maxOf(frame.width, frame.height)
        val small = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
        File(dir, "${Sync.newClientId()}.jpg").also { out -> FileOutputStream(out).use { small.compress(Bitmap.CompressFormat.JPEG, 75, it) } }
    }.getOrNull()

    /** The file name the user sees (e.g. "notes.pdf"). */
    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}
