package com.fixlens.app

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

const val TAG = "FixLens"

class FixLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "FixLens app started (offline build, no network permission)")
        // Before any ViewModel: FlowTracker allocates OpenCV Mats in its constructor.
        val openCv = OpenCVLoader.initLocal()
        Log.i(TAG, "OpenCV ${OpenCVLoader.OPENCV_VERSION} ${if (openCv) "loaded" else "FAILED to load"}")
    }
}
