package com.kinnect.i230577_i230839

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
 * 15 · Your profile — name, bio, photos and friend count from the web service (users/get.php),
 * shown from the SQLite cache first so it opens instantly and offline. The camera buttons upload a
 * new cover photo or profile picture; "Edit profile" opens Edit profile.
 */
class ProfileActivity : BaseActivity() {

    private var friendsWatch: Watch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tapCloses(R.id.btn_back)
        tapOpens(EditProfileActivity::class.java, R.id.btn_edit_profile)
        tapOpens(CameraActivity::class.java, R.id.btn_add_story)
        tapOpens(SearchActivity::class.java, R.id.btn_find_friends)
        onTap(R.id.btn_edit_avatar) { pickImageFile(maxSide = 600) { upload(avatar = it) } }
        onTap(R.id.btn_edit_cover) { pickImageFile { upload(cover = it) } }
        // Placeholder detail rows from the design that Kinnect does not store yet
        listOf(R.id.profile_work, R.id.profile_school, R.id.profile_lives).forEach {
            (findViewById<View>(it).parent as View).visibility = View.GONE
        }

        friendsWatch = Friends.watchAll { showFriends() }
    }

    override fun onResume() {
        super.onResume()
        val me = Session.uid ?: return
        Users.profile(me) { profile -> showUser(profile.user) }
        showFriends()
        Friends.refresh { showFriends() }
    }

    private fun upload(avatar: java.io.File? = null, cover: java.io.File? = null) {
        toast("Uploading…")
        Users.update(emptyMap(), avatar, cover) { user, error ->
            if (user != null) showUser(user) else toast(error ?: "Could not upload the photo")
        }
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

    private fun showFriends() {
        val uids = LocalDb.links("friends").map { it.userId }
        findViewById<TextView>(R.id.profile_friends_count).text =
            HtmlCompat.fromHtml("<b>${uids.size}</b> friends", HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<TextView>(R.id.profile_friends_label).text = "${uids.size} friends"
        showFriendTiles(this, uids, listOf(R.id.friend_tile_1, R.id.friend_tile_2, R.id.friend_tile_3))
    }

    override fun onDestroy() {
        friendsWatch?.stop()
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
                val user = LocalDb.user(uid) ?: return@forEachIndexed
                tile.text = user.initials
                tile.backgroundTintList = ColorStateList.valueOf(Avatars.colorFor(activity, user.uid))
            }
        }
    }
}
