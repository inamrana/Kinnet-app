package com.kinnect.i230577_i230839

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Offline queue. Every user action is first saved in SQLite (pending_actions) with a
 * client-generated UUID, shown in the UI straight away as "pending", then sent to the server
 * in the order it happened. If the phone is offline the queue simply waits; as soon as a
 * network comes back it is flushed automatically. The server remembers each UUID, so an
 * action that is retried (for example after a timeout) is never applied twice.
 */
object Sync {

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Done) -> Unit>()
    @Volatile private var running = false
    @Volatile private var again = false

    /** One queued action that reached the server (or was rejected by it). */
    class Done(val type: String, val target: String, val response: JSONObject?, val rejectedMessage: String?)

    fun start(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                finishLogout()
                flush()
            }
        })
        finishLogout()
        flush()
    }

    /**
     * Invalidates [token] on the server and marks the account offline. If the phone is offline
     * right now, the token is kept (encrypted) and the logout is retried when the network returns.
     */
    fun logOutOnServer(token: String) {
        Session.pendingLogoutToken = token
        finishLogout()
    }

    private fun finishLogout() {
        val token = Session.pendingLogoutToken ?: return
        worker.execute {
            val result = Api.request("POST", "auth/logout.php", emptyMap(), token = token)
            if (result.ok || result.code == 401) Session.pendingLogoutToken = null
        }
    }

    /**
     * Saves the action and tries to send it. [type] groups actions for "pending" badges
     * (e.g. "friend"), [target] is what it is about (e.g. the other user's id).
     */
    fun enqueue(type: String, endpoint: String, fields: Map<String, Any?>, target: String = ""): String {
        val clientId = UUID.randomUUID().toString()
        LocalDb.addPending(clientId, type, endpoint, Api.toJson(fields), target)
        flush()
        return clientId
    }

    fun isPending(type: String, target: String) = target in LocalDb.pendingTargets(type)

    fun addListener(listener: (Done) -> Unit) { listeners += listener }
    fun removeListener(listener: (Done) -> Unit) { listeners -= listener }

    /** Sends queued actions oldest first; stops at the first one that can't reach the server. */
    fun flush() {
        if (!Session.isLoggedIn) return
        if (running) { again = true; return }
        running = true
        worker.execute {
            var reachedServer = true
            try {
                for (action in LocalDb.pending()) {
                    val fields = buildMap<String, Any?> {
                        action.payload.keys().forEach { put(it, action.payload.get(it)) }
                        put("clientId", action.clientId)
                    }
                    val result = Api.request("POST", action.endpoint, fields)
                    when {
                        result.ok -> {
                            LocalDb.removePending(action.localId)
                            notify(Done(action.type, action.target, result.json, null))
                        }
                        result.offline || result.code >= 500 -> {
                            // Keep it, in order, and try again when the network or server is back
                            LocalDb.markAttempt(action.localId, result.message)
                            reachedServer = false
                            break
                        }
                        result.code == 401 -> { reachedServer = false; break }
                        else -> {
                            // The server refused it (e.g. edit window passed): drop it so the queue moves on
                            LocalDb.removePending(action.localId)
                            notify(Done(action.type, action.target, result.json, result.message))
                        }
                    }
                }
            } finally {
                running = false
                // Something was queued while we were busy: send it now too
                if (again && reachedServer) { again = false; flush() }
            }
        }
    }

    private fun notify(done: Done) = main.post { listeners.toList().forEach { it(done) } }
}
