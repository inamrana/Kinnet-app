package com.inamulhaq.i230839

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

/** App entry point: enables offline caching and starts presence for an already signed-in user. */
class KinnectApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        if (Db.uid != null) Presence.goOnline()
    }
}
