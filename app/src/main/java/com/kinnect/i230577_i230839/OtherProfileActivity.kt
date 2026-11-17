package com.kinnect.i230577_i230839

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat

/**
 * 17 · Another user's profile (opened with [EXTRA_UID]).
 * Shows their profile, friend count and mutual friends from the web service (cached in SQLite).
 * The friend button follows Add friend → Requested → Friends; a change made offline shows
 * immediately and is synced later. Message opens the chat with them.
 */
class OtherProfileActivity : BaseActivity() {

    private var stateWatch: Watch? = null
    private var state = FriendState.NONE
    private var user = User()
    private val uid by lazy { intent.getStringExtra(EXTRA_UID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_profile)

        tapCloses(R.id.btn_back)
        listOf(R.id.other_work, R.id.other_lives).forEach {
            (findViewById<View>(it).parent as View).visibility = View.GONE
        }
        if (uid.isEmpty() || Session.uid == null) { finish(); return }

        onTap(R.id.btn_add_friend) { onFriendButton() }
        onTap(R.id.btn_message) {
            startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_UID, uid))
        }

        stateWatch = Friends.watchState(uid, ::showState)
        showPhotos(emptyList())
    }

    override fun onResume() {
        super.onResume()
        Users.profile(uid) { profile ->
            showUser(profile.user)
            showCounts(profile)
            showState(Friends.state(uid))
        }
    }

    /** The Photos row shows the first photo of their three newest photo posts; tapping one opens it. */
    private fun showPhotos(posts: List<Post>) {
        val header = findViewById<View>(R.id.other_photos_title).parent as View
        val slots = listOf(R.id.other_photo_1, R.id.other_photo_2, R.id.other_photo_3)
            .map { findViewById<ImageView>(it) }
        val grid = (slots[0].parent as View).parent as View
        header.visibility = if (posts.isEmpty()) View.GONE else View.VISIBLE
        grid.visibility = header.visibility

        slots.forEachIndexed { index, image ->
            val card = image.parent as View
            val post = posts.getOrNull(index)
            card.visibility = if (post == null) View.INVISIBLE else View.VISIBLE
            if (post == null) return@forEachIndexed
            image.setImageDrawable(null)
            card.setOnClickListener { PostCard.openComments(this, post.id) }
            PostImages.get(post.id) { images -> images.firstOrNull()?.let { Images.show(image, it) } }
        }
    }

    private fun showUser(user: User) {
        this.user = user
        findViewById<TextView>(R.id.other_title).text = user.fullName
        findViewById<TextView>(R.id.other_name).text = user.fullName
        findViewById<TextView>(R.id.other_bio).apply {
            text = user.bio
            visibility = if (user.bio.isEmpty()) View.GONE else View.VISIBLE
        }
        Avatars.show(findViewById(R.id.other_avatar), user, keepShape = true)
        Images.show(findViewById<ImageView>(R.id.other_cover), user.coverImage)
        findViewById<TextView>(R.id.other_joined).text = ProfileActivity.joinedText(user.createdAt)
    }

    /** "<b>N</b> friends · <b>M</b> mutual" plus the "Friends with …" row and its small avatars. */
    private fun showCounts(profile: Users.Profile) {
        findViewById<TextView>(R.id.other_friends_count).text = HtmlCompat.fromHtml(
            "<b>${profile.friendCount}</b> friends · <b>${profile.mutualCount}</b> mutual",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        val text = findViewById<TextView>(R.id.other_mutual_text)
        (text.parent as View).visibility = if (profile.mutualCount == 0) View.GONE else View.VISIBLE
        val avatarIds = listOf(R.id.mutual_1, R.id.mutual_2, R.id.mutual_3)
        avatarIds.forEachIndexed { i, id ->
            val view = findViewById<TextView>(id)
            val friend = profile.mutual.getOrNull(i)
            view.visibility = if (friend == null) View.GONE else View.VISIBLE
            if (friend != null) Avatars.show(view, friend)
        }
        if (profile.mutual.isNotEmpty()) {
            text.text = mutualSentence(profile.mutual.take(2).map { it.fullName }, profile.mutualCount)
        }
    }

    private fun showState(state: FriendState) {
        this.state = state
        val (label, icon) = when (state) {
            FriendState.NONE -> "Add friend" to R.drawable.ic_person_add
            FriendState.REQUESTED -> "Requested" to R.drawable.ic_clock
            FriendState.INCOMING -> "Confirm" to R.drawable.ic_person_add
            FriendState.FRIENDS -> "Friends" to R.drawable.ic_check
        }
        // Teal for actions, grey once a request is pending or you are already friends
        val primary = state == FriendState.NONE || state == FriendState.INCOMING
        val color = ContextCompat.getColor(this, if (primary) R.color.white else R.color.text_primary)
        findViewById<View>(R.id.btn_add_friend)
            .setBackgroundResource(if (primary) R.drawable.bg_btn_primary_square else R.drawable.bg_btn_grey)
        findViewById<TextView>(R.id.add_friend_label).apply { text = label; setTextColor(color) }
        findViewById<ImageView>(R.id.add_friend_icon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(color)
        }
        // Made offline and not synced yet: show it at once, slightly faded
        findViewById<View>(R.id.btn_add_friend).alpha = if (Friends.isPending(uid)) 0.6f else 1f
    }

    private fun onFriendButton() {
        if (state != FriendState.FRIENDS) {
            Friends.toggle(uid, state)
            return
        }
        AlertDialog.Builder(this)
            .setMessage("Remove ${user.fullName} from your friends?")
            .setPositiveButton("Unfriend") { _, _ -> Friends.unfriend(uid) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        stateWatch?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_UID = "uid"

        private fun mutualSentence(names: List<String>, total: Int): CharSequence {
            val others = total - names.size
            val html = when {
                others > 0 -> "Friends with " + names.joinToString(", ") { "<b>$it</b>" } +
                    " and <b>$others other${if (others == 1) "" else "s"}</b>"
                names.size == 2 -> "Friends with <b>${names[0]}</b> and <b>${names[1]}</b>"
                else -> "Friends with <b>${names[0]}</b>"
            }
            return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }
}
