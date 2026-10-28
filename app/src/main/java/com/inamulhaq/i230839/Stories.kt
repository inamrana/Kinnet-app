package com.inamulhaq.i230839

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.firebase.database.ServerValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * A story under stories/{ownerUid}/{storyId}. The photo or video itself is in storyMedia/{storyId};
 * [thumb] is a small preview for the Home rail. Stories disappear 24 hours after [timestamp].
 */
data class Story(
    val id: String = "",
    val ownerId: String = "",
    val type: String = TYPE_IMAGE,
    val thumb: String = "",
    val label: String = "",
    val labelStyle: String = LABEL_RED,
    val labelX: Double = 0.1,
    val labelY: Double = 0.55,
    val audience: String = AUDIENCE_FRIENDS,
    val timestamp: Long = 0L,
    val viewers: Map<String, Long> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
) {
    val isVideo get() = type == TYPE_VIDEO
    val isLive get() = System.currentTimeMillis() - timestamp < Stories.LIFETIME_MS

    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val AUDIENCE_FRIENDS = "friends"
        const val AUDIENCE_CLOSE = "close"
        const val LABEL_RED = "red"
        const val LABEL_WHITE = "white"
    }
}

/** Sharing, viewing and expiring stories. */
object Stories {

    const val LIFETIME_MS = 24 * 60 * 60 * 1000L
    private const val MAX_VIDEO_BYTES = 8 * 1024 * 1024
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Encodes the media off the main thread and saves the story. [done] gets an error message or null. */
    fun share(
        context: Context,
        media: Uri,
        isVideo: Boolean,
        label: String,
        labelStyle: String,
        labelX: Double,
        labelY: Double,
        audience: String,
        done: (String?) -> Unit,
    ) {
        val me = Db.uid ?: return done("Please log in again")
        worker.execute {
            val encoded: String?
            val thumb: String?
            if (isVideo) {
                val bytes = runCatching { context.contentResolver.openInputStream(media)?.use { it.readBytes() } }.getOrNull()
                if (bytes == null || bytes.size > MAX_VIDEO_BYTES) {
                    main.post { done(if (bytes == null) "Could not read that video" else "That video is too long. Try a shorter clip.") }
                    return@execute
                }
                encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                thumb = videoThumb(context, media)
            } else {
                encoded = Images.encode(context, media, 1080)
                thumb = Images.encode(context, media, 360)
            }
            if (encoded == null) {
                main.post { done("Could not read that photo") }
                return@execute
            }
            main.post {
                val id = Db.stories.child(me).push().key ?: return@post done("Could not share")
                Db.update(
                    mapOf(
                        "stories/$me/$id" to mapOf(
                            "ownerId" to me,
                            "type" to if (isVideo) Story.TYPE_VIDEO else Story.TYPE_IMAGE,
                            "thumb" to (thumb ?: ""),
                            "label" to label,
                            "labelStyle" to labelStyle,
                            "labelX" to labelX,
                            "labelY" to labelY,
                            "audience" to audience,
                            "timestamp" to ServerValue.TIMESTAMP,
                        ),
                        "storyMedia/$id" to mapOf("owner" to me, "data" to encoded),
                    )
                ).addOnCompleteListener { done(if (it.isSuccessful) null else "Could not share. Check your connection.") }
            }
        }
    }

    private fun videoThumb(context: Context, uri: Uri): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(0) ?: return null
        retriever.release()
        val scale = 360f / maxOf(frame.width, frame.height)
        val small = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** The stories [viewer] may see right now (live, and close-friends ones only if allowed), oldest first. */
    fun visible(stories: List<Story>, viewer: String, isCloseFriend: Boolean): List<Story> =
        stories.filter { it.isLive && (it.ownerId == viewer || it.audience != Story.AUDIENCE_CLOSE || isCloseFriend) }
            .sortedBy { it.timestamp }

    /** Removes your own stories older than 24 hours (and their media). */
    fun deleteExpired(mine: List<Story>) {
        val me = Db.uid ?: return
        val updates = mutableMapOf<String, Any?>()
        mine.filter { !it.isLive }.forEach {
            updates["stories/$me/${it.id}"] = null
            updates["storyMedia/${it.id}"] = null
        }
        if (updates.isNotEmpty()) Db.update(updates)
    }

    fun delete(story: Story) {
        Db.update(mapOf("stories/${story.ownerId}/${story.id}" to null, "storyMedia/${story.id}" to null))
    }

    fun markViewed(story: Story) {
        val me = Db.uid ?: return
        if (story.ownerId == me || story.viewers.containsKey(me)) return
        Db.stories.child(story.ownerId).child(story.id).child("viewers").child(me).setValue(ServerValue.TIMESTAMP)
    }

    /** A quick reaction from the Story viewer: stored on the story and sent to the owner as a message. */
    fun react(story: Story, reaction: Reaction) {
        val me = Db.uid ?: return
        Db.stories.child(story.ownerId).child(story.id).child("reactions").child(me).setValue(reaction.key)
        val emoji = when (reaction) {
            Reaction.LOVE -> "❤️"
            Reaction.HAHA -> "😆"
            else -> "👍"
        }
        ChatSender.send(story.ownerId, Message.TYPE_TEXT, text = "$emoji Reacted to your story")
    }

    fun reply(story: Story, text: String) {
        ChatSender.send(story.ownerId, Message.TYPE_TEXT, text = "Replied to your story: $text")
    }

    /** Loads a story's photo or video. Videos are written to a cache file so VideoView can play them. */
    fun loadMedia(context: Context, story: Story, done: (image: String?, video: File?) -> Unit) {
        Db.storyMedia.child(story.id).child("data").get().addOnSuccessListener { snapshot ->
            val data = snapshot.getValue(String::class.java).orEmpty()
            if (!story.isVideo) return@addOnSuccessListener done(data, null)
            worker.execute {
                val file = File(File(context.cacheDir, "media").apply { mkdirs() }, "${story.id}.mp4")
                if (!file.exists()) runCatching { file.writeBytes(Base64.decode(data, Base64.DEFAULT)) }
                main.post { done(null, file.takeIf { it.exists() }) }
            }
        }.addOnFailureListener { done(null, null) }
    }
}
