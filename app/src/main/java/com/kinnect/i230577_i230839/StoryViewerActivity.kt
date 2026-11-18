package com.kinnect.i230577_i230839

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/**
 * 11 · Story viewer — a friend's stories from the last 24 hours, one after another.
 * You can reply with a message or send a quick reaction; both arrive in your chat with them.
 */
class StoryViewerActivity : BaseActivity() {

    private val ownerUid by lazy { intent.getStringExtra(EXTRA_OWNER).orEmpty() }
    private var storiesWatch: Watch? = null
    private lateinit var player: StoryPlayer
    private var all = listOf<Story>()
    private var isCloseFriend = false
    private var started = false
    private var storiesLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_viewer)
        val me = Session.uid
        if (ownerUid.isEmpty() || me == null) { finish(); return }

        player = StoryPlayer(this, onShow = { _, story -> showMeta(story) }, onFinished = { finish() })
        tapCloses(R.id.btn_close)
        onTap(R.id.btn_story_more) { toast("Coming soon") }
        onTap(R.id.story_owner_avatar) { PersonRow.openProfile(window.decorView, ownerUid) }

        val reply = findViewById<EditText>(R.id.input_story_reply)
        reply.setOnFocusChangeListener { _, focused -> if (focused) player.pause() else player.resume() }
        reply.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendReply(reply); true } else false
        }
        mapOf(
            R.id.story_react_like to Reaction.LIKE,
            R.id.story_react_love to Reaction.LOVE,
            R.id.story_react_haha to Reaction.HAHA,
        ).forEach { (id, reaction) ->
            onTap(id) {
                player.current?.let { Stories.react(it, reaction) }
                toast("Sent ${reaction.label.lowercase()}")
            }
        }

        UserCache.get(ownerUid) { owner ->
            Avatars.show(findViewById(R.id.story_owner_avatar), owner)
            findViewById<TextView>(R.id.story_owner_name).text = owner.fullName
        }
        // The server only returns stories I'm allowed to see (24 hours, close friends)
        isCloseFriend = true
        storiesWatch = Stories.watch { all = Stories.cachedFor(ownerUid); refresh() }
        all = Stories.cachedFor(ownerUid)
        storiesLoaded = all.isNotEmpty()
        if (storiesLoaded) refresh()
        Stories.refresh {
            all = Stories.cachedFor(ownerUid)
            storiesLoaded = true
            refresh()
        }
    }

    private fun refresh() {
        val me = Session.uid ?: return
        // The close-friends answer can arrive before the stories; wait for the stories themselves
        if (!storiesLoaded) return
        val visible = Stories.visible(all, me, isCloseFriend)
        if (!started) {
            // Start at the first story you have not seen yet
            val firstUnseen = visible.indexOfFirst { !it.viewers.containsKey(me) }.takeIf { it >= 0 } ?: 0
            if (visible.isNotEmpty()) started = true
            player.setStories(visible, firstUnseen)
        } else {
            player.setStories(visible)
        }
    }

    private fun showMeta(story: Story) {
        val close = story.audience == Story.AUDIENCE_CLOSE
        findViewById<TextView>(R.id.story_meta).text =
            "${TimeAgo.short(story.timestamp)} · ${if (close) "Close friends" else ""}"
        findViewById<View>(R.id.story_globe).visibility = if (close) View.GONE else View.VISIBLE
    }

    private fun sendReply(field: EditText) {
        val text = field.text.toString().trim()
        val story = player.current ?: return
        if (text.isEmpty()) return
        Stories.reply(story, text)
        field.setText("")
        field.clearFocus()
        toast("Reply sent")
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) player.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized) player.resume()
    }

    override fun onDestroy() {
        storiesWatch?.stop()
        if (::player.isInitialized) player.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OWNER = "owner"
    }
}
