package com.inamulhaq.i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout

/**
 * 04 · Home feed — live posts from you and your friends (newest first) and your friends' stories.
 * Links to Search, Chats, Create post, Comments, the reaction picker, Story viewer, your Profile
 * and (through "Create story") the Camera.
 */
class HomeActivity : BaseActivity() {

    private val watch = Watch()
    private lateinit var feed: LinearLayout

    private var friendIds = setOf<String>()
    private var posts = listOf<Post>()
    private val cards = hashMapOf<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setupTopTabs(selected = 0)
        feed = findViewById(R.id.feed_list)

        tapOpens(ProfileActivity::class.java, R.id.composer_avatar)
        tapOpens(CreatePostActivity::class.java, R.id.composer_input, R.id.composer_photo)
        tapOpens(CameraActivity::class.java, R.id.story_create)

        val me = Db.uid ?: return
        watch.on(Db.user(me)) { snapshot ->
            val user = snapshot.getValue(User::class.java) ?: return@on
            UserCache.put(user)
            Avatars.show(findViewById(R.id.composer_avatar), user)
            Avatars.show(findViewById(R.id.story_create_avatar), user)
            findViewById<android.widget.TextView>(R.id.composer_input).text =
                "What's on your mind, ${user.firstName.ifEmpty { user.fullName }}?"
        }
        watch.on(Db.friends.child(me)) { snapshot ->
            friendIds = snapshot.children.mapNotNull { it.key }.toSet()
            showFeed()
        }
        watch.on(Db.posts.orderByChild("timestamp").limitToLast(MAX_POSTS)) { snapshot ->
            posts = snapshot.children.mapNotNull { child ->
                child.getValue(Post::class.java)?.copy(id = child.key.orEmpty())
            }
            showFeed()
        }
    }

    /** Your posts and your friends' posts, newest first. Existing cards are rebound, not recreated. */
    private fun showFeed() {
        val me = Db.uid ?: return
        val visible = posts.filter { it.authorId == me || it.authorId in friendIds }.sortedByDescending { it.timestamp }
        feed.removeAllViews()
        visible.forEach { post ->
            val card = cards.getOrPut(post.id) {
                LayoutInflater.from(this).inflate(R.layout.item_post, feed, false)
            }
            PostCard.bind(card, post)
            feed.addView(card)
        }
        cards.keys.retainAll(visible.map { it.id }.toSet())
        findViewById<View>(R.id.feed_empty).visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    private companion object {
        const val MAX_POSTS = 100
    }
}
