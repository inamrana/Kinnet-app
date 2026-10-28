package com.inamulhaq.i230839

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * App entry point: enables offline caching, keeps the user's online status in step with
 * whether any Kinnect screen is on screen, and rings when someone calls the signed-in user.
 */
class KinnectApp : Application() {

    private var startedScreens = 0
    private var callWatch: Watch? = null
    private var watchedUid: String? = null

    /** True while any Kinnect screen is visible. */
    val inForeground get() = startedScreens > 0

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        Notifier.createChannels(this)
        FirebaseAuth.getInstance().addAuthStateListener { auth -> watchIncomingCalls(auth.currentUser?.uid) }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedScreens++ == 0 && Db.uid != null) Presence.goOnline()
            }

            override fun onActivityStopped(activity: Activity) {
                if (--startedScreens == 0 && Db.uid != null) Presence.goAway()
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Listens to calls/{uid}; a fresh ringing call opens the Incoming call screen (or a notification). */
    private fun watchIncomingCalls(uid: String?) {
        if (uid == watchedUid) return
        callWatch?.stop()
        callWatch = null
        watchedUid = uid
        uid ?: return
        val pointer: DatabaseReference = Db.calls.child(uid)
        callWatch = Watch().on(pointer) { snapshot ->
            val callId = snapshot.getValue(String::class.java) ?: return@on
            Db.callSessions.child(callId).get().addOnSuccessListener { sessionSnap ->
                val session = sessionSnap.getValue(CallSession::class.java)?.copy(id = callId) ?: return@addOnSuccessListener
                val fresh = System.currentTimeMillis() - session.createdAt < CallSession.RING_TIMEOUT_MS
                if (session.status != CallSession.STATUS_RINGING || session.calleeId != uid || !fresh) return@addOnSuccessListener
                if (inForeground) {
                    startActivity(
                        Intent(this, IncomingCallActivity::class.java)
                            .putExtra(CallScreenActivity.EXTRA_CALL_ID, callId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    UserCache.get(session.callerId) { caller ->
                        Notifier.incomingCall(this, callId, caller.fullName, session.video)
                    }
                }
            }
        }
    }
}
