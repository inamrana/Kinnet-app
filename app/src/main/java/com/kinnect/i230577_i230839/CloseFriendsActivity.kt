package com.kinnect.i230577_i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Close friends — tick the friends who can see your "Close friends" stories (stories/close_friends.php). */
class CloseFriendsActivity : BaseActivity() {

    private var chosen = setOf<String>()
    private var friends = listOf<String>()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_friends)
        list = findViewById(R.id.close_friends_list)
        tapCloses(R.id.btn_back)
        if (Session.uid == null) { finish(); return }

        friends = LocalDb.links("friends").map { it.userId }
        show()
        Friends.refresh { friends = LocalDb.links("friends").map { it.userId }; show() }
        Stories.closeFriends { chosen = it; show() }
    }

    private fun show() {
        list.removeAllViews()
        friends.forEach { uid ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_close_friend, list, false)
            val check = row.findViewById<CheckBox>(R.id.close_check)
            check.isChecked = uid in chosen
            row.setOnClickListener {
                // Tick straight away; the server's list replaces it when it answers
                chosen = if (uid in chosen) chosen - uid else chosen + uid
                show()
                Stories.setCloseFriend(uid, uid in chosen) { chosen = it; show() }
            }
            UserCache.get(uid) { user ->
                Avatars.show(row.findViewById(R.id.close_avatar), user)
                row.findViewById<TextView>(R.id.close_name).text = user.fullName
                row.findViewById<TextView>(R.id.close_username).text = "@${user.username}"
            }
            list.addView(row)
        }
        findViewById<View>(R.id.close_friends_empty).visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.close_friends_count).text =
            if (chosen.isEmpty()) "" else "${chosen.count { it in friends }} chosen"
    }
}
