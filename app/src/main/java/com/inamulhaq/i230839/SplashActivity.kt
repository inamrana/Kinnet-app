package com.inamulhaq.i230839

import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** 01 · Splash — shows the brand for a moment, then opens Log in automatically. */
class SplashActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val openLogin = Runnable {
        open(LoginActivity::class.java)
        finish() // Splash is not kept in the back stack
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(openLogin, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openLogin)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DELAY_MS = 1500L
    }
}
