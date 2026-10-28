package com.inamulhaq.i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Close friends — tick the friends who can see your "Close friends" stories (closeFriends/{uid}). */
class CloseFriendsActivity : BaseActivity() {

    private val watch = Watch()
    private var chosen = setOf<String>()
    private var friends = listOf<String>()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_friends)
        list = findViewById(R.id.close_friends_list)
        tapCloses(R.id.btn_back)

        val me = Db.uid ?: run { finish(); return }
        watch.on(Db.friends.child(me)) { snapshot ->
            friends = snapshot.children.mapNotNull { it.key }
            show()
        }
        watch.on(Db.closeFriends.child(me)) { snapshot ->
            chosen = snapshot.children.mapNotNull { it.key }.toSet()
            show()
        }
    }

    private fun show() {
        val me = Db.uid ?: return
        list.removeAllViews()
        friends.forEach { uid ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_close_friend, list, false)
            val check = row.findViewById<CheckBox>(R.id.close_check)
            check.isChecked = uid in chosen
            row.setOnClickListener {
                val ref = Db.closeFriends.child(me).child(uid)
                if (uid in chosen) ref.removeValue() else ref.setValue(true)
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

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }
}
