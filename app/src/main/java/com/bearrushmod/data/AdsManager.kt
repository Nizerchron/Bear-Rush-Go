package com.bearrushmod.data

import android.app.Activity
import android.content.Context
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

object AdsManager {
    private var startAppAd: StartAppAd? = null

    fun preloadAd(context: Context) {
        if (startAppAd == null) {
            startAppAd = StartAppAd(context.applicationContext)
        }
        startAppAd?.loadAd(object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                // Ad loaded successfully
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                // Failed to load
            }
        })
    }

    fun showInterstitial(
        activity: Activity,
        onCompleted: (() -> Unit)? = null,
        onSkipped: (() -> Unit)? = null
    ) {
        val ad = startAppAd
        if (ad != null && ad.isReady) {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad) {
                    activity.runOnUiThread {
                        onCompleted?.invoke()
                    }
                    preloadAd(activity)
                }

                override fun adDisplayed(ad: Ad) {
                    // No-op
                }

                override fun adClicked(ad: Ad) {
                    // No-op
                }

                override fun adNotDisplayed(ad: Ad) {
                    activity.runOnUiThread {
                        onSkipped?.invoke() ?: onCompleted?.invoke()
                    }
                    preloadAd(activity)
                }
            })
        } else {
            // Ad is not ready yet, skip showing and preload one for next time
            onSkipped?.invoke() ?: onCompleted?.invoke()
            preloadAd(activity)
        }
    }

    fun createBanner(): Nothing? = null
}