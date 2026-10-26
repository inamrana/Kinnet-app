package com.inamulhaq.i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat

/**
 * 14 · Friends tab — live friend requests (Confirm / Delete), "Your friends" and People you may know.
 */
class FriendsActivity : BaseActivity() {

    private val watch = Watch()
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

        val me = Db.uid ?: return
        watch.on(Db.friendRequests.child(me)) { snapshot ->
            val requests = snapshot.children.mapNotNull { child ->
                child.key?.let { it to (child.getValue(Long::class.java) ?: 0L) }
            }.sortedByDescending { it.second }
            showRequests(requests)
        }
        watch.on(Db.friends.child(me)) { snapshot ->
            showFriends(snapshot.children.mapNotNull { it.key })
            loadSuggestions(me, snapshot.children.mapNotNull { it.key }.toSet())
        }
    }

    private fun showRequests(requests: List<Pair<String, Long>>) {
        findViewById<TextView>(R.id.requests_title).text = HtmlCompat.fromHtml(
            "Friend requests <font color=\"#C0492C\">${requests.size}</font>", HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        findViewById<View>(R.id.requests_empty).visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        requestsList.removeAllViews()
        val me = Db.uid ?: return
        requests.forEach { (fromUid, time) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_friend_request, requestsList, false)
            requestsList.addView(row)
            row.findViewById<TextView>(R.id.request_time).text = TimeAgo.short(time)
            row.setOnClickListener { PersonRow.openProfile(row, fromUid) }
            row.findViewById<View>(R.id.request_confirm).setOnClickListener { Friends.confirm(fromUid) }
            row.findViewById<View>(R.id.request_delete).setOnClickListener { Friends.delete(fromUid) }
            Db.user(fromUid).get().addOnSuccessListener { snap ->
                val user = snap.getValue(User::class.java) ?: return@addOnSuccessListener
                Avatars.show(row.findViewById(R.id.request_avatar), user)
                row.findViewById<TextView>(R.id.request_name).text = user.fullName
            }
            Friends.mutualCount(me, fromUid) { count ->
                row.findViewById<TextView>(R.id.request_mutual).text = mutualText(count)
            }
        }
    }

    private fun showFriends(uids: List<String>) {
        findViewById<View>(R.id.friends_empty).visibility = if (uids.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.friends_title).text = "Your friends · ${uids.size}"
        friendsList.removeAllViews()
        uids.forEach { uid ->
            Db.user(uid).get().addOnSuccessListener { snap ->
                val user = snap.getValue(User::class.java) ?: return@addOnSuccessListener
                PersonRow.add(friendsList, user, "@${user.username}", showAction = false)
            }
        }
    }

    /** A few people who are not friends yet (newest accounts first). */
    private fun loadSuggestions(me: String, friendIds: Set<String>) {
        Db.users.orderByChild("createdAt").limitToLast(30).get().addOnSuccessListener { snapshot ->
            rowWatches.forEach(Watch::stop)
            rowWatches.clear()
            suggestionsList.removeAllViews()
            val people = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                .filter { it.uid != me && it.uid !in friendIds && it.profileComplete }
                .reversed().take(MAX_SUGGESTIONS)
            findViewById<View>(R.id.suggestions_title).visibility = if (people.isEmpty()) View.GONE else View.VISIBLE
            people.forEach { user ->
                PersonRow.add(suggestionsList, user, "@${user.username}")?.let(rowWatches::add)
            }
        }
    }

    private fun scrollTo(viewId: Int) {
        val target = findViewById<View>(viewId)
        (target.parent.parent as? ScrollView)?.smoothScrollTo(0, target.top)
    }

    override fun onDestroy() {
        watch.stop()
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
