package com.inamulhaq.i230839

import android.os.Bundle

/** 14 · Friends tab — tapping a friend request (or a suggestion) opens that user's profile. */
class FriendsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        setupTopTabs(selected = 1)

        tapOpens(
            OtherProfileActivity::class.java,
            R.id.request_sara, R.id.request_bilal, R.id.request_noor, R.id.request_daniyal,
            R.id.suggest_omar_siddiqui, R.id.suggest_maya
        )
    }
}
