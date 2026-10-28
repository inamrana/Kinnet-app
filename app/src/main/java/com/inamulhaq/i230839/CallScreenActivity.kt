package com.inamulhaq.i230839

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

/**
 * Everything the Voice call (22) and Video call screens share: following the call's status in
 * callSessions, joining the Agora channel once the call is accepted, the timer, mute and hang up.
 */
abstract class CallScreenActivity : BaseActivity() {

    protected val callId by lazy { intent.getStringExtra(EXTRA_CALL_ID).orEmpty() }
    protected val otherUid by lazy { intent.getStringExtra(EXTRA_OTHER_UID).orEmpty() }
    private val outgoing by lazy { intent.getBooleanExtra(EXTRA_OUTGOING, false) }

    protected abstract val isVideo: Boolean
    protected abstract val layout: Int

    protected var engine: RtcEngine? = null
    private val watch = Watch()
    private val handler = Handler(Looper.getMainLooper())
    private var session: CallSession? = null
    private var joined = false
    private var connectedAt = 0L
    private var finished = false
    protected var muted = false

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermissions()) joinIfReady() else {
            toast("Microphone permission is needed for calls")
            hangUp()
        }
    }

    private val handlerCallbacks = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) = runOnUiThread {
            connectedAt = System.currentTimeMillis()
            handler.post(tick)
            onRemoteJoined(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) = runOnUiThread { hangUp() }

        override fun onError(err: Int) = runOnUiThread { setStatus("Connection problem ($err)") }
    }

    private val tick = object : Runnable {
        override fun run() {
            setStatus(Calls.duration((System.currentTimeMillis() - connectedAt) / 1000))
            handler.postDelayed(this, 1000)
        }
    }

    private val ringTimeout = Runnable { if (session?.status == CallSession.STATUS_RINGING) hangUp() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (callId.isEmpty() || otherUid.isEmpty() || Db.uid == null) { finish(); return }

        onTap(R.id.btn_end_call) { hangUp() }
        onTap(R.id.btn_mute) { toggleMute() }
        setStatus(if (outgoing) "Calling…" else "Connecting…")

        UserCache.get(otherUid) { user ->
            Avatars.show(findViewById(R.id.call_avatar), user, keepShape = true)
            findViewById<TextView>(R.id.call_name).text = user.fullName
        }
        watch.on(Db.callSessions.child(callId)) { snapshot ->
            val current = snapshot.getValue(CallSession::class.java)?.copy(id = callId) ?: return@on
            session = current
            when (current.status) {
                CallSession.STATUS_RINGING -> if (outgoing) setStatus("Ringing…")
                CallSession.STATUS_ACCEPTED -> joinIfReady()
                CallSession.STATUS_DECLINED -> { toast("Call declined"); hangUp() }
                else -> hangUp()
            }
        }
        if (outgoing) handler.postDelayed(ringTimeout, CallSession.RING_TIMEOUT_MS)
        if (!hasPermissions()) askPermissions.launch(permissions())
    }

    private fun permissions(): Array<String> =
        if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)

    private fun hasPermissions() = permissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Joins the Agora channel once the call is accepted and permissions are granted. */
    private fun joinIfReady() {
        if (joined || finished || session?.status != CallSession.STATUS_ACCEPTED || !hasPermissions()) return
        if (BuildConfig.AGORA_APP_ID.isEmpty()) {
            setStatus("Add your Agora App ID to agora.properties")
            return
        }
        joined = true
        handler.removeCallbacks(ringTimeout)
        setStatus("Connecting…")
        val created = runCatching {
            RtcEngine.create(RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = BuildConfig.AGORA_APP_ID
                mEventHandler = handlerCallbacks
            })
        }.getOrNull()
        if (created == null) {
            setStatus("Could not start the call")
            return
        }
        engine = created
        created.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        if (isVideo) {
            created.enableVideo()
            setupLocalVideo(created)
        } else {
            created.disableVideo()
            created.setDefaultAudioRoutetoSpeakerphone(false)
        }
        val options = ChannelMediaOptions().apply {
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            publishMicrophoneTrack = true
            publishCameraTrack = isVideo
            autoSubscribeAudio = true
            autoSubscribeVideo = isVideo
        }
        // Testing-mode Agora projects need no token; the call id is the channel name
        created.joinChannel(null, callId, 0, options)
    }

    protected open fun setupLocalVideo(engine: RtcEngine) = Unit
    protected open fun onRemoteJoined(uid: Int) = Unit

    private fun toggleMute() {
        muted = !muted
        engine?.muteLocalAudioStream(muted)
        findViewById<View>(R.id.btn_mute)?.backgroundTintList =
            if (muted) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)) else null
        findViewById<android.widget.ImageView>(R.id.btn_mute_icon)?.setColorFilter(
            ContextCompat.getColor(this, if (muted) R.color.teal else R.color.white)
        ) ?: findViewById<android.widget.ImageView>(R.id.btn_mute)?.setColorFilter(
            ContextCompat.getColor(this, if (muted) R.color.teal else R.color.white)
        )
        toast(if (muted) "Muted" else "Unmuted")
    }

    protected fun setStatus(text: String) {
        findViewById<TextView>(R.id.call_status)?.text = text
    }

    /** Ends the call for both people and leaves the Agora channel. */
    protected fun hangUp() {
        if (finished) return
        finished = true
        val duration = if (connectedAt > 0) (System.currentTimeMillis() - connectedAt) / 1000 else 0
        session?.let { Calls.end(it, duration) }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        watch.stop()
        if (!finished) {
            finished = true
            session?.let { Calls.end(it, if (connectedAt > 0) (System.currentTimeMillis() - connectedAt) / 1000 else 0) }
        }
        engine?.let {
            it.leaveChannel()
            handler.post { RtcEngine.destroy() }
        }
        engine = null
        super.onDestroy()
    }

    /** Opens the chat with the other person (the call keeps running). */
    protected fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_UID, otherUid))
    }

    companion object {
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_OTHER_UID = "otherUid"
        const val EXTRA_OUTGOING = "outgoing"
    }
}
