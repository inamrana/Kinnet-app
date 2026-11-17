package com.kinnect.i230577_i230839

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
 * Data comes from friends/list.php and notifications/list.php, cached in SQLite for offline use.
 */
class NotificationsActivity : BaseActivity() {

    private var friendsWatch: Watch? = null
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

        friendsWatch = Friends.watchAll { loadRequests(); show() }
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
        LocalDb.cache("notifications")?.let { notices = parse(org.json.JSONObject(it)) }
        show()
        Friends.refresh { loadRequests(); show() }
        Api.get("notifications/list.php") { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get
            LocalDb.putCache("notifications", json.toString())
            notices = parse(json)
            show()
        }
        markAllRead()
    }

    private fun loadRequests() {
        requests = LocalDb.links("incoming").map { it.userId to it.time }
    }

    private fun parse(json: org.json.JSONObject): List<Notice> = json.optJSONArray("notices").mapObjects {
        Notice(
            id = it.optString("id"), type = it.optString("type"), fromUid = it.optString("fromUid"),
            postId = it.optString("postId"), text = it.optString("text"), read = it.optBoolean("read"),
            timestamp = it.optLong("timestamp"),
        )
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
            notices = notices - notice
            show()
            Api.post("notifications/read.php", mapOf("id" to notice.id, "remove" to "1")) {}
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
        Api.post("notifications/read.php") {}
    }

    override fun onDestroy() {
        friendsWatch?.stop()
        super.onDestroy()
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
