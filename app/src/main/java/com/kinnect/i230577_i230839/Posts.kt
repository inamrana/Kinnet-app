package com.kinnect.i230577_i230839

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * A post from posts/feed.php. [images] are photo URLs (or outbox file paths while the post is
 * still [pending], i.e. created offline and not on the server yet).
 */
data class Post(
    val id: String = "",
    val authorId: String = "",
    val text: String = "",
    val bgColor: String = "",
    val images: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val timestamp: Long = 0L,
    val pending: Boolean = false,
) {
    val imageCount get() = images.size
}

/** A comment. Replies point at their top-level comment with [parentId]. */
data class Comment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val text: String = "",
    val image: String = "",
    val parentId: String = "",
    val replyToName: String = "",
    val likes: Map<String, Boolean> = emptyMap(),
    val timestamp: Long = 0L,
    val pending: Boolean = false,
)

/** The six reactions of the reaction picker (05), in the order they appear. */
enum class Reaction(val key: String, val label: String, val icon: Int, val color: Int) {
    LIKE("like", "Like", R.drawable.react_like, R.color.teal),
    LOVE("love", "Love", R.drawable.react_love, R.color.love_red),
    HAHA("haha", "Haha", R.drawable.react_haha, R.color.gold),
    WOW("wow", "Wow", R.drawable.react_wow, R.color.gold),
    SAD("sad", "Sad", R.drawable.react_sad, R.color.gold),
    ANGRY("angry", "Angry", R.drawable.react_angry, R.color.badge_red);

    companion object {
        fun of(key: String?): Reaction? = entries.firstOrNull { it.key == key }

        /** The (up to three) most used reactions on a post, most popular first. */
        fun top(reactions: Map<String, String>): List<Reaction> =
            reactions.values.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.mapNotNull { of(it.key) }.take(3)
    }
}

/**
 * Posts, reactions and comments through the web services, cached in SQLite (posts / comments
 * tables). Every change is shown at once and queued in [Sync], so it also works offline.
 */
object Posts {

    private val me get() = Db.uid.orEmpty()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()

    init {
        Sync.addListener { done ->
            when (done.type) {
                "post" -> {
                    LocalDb.deleteItem("posts", Sync.localId(done.clientId))
                    done.response?.optJSONObject("post")?.let(::store)
                }
                "react", "share" -> done.response?.optJSONObject("post")?.let(::store)
                "comment" -> {
                    LocalDb.deleteItem("comments", Sync.localId(done.clientId))
                    done.response?.optJSONObject("comment")?.let(::storeComment)
                }
                "commentlike" -> done.response?.optJSONObject("comment")?.let(::storeComment)
                else -> return@addListener
            }
            changed()
        }
    }

    // ---- reading ----------------------------------------------------------------------------

    /** The cached feed (mine + friends', newest first), including posts not synced yet. */
    fun cachedFeed(): List<Post> = LocalDb.items("posts", newestFirst = true, limit = 100).map { it.toPost() }

    fun cached(id: String): Post? = LocalDb.item("posts", id)?.toPost()

    /** Fetches the feed from the server into SQLite. [done] gets false when offline. */
    fun refreshFeed(done: (Boolean) -> Unit = {}) {
        Api.get("posts/feed.php") { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get done(false)
            val items = json.optJSONArray("posts").mapObjects { it.toItem() }
            LocalDb.replaceItems("posts", null, items)
            dropSyncedCopies("posts", json.optJSONArray("posts").mapObjects { it })
            done(true)
        }
    }

    /** One post: cached first, then from the server. */
    fun load(id: String, onPost: (Post?) -> Unit) {
        cached(id)?.let(onPost)
        if (id.startsWith(Sync.LOCAL_PREFIX)) return
        Api.get("posts/get.php", mapOf("id" to id)) { result ->
            when {
                result.ok -> result.json?.optJSONObject("post")?.let { store(it); onPost(cached(id)) }
                result.code == 404 -> onPost(null)
            }
        }
    }

    /** Someone's posts (for the Photos row on their profile). */
    fun byAuthor(uid: String, onPosts: (List<Post>) -> Unit) {
        LocalDb.cache("posts:$uid")?.let { onPosts(JSONObject(it).optJSONArray("posts").mapObjects { p -> p.toPost() }) }
        Api.get("posts/get.php", mapOf("author" to uid)) { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get
            LocalDb.putCache("posts:$uid", json.toString())
            onPosts(json.optJSONArray("posts").mapObjects { it.toPost() })
        }
    }

