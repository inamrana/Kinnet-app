package com.inamulhaq.i230839

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Users read once and kept in memory, for names and avatars in feeds and comments. */
object UserCache {
    private val users = hashMapOf<String, User>()
    private val waiting = hashMapOf<String, MutableList<(User) -> Unit>>()

    fun peek(uid: String): User? = users[uid]

    fun get(uid: String, done: (User) -> Unit) {
        users[uid]?.let { done(it); return }
        val queue = waiting.getOrPut(uid) { mutableListOf() }
        queue += done
        if (queue.size > 1) return
        Db.user(uid).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java) ?: User(uid = uid, fullName = "Kinnect user")
            put(user)
            waiting.remove(uid)?.forEach { it(user) }
        }.addOnFailureListener { waiting.remove(uid) }
    }

    fun put(user: User) {
        users[user.uid] = user
    }

    fun names(): Map<String, String> = users.mapValues { it.value.firstName.ifEmpty { it.value.fullName } }
}

/** Photos of posts, loaded once per post from postImages/{postId}. */
object PostImages {
    private val loaded = hashMapOf<String, List<String>>()

    fun get(postId: String, done: (List<String>) -> Unit) {
        loaded[postId]?.let { done(it); return }
        Db.postImages.child(postId).get().addOnSuccessListener { snapshot ->
            val images = snapshot.children.sortedBy { it.key?.toIntOrNull() ?: 0 }
                .mapNotNull { it.getValue(String::class.java) }
            loaded[postId] = images
            done(images)
        }
    }
}

/** Fills an item_post view (Home feed, Profile) with a live [Post]. */
object PostCard {

    fun bind(view: View, post: Post) {
        val activity = view.context as Activity
        val me = Db.uid.orEmpty()

        UserCache.get(post.authorId) { author ->
            Avatars.show(view.findViewById(R.id.post_avatar), author)
            view.findViewById<TextView>(R.id.post_author).text = author.fullName
        }
        view.findViewById<TextView>(R.id.post_time).text = "${TimeAgo.short(post.timestamp)} · "
        val openAuthor = View.OnClickListener { PersonRow.openProfile(view, post.authorId) }
        view.findViewById<View>(R.id.post_avatar).setOnClickListener(openAuthor)
        view.findViewById<View>(R.id.post_author_block).setOnClickListener(openAuthor)

        bindText(view, post)
        bindPhotos(view, post)
        bindReactions(view, post, me)

        val openComments = View.OnClickListener { openComments(activity, post.id) }
        view.findViewById<View>(R.id.action_comment).setOnClickListener(openComments)
        view.findViewById<View>(R.id.post_comment_count).setOnClickListener(openComments)
        view.findViewById<View>(R.id.post_photos).setOnClickListener(openComments)
        view.findViewById<View>(R.id.action_share).setOnClickListener {
            activity.startActivity(
                Intent(activity, ChatsActivity::class.java).putExtra(ChatsActivity.EXTRA_SHARE_POST, post.id)
            )
        }
        view.findViewById<View>(R.id.post_more).visibility = View.GONE
        view.findViewById<View>(R.id.post_hide).setOnClickListener { view.visibility = View.GONE }
    }

    private fun bindText(view: View, post: Post) {
        val plain = view.findViewById<TextView>(R.id.post_text)
        val coloured = view.findViewById<TextView>(R.id.post_bg_text)
        if (post.bgColor.isNotEmpty() && post.imageCount == 0) {
            plain.visibility = View.GONE
            coloured.visibility = View.VISIBLE
            coloured.text = post.text
            coloured.setBackgroundColor(runCatching { Color.parseColor(post.bgColor) }.getOrDefault(Color.DKGRAY))
        } else {
            coloured.visibility = View.GONE
            plain.visibility = if (post.text.isEmpty()) View.GONE else View.VISIBLE
            plain.text = post.text
        }
    }

