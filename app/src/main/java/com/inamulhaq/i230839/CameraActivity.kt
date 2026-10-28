package com.inamulhaq.i230839

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.File

/**
 * 09 · Camera — live CameraX preview. Tap the shutter for a photo, hold it to record a video
 * (up to 15 seconds). The gallery button picks an existing photo or video instead.
 * Whatever is captured opens in the Story editor.
 */
class CameraActivity : BaseActivity() {

    private lateinit var preview: PreviewView
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashOn = false
    private var recordStart = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var holdStarted = false

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasCamera()) startCamera() else {
            toast("Camera permission is needed to take stories")
            findViewById<TextView>(R.id.camera_hint).text = "Allow camera access, or pick from the gallery"
        }
    }

    private val pickFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uri = IntentCompat.getParcelableArrayListExtra(data, PhotoPickerActivity.EXTRA_URIS, Uri::class.java)?.firstOrNull()
            ?: return@registerForActivityResult
        openEditor(uri, data.getBooleanExtra(PhotoPickerActivity.EXTRA_IS_VIDEO, false))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        preview = findViewById(R.id.camera_preview)

        tapCloses(R.id.btn_close)
        onTap(R.id.btn_flip) { flipCamera() }
        onTap(R.id.btn_flash) { toggleFlash() }
        onTap(R.id.btn_gallery) {
            pickFromGallery.launch(
                Intent(this, PhotoPickerActivity::class.java)
                    .putExtra(PhotoPickerActivity.EXTRA_MODE, PhotoPickerActivity.MODE_STORY)
            )
        }
        onTap(R.id.mode_post) { open(CreatePostActivity::class.java) }
        listOf(R.id.btn_settings, R.id.btn_effects).forEach { onTap(it) { toast("Coming soon") } }
        setupShutter()

        if (hasCamera()) startCamera()
        else askPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()
            // Low quality keeps short story videos small enough to store in the database
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.LOWEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, previewUseCase, imageCapture, videoCapture)
            }.onFailure {
                toast("Could not open the camera")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
        else CameraSelector.LENS_FACING_BACK
        if (hasCamera()) startCamera()
    }

    private fun toggleFlash() {
        flashOn = !flashOn
        imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        findViewById<ImageView>(R.id.btn_flash).backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (flashOn) R.color.teal else R.color.glass_dark)
        )
        toast(if (flashOn) "Flash on" else "Flash off")
    }

    /** Tap = photo, hold = video. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupShutter() {
        val shutter = findViewById<View>(R.id.btn_shutter)
        val startRecordingLater = Runnable { holdStarted = true; startRecording() }
        shutter.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdStarted = false
                    handler.postDelayed(startRecordingLater, HOLD_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(startRecordingLater)
                    if (holdStarted) stopRecording() else if (event.action == MotionEvent.ACTION_UP) takePhoto()
                }
            }
            true
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return toast("Camera is not ready yet")
        val file = newFile("jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = openEditor(Uri.fromFile(file), false)
                override fun onError(exception: ImageCaptureException) = toast("Could not take the photo")
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val capture = videoCapture ?: return
        val file = newFile("mp4")
        val withAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
        recording = (if (withAudio) pending.withAudioEnabled() else pending)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    showRecording(false)
                    if (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
                        openEditor(Uri.fromFile(file), true)
                    } else {
                        toast("Could not record the video")
                    }
                }
            }
        recordStart = System.currentTimeMillis()
        showRecording(true)
        handler.postDelayed(stopAtLimit, MAX_VIDEO_MS)
    }

    private val stopAtLimit = Runnable { stopRecording() }
    private val tick = object : Runnable {
        override fun run() {
            val seconds = (System.currentTimeMillis() - recordStart) / 1000
            findViewById<TextView>(R.id.camera_recording).text = "● REC  0:%02d".format(seconds)
            handler.postDelayed(this, 500)
        }
    }

    private fun stopRecording() {
        handler.removeCallbacks(stopAtLimit)
        recording?.stop()
        recording = null
    }

    private fun showRecording(on: Boolean) {
        findViewById<View>(R.id.camera_recording).visibility = if (on) View.VISIBLE else View.GONE
        findViewById<View>(R.id.camera_hint).visibility = if (on) View.GONE else View.VISIBLE
        findViewById<View>(R.id.shutter_disc).backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (on) R.color.call_red else R.color.white)
        )
        if (on) handler.post(tick) else handler.removeCallbacks(tick)
    }

    private fun newFile(extension: String): File =
        File(File(cacheDir, "media").apply { mkdirs() }, "story_${System.currentTimeMillis()}.$extension")

    private fun openEditor(uri: Uri, isVideo: Boolean) {
        startActivity(
            Intent(this, StoryEditorActivity::class.java)
                .putExtra(StoryEditorActivity.EXTRA_MEDIA, uri)
                .putExtra(StoryEditorActivity.EXTRA_IS_VIDEO, isVideo)
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recording?.stop()
        super.onDestroy()
    }

    private companion object {
        const val HOLD_MS = 350L
        const val MAX_VIDEO_MS = 15_000L
    }
}
