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

    // Stories rail: one listener per person (you + friends)
    private val storyWatches = hashMapOf<String, Watch>()
    private val storiesByOwner = hashMapOf<String, List<Story>>()
    private val closeFriendOf = hashMapOf<String, Boolean>()
    private lateinit var rail: LinearLayout

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
            watchStories(friendIds + me)
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

    /** Android 13+ needs permission before Kinnect can show message and call notifications. */
    private fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 0)
        }
    }

    // ---- stories rail -------------------------------------------------------------------

    private fun watchStories(owners: Set<String>) {
        val me = Db.uid ?: return
        (storyWatches.keys - owners).forEach { gone ->
            storyWatches.remove(gone)?.stop()
            storiesByOwner.remove(gone)
        }
        (owners - storyWatches.keys).forEach { owner ->
            val ownerWatch = Watch().on(Db.stories.child(owner)) { snapshot ->
                val all = snapshot.children.mapNotNull { it.getValue(Story::class.java)?.copy(id = it.key.orEmpty(), ownerId = owner) }
                if (owner == me) Stories.deleteExpired(all)
                storiesByOwner[owner] = all
                showStories()
            }
            if (owner != me) {
                ownerWatch.on(Db.closeFriends.child(owner).child(me)) {
                    closeFriendOf[owner] = it.exists()
                    showStories()
                }
            }
            storyWatches[owner] = ownerWatch
        }
        showStories()
    }

    /** "Create story" stays first, then your story, then friends with stories from the last 24 hours. */
    private fun showStories() {
        val me = Db.uid ?: return
        while (rail.childCount > 1) rail.removeViewAt(1)
        val owners = storiesByOwner.mapNotNull { (owner, stories) ->
            val visible = Stories.visible(stories, me, closeFriendOf[owner] == true)
            if (visible.isEmpty()) null else owner to visible
        }.sortedWith(compareBy<Pair<String, List<Story>>> { it.first != me }.thenByDescending { it.second.last().timestamp })

        owners.forEach { (owner, stories) ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_story_card, rail, false)
            Images.show(card.findViewById(R.id.story_image), stories.last().thumb)
            val unseen = owner == me || stories.any { !it.viewers.containsKey(me) }
            card.findViewById<View>(R.id.story_avatar).alpha = if (unseen) 1f else 0.6f
            UserCache.get(owner) { user ->
                Avatars.show(card.findViewById(R.id.story_avatar), user)
                card.findViewById<android.widget.TextView>(R.id.story_name).text =
                    if (owner == me) "Your story" else user.fullName
            }
            card.setOnClickListener {
                if (owner == me) open(YourStoryActivity::class.java)
                else startActivity(
                    android.content.Intent(this, StoryViewerActivity::class.java).putExtra(StoryViewerActivity.EXTRA_OWNER, owner)
                )
            }
            rail.addView(card)
        }
    }

    override fun onDestroy() {
        storyWatches.values.forEach(Watch::stop)
        watch.stop()
        super.onDestroy()
    }

    private companion object {
        const val MAX_POSTS = 100
    }
}
