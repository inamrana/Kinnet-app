package com.inamulhaq.i230839

import android.os.Bundle

/** 19 · Menu tab — opens your Profile, jumps to tabs from shortcuts, and logs out to Log in. */
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
}
