package com.kinnect.i230577_i230839

import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * 01 · Splash — shows the brand for 5 seconds, then opens Home if a session token is stored on
 * this device (this works offline too), otherwise Log in. If the server later says the token is no
 * longer valid, [Api.onUnauthorized] sends the user back to Log in.
 */
class SplashActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val openNext = Runnable {
        if (Session.isLoggedIn) {
            // Signed in: straight to Home (or finish the profile setup if it was skipped)
            if (Session.user?.profileComplete == false) enterProfileSetup() else enterHome()
        } else {
            open(LoginActivity::class.java)
            finish() // Splash is not kept in the back stack
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(openNext, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openNext)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DELAY_MS = 5000L
    }
}
