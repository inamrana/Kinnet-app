package com.inamulhaq.i230839

import android.os.Bundle

/** 22 · Voice call — the red button ends the call and returns to the Chat. */
class VoiceCallActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        tapCloses(R.id.btn_end_call)
    }
}
