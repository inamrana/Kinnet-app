package com.inamulhaq.i230839

import android.os.Bundle

/** 08 · Photo picker — Cancel and Next return to Create post. */
class PhotoPickerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_picker)

        tapCloses(R.id.btn_cancel, R.id.btn_next)
    }
}
