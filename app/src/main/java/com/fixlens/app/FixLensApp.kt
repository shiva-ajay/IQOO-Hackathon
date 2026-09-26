package com.fixlens.app

import android.app.Application
import android.util.Log

const val TAG = "FixLens"

class FixLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "FixLens app started (offline build, no network permission)")
    }
}
