package com.inamulhaq.i230839

import android.os.Bundle

/** 10 · Story editor — "Your story", "Close friends" or the arrow share to Your story. */
class StoryEditorActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_editor)

        tapCloses(R.id.btn_close)
        tapOpens(
            YourStoryActivity::class.java,
            R.id.share_your_story, R.id.share_close_friends, R.id.btn_share
        )
    }
}
