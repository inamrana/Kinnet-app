package com.kinnect.i230577_i230839

import android.os.Bundle
import android.widget.TextView

/** 19 · Menu tab — shows who is signed in, opens your Profile, jumps to tabs and logs out to Log in. */
class MenuActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        setupTopTabs(selected = 4)

        tapOpens(ProfileActivity::class.java, R.id.menu_profile)
        onTap(R.id.menu_marketplace) { switchTab(MarketplaceActivity::class.java) }
        onTap(R.id.menu_friends) { switchTab(FriendsActivity::class.java) }
        onTap(R.id.btn_logout) { logOut() }
    }

    override fun onResume() {
        super.onResume()
        val me = Session.uid ?: return
        Users.load(me) { user ->
            findViewById<TextView>(R.id.menu_name).text = user.fullName
            Avatars.show(findViewById(R.id.menu_avatar), user)
        }
    }
}
