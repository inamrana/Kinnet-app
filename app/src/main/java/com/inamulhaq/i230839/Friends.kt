package com.inamulhaq.i230839

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/** Where the signed-in user stands with someone else. Drives the Add friend → Requested → Friends button. */
enum class FriendState { NONE, REQUESTED, INCOMING, FRIENDS }

/** Friend requests and friendships. Every change writes both sides at once with [Db.update]. */
object Friends {

    private val me get() = Db.uid!!

    fun sendRequest(to: String) = Db.update(
        mapOf(
            "sentRequests/$me/$to" to ServerValue.TIMESTAMP,
            "friendRequests/$to/$me" to ServerValue.TIMESTAMP,
        )
    )

    fun cancelRequest(to: String) = Db.update(
        mapOf("sentRequests/$me/$to" to null, "friendRequests/$to/$me" to null)
    )

    fun confirm(from: String) = Db.update(
        mapOf(
            "friends/$me/$from" to ServerValue.TIMESTAMP,
            "friends/$from/$me" to ServerValue.TIMESTAMP,
            "friendRequests/$me/$from" to null,
            "sentRequests/$from/$me" to null,
        )
    )

    fun delete(from: String) = Db.update(
        mapOf("friendRequests/$me/$from" to null, "sentRequests/$from/$me" to null)
    )

    fun unfriend(other: String) = Db.update(
        mapOf("friends/$me/$other" to null, "friends/$other/$me" to null)
    )

    /** Runs the right action for the button's current [state]. */
    fun toggle(other: String, state: FriendState) {
        when (state) {
            FriendState.NONE -> sendRequest(other)
            FriendState.REQUESTED -> cancelRequest(other)
            FriendState.INCOMING -> confirm(other)
            FriendState.FRIENDS -> unfriend(other)
        }
    }

    /** Number of friends [a] and [b] have in common, read once. */
    fun mutualCount(a: String, b: String, done: (Int) -> Unit) {
        Db.friends.child(a).get().addOnSuccessListener { mine ->
            Db.friends.child(b).get().addOnSuccessListener { theirs ->
                val mineIds = mine.children.mapNotNull { it.key }.toSet()
                done(theirs.children.count { it.key in mineIds })
            }
        }
    }

    /**
     * Live [FriendState] between me and [other]. Call [Watch.stop] in onDestroy.
     */
    fun watchState(other: String, onChange: (FriendState) -> Unit): Watch {
        var isFriend = false
        var sent = false
        var incoming = false
        fun emit() = onChange(
            when {
                isFriend -> FriendState.FRIENDS
                sent -> FriendState.REQUESTED
                incoming -> FriendState.INCOMING
                else -> FriendState.NONE
            }
        )
        return Watch()
            .on(Db.friends.child(me).child(other)) { isFriend = it.exists(); emit() }
            .on(Db.sentRequests.child(me).child(other)) { sent = it.exists(); emit() }
            .on(Db.friendRequests.child(me).child(other)) { incoming = it.exists(); emit() }
    }
}

/** Keeps track of value listeners so a screen can remove them all at once in onDestroy. */
class Watch {
    private val active = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    fun on(ref: DatabaseReference, onData: (DataSnapshot) -> Unit): Watch {
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
