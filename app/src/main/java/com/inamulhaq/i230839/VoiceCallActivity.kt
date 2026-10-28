package com.inamulhaq.i230839

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageView
import androidx.core.content.ContextCompat

/** 22 · Voice call — an Agora audio call. Speaker switches between earpiece and loudspeaker. */
class VoiceCallActivity : CallScreenActivity() {

    override val isVideo = false
    override val layout = R.layout.activity_voice_call
    private var speaker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onTap(R.id.btn_speaker) { toggleSpeaker() }
        onTap(R.id.btn_call_chat) { openChat() }
    }

    private fun toggleSpeaker() {
        speaker = !speaker
        engine?.setEnableSpeakerphone(speaker)
        findViewById<ImageView>(R.id.btn_speaker).apply {
            backgroundTintList = if (speaker) ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)) else null
            setColorFilter(ContextCompat.getColor(context, if (speaker) R.color.teal else R.color.white))
        }
        toast(if (speaker) "Speaker on" else "Speaker off")
    }
}
