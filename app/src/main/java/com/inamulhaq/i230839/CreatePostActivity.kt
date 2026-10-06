package com.inamulhaq.i230839

import android.os.Bundle

/** 07 · Create post — opens the Photo picker and the Camera; X / Post return to Home. */
class CreatePostActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        tapCloses(R.id.btn_close, R.id.btn_post)
        tapOpens(PhotoPickerActivity::class.java, R.id.opt_photo)
        tapOpens(CameraActivity::class.java, R.id.opt_camera)
    }
}
