package com.inamulhaq.i230839

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Keeps this device's FCM token in fcmTokens/{uid} so the Cloud Function can reach it. */
object Push {

    /** Which chat is open right now, so we don't notify about messages you are already reading. */
    @Volatile var openChatUid: String? = null

    fun saveToken() {
        val me = Db.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Db.fcmTokens.child(me).setValue(token)
        }
    }

    fun clearToken(uid: String) {
        Db.fcmTokens.child(uid).removeValue()
    }
}

/**
 * Receives Kinnect's push messages (sent by functions/index.js) and turns them into notifications:
 * "message" (new chat message or screenshot alert) and "call" (incoming call).
 * While the app is open, in-app notifications come from [KinnectApp] instead, so nothing shows twice.
 */
class KinnectMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val me = Db.uid ?: return
        Db.fcmTokens.child(me).setValue(token)
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
