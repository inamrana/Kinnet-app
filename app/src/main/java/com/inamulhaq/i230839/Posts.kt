package com.inamulhaq.i230839

import com.google.firebase.database.ServerValue

/** A post in posts/{postId}. Photos live separately in postImages/{postId} so the feed loads fast. */
data class Post(
    val id: String = "",
    val authorId: String = "",
    val text: String = "",
    val bgColor: String = "",
    val imageCount: Int = 0,
    val reactions: Map<String, String> = emptyMap(),
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val timestamp: Long = 0L,
)

/** A comment in comments/{postId}/{commentId}. Replies point at their top-level comment with [parentId]. */
data class Comment(
    val id: String = "",
    val authorId: String = "",
    val text: String = "",
    val image: String = "",
    val parentId: String = "",
    val replyToName: String = "",
    val likes: Map<String, Boolean> = emptyMap(),
    val timestamp: Long = 0L,
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

/** Creating posts, reacting and commenting. */
object Posts {

    private val me get() = Db.uid!!

    /** Saves a post and its photos in one write, so the feed never shows a half-made post. */
    fun create(text: String, images: List<String>, bgColor: String, done: (Boolean) -> Unit) {
        val id = Db.posts.push().key ?: return done(false)
        val updates = mutableMapOf<String, Any?>(
            "posts/$id/authorId" to me,
            "posts/$id/text" to text,
            "posts/$id/bgColor" to if (images.isEmpty()) bgColor else "",
            "posts/$id/imageCount" to images.size,
            "posts/$id/commentCount" to 0,
            "posts/$id/shareCount" to 0,
            "posts/$id/timestamp" to ServerValue.TIMESTAMP,
        )
        images.forEachIndexed { index, image -> updates["postImages/$id/$index"] = image }
        Db.update(updates).addOnCompleteListener { done(it.isSuccessful) }
    }

    /**
     * One reaction per user per post: picking a new one replaces it, picking the same one removes it.
     */
    fun react(post: Post, reaction: Reaction?) {
        val current = post.reactions[me]
        val ref = Db.posts.child(post.id).child("reactions").child(me)
        if (reaction == null || reaction.key == current) {
            ref.removeValue()
        } else {
            ref.setValue(reaction.key)
            if (current == null) Notices.send(post.authorId, Notice.TYPE_REACTION, post.id, reaction.key)
        }
    }

    fun addComment(post: Post, text: String, image: String, parent: Comment?, replyToName: String) {
        val ref = Db.comments.child(post.id).push()
        val data = mapOf(
            "authorId" to me,
            "text" to text,
            "image" to image,
            "parentId" to (parent?.let { it.parentId.ifEmpty { it.id } } ?: ""),
            "replyToName" to replyToName,
            "timestamp" to ServerValue.TIMESTAMP,
        )
        Db.update(
            mapOf(
                "comments/${post.id}/${ref.key}" to data,
                "posts/${post.id}/commentCount" to ServerValue.increment(1),
            )
        )
        val preview = text.ifEmpty { "a photo" }
        if (parent != null) Notices.send(parent.authorId, Notice.TYPE_REPLY, post.id, preview)
        if (parent?.authorId != post.authorId) Notices.send(post.authorId, Notice.TYPE_COMMENT, post.id, preview)
    }

    fun toggleCommentLike(postId: String, comment: Comment) {
        val ref = Db.comments.child(postId).child(comment.id).child("likes").child(me)
        if (comment.likes.containsKey(me)) ref.removeValue() else ref.setValue(true)
    }

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
}
