package com.inamulhaq.i230839

import android.os.Bundle

/** 15 · Your profile — "Edit profile" opens Edit profile, "Add to story" opens the Camera. */
class ProfileActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tapCloses(R.id.btn_back)
        tapOpens(EditProfileActivity::class.java, R.id.btn_edit_profile)
        tapOpens(CameraActivity::class.java, R.id.btn_add_story)
    }
}
