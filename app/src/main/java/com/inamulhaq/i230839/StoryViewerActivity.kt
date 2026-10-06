package com.inamulhaq.i230839

import android.os.Bundle

/** 11 · Story viewer — Omar's story; X returns to Home. */
class StoryViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_viewer)

        tapCloses(R.id.btn_close)
    }
}
