package com.kinnect.i230577_i230839

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 04 · Home feed — posts from you and your friends (newest first) and your friends' stories.
 * Both are read from SQLite first, so the feed opens instantly and stays viewable offline, then
 * refreshed from the web services (and again every few seconds while the screen is open).
 * Links to Search, Chats, Create post, Comments, the reaction picker, Story viewer, your Profile
 * and (through "Create story") the Camera.
 */
class HomeActivity : BaseActivity() {

    private lateinit var feed: LinearLayout
    private lateinit var rail: LinearLayout
    private val cards = hashMapOf<String, View>()
    private val watches = mutableListOf<Watch>()
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setupTopTabs(selected = 0)
        feed = findViewById(R.id.feed_list)
        rail = findViewById(R.id.stories_rail)

        tapOpens(ProfileActivity::class.java, R.id.composer_avatar)
        tapOpens(CreatePostActivity::class.java, R.id.composer_input, R.id.composer_photo)
        tapOpens(CameraActivity::class.java, R.id.story_create)
        askForNotifications()

        watches += Posts.watch { showFeed() }
        watches += Stories.watch { showStories() }
        watches += Friends.watchAll { showFeed() }
    }

    override fun onResume() {
        super.onResume()
        Session.uid?.let { me ->
            Users.load(me) { user ->
                UserCache.put(user)
                Avatars.show(findViewById(R.id.composer_avatar), user)
                Avatars.show(findViewById(R.id.story_create_avatar), user)
                findViewById<TextView>(R.id.composer_input).text =
                    "What's on your mind, ${user.firstName.ifEmpty { user.fullName }}?"
            }
        }
        showFeed()
        showStories()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun refresh() {
        Posts.refreshFeed { ok -> if (ok) showFeed() }
        Stories.refresh { ok -> if (ok) showStories() }
        Friends.refresh()
    }

    /** Your posts and your friends' posts, newest first. Existing cards are rebound, not recreated. */
    private fun showFeed() {
        val visible = Posts.cachedFeed()
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

    /** Android 13+ needs permission before Kinnect can show message and call notifications. */
    private fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 0)
        }
    }

    // ---- stories rail -------------------------------------------------------------------

    /** "Create story" stays first, then your story, then friends with stories from the last 24 hours. */
    private fun showStories() {
        val me = Session.uid ?: return
        while (rail.childCount > 1) rail.removeViewAt(1)
        val owners = Stories.cached().groupBy { it.ownerId }.toList()
            .sortedWith(compareBy<Pair<String, List<Story>>> { it.first != me }.thenByDescending { it.second.last().timestamp })

        owners.forEach { (owner, stories) ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_story_card, rail, false)
            Images.show(card.findViewById(R.id.story_image), stories.last().thumb)
            val unseen = owner == me || stories.any { !it.viewers.containsKey(me) }
            card.findViewById<View>(R.id.story_avatar).alpha = if (unseen) 1f else 0.6f
            UserCache.get(owner) { user ->
                Avatars.show(card.findViewById(R.id.story_avatar), user)
                card.findViewById<TextView>(R.id.story_name).text =
                    if (owner == me) "Your story" else user.fullName
            }
            card.setOnClickListener {
                if (owner == me) open(YourStoryActivity::class.java)
                else startActivity(Intent(this, StoryViewerActivity::class.java).putExtra(StoryViewerActivity.EXTRA_OWNER, owner))
            }
            rail.addView(card)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        watches.forEach(Watch::stop)
        super.onDestroy()
    }

    private companion object {
        const val POLL_MS = 10_000L
    }
}
