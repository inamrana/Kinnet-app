package com.inamulhaq.i230839

import android.os.Bundle

/** 23 · Marketplace tab — Today's picks near Karachi. */
class MarketplaceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marketplace)
        setupTopTabs(selected = 2)
    }
}
