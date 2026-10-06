package com.inamulhaq.i230839

import android.os.Bundle

/** 13 · Search — people results open another user's profile. */
class SearchActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        tapCloses(R.id.btn_back)
        tapOpens(
            OtherProfileActivity::class.java,
            R.id.result_omar_farooq, R.id.result_omar_siddiqui, R.id.result_omar_tariq
        )
    }
}
