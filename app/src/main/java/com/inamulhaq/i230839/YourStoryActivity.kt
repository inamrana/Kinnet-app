package com.inamulhaq.i230839

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * 12 · Your story — your stories from the last 24 hours with who has seen each one and
 * floating hearts for love reactions. "More" lets you delete a story; "Create" adds another.
 */
class YourStoryActivity : BaseActivity() {

    private val watch = Watch()
    private lateinit var player: StoryPlayer
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_your_story)
        val me = Db.uid ?: run { finish(); return }

        player = StoryPlayer(this, onShow = { _, story -> showMeta(story) }, onFinished = { finish() })
        tapCloses(R.id.btn_close)
        tapOpens(CameraActivity::class.java, R.id.story_create)
        onTap(R.id.story_viewers) { player.current?.let(::showViewers) }
        onTap(R.id.story_more) { player.current?.let(::confirmDelete) }
        onTap(R.id.story_highlight) { toast("Added to highlights") }
        onTap(R.id.story_send) { toast("Coming soon") }

        UserCache.get(me) { user ->
            Avatars.show(findViewById(R.id.story_owner_avatar), user)
        }
        findViewById<TextView>(R.id.story_owner_name).text = "Your story"
        watch.on(Db.stories.child(me)) { snapshot ->
            val all = snapshot.children.mapNotNull { it.getValue(Story::class.java)?.copy(id = it.key.orEmpty(), ownerId = me) }
            Stories.deleteExpired(all)
            val live = all.filter { it.isLive }.sortedBy { it.timestamp }
            // Open on your newest story the first time
            player.setStories(live, if (!started && live.isNotEmpty()) live.lastIndex.also { started = true } else -1)
            player.current?.let(::showMeta)
        }
    }

    private fun showMeta(story: Story) {
        val close = story.audience == Story.AUDIENCE_CLOSE
        findViewById<TextView>(R.id.story_meta).text = "${TimeAgo.short(story.timestamp)} · ${if (close) "Close friends" else "Friends"}"
        findViewById<View>(R.id.story_globe).visibility = View.GONE

        val viewers = story.viewers.entries.sortedByDescending { it.value }.map { it.key }
        findViewById<TextView>(R.id.story_seen_by).text = if (viewers.isEmpty()) "No views yet" else "Seen by ${viewers.size}"
        val avatars = findViewById<LinearLayout>(R.id.story_viewer_avatars)
        avatars.removeAllViews()
        viewers.take(3).forEachIndexed { i, uid ->
            val avatar = layoutInflater.inflate(R.layout.view_small_avatar, avatars, false) as TextView
            (avatar.layoutParams as ViewGroup.MarginLayoutParams).marginStart = if (i == 0) 0 else dp(-8)
            avatars.addView(avatar)
            UserCache.get(uid) { Avatars.show(avatar, it) }
        }

        // One floating heart per love reaction, fading out towards the top
        val hearts = findViewById<LinearLayout>(R.id.story_hearts)
        hearts.removeAllViews()
        val loves = story.reactions.values.count { it == Reaction.LOVE.key }.coerceAtMost(5)
        for (i in 0 until loves) {
            val heart = ImageView(this).apply {
                setImageResource(R.drawable.react_love)
                alpha = 0.35f + 0.65f * (i + 1) / loves
            }
            val size = dp(30 + i * 3)
            val params = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dp(8)
                marginStart = if (i % 2 == 0) dp(14) else 0
            }
            hearts.addView(heart, 0, params)
        }
    }

    private fun showViewers(story: Story) {
        val uids = story.viewers.entries.sortedByDescending { it.value }.map { it.key }
        if (uids.isEmpty()) { toast("No one has seen this story yet"); return }
        player.pause()
        val names = uids.map { uid -> UserCache.peek(uid)?.fullName ?: "Kinnect user" }
        uids.forEach { UserCache.get(it) { } }
        AlertDialog.Builder(this)
            .setTitle("Seen by ${uids.size}")
            .setItems(names.toTypedArray(), null)
            .setOnDismissListener { player.resume() }
            .show()
    }

    private fun confirmDelete(story: Story) {
        player.pause()
        AlertDialog.Builder(this)
            .setTitle("Delete this story?")
            .setPositiveButton("Delete") { _, _ -> Stories.delete(story) }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { player.resume() }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) player.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized) player.resume()
    }

    override fun onDestroy() {
        watch.stop()
        if (::player.isInitialized) player.stop()
        super.onDestroy()
    }
}
