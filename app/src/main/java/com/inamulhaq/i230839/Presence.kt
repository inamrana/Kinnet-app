package com.inamulhaq.i230839

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Online / offline status for the signed-in user.
 * Listens to Firebase's own ".info/connected" flag and uses onDisconnect()
 * so the user is marked offline even if the app is killed or loses network.
 */
object Presence {

    private val connectedRef get() = FirebaseDatabase.getInstance().getReference(".info/connected")
    private var listener: ValueEventListener? = null

    fun goOnline() {
        if (listener != null) return
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                val me = Db.me() ?: return
                me.child("online").onDisconnect().setValue(false)
                me.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                me.child("online").setValue(true)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }.also { connectedRef.addValueEventListener(it) }
    }

    /** Called on log out, before signing out, while we still have write access. */
    fun goOffline() {
        listener?.let { connectedRef.removeEventListener(it) }
        listener = null
        Db.me()?.updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }
}
