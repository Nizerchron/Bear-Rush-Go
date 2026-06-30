package com.bearrushbuilder.app

import android.app.Application
import com.bearrushbuilder.app.BuildConfig
import com.bearrushbuilder.app.data.AdsManager

class BearRushModApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Preload the first AdMob ad
            AdsManager.preloadAd(this)
        } catch (t: Throwable) {
            // ponytail: silent fail
        }
    }
}
