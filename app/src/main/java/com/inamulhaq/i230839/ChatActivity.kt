package com.inamulhaq.i230839

import android.os.Bundle

/** 21 · Chat — the phone icon starts the Voice call. */
class ChatActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        tapCloses(R.id.btn_back)
        tapOpens(VoiceCallActivity::class.java, R.id.btn_call)
    }
}