    /** 1 photo fills the width, 2 sit side by side, 3+ use the big + two stacked layout with "+N". */
    private fun bindPhotos(view: View, post: Post) {
        val grid = view.findViewById<View>(R.id.post_photos)
        if (post.imageCount == 0) {
            grid.visibility = View.GONE
            return
        }
        grid.visibility = View.VISIBLE
        val a = view.findViewById<ImageView>(R.id.photo_a)
        val b = view.findViewById<ImageView>(R.id.photo_b)
        val c = view.findViewById<ImageView>(R.id.photo_c)
        view.findViewById<View>(R.id.photo_right).visibility = if (post.imageCount >= 2) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.photo_c_frame).visibility = if (post.imageCount >= 3) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.photo_more).apply {
            visibility = if (post.imageCount > 3) View.VISIBLE else View.GONE
            text = "+${post.imageCount - 3}"
        }
        val tag = post.id
        grid.tag = tag
        PostImages.get(post.id) { images ->
            if (grid.tag != tag) return@get
            images.getOrNull(0)?.let { Images.show(a, it) }
            images.getOrNull(1)?.let { Images.show(b, it) }
            images.getOrNull(2)?.let { Images.show(c, it) }
        }
    }

    private fun bindReactions(view: View, post: Post, me: String) {
        val top = Reaction.top(post.reactions)
        val stackIds = listOf(R.id.reaction_stack_1, R.id.reaction_stack_2, R.id.reaction_stack_3)
        stackIds.forEachIndexed { index, id ->
            val icon = view.findViewById<ImageView>(id)
            val reaction = top.getOrNull(index)
            icon.visibility = if (reaction == null) View.GONE else View.VISIBLE
            reaction?.let { icon.setImageResource(it.icon) }
        }
        val names = UserCache.names()
        post.reactions.keys.filter { UserCache.peek(it) == null }.take(1).forEach { uid ->
            UserCache.get(uid) { view.findViewById<TextView>(R.id.post_reaction_text).text = Posts.reactionSummary(post, UserCache.names()) }
        }
        view.findViewById<TextView>(R.id.post_reaction_text).text = Posts.reactionSummary(post, names)
        view.findViewById<TextView>(R.id.post_comment_count).text = Posts.commentsLine(post)
        view.findViewById<View>(R.id.post_summary).visibility =
            if (post.reactions.isEmpty() && post.commentCount == 0 && post.shareCount == 0) View.GONE else View.VISIBLE

        // Like button shows my current reaction in its colour
        val mine = Reaction.of(post.reactions[me])
        val context = view.context
        val label = view.findViewById<TextView>(R.id.action_like_label)
        val icon = view.findViewById<ImageView>(R.id.action_like_icon)
        if (mine == null) {
            label.text = "Like"
            label.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            icon.setImageResource(R.drawable.ic_like)
            icon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
        } else {
            label.text = mine.label
            label.setTextColor(ContextCompat.getColor(context, mine.color))
            if (mine == Reaction.LIKE) {
                icon.setImageResource(R.drawable.ic_like)
                icon.setColorFilter(ContextCompat.getColor(context, R.color.teal))
            } else {
                icon.clearColorFilter()
                icon.setImageResource(mine.icon)
            }
        }
        val like = view.findViewById<View>(R.id.action_like)
        // Tap: like / remove my reaction. Long press: open the reaction picker.
        like.setOnClickListener { Posts.react(post, if (mine == null) Reaction.LIKE else null) }
        like.setOnLongClickListener {
            context.startActivity(
                Intent(context, ReactionPickerActivity::class.java).putExtra(ReactionPickerActivity.EXTRA_POST_ID, post.id)
            )
            true
        }
    }

    fun openComments(activity: Activity, postId: String) {
        activity.startActivity(Intent(activity, CommentsActivity::class.java).putExtra(CommentsActivity.EXTRA_POST_ID, postId))
    }
}
