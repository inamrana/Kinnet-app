package com.inamulhaq.i230839

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.IntentCompat
import kotlin.math.abs

/**
 * 10 · Story editor — previews the photo or video, lets you add a text label (Aa), drag it
 * around and tap it to switch colour, then shares to Your story or Close friends.
 */
class StoryEditorActivity : BaseActivity() {

    private val media by lazy { IntentCompat.getParcelableExtra(intent, EXTRA_MEDIA, Uri::class.java) }
    private val isVideo by lazy { intent.getBooleanExtra(EXTRA_IS_VIDEO, false) }
    private lateinit var label: TextView
    private var labelStyle = Story.LABEL_RED
    private var sharing = false
    private var closeFriendCount = 0
    private val watch = Watch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_editor)
        val uri = media ?: run { finish(); return }

        label = findViewById(R.id.story_label)
        showMedia(uri)
        tapCloses(R.id.btn_close)
        onTap(R.id.tool_text) { editLabel() }
        listOf(R.id.tool_tag, R.id.tool_sticker, R.id.tool_music, R.id.tool_effects).forEach { onTap(it) { toast("Coming soon") } }
        onTap(R.id.share_your_story) { share(Story.AUDIENCE_FRIENDS) }
        onTap(R.id.btn_share) { share(Story.AUDIENCE_FRIENDS) }
        onTap(R.id.share_close_friends) {
            if (closeFriendCount == 0) {
                toast("Choose your close friends first")
                open(CloseFriendsActivity::class.java)
            } else share(Story.AUDIENCE_CLOSE)
        }
        findViewById<View>(R.id.share_close_friends).setOnLongClickListener {
            open(CloseFriendsActivity::class.java)
            true
        }
        setupLabelDragging()

        val me = Db.uid ?: return
        watch.on(Db.user(me)) { snapshot ->
            snapshot.getValue(User::class.java)?.let { Avatars.show(findViewById(R.id.share_avatar), it) }
        }
        watch.on(Db.closeFriends.child(me)) { closeFriendCount = it.childrenCount.toInt() }
    }

    private fun showMedia(uri: Uri) {
        val image = findViewById<ImageView>(R.id.story_image)
        val video = findViewById<VideoView>(R.id.story_video)
        if (isVideo) {
            image.visibility = View.GONE
            video.visibility = View.VISIBLE
            video.setVideoURI(uri)
            video.setOnPreparedListener { it.isLooping = true; video.start() }
        } else {
            image.setImageURI(uri)
        }
    }

    private fun editLabel() {
        val field = EditText(this).apply {
            setText(label.text)
            hint = "Add text"
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Story text")
            .setView(field)
            .setPositiveButton("Done") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isEmpty()) {
                    label.visibility = View.GONE
                    label.text = ""
                } else {
                    val first = label.visibility != View.VISIBLE
                    StoryLabel.apply(label, text, labelStyle, if (first) 0.1 else xFraction(), if (first) 0.55 else yFraction())
                    findViewById<View>(R.id.editor_hint).visibility = View.GONE
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Drag the label anywhere on the card; a quick tap switches between the orange and white looks. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupLabelDragging() {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        label.setOnTouchListener { view, event ->
            val parent = view.parent as View
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = view.translationX; startY = view.translationY
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX = (startX + event.rawX - downX).coerceIn(0f, (parent.width - view.width).toFloat())
                    view.translationY = (startY + event.rawY - downY).coerceIn(0f, (parent.height - view.height).toFloat())
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downX) < 10 && abs(event.rawY - downY) < 10) {
                        labelStyle = if (labelStyle == Story.LABEL_RED) Story.LABEL_WHITE else Story.LABEL_RED
                        StoryLabel.style(label, labelStyle)
                    }
                }
            }
            true
        }
    }

    private fun xFraction(): Double {
        val room = (label.parent as View).width - label.width
        return if (room <= 0) 0.0 else (label.translationX / room).toDouble().coerceIn(0.0, 1.0)
    }

    private fun yFraction(): Double {
        val room = (label.parent as View).height - label.height
        return if (room <= 0) 0.0 else (label.translationY / room).toDouble().coerceIn(0.0, 1.0)
    }

    private fun share(audience: String) {
        if (sharing) return
        val uri = media ?: return
        sharing = true
        findViewById<View>(R.id.editor_progress).visibility = View.VISIBLE
        val text = if (label.visibility == View.VISIBLE) label.text.toString() else ""
        Stories.share(this, uri, isVideo, text, labelStyle, xFraction(), yFraction(), audience) { error ->
            sharing = false
            findViewById<View>(R.id.editor_progress).visibility = View.GONE
            if (error != null) {
                toast(error)
                return@share
            }
            toast(if (audience == Story.AUDIENCE_CLOSE) "Shared with close friends" else "Shared to your story")
            // Land on Your story, with Home underneath so Back returns to the feed
            startActivities(
                arrayOf(
                    Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    Intent(this, YourStoryActivity::class.java),
                )
            )
            finish()
        }
    }

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA = "media"
        const val EXTRA_IS_VIDEO = "isVideo"
    }
}
