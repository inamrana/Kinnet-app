package com.inamulhaq.i230839

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import java.io.File
import java.util.concurrent.Executors

/**
 * 07 · Create post — text plus one or more photos (from the Photo picker or the camera),
 * or a text-only post on one of the coloured backgrounds. "Post" saves it to Firebase.
 */
class CreatePostActivity : BaseActivity() {

    private lateinit var input: EditText
    private lateinit var photosRow: LinearLayout
    private val photos = mutableListOf<Uri>()
    private var bgColor = ""
    private var cameraFile: Uri? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val watch = Watch()

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uris = result.data?.let {
            IntentCompat.getParcelableArrayListExtra(it, PhotoPickerActivity.EXTRA_URIS, Uri::class.java)
        }.orEmpty()
        addPhotos(uris)
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraFile?.let { addPhotos(listOf(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        input = findViewById(R.id.input_post)
        photosRow = findViewById(R.id.selected_photos)
        tapCloses(R.id.btn_close)
        onTap(R.id.btn_post) { publish() }
        onTap(R.id.opt_photo) {
            pickPhotos.launch(
                Intent(this, PhotoPickerActivity::class.java)
                    .putExtra(PhotoPickerActivity.EXTRA_MODE, PhotoPickerActivity.MODE_POST)
            )
        }
        onTap(R.id.opt_camera) { openCamera() }
        listOf(R.id.opt_tag, R.id.opt_feeling, R.id.opt_checkin, R.id.opt_live, R.id.opt_event).forEach { id ->
            onTap(id) { toast("Coming soon") }
        }
        setupSwatches()

        Db.me()?.let { ref ->
            watch.on(ref) { snapshot ->
                val user = snapshot.getValue(User::class.java) ?: return@on
                findViewById<TextView>(R.id.create_name).text = user.fullName
                Avatars.show(findViewById(R.id.create_avatar), user)
                input.hint = "What's on your mind, ${user.firstName.ifEmpty { user.fullName }}?"
            }
        }
    }

    // ---- photos --------------------------------------------------------------------------

    private fun openCamera() {
        val file = File(cacheDir, "camera").apply { mkdirs() }.let { File(it, "post_${System.currentTimeMillis()}.jpg") }
        cameraFile = FileProvider.getUriForFile(this, "$packageName.files", file)
        takePhoto.launch(cameraFile!!)
    }

    private fun addPhotos(uris: List<Uri>) {
        uris.filter { it !in photos }.take(MAX_PHOTOS - photos.size).forEach(photos::add)
        if (photos.isNotEmpty()) chooseBackground("")
        showPhotos()
    }

    private fun showPhotos() {
        photosRow.removeAllViews()
        photos.forEach { uri ->
            val cell = LayoutInflater.from(this).inflate(R.layout.item_selected_photo, photosRow, false)
            cell.findViewById<ImageView>(R.id.selected_image).setImageURI(uri)
            cell.findViewById<View>(R.id.selected_remove).setOnClickListener {
                photos.remove(uri)
                showPhotos()
            }
            photosRow.addView(cell)
        }
        findViewById<View>(R.id.selected_photos_scroll).visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE
        // Coloured backgrounds are only for text posts
        findViewById<View>(R.id.swatch_row).visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---- coloured background for text posts -------------------------------------------

    private fun setupSwatches() {
        onTap(R.id.swatch_0) { chooseBackground("") }
        SWATCHES.forEachIndexed { index, color ->
            val id = resources.getIdentifier("swatch_${index + 1}", "id", packageName)
            onTap(id) { chooseBackground(color) }
        }
    }

    private fun chooseBackground(color: String) {
        bgColor = color
        if (color.isEmpty()) {
            input.setBackgroundColor(Color.TRANSPARENT)
            input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            input.gravity = Gravity.TOP or Gravity.START
            input.minHeight = 0
        } else {
            input.setBackgroundColor(Color.parseColor(color))
            input.setTextColor(Color.WHITE)
            input.gravity = Gravity.CENTER
            input.minHeight = (220 * resources.displayMetrics.density).toInt()
        }
        val selected = if (color.isEmpty()) 0 else SWATCHES.indexOf(color) + 1
        for (i in 0..SWATCHES.size) {
            val view = findViewById<View>(resources.getIdentifier("swatch_$i", "id", packageName))
            view.scaleX = if (i == selected) 1.12f else 1f
            view.scaleY = view.scaleX
        }
    }

    // ---- publish ------------------------------------------------------------------------

    private fun publish() {
        val text = input.text.toString().trim()
        if (text.isEmpty() && photos.isEmpty()) {
            toast("Write something or add a photo")
            return
        }
        val button = findViewById<Button>(R.id.btn_post)
        button.isEnabled = false
        button.text = "Posting…"
        val uris = photos.toList()
        worker.execute {
            val encoded = uris.mapNotNull { Images.encode(this, it, POST_IMAGE_SIZE) }
            main.post {
                Posts.create(text, encoded, bgColor) { ok ->
                    if (ok) {
                        toast("Posted")
                        finish()
                    } else {
                        button.isEnabled = true
                        button.text = "Post"
                        toast("Could not post. Check your connection.")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    private companion object {
        const val MAX_PHOTOS = 10
        const val POST_IMAGE_SIZE = 1080
        /** Same colours as the swatches in the design, left to right. */
        val SWATCHES = listOf("#0B5F63", "#BF4B2B", "#4D5293", "#2F6B4C", "#CC8A12", "#7B4F80", "#1C1B1A")
    }
}
