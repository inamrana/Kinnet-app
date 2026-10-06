package com.inamulhaq.i230839

import android.os.Bundle

/** 05 · Reaction picker — picking a reaction, tapping the dimmed post or Back returns to the feed. */
class ReactionPickerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reaction_picker)

        tapCloses(
            R.id.scrim, R.id.btn_back,
            R.id.react_like, R.id.react_love, R.id.react_haha,
            R.id.react_wow, R.id.react_sad, R.id.react_angry
        )
    }
}
