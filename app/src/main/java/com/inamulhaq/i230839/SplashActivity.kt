package com.inamulhaq.i230839

import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** 01 · Splash — shows the brand for 5 seconds, then opens Home if signed in, otherwise Log in. */
class SplashActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val openNext = Runnable {
        val me = Db.me()
        if (me != null) {
            // Signed in: straight to Home (or finish the profile setup if it was skipped)
            me.child("profileComplete").get().addOnCompleteListener { task ->
                val complete = if (task.isSuccessful) task.result.getValue(Boolean::class.java) else null
                if (complete == false) enterProfileSetup() else enterHome()
            }
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
