package com.inamulhaq.i230839

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.database.FirebaseDatabase

/**
 * App entry point: enables offline caching and keeps the user's online status in step with
 * whether any Kinnect screen is on screen.
 */
class KinnectApp : Application() {

    private var startedScreens = 0

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedScreens++ == 0 && Db.uid != null) Presence.goOnline()
            }

            override fun onActivityStopped(activity: Activity) {
                if (--startedScreens == 0 && Db.uid != null) Presence.goAway()
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
