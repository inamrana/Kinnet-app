package com.kinnect.i230577_i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import org.json.JSONObject

/**
 * 14 · Friends tab — friend requests (Confirm / Delete), "Your friends" and People you may know.
 * Everything is shown from SQLite first and refreshed from friends/list.php; Confirm / Delete
 * work offline too (queued and synced, marked "Pending" until the server has them).
 */
class FriendsActivity : BaseActivity() {

    private var friendsWatch: Watch? = null
    private val rowWatches = mutableListOf<Watch>()
    private lateinit var requestsList: LinearLayout
    private lateinit var friendsList: LinearLayout
    private lateinit var suggestionsList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        setupTopTabs(selected = 1)

        requestsList = findViewById(R.id.requests_list)
        friendsList = findViewById(R.id.friends_list)
        suggestionsList = findViewById(R.id.suggestions_list)
        onTap(R.id.chip_suggestions) { scrollTo(R.id.suggestions_title) }
        onTap(R.id.chip_your_friends) { scrollTo(R.id.friends_title) }
        friendsWatch = Friends.watchAll { showLocal() }
    }

    override fun onResume() {
        super.onResume()
        showLocal()
        LocalDb.cache("suggestions")?.let { showSuggestions(JSONObject(it)) }
        Friends.refresh { showLocal() }
        Api.get("users/suggestions.php") { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get
            LocalDb.putCache("suggestions", json.toString())
            showSuggestions(json)
        }
    }

    private fun showLocal() {
        showRequests(LocalDb.links("incoming"))
        showFriends(LocalDb.links("friends").mapNotNull { LocalDb.user(it.userId) })
    }

    private fun showRequests(requests: List<LocalDb.Link>) {
        findViewById<TextView>(R.id.requests_title).text = HtmlCompat.fromHtml(
            "Friend requests <font color=\"#C0492C\">${requests.size}</font>", HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        findViewById<View>(R.id.requests_empty).visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        requestsList.removeAllViews()
        requests.forEach { link ->
            val user = LocalDb.user(link.userId) ?: return@forEach
            val row = LayoutInflater.from(this).inflate(R.layout.item_friend_request, requestsList, false)
            requestsList.addView(row)
            Avatars.show(row.findViewById(R.id.request_avatar), user)
            row.findViewById<TextView>(R.id.request_name).text = user.fullName
            row.findViewById<TextView>(R.id.request_time).text = TimeAgo.short(link.time)
            row.findViewById<TextView>(R.id.request_mutual).text = mutualText(link.mutual)
            row.setOnClickListener { PersonRow.openProfile(row, user.uid) }
            row.findViewById<View>(R.id.request_confirm).setOnClickListener { Friends.confirm(user.uid) }
            row.findViewById<View>(R.id.request_delete).setOnClickListener { Friends.delete(user.uid) }
        }
    }

    private fun showFriends(friends: List<User>) {
        findViewById<View>(R.id.friends_empty).visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.friends_title).text = "Your friends · ${friends.size}"
        friendsList.removeAllViews()
        friends.sortedBy { it.fullName }.forEach { user ->
            val pending = if (Friends.isPending(user.uid)) " · Pending" else ""
            PersonRow.add(friendsList, user, "@${user.username}$pending", showAction = false)
        }
    }

    /** People you may know: newest accounts that aren't friends yet. */
    private fun showSuggestions(json: JSONObject) {
        rowWatches.forEach(Watch::stop)
        rowWatches.clear()
        suggestionsList.removeAllViews()
        val people = json.optJSONArray("people").mapObjects { item ->
            item.getJSONObject("user").toUser().also { Friends.remember(it.uid, item.optString("state")) }
        }.filter { Friends.state(it.uid) != FriendState.FRIENDS }.take(MAX_SUGGESTIONS)
        LocalDb.putUsers(people)
        findViewById<View>(R.id.suggestions_title).visibility = if (people.isEmpty()) View.GONE else View.VISIBLE
        people.forEach { user ->
            PersonRow.add(suggestionsList, user, "@${user.username}")?.let(rowWatches::add)
        }
    }

    private fun scrollTo(viewId: Int) {
        val target = findViewById<View>(viewId)
        (target.parent.parent as? ScrollView)?.smoothScrollTo(0, target.top)
    }

    override fun onDestroy() {
        friendsWatch?.stop()
        rowWatches.forEach(Watch::stop)
        super.onDestroy()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 8

        fun mutualText(count: Int) = when (count) {
            0 -> "No mutual friends"
            1 -> "1 mutual friend"
            else -> "$count mutual friends"
        }
    }
}