    fun cachedComments(postId: String): List<Comment> = LocalDb.items("comments", postId).map { it.toComment() }

    fun refreshComments(postId: String, done: (Boolean) -> Unit = {}) {
        if (postId.startsWith(Sync.LOCAL_PREFIX)) return done(false)
        Api.get("comments/list.php", mapOf("postId" to postId)) { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get done(false)
            LocalDb.replaceItems("comments", postId, json.optJSONArray("comments").mapObjects { it.toCommentItem() })
            done(true)
        }
    }

    /** Calls [onChange] whenever a post or comment changes locally or syncs. Stop it in onDestroy. */
    fun watch(onChange: () -> Unit): Watch {
        listeners += onChange
        return Watch().onStop { listeners -= onChange }
    }

    private fun changed() = listeners.toList().forEach { it() }

    // ---- writing ----------------------------------------------------------------------------

    /**
     * Creates a post. The photos are compressed into the [Outbox] off the main thread, the post
     * shows in the feed straight away as pending, and [Sync] uploads it (now or once back online).
     */
    fun create(context: Context, text: String, photos: List<Uri>, bgColor: String, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val files = photos.mapNotNull { Outbox.photo(app, it, POST_IMAGE_SIZE) }
            main.post {
                if (files.size < photos.size) return@post done(false)
                val clientId = Sync.newClientId()
                val local = Post(
                    id = Sync.localId(clientId), authorId = me, text = text,
                    bgColor = if (files.isEmpty()) bgColor else "", images = files.map { it.absolutePath },
                    timestamp = System.currentTimeMillis(), pending = true,
                )
                save(local)
                Sync.enqueue(
                    "post", "posts/create.php", mapOf("text" to text, "bgColor" to bgColor), target = local.id,
                    files = files.withIndex().associate { (i, f) -> "image$i" to f }, clientId = clientId,
                )
                changed()
                done(true)
            }
        }
    }

    /** One reaction per user per post: picking a new one replaces it, picking the same one removes it. */
    fun react(post: Post, reaction: Reaction?) {
        val current = post.reactions[me]
        val newKey = if (reaction == null || reaction.key == current) null else reaction.key
        val reactions = post.reactions.toMutableMap().apply { if (newKey == null) remove(me) else put(me, newKey) }
        save(post.copy(reactions = reactions))
        Sync.enqueue("react", "posts/react.php", mapOf("postId" to Sync.ref(post.id), "type" to (newKey ?: "")), target = post.id)
        changed()
    }

    fun addComment(context: Context, post: Post, text: String, photo: Uri?, parent: Comment?, replyToName: String) {
        val app = context.applicationContext
        worker.execute {
            val file = photo?.let { Outbox.photo(app, it, POST_IMAGE_SIZE) }
            main.post {
                val clientId = Sync.newClientId()
                val parentId = parent?.let { it.parentId.ifEmpty { it.id } } ?: ""
                val local = Comment(
                    id = Sync.localId(clientId), postId = post.id, authorId = me, text = text,
                    image = file?.absolutePath.orEmpty(), parentId = parentId, replyToName = replyToName,
                    timestamp = System.currentTimeMillis(), pending = true,
                )
                saveComment(local)
                save(post.copy(commentCount = post.commentCount + 1))
                Sync.enqueue(
                    "comment", "comments/add.php",
                    mapOf("postId" to Sync.ref(post.id), "text" to text, "parentId" to Sync.ref(parentId), "replyToName" to replyToName),
                    target = post.id, files = file?.let { mapOf("image" to it) }.orEmpty(), clientId = clientId,
                )
                changed()
            }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val liked = comment.likes.containsKey(me)
        val likes = comment.likes.toMutableMap().apply { if (liked) remove(me) else put(me, true) }
        saveComment(comment.copy(likes = likes))
        Sync.enqueue("commentlike", "comments/like.php", mapOf("commentId" to Sync.ref(comment.id), "like" to if (liked) "0" else "1"), target = comment.postId)
        changed()
    }

    // ---- text helpers -----------------------------------------------------------------------

    /** Text under a post: "You and 4 others", "Aisha and 1.2K others", "Aisha". */
    fun reactionSummary(post: Post, names: Map<String, String>): String {
        val count = post.reactions.size
        if (count == 0) return ""
        val first = when {
            post.reactions.containsKey(Db.uid) -> "You"
            else -> post.reactions.keys.firstNotNullOfOrNull { names[it] } ?: return compact(count)
        }
        return if (count == 1) first else "$first and ${compact(count - 1)} ${if (count == 2) "other" else "others"}"
    }

    /** 1250 -> "1.2K" */
    fun compact(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f).replace(".0M", "M")
        n >= 1_000 -> String.format("%.1fK", n / 1_000f).replace(".0K", "K")
        else -> n.toString()
    }

    fun commentsLine(post: Post): String {
        val parts = mutableListOf<String>()
        if (post.commentCount > 0) parts += "${compact(post.commentCount)} ${if (post.commentCount == 1) "comment" else "comments"}"
        if (post.shareCount > 0) parts += "${compact(post.shareCount)} ${if (post.shareCount == 1) "share" else "shares"}"
        return parts.joinToString(" · ")
    }

    // ---- JSON <-> SQLite --------------------------------------------------------------------

    private const val POST_IMAGE_SIZE = 1080

    /** Server rows created from this phone replace their local "Sending…" copies (matched by client UUID). */
    fun dropSyncedCopies(table: String, rows: List<JSONObject>) =
        rows.mapNotNull { it.optString("clientId").takeIf { id -> id.isNotEmpty() } }
            .forEach { LocalDb.deleteItem(table, Sync.localId(it)) }

    private fun store(json: JSONObject) = json.toItem().let { LocalDb.putItem("posts", it.id, it.parent, it.sort, it.json) }
    private fun storeComment(json: JSONObject) = json.toCommentItem().let { LocalDb.putItem("comments", it.id, it.parent, it.sort, it.json) }

    private fun save(post: Post) {
        val existing = LocalDb.item("posts", post.id)
        LocalDb.putItem("posts", post.id, post.authorId, post.timestamp, post.toJson(), pending = existing?.pending ?: post.pending)
    }

    private fun saveComment(comment: Comment) {
        val existing = LocalDb.item("comments", comment.id)
        LocalDb.putItem("comments", comment.id, comment.postId, comment.timestamp, comment.toJson(), pending = existing?.pending ?: comment.pending)
    }

    private fun JSONObject.toItem() = LocalDb.Item(optString("id"), optString("authorId"), optLong("timestamp"), false, this)
    private fun JSONObject.toCommentItem() = LocalDb.Item(optString("id"), optString("postId"), optLong("timestamp"), false, this)

    private fun LocalDb.Item.toPost() = json.toPost().copy(pending = pending)
    private fun LocalDb.Item.toComment() = json.toComment().copy(pending = pending)

    fun JSONObject.toPost() = Post(
        id = optString("id"), authorId = optString("authorId"), text = optString("text"), bgColor = optString("bgColor"),
        images = optJSONArray("images")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        reactions = optJSONObject("reactions").toStringMap(), commentCount = optInt("commentCount"),
        shareCount = optInt("shareCount"), timestamp = optLong("timestamp"),
    )

    private fun JSONObject.toComment() = Comment(
        id = optString("id"), postId = optString("postId"), authorId = optString("authorId"), text = optString("text"),
        image = optString("image"), parentId = optString("parentId"), replyToName = optString("replyToName"),
        likes = optJSONObject("likes").toStringMap().mapValues { true }, timestamp = optLong("timestamp"),
    )

    private fun Post.toJson() = JSONObject()
        .put("id", id).put("authorId", authorId).put("text", text).put("bgColor", bgColor)
        .put("images", org.json.JSONArray(images)).put("reactions", JSONObject(reactions as Map<*, *>))
        .put("commentCount", commentCount).put("shareCount", shareCount).put("timestamp", timestamp)

    private fun Comment.toJson() = JSONObject()
        .put("id", id).put("postId", postId).put("authorId", authorId).put("text", text).put("image", image)
        .put("parentId", parentId).put("replyToName", replyToName).put("likes", JSONObject(likes as Map<*, *>))
        .put("timestamp", timestamp)
}

/** A JSON object of string values (or anything) as a Kotlin map of strings. */
fun JSONObject?.toStringMap(): Map<String, String> =
    this?.keys()?.asSequence()?.associateWith { optString(it) }.orEmpty()
