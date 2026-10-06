package com.inamulhaq.i230839

import android.os.Bundle

/** 17 · Another user's profile (Omar) — "Message" opens the Chats list; Back returns. */
class OtherProfileActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_profile)

        tapCloses(R.id.btn_back)
        tapOpens(ChatsActivity::class.java, R.id.btn_message)
    }
}
