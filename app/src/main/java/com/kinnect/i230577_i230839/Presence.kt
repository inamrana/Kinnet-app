package com.kinnect.i230577_i230839

import android.os.Handler
import android.os.Looper

/**
 * Online / offline status through the web service. While any Kinnect screen is visible the app
 * sends a heartbeat ("online") every 30 seconds; going to the background sends "away".
 * The server treats anyone without a heartbeat for 70 seconds as offline, so a killed app or a
 * lost connection also shows as offline. Other people's status is read with users/statuses.php.
 */
object Presence {

    private const val HEARTBEAT_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false

    private val heartbeat = object : Runnable {
        override fun run() {
            if (!visible || !Session.isLoggedIn) return
            Api.post("users/presence.php", mapOf("state" to "online")) {}
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun goOnline() {
        visible = true
        handler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    /** App went to the background: show as offline with a fresh "last seen" time. */
    fun goAway() {
        visible = false
        handler.removeCallbacks(heartbeat)
        if (Session.isLoggedIn) Api.post("users/presence.php", mapOf("state" to "away")) {}
    }

    /** Log out: stop the heartbeat (the logout call itself marks the account offline). */
    fun goOffline() {
        visible = false
        handler.removeCallbacks(heartbeat)
    }

    /** Fetches online / last-seen for [ids]: id → (online, lastSeen). */
    fun statuses(ids: Collection<String>, done: (Map<String, Pair<Boolean, Long>>) -> Unit) {
        if (ids.isEmpty()) return done(emptyMap())
        Api.get("users/statuses.php", mapOf("ids" to ids.joinToString(","))) { result ->
            val statuses = result.json?.optJSONObject("statuses") ?: return@get
            done(statuses.keys().asSequence().associateWith { id ->
                val s = statuses.getJSONObject(id)
                s.optBoolean("online") to s.optLong("lastSeen")
            })
        }
    }
}
