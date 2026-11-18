package com.kinnect.i230577_i230839

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Keeps this device's FCM token on the Kinnect server so the PHP backend can push to it. */
object Push {

    /** Which chat is open right now, so we don't notify about messages you are already reading. */
    @Volatile var openChatUid: String? = null

    /** Sends this device's FCM token to the PHP backend, which uses it to push notifications. */
    fun saveToken() {
        if (!Session.isLoggedIn) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Api.post("fcm/token.php", mapOf("token" to token)) {}
        }
    }
}

/**
 * Receives Kinnect's push messages (sent by the PHP backend, see push_to in backend/lib.php) and turns them into notifications:
 * "message" (new chat message or screenshot alert) and "call" (incoming call).
 * While the app is open, in-app notifications come from [KinnectApp] instead, so nothing shows twice.
 */
class KinnectMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        if (Session.isLoggedIn) Api.post("fcm/token.php", mapOf("token" to token)) {}
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val app = application as KinnectApp
        when (data["type"]) {
            "call" -> {
                val callId = data["callId"].orEmpty()
                if (callId.isNotEmpty() && !app.inForeground) {
                    Notifier.incomingCall(this, callId, data["title"].orEmpty(), data["video"] == "true")
                }
            }
            else -> {
                if (app.inForeground) return
                val from = data["fromUid"].orEmpty()
                val channel = if (data["kind"] == "screenshot") Notifier.CHANNEL_ALERTS else Notifier.CHANNEL_MESSAGES
                Notifier.message(this, from, data["title"] ?: "Kinnect", data["body"].orEmpty(), channel)
            }
        }
    }
}
