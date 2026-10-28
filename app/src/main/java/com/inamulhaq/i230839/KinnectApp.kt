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
    private var messageWatch: Watch? = null
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
        messageWatch?.stop()
        messageWatch = null
        watchedUid = uid
        uid ?: return
        Push.saveToken()
        watchNewMessages(uid)
        val pointer: DatabaseReference = Db.calls.child(uid)
        callWatch = Watch().on(pointer) { snapshot ->
            val callId = snapshot.getValue(String::class.java) ?: return@on
            Db.callSessions.child(callId).get().addOnSuccessListener { sessionSnap ->
                val session = sessionSnap.getValue(CallSession::class.java)?.copy(id = callId) ?: return@addOnSuccessListener
                val fresh = System.currentTimeMillis() - session.createdAt < CallSession.RING_TIMEOUT_MS
                if (session.status != CallSession.STATUS_RINGING || session.calleeId != uid || !fresh) return@addOnSuccessListener
                if (inForeground) {
                    Notifier.cancelCall(this)
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

    /**
     * In-app notification for a new message while you are on another Kinnect screen.
     * (When the app is in the background, FCM delivers it through [KinnectMessagingService].)
     */
    private fun watchNewMessages(uid: String) {
        val lastTimes = hashMapOf<String, Long>()
        var baseline = true
        messageWatch = Watch().on(Db.userChats.child(uid)) { snapshot ->
            snapshot.children.forEach { child ->
                val other = child.key ?: return@forEach
                val summary = child.getValue(ChatSummary::class.java) ?: return@forEach
                val previous = lastTimes.put(other, summary.lastTime)
                val isNew = !baseline && summary.lastTime > (previous ?: 0L) && summary.unread > 0
                if (isNew && inForeground && Push.openChatUid != other) {
                    UserCache.get(other) { user ->
                        val alert = summary.lastMessage.contains("took a screenshot")
                        Notifier.message(
                            this, other, user.fullName, summary.lastMessage,
                            if (alert) Notifier.CHANNEL_ALERTS else Notifier.CHANNEL_MESSAGES
                        )
                    }
                }
            }
            baseline = false
        }
    }
}
