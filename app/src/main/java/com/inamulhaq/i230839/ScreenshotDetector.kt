package com.inamulhaq.i230839

import android.app.Activity
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Calls [onScreenshot] when the user takes a screenshot while [activity] is on screen.
 *
 * Android 14+ tells apps directly (Activity.ScreenCaptureCallback). On older versions we watch
 * the gallery for a new image whose name or folder contains "screenshot".
 */
class ScreenshotDetector(private val activity: Activity, private val onScreenshot: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastAlert = 0L
    private var callback: Any? = null

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (isNewScreenshot(uri)) report()
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val screenCapture = Activity.ScreenCaptureCallback { report() }
            activity.registerScreenCaptureCallback(activity.mainExecutor, screenCapture)
            callback = screenCapture
        } else {
            activity.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
            )
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            (callback as? Activity.ScreenCaptureCallback)?.let(activity::unregisterScreenCaptureCallback)
            callback = null
        } else {
            activity.contentResolver.unregisterContentObserver(observer)
        }
    }

    /** One alert per screenshot, even though the gallery reports several changes for one file. */
    private fun report() {
        val now = System.currentTimeMillis()
        if (now - lastAlert < DEBOUNCE_MS) return
        lastAlert = now
        onScreenshot()
    }

    private fun isNewScreenshot(uri: Uri?): Boolean {
        val target = uri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
        )
        return runCatching {
            activity.contentResolver.query(
                target, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val name = cursor.getString(0).orEmpty()
                val path = cursor.getString(1).orEmpty()
                val addedSeconds = cursor.getLong(2)
                val recent = System.currentTimeMillis() / 1000 - addedSeconds < RECENT_SECONDS
                recent && (name.contains("screenshot", true) || path.contains("screenshot", true))
            } ?: false
        }.getOrDefault(false)
    }

    private companion object {
        const val DEBOUNCE_MS = 3_000L
        const val RECENT_SECONDS = 15
    }
}
