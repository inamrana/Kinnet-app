package com.inamulhaq.i230839

import android.os.Bundle

/** 12 · Your story — the story you just shared; X goes back, "Create" opens the Camera again. */
class YourStoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_your_story)

        tapCloses(R.id.btn_close)
        tapOpens(CameraActivity::class.java, R.id.story_create)
    }
}
