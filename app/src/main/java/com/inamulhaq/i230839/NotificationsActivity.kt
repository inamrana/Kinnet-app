package com.inamulhaq.i230839

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat

/**
 * 18 · Notifications tab — pending friend requests (Confirm / Delete) and activity on your posts
 * (reactions, comments, replies, accepted requests). "New" holds requests and the last day;
 * everything older is under "Earlier". Opening the tab marks notifications as read.
 */
class NotificationsActivity : BaseActivity() {

    private val watch = Watch()
    private var requests = listOf<Pair<String, Long>>()
    private var notices = listOf<Notice>()
    private lateinit var newList: LinearLayout
    private lateinit var earlierList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        setupTopTabs(selected = 3)
        newList = findViewById(R.id.notif_new_list)
        earlierList = findViewById(R.id.notif_earlier_list)
        findViewById<View>(R.id.notif_see_previous).visibility = View.GONE

        val me = Db.uid ?: return
        watch.on(Db.friendRequests.child(me)) { snapshot ->
            requests = snapshot.children.mapNotNull { child ->
                child.key?.let { it to (child.getValue(Long::class.java) ?: 0L) }
            }.sortedByDescending { it.second }
            show()
        }
        watch.on(Db.notifications.child(me).orderByChild("timestamp").limitToLast(MAX_NOTICES)) { snapshot ->
            notices = snapshot.children.mapNotNull { child ->
                child.getValue(Notice::class.java)?.copy(id = child.key.orEmpty())
            }.sortedByDescending { it.timestamp }
            show()
        }
    }

    override fun onResume() {
        super.onResume()
        markAllRead()
    }

    private fun show() {
        newList.removeAllViews()
        earlierList.removeAllViews()
        val dayAgo = System.currentTimeMillis() - DAY_MS
        requests.forEach { (from, time) -> newList.addView(requestRow(from, time)) }
        notices.forEach { notice ->
            val target = if (notice.timestamp >= dayAgo) newList else earlierList
            target.addView(noticeRow(notice, isNew = target == newList))
        }
        val hasNew = newList.childCount > 0
        val hasEarlier = earlierList.childCount > 0
        findViewById<View>(R.id.notif_new_title).visibility = if (hasNew) View.VISIBLE else View.GONE
        newList.visibility = if (hasNew) View.VISIBLE else View.GONE
        findViewById<View>(R.id.notif_earlier_title).visibility = if (hasEarlier) View.VISIBLE else View.GONE
        findViewById<View>(R.id.notif_empty).visibility = if (!hasNew && !hasEarlier) View.VISIBLE else View.GONE
    }

    private fun requestRow(from: String, time: Long): View {
        val row = baseRow(from, R.drawable.ic_person, R.color.teal, time, isNew = true)
        row.findViewById<View>(R.id.notif_buttons).visibility = View.VISIBLE
        row.findViewById<View>(R.id.notif_confirm).setOnClickListener { Friends.confirm(from) }
        row.findViewById<View>(R.id.notif_delete).setOnClickListener { Friends.delete(from) }
        row.setOnClickListener { PersonRow.openProfile(row, from) }
        UserCache.get(from) { user ->
            setSentence(row, "<b>${android.text.TextUtils.htmlEncode(user.fullName)}</b> sent you a friend request.")
        }
        return row
    }

    private fun noticeRow(notice: Notice, isNew: Boolean): View {
        val (icon, color) = badgeFor(notice)
        val row = baseRow(notice.fromUid, icon, color, notice.timestamp, isNew)
        UserCache.get(notice.fromUid) { user -> setSentence(row, Notices.sentence(notice, user.fullName)) }
        row.setOnClickListener {
            if (notice.postId.isNotEmpty()) PostCard.openComments(this, notice.postId)
            else PersonRow.openProfile(row, notice.fromUid)
        }
        row.findViewById<View>(R.id.notif_more).setOnClickListener {
            Db.uid?.let { Db.notifications.child(it).child(notice.id).removeValue() }
        }
        return row
    }

    private fun baseRow(uid: String, badge: Int, badgeColor: Int, time: Long, isNew: Boolean): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_notification, newList, false)
        UserCache.get(uid) { Avatars.show(row.findViewById(R.id.notif_avatar), it) }
        val badgeView = row.findViewById<ImageView>(R.id.notif_badge)
        if (badge == R.drawable.react_love) {
            badgeView.setImageResource(badge)
            badgeView.imageTintList = null
            badgeView.setPadding(0, 0, 0, 0)
        } else {
            badgeView.setImageResource(badge)
            badgeView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, badgeColor))
        }
        row.findViewById<TextView>(R.id.notif_time).apply {
            text = TimeAgo.short(time)
            // New items show the time in bold teal, like the design
            if (isNew) {
                setTextAppearance(R.style.Text_Bold)
                setTextColor(ContextCompat.getColor(context, R.color.teal))
                textSize = 14f
            }
        }
        return row
    }

    private fun setSentence(row: View, html: String) {
        row.findViewById<TextView>(R.id.notif_text).text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun badgeFor(notice: Notice): Pair<Int, Int> = when (notice.type) {
        Notice.TYPE_REACTION -> (Reaction.of(notice.text)?.icon ?: R.drawable.react_like).let {
            if (it == R.drawable.react_love) it to R.color.white else R.drawable.ic_like to R.color.teal
        }
        Notice.TYPE_COMMENT, Notice.TYPE_REPLY -> R.drawable.ic_comment to R.color.av_green
        Notice.TYPE_FRIEND_ACCEPTED -> R.drawable.ic_person to R.color.teal
        else -> R.drawable.ic_bell to R.color.gold
    }

    private fun markAllRead() {
        val me = Db.uid ?: return
        Db.notifications.child(me).orderByChild("read").equalTo(false).get().addOnSuccessListener { snapshot ->
            val updates = snapshot.children.associate { "notifications/$me/${it.key}/read" to true as Any? }
            if (updates.isNotEmpty()) Db.update(updates)
        }
    }

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    private companion object {
        const val MAX_NOTICES = 60
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
