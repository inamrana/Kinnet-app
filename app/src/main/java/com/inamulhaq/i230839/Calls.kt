package com.inamulhaq.i230839

import android.content.Context
import android.content.Intent
import com.google.firebase.database.ServerValue

/**
 * One call in callSessions/{callId}. The Agora channel name is the call id.
 * calls/{calleeUid} points at the call while it is ringing, so the callee's app can show it.
 */
data class CallSession(
    val id: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val video: Boolean = false,
    val status: String = STATUS_RINGING,
    val createdAt: Long = 0L,
    val acceptedAt: Long = 0L,
    val endedAt: Long = 0L,
) {
    fun otherThan(uid: String) = if (callerId == uid) calleeId else callerId

    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
        const val STATUS_MISSED = "missed"
        const val RING_TIMEOUT_MS = 40_000L
    }
}

/** A row of call history in callLogs/{uid}/{callId}. */
data class CallLog(
    val otherUid: String = "",
    val video: Boolean = false,
    val outgoing: Boolean = false,
    val status: String = "",
    val durationSec: Long = 0L,
    val timestamp: Long = 0L,
)

/** Call signalling over the Realtime Database; the audio / video itself goes through Agora. */
object Calls {

    /** Starts ringing [otherUid] and opens the call screen. */
    fun start(context: Context, otherUid: String, video: Boolean) {
        val me = Db.uid ?: return
        val id = Db.callSessions.push().key ?: return
        Db.update(
            mapOf(
                "callSessions/$id" to mapOf(
                    "callerId" to me,
                    "calleeId" to otherUid,
                    "video" to video,
                    "status" to CallSession.STATUS_RINGING,
                    "createdAt" to ServerValue.TIMESTAMP,
                ),
                "calls/$otherUid" to id,
            )
        )
        open(context, id, otherUid, video, outgoing = true)
    }

    fun open(context: Context, callId: String, otherUid: String, video: Boolean, outgoing: Boolean) {
        val screen = if (video) VideoCallActivity::class.java else VoiceCallActivity::class.java
        context.startActivity(
            Intent(context, screen)
                .putExtra(CallScreenActivity.EXTRA_CALL_ID, callId)
                .putExtra(CallScreenActivity.EXTRA_OTHER_UID, otherUid)
                .putExtra(CallScreenActivity.EXTRA_OUTGOING, outgoing)
                .addFlags(if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun accept(session: CallSession) {
        val me = Db.uid ?: return
        Db.update(
            mapOf(
                "callSessions/${session.id}/status" to CallSession.STATUS_ACCEPTED,
                "callSessions/${session.id}/acceptedAt" to ServerValue.TIMESTAMP,
                "calls/$me" to null,
            )
        )
    }

    fun decline(session: CallSession) {
        val me = Db.uid ?: return
        Db.update(
            mapOf(
                "callSessions/${session.id}/status" to CallSession.STATUS_DECLINED,
                "callSessions/${session.id}/endedAt" to ServerValue.TIMESTAMP,
                "calls/$me" to null,
            )
        )
        log(session, CallSession.STATUS_DECLINED, 0)
    }

    /**
     * Hangs up. A call that was never answered counts as missed. The caller also posts the
     * call in the chat ("Voice call · 2:31" / "Missed video call").
     */
    fun end(session: CallSession, durationSec: Long) {
        val me = Db.uid ?: return
        val answered = session.status == CallSession.STATUS_ACCEPTED
        val finalStatus = when {
            session.status == CallSession.STATUS_DECLINED -> CallSession.STATUS_DECLINED
            answered -> CallSession.STATUS_ENDED
            else -> CallSession.STATUS_MISSED
        }
        if (session.status == CallSession.STATUS_RINGING || answered) {
            Db.update(
                mapOf(
                    "callSessions/${session.id}/status" to finalStatus,
                    "callSessions/${session.id}/endedAt" to ServerValue.TIMESTAMP,
                    "calls/${session.calleeId}" to null,
                )
            )
        }
        log(session, finalStatus, durationSec)
        if (session.callerId == me) {
            val kind = if (session.video) "video call" else "voice call"
            val text = when (finalStatus) {
                CallSession.STATUS_ENDED -> "📞 ${kind.replaceFirstChar { it.uppercase() }} · ${duration(durationSec)}"
                CallSession.STATUS_DECLINED -> "📞 ${kind.replaceFirstChar { it.uppercase() }} declined"
                else -> "📞 Missed $kind"
            }
            ChatSender.send(session.calleeId, Message.TYPE_SYSTEM, text = text, preview = text, bumpUnread = finalStatus == CallSession.STATUS_MISSED)
        }
    }

    private fun log(session: CallSession, status: String, durationSec: Long) {
        val me = Db.uid ?: return
        Db.callLogs.child(me).child(session.id).setValue(
            mapOf(
                "otherUid" to session.otherThan(me),
                "video" to session.video,
                "outgoing" to (session.callerId == me),
                "status" to status,
                "durationSec" to durationSec,
                "timestamp" to ServerValue.TIMESTAMP,
            )
        )
    }

    /** 151 -> "2:31" */
    fun duration(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
}
