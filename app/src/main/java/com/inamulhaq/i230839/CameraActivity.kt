package com.inamulhaq.i230839

import android.os.Bundle

/** 09 · Camera — the shutter opens the Story editor; X goes back. */
class CameraActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        tapCloses(R.id.btn_close)
        tapOpens(StoryEditorActivity::class.java, R.id.btn_shutter)
        tapOpens(PhotoPickerActivity::class.java, R.id.btn_gallery)
    }
}
