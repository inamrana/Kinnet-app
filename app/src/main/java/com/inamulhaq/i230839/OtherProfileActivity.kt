package com.inamulhaq.i230839

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
 * Shows their live profile, friend count and mutual friends; the friend button follows
 * Add friend → Requested → Friends, and Message opens the chat with them.
 */
class OtherProfileActivity : BaseActivity() {

    private val watch = Watch()
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
        val me = Db.uid
        if (uid.isEmpty() || me == null) { finish(); return }

        onTap(R.id.btn_add_friend) { onFriendButton() }
        onTap(R.id.btn_message) {
            startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_UID, uid))
        }

        watch.on(Db.user(uid)) { snapshot -> snapshot.getValue(User::class.java)?.let(::showUser) }
        watch.on(Db.friends.child(uid)) { snapshot -> showCounts(me, snapshot.children.mapNotNull { it.key }) }
        stateWatch = Friends.watchState(uid, ::showState)
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
    private fun showCounts(me: String, theirFriends: List<String>) {
        Db.friends.child(me).get().addOnSuccessListener { mine ->
            val myFriends = mine.children.mapNotNull { it.key }.toSet()
            val mutual = theirFriends.filter { it in myFriends }
            findViewById<TextView>(R.id.other_friends_count).text = HtmlCompat.fromHtml(
                "<b>${theirFriends.size}</b> friends · <b>${mutual.size}</b> mutual",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            showMutual(mutual)
        }
    }

    private fun showMutual(mutual: List<String>) {
        val text = findViewById<TextView>(R.id.other_mutual_text)
        (text.parent as View).visibility = if (mutual.isEmpty()) View.GONE else View.VISIBLE
        val avatarIds = listOf(R.id.mutual_1, R.id.mutual_2, R.id.mutual_3)
        avatarIds.forEachIndexed { i, id -> findViewById<View>(id).visibility = if (i < mutual.size) View.VISIBLE else View.GONE }
        if (mutual.isEmpty()) return

        val names = arrayOfNulls<String>(minOf(2, mutual.size))
        mutual.take(3).forEachIndexed { index, friendUid ->
            Db.user(friendUid).get().addOnSuccessListener { snap ->
                val friend = snap.getValue(User::class.java) ?: return@addOnSuccessListener
                Avatars.show(findViewById(avatarIds[index]), friend)
                if (index < names.size) {
                    names[index] = friend.fullName
                    if (names.all { it != null }) text.text = mutualSentence(names.filterNotNull(), mutual.size)
                }
            }
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
        watch.stop()
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
