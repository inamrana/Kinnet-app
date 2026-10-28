package com.inamulhaq.i230839

import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat

/**
 * 06 · Comments — live comments on a post. Replies sit under their comment with the curved
 * connector, comments can be liked, and a comment can carry a photo. "Replying to … / Cancel"
 * shows while answering someone.
 */
class CommentsActivity : BaseActivity() {

    private val postId by lazy { intent.getStringExtra(EXTRA_POST_ID).orEmpty() }
    private val watch = Watch()
    private var post: Post? = null
    private var comments = listOf<Comment>()
    private var replyTarget: Comment? = null
    private var photo = ""
    private var firstLoad = true

    private lateinit var list: LinearLayout
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)
        if (postId.isEmpty() || Db.uid == null) { finish(); return }

        list = findViewById(R.id.comments_list)
        input = findViewById(R.id.input_comment)
        tapCloses(R.id.btn_back)
        onTap(R.id.reply_cancel) { setReplyTarget(null) }
        onTap(R.id.btn_send_comment) { send() }
        onTap(R.id.btn_comment_photo) { pickImage { attachPhoto(it) } }
        onTap(R.id.comment_photo_remove) { attachPhoto("") }
        onTap(R.id.comments_reactions) {
            startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra(ReactionPickerActivity.EXTRA_POST_ID, postId))
        }

        watch.on(Db.posts.child(postId)) { snapshot ->
            post = snapshot.getValue(Post::class.java)?.copy(id = postId)
            post?.let(::showReactions)
        }
        watch.on(Db.comments.child(postId).orderByChild("timestamp")) { snapshot ->
            comments = snapshot.children.mapNotNull { child ->
                child.getValue(Comment::class.java)?.copy(id = child.key.orEmpty())
            }
            showComments()
        }
    }

    private fun showReactions(post: Post) {
        val top = Reaction.top(post.reactions)
        listOf(R.id.reaction_stack_1, R.id.reaction_stack_2, R.id.reaction_stack_3).forEachIndexed { i, id ->
            val icon = findViewById<ImageView>(id)
            icon.visibility = if (i < top.size) View.VISIBLE else View.GONE
            top.getOrNull(i)?.let { icon.setImageResource(it.icon) }
        }
        findViewById<TextView>(R.id.comments_reaction_count).text =
            if (post.reactions.isEmpty()) "Be the first to react" else Posts.compact(post.reactions.size)
    }

    /** Top-level comments oldest first, each followed by its replies. */
    private fun showComments() {
        val replies = comments.filter { it.parentId.isNotEmpty() }.groupBy { it.parentId }
        list.removeAllViews()
        comments.filter { it.parentId.isEmpty() }.forEach { comment ->
            list.addView(row(comment, isReply = false))
            replies[comment.id].orEmpty().forEach { list.addView(row(it, isReply = true)) }
        }
        findViewById<View>(R.id.comments_empty).visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        if (!firstLoad) {
            findViewById<ScrollView>(R.id.comments_scroll).post {
                findViewById<ScrollView>(R.id.comments_scroll).fullScroll(View.FOCUS_DOWN)
            }
        }
        firstLoad = false
    }

    private fun row(comment: Comment, isReply: Boolean): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_comment, list, false)
        val me = Db.uid.orEmpty()
        val avatar = view.findViewById<TextView>(R.id.comment_avatar)
        if (isReply) {
            view.setPadding(dp(33), view.paddingTop, view.paddingRight, view.paddingBottom)
            (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(10)
            view.findViewById<View>(R.id.comment_elbow).visibility = View.VISIBLE
            avatar.layoutParams = avatar.layoutParams.apply { width = dp(30); height = dp(30) }
            (avatar.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(2)
            avatar.textSize = 11f
        }
        UserCache.get(comment.authorId) { author ->
            Avatars.show(avatar, author)
            view.findViewById<TextView>(R.id.comment_name).text = author.fullName
        }
        avatar.setOnClickListener { PersonRow.openProfile(view, comment.authorId) }

        val text = view.findViewById<TextView>(R.id.comment_text)
        text.text = withMention(comment)
        text.visibility = if (text.text.isEmpty()) View.GONE else View.VISIBLE
        if (comment.image.isNotEmpty()) {
            view.findViewById<View>(R.id.comment_image_card).visibility = View.VISIBLE
            Images.show(view.findViewById(R.id.comment_image), comment.image)
        }

        val likes = comment.likes.size
        val chip = view.findViewById<View>(R.id.comment_like_chip)
        if (likes > 0) {
            chip.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.comment_like_count).text = likes.toString()
            (view.findViewById<View>(R.id.comment_bubble).layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginEnd = dp(18)
                bottomMargin = dp(10)
            }
        }
        view.findViewById<TextView>(R.id.comment_time).text = TimeAgo.short(comment.timestamp)
        view.findViewById<TextView>(R.id.comment_like).apply {
            setTextColor(ContextCompat.getColor(context, if (comment.likes.containsKey(me)) R.color.teal else R.color.text_secondary))
            setOnClickListener { Posts.toggleCommentLike(postId, comment) }
        }
        view.findViewById<View>(R.id.comment_reply).setOnClickListener { setReplyTarget(comment) }
        return view
    }

    /** "@Lina Marsh next time…" with the mention in bold teal, as in the design. */
    private fun withMention(comment: Comment): CharSequence {
        if (comment.replyToName.isEmpty()) return comment.text
        val mention = "@${comment.replyToName}"
        return SpannableStringBuilder("$mention ${comment.text}").apply {
            setSpan(ForegroundColorSpan(ContextCompat.getColor(this@CommentsActivity, R.color.teal)), 0, mention.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, mention.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun setReplyTarget(comment: Comment?) {
        replyTarget = comment
        val bar = findViewById<View>(R.id.reply_bar)
        if (comment == null) {
            bar.visibility = View.GONE
            input.hint = "Write a comment…"
            return
        }
        UserCache.get(comment.authorId) { author ->
            findViewById<TextView>(R.id.reply_title).text = HtmlCompat.fromHtml(
                "Replying to <b>${android.text.TextUtils.htmlEncode(author.fullName)}</b>", HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }
        bar.visibility = View.VISIBLE
        input.hint = "Write a reply…"
        input.requestFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun attachPhoto(encoded: String) {
        photo = encoded
        val preview = findViewById<View>(R.id.comment_photo_preview)
        preview.visibility = if (encoded.isEmpty()) View.GONE else View.VISIBLE
        if (encoded.isNotEmpty()) Images.show(findViewById(R.id.comment_photo_image), encoded)
    }

    private fun send() {
        val current = post ?: return
        val text = input.text.toString().trim()
        if (text.isEmpty() && photo.isEmpty()) return
        val target = replyTarget
        // Replying to a reply mentions that person, like "@Lina Marsh" in the design
        val mention = if (target != null && target.parentId.isNotEmpty()) UserCache.peek(target.authorId)?.fullName.orEmpty() else ""
        Posts.addComment(current, text, photo, target, mention)
        input.setText("")
        attachPhoto("")
        setReplyTarget(null)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_POST_ID = "postId"
    }
}
