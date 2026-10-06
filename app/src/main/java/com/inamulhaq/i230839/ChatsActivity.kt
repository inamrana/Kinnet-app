package com.inamulhaq.i230839

import android.os.Bundle

/** 20 · Chats — each conversation opens the Chat screen. */
class ChatsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        tapCloses(R.id.btn_back)
        tapOpens(
            ChatActivity::class.java,
            R.id.chat_aisha, R.id.chat_design, R.id.chat_lina,
            R.id.chat_omar, R.id.chat_bilal, R.id.chat_noor
        )
    }
}
