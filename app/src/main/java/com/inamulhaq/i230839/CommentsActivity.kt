package com.inamulhaq.i230839

import android.os.Bundle

/** 06 · Comments — threaded comments on Lina's post; Back returns to Home. */
class CommentsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        tapCloses(R.id.btn_back)
    }
}
