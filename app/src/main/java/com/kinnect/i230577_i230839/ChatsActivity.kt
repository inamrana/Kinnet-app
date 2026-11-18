package com.kinnect.i230577_i230839

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

/**
 * 20 · Chats — real conversations from chats/list.php, newest first, with the last message, its time
 * and an unread dot. The list is cached in SQLite (works offline) and refreshed every few seconds;
 * green dots (and the rail of active friends) come from users/statuses.php.
 *
 * Opened with [EXTRA_SHARE_POST] it becomes "Send to": tapping a person sends them the post.
 */
class ChatsActivity : BaseActivity() {

    private var chatWatch: Watch? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, POLL_MS)
        }
    }
    private val people = hashMapOf<String, User>()
    private var summaries = listOf<ChatSummary>()
    private var friendIds = listOf<String>()
    private lateinit var chatsList: LinearLayout
    private lateinit var activeList: LinearLayout
    private lateinit var search: EditText
    private val sharePostId by lazy { intent.getStringExtra(EXTRA_SHARE_POST).orEmpty() }
    private val sharing get() = sharePostId.isNotEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        chatsList = findViewById(R.id.chats_list)
        activeList = findViewById(R.id.active_list)
        search = findViewById(R.id.input_chat_search)
        tapCloses(R.id.btn_back)
        search.doAfterTextChanged { showChats() }
        if (sharing) {
            findViewById<TextView>(R.id.chats_title).text = "Send to"
            search.hint = "Search friends"
        }

        if (Session.uid == null) return
        LocalDb.cache("chats")?.let { parse(org.json.JSONObject(it)) }
        loadFriends()
        showChats()
        showActive()
        chatWatch = ChatSender.watch { refresh() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun loadFriends() {
        friendIds = LocalDb.links("friends").map { it.userId }
        friendIds.forEach { id -> LocalDb.user(id)?.let { people[id] = it } }
    }

    /** Conversations from the server (cached for offline), then fresh online dots for everyone shown. */
    private fun refresh() {
        Api.get("chats/list.php") { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get
            LocalDb.putCache("chats", json.toString())
            parse(json)
            showChats()
        }
        Friends.refresh { loadFriends(); showActive(); if (sharing) showChats() }
        Presence.statuses(people.keys) { statuses ->
            statuses.forEach { (id, status) -> people[id]?.let { people[id] = it.copy(online = status.first, lastSeen = status.second) } }
            showChats()
            showActive()
        }
    }

    private fun parse(json: org.json.JSONObject) {
        summaries = json.optJSONArray("chats").mapObjects { row ->
            val user = row.getJSONObject("user").toUser()
            people[user.uid] = user
            LocalDb.putUser(user)
            ChatSummary(
                otherUid = row.optString("otherUid"), lastMessage = row.optString("lastMessage"),
                lastTime = row.optLong("lastTime"), unread = row.optInt("unread"),
            )
        }
    }

    private fun showChats() {
        val filter = search.text.toString().trim().lowercase()
        // In "Send to" mode, friends you have not chatted with yet are listed too
        val source = if (sharing) {
            summaries + friendIds.filter { id -> summaries.none { it.otherUid == id } }.map { ChatSummary(otherUid = it) }
        } else summaries
        val rows = source.filter { summary ->
            filter.isEmpty() || people[summary.otherUid]?.nameLower?.contains(filter) == true
        }
        chatsList.removeAllViews()
        rows.forEach { summary ->
            val user = people[summary.otherUid] ?: return@forEach
            val row = LayoutInflater.from(this).inflate(R.layout.item_chat, chatsList, false)
            Avatars.show(row.findViewById(R.id.chat_avatar), user)
            row.findViewById<TextView>(R.id.chat_name).text = user.fullName
            row.findViewById<View>(R.id.chat_online).visibility = if (user.online) View.VISIBLE else View.GONE

            val unread = summary.unread > 0 && !sharing
            row.findViewById<TextView>(R.id.chat_preview).apply {
                text = when {
                    sharing -> "@${user.username}"
                    else -> "${summary.lastMessage} · ${TimeAgo.short(summary.lastTime)}"
                }
                // Unread rows use the bold preview + teal dot from the design
                if (unread) setTextAppearance(R.style.Text_Bold) else setTextAppearance(R.style.Text_Secondary)
                textSize = 15f
            }
            row.findViewById<View>(R.id.chat_unread).visibility = if (unread) View.VISIBLE else View.GONE
            row.setOnClickListener { if (sharing) sharePost(user) else openChat(summary.otherUid) }
            chatsList.addView(row)
        }
        findViewById<View>(R.id.chats_empty).visibility = if (source.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Friends who are online right now, in the notes rail. */
    private fun showActive() {
        activeList.removeAllViews()
        friendIds.mapNotNull { people[it] }.filter { it.online }.forEach { user ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_active_friend, activeList, false)
            Avatars.show(item.findViewById(R.id.active_avatar), user)
            item.findViewById<TextView>(R.id.active_name).text = user.firstName.ifEmpty { user.fullName }
            item.setOnClickListener { if (sharing) sharePost(user) else openChat(user.uid) }
            activeList.addView(item)
        }
    }

    /** Sends the post as a message and counts the share on the post. */
    private fun sharePost(user: User) {
        // The server counts the share when it receives the shared-post message
        ChatSender.send(user.uid, Message.TYPE_POST, postId = sharePostId)
        toast("Sent to ${user.firstName.ifEmpty { user.fullName }}")
        finish()
    }

    private fun openChat(uid: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_UID, uid))
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        chatWatch?.stop()
        super.onDestroy()
    }

    companion object {
        /** Opens the list in "send this post" mode: tapping a chat shares the post there. */
        const val EXTRA_SHARE_POST = "sharePostId"
        private const val POLL_MS = 5_000L
    }
}
