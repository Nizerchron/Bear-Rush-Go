package com.bearrushmod

import android.app.Application
import com.bearrushmod.data.AdsManager
import com.startapp.sdk.adsbase.StartAppSDK

class BearRushModApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Start.io SDK
        StartAppSDK.init(this, "205794821", false)
        // Enable test ads in debug mode, disable in release
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
        // Preload the first ad
        AdsManager.preloadAd(this)
    }
}
