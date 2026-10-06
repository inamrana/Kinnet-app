package com.inamulhaq.i230839

import android.os.Bundle

/** 03 · Sign up — back / "Log in" return to Log in, "Create account" enters Home. */
class SignUpActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        tapCloses(R.id.btn_back, R.id.link_login)
        onTap(R.id.btn_create) { enterHome() }
    }
}
