package com.inamulhaq.i230839

import android.os.Bundle

/** 16 · Edit profile — Cancel and Save return to your Profile. */
class EditProfileActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        tapCloses(R.id.btn_cancel, R.id.btn_save)
    }
}
