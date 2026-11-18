package com.kinnect.i230577_i230839

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject

/**
 * One call from the calls web services (calls/start.php, calls/get.php …). The Agora channel name
 * is the call id. The server keeps every call, so the same rows are the call history
 * (calls/logs.php) with how each one ended and how long it lasted.
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
    val durationSec: Long = 0L,
) {
    fun otherThan(uid: String) = if (callerId == uid) calleeId else callerId

    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
        const val STATUS_MISSED = "missed"
        const val RING_TIMEOUT_MS = 40_000L

        fun from(json: JSONObject) = CallSession(
            id = json.optString("id"),
            callerId = json.optString("callerId"),
            calleeId = json.optString("calleeId"),
            video = json.optBoolean("video"),
            status = json.optString("status", STATUS_RINGING),
            createdAt = json.optLong("createdAt"),
            acceptedAt = json.optLong("acceptedAt"),
            endedAt = json.optLong("endedAt"),
            durationSec = json.optLong("durationSec"),
        )
    }
}

/** A row of call history from calls/logs.php. */
data class CallLog(
    val callId: String = "",
    val otherUid: String = "",
    val video: Boolean = false,
    val outgoing: Boolean = false,
    val status: String = "",
    val durationSec: Long = 0L,
    val timestamp: Long = 0L,
)

/** Call signalling over the PHP web services; the audio / video itself goes through Agora. */
object Calls {

    /** How often an open call screen asks the server for the call's status. */
    private const val POLL_MS = 1_500L

    /** Starts ringing [otherUid] and opens the call screen once the server has created the call. */
    fun start(context: Context, otherUid: String, video: Boolean) {
        if (Session.uid == null) return
        Api.post("calls/start.php", mapOf("to" to otherUid, "video" to if (video) "1" else "0")) { result ->
            val call = result.json?.optJSONObject("call")?.takeIf { result.ok }?.let(CallSession::from)
            if (call == null) {
                val message = if (result.offline) "You're offline, so the call can't start" else result.message
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return@post
            }
            open(context, call.id, otherUid, video, outgoing = true)
        }
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

    /**
     * Follows call [callId] until the returned [Watch] is stopped: [onCall] runs with the call as
     * the server sees it, straight away and then every [POLL_MS]. [onGone] runs if the call
     * doesn't exist (or isn't ours).
     */
    fun watch(callId: String, onGone: () -> Unit = {}, onCall: (CallSession) -> Unit): Watch {
        val handler = Handler(Looper.getMainLooper())
        var stopped = false
        val poll = object : Runnable {
            override fun run() {
                Api.get("calls/get.php", mapOf("id" to callId)) { result ->
                    if (stopped) return@get
                    val call = result.json?.optJSONObject("call")?.takeIf { result.ok }
                    when {
                        call != null -> onCall(CallSession.from(call))
                        result.code == 404 -> { onGone(); return@get }
                    }
                    if (!stopped) handler.postDelayed(this, POLL_MS)
                }
            }
        }
        handler.post(poll)
        return Watch().onStop { stopped = true; handler.removeCallbacksAndMessages(null) }
    }

    /** The call ringing for the signed-in user right now, or null. */
    fun incoming(done: (CallSession?) -> Unit) {
        Api.get("calls/incoming.php") { result ->
            if (result.ok) done(result.json?.optJSONObject("call")?.let(CallSession::from))
        }
    }

    fun accept(session: CallSession, done: (CallSession?) -> Unit) = update(session.id, "accept", done)

    fun decline(session: CallSession) = update(session.id, "decline") {}

    /**
     * Hangs up. The server decides how the call ended (answered → "ended", still ringing →
     * "missed") and how long it lasted, then the caller posts it in the chat
     * ("Voice call · 2:31" / "Missed video call").
     */
    fun end(session: CallSession) {
        val me = Session.uid ?: return
        update(session.id, "end") { final ->
            val call = final ?: return@update
            if (call.callerId != me) return@update
            val kind = if (call.video) "video call" else "voice call"
            val text = when (call.status) {
                CallSession.STATUS_ENDED -> "📞 ${kind.replaceFirstChar { it.uppercase() }} · ${duration(call.durationSec)}"
                CallSession.STATUS_DECLINED -> "📞 ${kind.replaceFirstChar { it.uppercase() }} declined"
                CallSession.STATUS_MISSED -> "📞 Missed $kind"
                else -> return@update
            }
            // Only post once per call, even if both screens hang up
            if (LocalDb.cache("callnote:${call.id}") != null) return@update
            LocalDb.putCache("callnote:${call.id}", "1")
            ChatSender.send(call.calleeId, Message.TYPE_SYSTEM, text = text, preview = text, bumpUnread = call.status == CallSession.STATUS_MISSED)
        }
    }

    private fun update(callId: String, action: String, done: (CallSession?) -> Unit) {
        Api.post("calls/update.php", mapOf("id" to callId, "action" to action)) { result ->
            done(result.json?.optJSONObject("call")?.takeIf { result.ok }?.let(CallSession::from))
        }
    }

    /** My call history, newest first. */
    fun logs(done: (List<CallLog>) -> Unit) {
        Api.get("calls/logs.php") { result ->
            val logs = result.json?.optJSONArray("logs")?.takeIf { result.ok } ?: return@get
            done((0 until logs.length()).map { i ->
                val log = logs.getJSONObject(i)
                CallLog(
                    callId = log.optString("id"),
                    otherUid = log.optString("otherUid"),
                    video = log.optBoolean("video"),
                    outgoing = log.optBoolean("outgoing"),
                    status = log.optString("status"),
                    durationSec = log.optLong("durationSec"),
                    timestamp = log.optLong("timestamp"),
                )
            })
        }
    }

    /** 151 -> "2:31" */
    fun duration(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
}
