package com.inamulhaq.i230839

import android.os.Bundle

/** 18 · Notifications tab — the friend request notification opens Sara's profile. */
class NotificationsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        setupTopTabs(selected = 3)

        tapOpens(OtherProfileActivity::class.java, R.id.notif_friend_request)
        tapOpens(CommentsActivity::class.java, R.id.notif_reply)
    }
}
