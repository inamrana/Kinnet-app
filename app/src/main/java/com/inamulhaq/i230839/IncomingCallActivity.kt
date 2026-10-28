package com.inamulhaq.i230839

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/** Incoming call — shows who is calling with Decline / Accept. Closes itself if the caller hangs up. */
class IncomingCallActivity : BaseActivity() {

    private val callId by lazy { intent.getStringExtra(CallScreenActivity.EXTRA_CALL_ID).orEmpty() }
    private val watch = Watch()
    private var session: CallSession? = null
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and wake the phone, like a normal phone call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(R.layout.activity_incoming_call)
        if (callId.isEmpty()) { finish(); return }
        Notifier.cancelCall(this)

        onTap(R.id.btn_decline) { session?.let(Calls::decline); finish() }
        onTap(R.id.btn_accept) { accept() }

        watch.on(Db.callSessions.child(callId)) { snapshot ->
            val current = snapshot.getValue(CallSession::class.java)?.copy(id = callId)
            if (current == null || current.status != CallSession.STATUS_RINGING) {
                if (current?.status != CallSession.STATUS_ACCEPTED) finish()
                return@on
            }
            session = current
            findViewById<TextView>(R.id.call_status).text =
                if (current.video) "Incoming video call" else "Incoming voice call"
            UserCache.get(current.callerId) { caller ->
                Avatars.show(findViewById(R.id.call_avatar), caller, keepShape = true)
                findViewById<TextView>(R.id.call_name).text = caller.fullName
            }
        }
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        runCatching { ringtone?.play() }
    }

    private fun accept() {
        val current = session ?: return
        Calls.accept(current)
        Calls.open(this, current.id, current.callerId, current.video, outgoing = false)
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        watch.stop()
        super.onDestroy()
    }
}
