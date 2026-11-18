package com.kinnect.i230577_i230839

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/** Incoming call — shows who is calling with Decline / Accept. Closes itself if the caller hangs up. */
class IncomingCallActivity : BaseActivity() {

    private val callId by lazy { intent.getStringExtra(CallScreenActivity.EXTRA_CALL_ID).orEmpty() }
    private var watch: Watch? = null
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
        showing = callId
        Notifier.cancelCall(this)

        onTap(R.id.btn_decline) { session?.let(Calls::decline); finish() }
        onTap(R.id.btn_accept) { accept() }

        watch = Calls.watch(callId, onGone = { finish() }) { current ->
            if (current.status != CallSession.STATUS_RINGING) {
                if (current.status != CallSession.STATUS_ACCEPTED) finish()
                return@watch
            }
            val first = session == null
            session = current
            if (!first) return@watch
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
        findViewById<android.view.View>(R.id.btn_accept).isEnabled = false
        Calls.accept(current) { accepted ->
            if (accepted?.status == CallSession.STATUS_ACCEPTED) {
                Calls.open(this, current.id, current.callerId, current.video, outgoing = false)
            } else {
                toast("This call has ended")
            }
            finish()
        }
    }

    override fun onDestroy() {
        if (showing == callId) showing = null
        ringtone?.stop()
        watch?.stop()
        super.onDestroy()
    }

    companion object {
        /** The call this screen is showing, so the app doesn't open a second one for it. */
        @Volatile var showing: String? = null
    }
}
