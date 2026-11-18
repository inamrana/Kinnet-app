package com.kinnect.i230577_i230839

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * A story from stories/feed.php. The photo or video is a file on the server ([media]); [thumb] is
 * a small preview for the Home rail. Stories disappear 24 hours after [timestamp]: the server stops
 * returning them and they are removed from the SQLite cache too.
 */
data class Story(
    val id: String = "",
    val ownerId: String = "",
    val type: String = TYPE_IMAGE,
    val media: String = "",
    val thumb: String = "",
    val label: String = "",
    val labelStyle: String = LABEL_RED,
    val labelX: Double = 0.1,
    val labelY: Double = 0.55,
    val audience: String = AUDIENCE_FRIENDS,
    val timestamp: Long = 0L,
    val viewers: Map<String, Long> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val pending: Boolean = false,
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

/** Sharing, viewing and expiring stories through the web services, cached in SQLite. */
object Stories {

    const val LIFETIME_MS = 24 * 60 * 60 * 1000L
    private const val MAX_VIDEO_BYTES = 38L * 1024 * 1024
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()

    init {
        Sync.addListener { done ->
            when (done.type) {
                "story" -> {
                    LocalDb.deleteItem("stories", Sync.localId(done.clientId))
                    done.response?.optJSONObject("story")?.let(::store)
                }
                "storydelete" -> Unit
                else -> return@addListener
            }
            changed()
        }
    }

    // ---- reading ----------------------------------------------------------------------------

    /** Live stories from the cache (mine and friends'), oldest first. Expired ones are deleted first. */
    fun cached(): List<Story> {
        LocalDb.deleteOlderThan("stories", System.currentTimeMillis() - LIFETIME_MS)
        return LocalDb.items("stories").map { it.toStory() }.filter { it.isLive }
    }

    fun cachedFor(owner: String) = cached().filter { it.ownerId == owner }

    fun refresh(done: (Boolean) -> Unit = {}) {
        Api.get("stories/feed.php") { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get done(false)
            LocalDb.replaceItems("stories", null, json.optJSONArray("stories").mapObjects { it.toItem() })
            Posts.dropSyncedCopies("stories", json.optJSONArray("stories").mapObjects { it })
            LocalDb.deleteOlderThan("stories", System.currentTimeMillis() - LIFETIME_MS)
            done(true)
        }
    }

    fun watch(onChange: () -> Unit): Watch {
        listeners += onChange
        return Watch().onStop { listeners -= onChange }
    }

    private fun changed() = listeners.toList().forEach { it() }

    /** The stories [viewer] may see right now, oldest first (the server already applies "Close friends"). */
    fun visible(stories: List<Story>, @Suppress("UNUSED_PARAMETER") viewer: String, @Suppress("UNUSED_PARAMETER") isCloseFriend: Boolean): List<Story> =
        stories.filter { it.isLive }.sortedBy { it.timestamp }

    // ---- writing ----------------------------------------------------------------------------

    /**
     * Copies the photo or video into the [Outbox] off the main thread, shows the story at once as
     * pending and uploads it through [Sync]. [done] gets an error message or null.
     */
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
        val app = context.applicationContext
        worker.execute {
            val file = if (isVideo) Outbox.copy(app, media, MAX_VIDEO_BYTES) else Outbox.photo(app, media, 1080)
            val thumb = if (isVideo) Outbox.videoThumb(app, media) else null
            main.post {
                if (file == null) return@post done(if (isVideo) "That video is too long. Try a shorter clip." else "Could not read that photo")
                val clientId = Sync.newClientId()
                val local = Story(
                    id = Sync.localId(clientId), ownerId = me, type = if (isVideo) Story.TYPE_VIDEO else Story.TYPE_IMAGE,
                    media = file.absolutePath, thumb = if (isVideo) thumb?.absolutePath.orEmpty() else file.absolutePath,
                    label = label, labelStyle = labelStyle, labelX = labelX, labelY = labelY, audience = audience,
                    timestamp = System.currentTimeMillis(), pending = true,
                )
                LocalDb.putItem("stories", local.id, me, local.timestamp, local.toJson(), pending = true)
                Sync.enqueue(
                    "story", "stories/create.php",
                    mapOf("label" to label, "labelStyle" to labelStyle, "labelX" to labelX, "labelY" to labelY, "audience" to audience),
                    files = buildMap { put("media", file); thumb?.let { put("thumb", it) } }, clientId = clientId,
                )
                changed()
                done(null)
            }
        }
    }

    fun delete(story: Story) {
        LocalDb.deleteItem("stories", story.id)
        Sync.enqueue("storydelete", "stories/delete.php", mapOf("storyId" to Sync.ref(story.id)))
        changed()
    }

    fun markViewed(story: Story) {
        val me = Db.uid ?: return
        if (story.ownerId == me || story.viewers.containsKey(me) || story.pending) return
        val seen = story.copy(viewers = story.viewers + (me to System.currentTimeMillis()))
        LocalDb.putItem("stories", seen.id, seen.ownerId, seen.timestamp, seen.toJson())
        Sync.enqueue("storyview", "stories/view.php", mapOf("storyId" to story.id))
    }

    /** A quick reaction from the Story viewer: stored on the story and sent to the owner as a message. */
    fun react(story: Story, reaction: Reaction) {
        Sync.enqueue("storyview", "stories/view.php", mapOf("storyId" to Sync.ref(story.id), "reaction" to reaction.key))
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

    /**
     * Gets a story's media: a photo source for [Images.show], or for videos a local file VideoView
     * can play (downloaded once into the cache, so it also plays offline afterwards).
     */
    fun loadMedia(context: Context, story: Story, done: (image: String?, video: File?) -> Unit) {
        if (!story.isVideo) return done(story.media, null)
        if (Images.isLocalFile(story.media)) return done(null, File(story.media))
        val app = context.applicationContext
        worker.execute {
            val dir = File(app.cacheDir, "media").apply { mkdirs() }
            val file = File(dir, sha1(story.media) + ".mp4")
            if (!file.exists()) runCatching {
                val connection = URL(story.media).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 60_000
                val temp = File(dir, file.name + ".part")
                connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                connection.disconnect()
                temp.renameTo(file)
            }
            main.post { done(null, file.takeIf { it.exists() }) }
        }
    }

    // ---- close friends ----------------------------------------------------------------------

    fun closeFriends(done: (Set<String>) -> Unit) {
        LocalDb.cache("closeFriends")?.let { done(parseIds(JSONObject(it))) }
        Api.get("stories/close_friends.php") { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get
            LocalDb.putCache("closeFriends", json.toString())
            done(parseIds(json))
        }
    }

    fun setCloseFriend(uid: String, on: Boolean, done: (Set<String>) -> Unit) {
        Api.post("stories/close_friends.php", mapOf("friendId" to uid, "on" to if (on) "1" else "0")) { result ->
            val json = result.json?.takeIf { result.ok }
            if (json == null) return@post
            LocalDb.putCache("closeFriends", json.toString())
            done(parseIds(json))
        }
    }

    private fun parseIds(json: JSONObject): Set<String> =
        json.optJSONArray("closeFriends")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()

    // ---- JSON <-> SQLite --------------------------------------------------------------------

    private fun store(json: JSONObject) = json.toItem().let { LocalDb.putItem("stories", it.id, it.parent, it.sort, it.json) }

    private fun JSONObject.toItem() = LocalDb.Item(optString("id"), optString("ownerId"), optLong("timestamp"), false, this)

    private fun LocalDb.Item.toStory() = Story(
        id = json.optString("id"), ownerId = json.optString("ownerId"), type = json.optString("type", Story.TYPE_IMAGE),
        media = json.optString("media"), thumb = json.optString("thumb"), label = json.optString("label"),
        labelStyle = json.optString("labelStyle", Story.LABEL_RED), labelX = json.optDouble("labelX", 0.1),
        labelY = json.optDouble("labelY", 0.55), audience = json.optString("audience", Story.AUDIENCE_FRIENDS),
        timestamp = json.optLong("timestamp"),
        viewers = json.optJSONObject("viewers")?.let { v -> v.keys().asSequence().associateWith { v.optLong(it) } }.orEmpty(),
        reactions = json.optJSONObject("reactions").toStringMap(), pending = pending,
    )

    private fun Story.toJson() = JSONObject()
        .put("id", id).put("ownerId", ownerId).put("type", type).put("media", media).put("thumb", thumb)
        .put("label", label).put("labelStyle", labelStyle).put("labelX", labelX).put("labelY", labelY)
        .put("audience", audience).put("timestamp", timestamp)
        .put("viewers", JSONObject(viewers as Map<*, *>)).put("reactions", JSONObject(reactions as Map<*, *>))

    private fun sha1(text: String) =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
