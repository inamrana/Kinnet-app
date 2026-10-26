package com.inamulhaq.i230839

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 15 · Your profile — live name, bio, photos and friend count from users/{uid} and friends/{uid}.
 * The camera buttons change the cover photo and profile picture; "Edit profile" opens Edit profile.
 */
class ProfileActivity : BaseActivity() {

    private val watch = Watch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tapCloses(R.id.btn_back)
        tapOpens(EditProfileActivity::class.java, R.id.btn_edit_profile)
        tapOpens(CameraActivity::class.java, R.id.btn_add_story)
        tapOpens(SearchActivity::class.java, R.id.btn_find_friends)
        onTap(R.id.btn_edit_avatar) { pickImage(maxSide = 400) { Db.me()?.child("profileImage")?.setValue(it) } }
        onTap(R.id.btn_edit_cover) { pickImage { Db.me()?.child("coverImage")?.setValue(it) } }
        // Placeholder detail rows from the design that Kinnect does not store yet
        listOf(R.id.profile_work, R.id.profile_school, R.id.profile_lives).forEach {
            (findViewById<View>(it).parent as View).visibility = View.GONE
        }

        val me = Db.me() ?: return
        watch.on(me) { snapshot -> snapshot.getValue(User::class.java)?.let(::showUser) }
        watch.on(Db.friends.child(me.key!!)) { snapshot -> showFriends(snapshot.children.mapNotNull { it.key }) }
    }

    private fun showUser(user: User) {
        findViewById<TextView>(R.id.profile_title).text = user.fullName
        findViewById<TextView>(R.id.profile_name).text = user.fullName
        findViewById<TextView>(R.id.profile_bio).apply {
            text = user.bio
            visibility = if (user.bio.isEmpty()) View.GONE else View.VISIBLE
        }
        Avatars.show(findViewById(R.id.profile_avatar), user, keepShape = true)
        Images.show(findViewById<ImageView>(R.id.profile_cover), user.coverImage)
        findViewById<TextView>(R.id.profile_joined).text = joinedText(user.createdAt)
    }

    private fun showFriends(uids: List<String>) {
        findViewById<TextView>(R.id.profile_friends_count).text =
            HtmlCompat.fromHtml("<b>${uids.size}</b> friends", HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<TextView>(R.id.profile_friends_label).text = "${uids.size} friends"
        showFriendTiles(this, uids, listOf(R.id.friend_tile_1, R.id.friend_tile_2, R.id.friend_tile_3))
    }

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    companion object {
        fun joinedText(createdAt: Long): String =
            if (createdAt == 0L) "Joined Kinnect"
            else "Joined " + SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(createdAt))

        /** Fills the rounded friend tiles with the first friends; unused tiles stay as empty space. */
        fun showFriendTiles(activity: BaseActivity, uids: List<String>, tileIds: List<Int>) {
            tileIds.forEachIndexed { index, id ->
                val tile = activity.findViewById<TextView>(id)
                val uid = uids.getOrNull(index)
                tile.visibility = if (uid == null) View.INVISIBLE else View.VISIBLE
                if (uid == null) return@forEachIndexed
                tile.setOnClickListener { PersonRow.openProfile(tile, uid) }
                Db.user(uid).get().addOnSuccessListener { snap ->
                    val user = snap.getValue(User::class.java) ?: return@addOnSuccessListener
                    tile.text = user.initials
                    tile.backgroundTintList = ColorStateList.valueOf(Avatars.colorFor(activity, user.uid))
                }
            }
        }
    }
}
