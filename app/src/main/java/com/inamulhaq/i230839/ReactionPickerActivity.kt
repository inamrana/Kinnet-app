package com.inamulhaq.i230839

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * 05 · Reaction picker — shows the post under a dim scrim with the six reactions.
 * Your current reaction is lifted with its name above it. Tapping a reaction sets it,
 * tapping your current one removes it; tapping the scrim or Back just closes.
 */
class ReactionPickerActivity : BaseActivity() {

    private val postId by lazy { intent.getStringExtra(EXTRA_POST_ID).orEmpty() }
    private val watch = Watch()
    private var post: Post? = null

    private val buttons by lazy {
        mapOf(
            Reaction.LIKE to R.id.react_like, Reaction.LOVE to R.id.react_love,
            Reaction.HAHA to R.id.react_haha, Reaction.WOW to R.id.react_wow,
            Reaction.SAD to R.id.react_sad, Reaction.ANGRY to R.id.react_angry,
        ).mapValues { findViewById<ImageView>(it.value) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reaction_picker)
        if (postId.isEmpty() || Db.uid == null) { finish(); return }

        tapCloses(R.id.scrim, R.id.btn_back)
        buttons.forEach { (reaction, button) ->
            button.setOnClickListener {
                post?.let { Posts.react(it, reaction) }
                finish()
            }
        }

        watch.on(Db.posts.child(postId)) { snapshot ->
            val loaded = snapshot.getValue(Post::class.java)?.copy(id = postId) ?: run { finish(); return@on }
            post = loaded
            showPost(loaded)
        }
        watch.on(Db.comments.child(postId).orderByChild("timestamp").limitToFirst(1)) { snapshot ->
            val first = snapshot.children.firstOrNull()?.getValue(Comment::class.java)
            showFirstComment(first)
        }
    }

    private fun showPost(post: Post) {
        UserCache.get(post.authorId) { author ->
            Avatars.show(findViewById(R.id.post_avatar), author)
            findViewById<TextView>(R.id.post_author).text = author.fullName
            findViewById<TextView>(R.id.post_header_title).text = "${author.firstName.ifEmpty { author.fullName }}'s post"
        }
        findViewById<TextView>(R.id.post_time).text = "${TimeAgo.short(post.timestamp)} · "
        findViewById<TextView>(R.id.post_text).apply {
            text = post.text
            visibility = if (post.text.isEmpty()) View.GONE else View.VISIBLE
        }
        val photo = findViewById<ImageView>(R.id.post_photo)
        photo.visibility = if (post.imageCount == 0) View.GONE else View.VISIBLE
        PostImages.get(post.id) { images -> images.firstOrNull()?.let { Images.show(photo, it) } }

        val top = Reaction.top(post.reactions)
        listOf(R.id.reaction_stack_1, R.id.reaction_stack_2, R.id.reaction_stack_3).forEachIndexed { i, id ->
            val icon = findViewById<ImageView>(id)
            icon.visibility = if (i < top.size) View.VISIBLE else View.GONE
            top.getOrNull(i)?.let { icon.setImageResource(it.icon) }
        }
        findViewById<TextView>(R.id.post_reaction_text).text = Posts.reactionSummary(post, UserCache.names())
        findViewById<TextView>(R.id.post_comment_count).text = Posts.commentsLine(post)
        highlight(Reaction.of(post.reactions[Db.uid]))
    }

    /** Lifts and enlarges your current reaction and puts its name in the tooltip above it. */
    private fun highlight(current: Reaction?) {
        buttons.forEach { (reaction, button) ->
            val chosen = reaction == current
            button.animate().scaleX(if (chosen) 1.35f else 1f).scaleY(if (chosen) 1.35f else 1f)
                .translationY(if (chosen) -dp(14f) else 0f).setDuration(150).start()
        }
        val tooltip = findViewById<TextView>(R.id.reaction_tooltip)
        val button = current?.let { buttons[it] }
        if (button == null) {
            tooltip.visibility = View.INVISIBLE
            return
        }
        tooltip.text = current.label
        val bar = findViewById<View>(R.id.reaction_bar)
        tooltip.post {
            tooltip.translationX = bar.left + button.left + button.width / 2f - tooltip.width / 2f
            tooltip.visibility = View.VISIBLE
        }
    }

    private fun showFirstComment(comment: Comment?) {
        val row = findViewById<View>(R.id.first_comment)
        findViewById<View>(R.id.sort_row).visibility = if (comment == null) View.GONE else View.VISIBLE
        row.visibility = if (comment == null) View.GONE else View.VISIBLE
        comment ?: return
        UserCache.get(comment.authorId) { author ->
            Avatars.show(findViewById(R.id.first_comment_avatar), author)
            findViewById<TextView>(R.id.first_comment_name).text = author.fullName
        }
        findViewById<TextView>(R.id.first_comment_text).text = comment.text.ifEmpty { "Sent a photo" }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_POST_ID = "postId"
    }
}
