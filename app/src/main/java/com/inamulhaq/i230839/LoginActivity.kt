package com.inamulhaq.i230839

import android.os.Bundle

/** 02 · Log in — "Log in" (or the recent login card) enters Home, "Create new account" opens Sign up. */
class LoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        onTap(R.id.btn_login) { enterHome() }
        onTap(R.id.recent_login_card) { enterHome() }
        tapOpens(SignUpActivity::class.java, R.id.btn_create_account)
    }
}
