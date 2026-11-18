package com.kinnect.i230577_i230839

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * App entry point: keeps the user's online status in step with whether any Kinnect screen is on
 * screen and, while one is, polls the web services so an incoming call rings and new messages
 * show an in-app notification.
 */
class KinnectApp : Application() {

    private var startedScreens = 0
    private var watchedUid: String? = null

    /** True while any Kinnect screen is visible. */
    val inForeground get() = startedScreens > 0

    override fun onCreate() {
        super.onCreate()
        LocalDb.init(this)
        Session.init(this)
        Images.init(this)
        Outbox.init(this)
        Notifier.createChannels(this)
        Friends // registers for sync results
        Sync.start(this)
        onSignedIn()
        // The server rejected our token (expired or logged out elsewhere): back to Log in
        Api.onUnauthorized = {
            Session.clear()
            LocalDb.clearAll()
            startActivity(Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedScreens++ == 0 && Session.isLoggedIn) {
                    Presence.goOnline()
                    startPolling()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (--startedScreens == 0) {
                    stopPolling()
                    if (Session.isLoggedIn) Presence.goAway()
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Called at start-up and after logging in or signing up. */
    fun onSignedIn() {
        watchedUid = Session.uid
        lastTimes.clear()
        chatBaseline = true
        shownCallId = null
        if (Session.isLoggedIn) {
            Push.saveToken()
            Sync.flush()
            if (inForeground) {
                Presence.goOnline()
                startPolling()
            }
        } else {
            stopPolling()
        }
    }

    // ---- while a Kinnect screen is open ------------------------------------------------------
    // Closed or in the background, FCM pushes from the PHP backend do this job instead
    // (see KinnectMessagingService).

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var ticks = 0
    private var shownCallId: String? = null
    private val lastTimes = hashMapOf<String, Long>()
    private var chatBaseline = true

    private val poll = object : Runnable {
        override fun run() {
            if (!polling || !Session.isLoggedIn) return
            checkIncomingCall()
            // Chats change less urgently than a ringing phone: every 4th tick
            if (ticks++ % 4 == 0) checkNewMessages()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun startPolling() {
        if (polling || !Session.isLoggedIn) return
        polling = true
        ticks = 0
        handler.post(poll)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(poll)
    }

    /** A call ringing for me opens the Incoming call screen (once per call). */
    private fun checkIncomingCall() {
        val uid = watchedUid ?: return
        Calls.incoming { session ->
            session ?: return@incoming
            val fresh = System.currentTimeMillis() - session.createdAt < CallSession.RING_TIMEOUT_MS
            if (session.calleeId != uid || session.id == shownCallId || session.id == IncomingCallActivity.showing || !fresh || !inForeground) return@incoming
            shownCallId = session.id
            Notifier.cancelCall(this)
            startActivity(
                Intent(this, IncomingCallActivity::class.java)
                    .putExtra(CallScreenActivity.EXTRA_CALL_ID, session.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** In-app notification for a new message while you are on another Kinnect screen. */
    private fun checkNewMessages() {
        Api.get("chats/list.php") { result ->
            val chats = result.json?.optJSONArray("chats")?.takeIf { result.ok } ?: return@get
            for (i in 0 until chats.length()) {
                val chat = chats.getJSONObject(i)
                val other = chat.optString("otherUid")
                val lastTime = chat.optLong("lastTime")
                val lastMessage = chat.optString("lastMessage")
                val previous = lastTimes.put(other, lastTime)
                val isNew = !chatBaseline && lastTime > (previous ?: 0L) && chat.optInt("unread") > 0
                if (!isNew || !inForeground || Push.openChatUid == other) continue
                val name = chat.optJSONObject("user")?.optString("fullName").orEmpty().ifEmpty { "Kinnect" }
                val alert = lastMessage.contains("took a screenshot")
                Notifier.message(
                    this, other, name, lastMessage,
                    if (alert) Notifier.CHANNEL_ALERTS else Notifier.CHANNEL_MESSAGES
                )
            }
            chatBaseline = false
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
