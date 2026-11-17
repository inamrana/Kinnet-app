package com.kinnect.i230577_i230839

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/** Where the signed-in user stands with someone else. Drives the Add friend → Requested → Friends button. */
enum class FriendState(val key: String) {
    NONE("none"), REQUESTED("requested"), INCOMING("incoming"), FRIENDS("friends");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * Friend requests and friendships through the web service (friends/action.php).
 * Each change is applied to the local SQLite copy at once (so the button flips immediately,
 * even offline) and queued in [Sync] with a UUID; the server's answer then confirms it.
 */
object Friends {

    private val listeners = mutableSetOf<(String) -> Unit>()

    init {
        Sync.addListener { done ->
            if (done.type != "friend") return@addListener
            val state = done.response?.optString("state")
            if (done.rejectedMessage == null && !state.isNullOrEmpty()) LocalDb.setLink(done.target, state)
            changed(done.target)
        }
    }

    fun state(uid: String): FriendState = FriendState.of(LocalDb.linkState(uid))

    /** True while a change for this person is still waiting to reach the server. */
    fun isPending(uid: String) = Sync.isPending("friend", uid)

    fun sendRequest(to: String) = act(to, "request", FriendState.REQUESTED)
    fun cancelRequest(to: String) = act(to, "cancel", FriendState.NONE)
    fun confirm(from: String) = act(from, "confirm", FriendState.FRIENDS)
    fun delete(from: String) = act(from, "delete", FriendState.NONE)
    fun unfriend(other: String) = act(other, "unfriend", FriendState.NONE)

    /** Runs the right action for the button's current [state]. */
    fun toggle(other: String, state: FriendState) {
        when (state) {
            FriendState.NONE -> sendRequest(other)
            FriendState.REQUESTED -> cancelRequest(other)
            FriendState.INCOMING -> confirm(other)
            FriendState.FRIENDS -> unfriend(other)
        }
    }

    private fun act(uid: String, action: String, optimistic: FriendState) {
        LocalDb.setLink(uid, optimistic.key)
        Sync.enqueue("friend", "friends/action.php", mapOf("action" to action, "userId" to uid), target = uid)
        changed(uid)
    }

    /** Remembers what the server said about someone (search results, profiles), unless a change is queued. */
    fun remember(uid: String, state: String) {
        if (!isPending(uid)) LocalDb.setLink(uid, state)
    }

    /** Downloads friends, incoming and sent requests into SQLite. [done] gets false when offline. */
    fun refresh(done: (Boolean) -> Unit = {}) {
        Api.get("friends/list.php") { result ->
            val json = result.json
            if (!result.ok || json == null) return@get done(false)
            val links = mutableListOf<LocalDb.Link>()
            val users = mutableListOf<User>()
            json.optJSONArray("friends").mapObjects { it.toUser() }.forEach {
                users += it; links += LocalDb.Link(it.uid, "friends", 0, 0)
            }
            json.optJSONArray("incoming").mapObjects { it }.forEach {
                val u = it.getJSONObject("user").toUser()
                users += u; links += LocalDb.Link(u.uid, "incoming", it.optLong("time"), it.optInt("mutual"))
            }
            json.optJSONArray("sent").mapObjects { it }.forEach {
                val u = it.getJSONObject("user").toUser()
                users += u; links += LocalDb.Link(u.uid, "requested", it.optLong("time"), 0)
            }
            LocalDb.putUsers(users)
            LocalDb.replaceLinks(links)
            done(true)
        }
    }

    /** Calls [onChange] whenever my relation to [other] changes. Call [Watch.stop] in onDestroy. */
    fun watchState(other: String, onChange: (FriendState) -> Unit): Watch {
        val listener: (String) -> Unit = { uid -> if (uid == other) onChange(state(other)) }
        listeners += listener
        onChange(state(other))
        return Watch().onStop { listeners -= listener }
    }

    /** Calls [onChange] after any friend change (to refresh lists). */
    fun watchAll(onChange: () -> Unit): Watch {
        val listener: (String) -> Unit = { onChange() }
        listeners += listener
        return Watch().onStop { listeners -= listener }
    }

    private fun changed(uid: String) = listeners.toList().forEach { it(uid) }
}

/** Keeps track of value listeners so a screen can remove them all at once in onDestroy. */
class Watch {
    private val active = mutableListOf<Pair<Query, ValueEventListener>>()
    private val cleanups = mutableListOf<() -> Unit>()

    /** Runs [cleanup] on [stop] (for listeners that aren't Firebase ones). */
    fun onStop(cleanup: () -> Unit): Watch {
        cleanups += cleanup
        return this
    }

    /** Listens to [ref] (a path or a query) until [stop]. */
    fun on(ref: Query, onData: (DataSnapshot) -> Unit): Watch {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        active += ref to listener
        return this
    }

    fun stop() {
        active.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        active.clear()
        cleanups.forEach { it() }
        cleanups.clear()
    }
}

/** "2m", "3h", "4d", "2w" — the short times used across Kinnect. */
object TimeAgo {
    fun short(time: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = (now - time) / 60_000
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
            else -> "${minutes / (60 * 24 * 7)}w"
        }
    }
}
