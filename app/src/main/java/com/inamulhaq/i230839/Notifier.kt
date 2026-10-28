package com.inamulhaq.i230839

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** System notifications: new messages, incoming calls and screenshot alerts. */
object Notifier {

    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_CALLS = "calls"
    const val CHANNEL_ALERTS = "alerts"
    private const val CALL_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Privacy alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Aisha Khan: Sending the photos tonight" — tapping opens the chat. */
    fun message(context: Context, fromUid: String, title: String, text: String, channel: String = CHANNEL_MESSAGES) {
        if (!allowed(context)) return
        val open = Intent(context, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_UID, fromUid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, fromUid.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_logo_rings)
            .setColor(ContextCompat.getColor(context, R.color.teal))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(fromUid.hashCode() + channel.hashCode(), notification) }
    }

    /** A ringing notification that opens the Incoming call screen (also over the lock screen). */
    fun incomingCall(context: Context, callId: String, callerName: String, video: Boolean) {
        if (!allowed(context)) return
        val open = Intent(context, IncomingCallActivity::class.java)
            .putExtra(CallScreenActivity.EXTRA_CALL_ID, callId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, CALL_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_phone)
            .setColor(ContextCompat.getColor(context, R.color.teal))
            .setContentTitle(callerName)
            .setContentText(if (video) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(CallSession.RING_TIMEOUT_MS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(CALL_ID, notification) }
    }

    fun cancelCall(context: Context) {
        NotificationManagerCompat.from(context).cancel(CALL_ID)
    }
}
