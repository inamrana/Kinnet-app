package com.inamulhaq.i230839

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

/**
 * 20 · Chats — real conversations from userChats/{uid}, newest first, with the last message,
 * its time and an unread dot. Green dots (and the rail of active friends) follow presence live.
 */
class ChatsActivity : BaseActivity() {

    private val watch = Watch()
    private val userWatches = hashMapOf<String, Watch>()
    private val people = hashMapOf<String, User>()
    private var summaries = listOf<ChatSummary>()
    private var friendIds = listOf<String>()
    private lateinit var chatsList: LinearLayout
    private lateinit var activeList: LinearLayout
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        chatsList = findViewById(R.id.chats_list)
        activeList = findViewById(R.id.active_list)
        search = findViewById(R.id.input_chat_search)
        tapCloses(R.id.btn_back)
        search.doAfterTextChanged { showChats() }

        val me = Db.uid ?: return
        watch.on(Db.userChats.child(me)) { snapshot ->
            summaries = snapshot.children.mapNotNull { child ->
                child.getValue(ChatSummary::class.java)?.copy(otherUid = child.key.orEmpty())
            }.sortedByDescending { it.lastTime }
            summaries.forEach { watchUser(it.otherUid) }
            showChats()
        }
        watch.on(Db.friends.child(me)) { snapshot ->
            friendIds = snapshot.children.mapNotNull { it.key }
            friendIds.forEach(::watchUser)
            showActive()
        }
    }

    /** One live listener per person, so names, photos and online dots stay current. */
    private fun watchUser(uid: String) {
        if (uid in userWatches) return
        userWatches[uid] = Watch().on(Db.user(uid)) { snapshot ->
            snapshot.getValue(User::class.java)?.let { people[uid] = it }
            showChats()
            showActive()
        }
    }

    private fun showChats() {
        val filter = search.text.toString().trim().lowercase()
        val rows = summaries.filter { summary ->
            filter.isEmpty() || people[summary.otherUid]?.nameLower?.contains(filter) == true
        }
        chatsList.removeAllViews()
        rows.forEach { summary ->
            val user = people[summary.otherUid] ?: return@forEach
            val row = LayoutInflater.from(this).inflate(R.layout.item_chat, chatsList, false)
            Avatars.show(row.findViewById(R.id.chat_avatar), user)
            row.findViewById<TextView>(R.id.chat_name).text = user.fullName
            row.findViewById<View>(R.id.chat_online).visibility = if (user.online) View.VISIBLE else View.GONE

            val unread = summary.unread > 0
            row.findViewById<TextView>(R.id.chat_preview).apply {
                text = "${summary.lastMessage} · ${TimeAgo.short(summary.lastTime)}"
                // Unread rows use the bold preview + teal dot from the design
                if (unread) setTextAppearance(R.style.Text_Bold) else setTextAppearance(R.style.Text_Secondary)
                textSize = 15f
            }
            row.findViewById<View>(R.id.chat_unread).visibility = if (unread) View.VISIBLE else View.GONE
            row.setOnClickListener { openChat(summary.otherUid) }
            chatsList.addView(row)
        }
        findViewById<View>(R.id.chats_empty).visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Friends who are online right now, in the notes rail. */
    private fun showActive() {
        activeList.removeAllViews()
        friendIds.mapNotNull { people[it] }.filter { it.online }.forEach { user ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_active_friend, activeList, false)
            Avatars.show(item.findViewById(R.id.active_avatar), user)
            item.findViewById<TextView>(R.id.active_name).text = user.firstName.ifEmpty { user.fullName }
            item.setOnClickListener { openChat(user.uid) }
            activeList.addView(item)
        }
    }

    private fun openChat(uid: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_UID, uid))
    }

    override fun onDestroy() {
        watch.stop()
        userWatches.values.forEach(Watch::stop)
        super.onDestroy()
    }

    companion object {
        /** Opens the list in "send this post" mode: tapping a chat shares the post there. */
        const val EXTRA_SHARE_POST = "sharePostId"
    }
}
