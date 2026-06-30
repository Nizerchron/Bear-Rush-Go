package com.bearrushbuilder.app.data

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdsManager {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    // Ad Unit IDs
    // Real Ad Unit IDs for production build (uncommented for playstore upload)
    private val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // (Still test ID, replace when real ID is ready)
    private val REWARDED_UNIT_ID     = "ca-app-pub-9044031890493583/2309698635"
    val BANNER_UNIT_ID               = "ca-app-pub-9044031890493583/7347805262"

    /* ==== UNTUK TESTING LOKAL (Test IDs) ====
    private val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private val REWARDED_UNIT_ID     = "ca-app-pub-3940256099942544/5224354917"
    val BANNER_UNIT_ID               = "ca-app-pub-3940256099942544/6300978111"
    */
    
    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    fun preloadAd(context: Context) {
        try {
            // Inisialisasi MobileAds jika belum
            MobileAds.initialize(context) {}
            
            // Preload Interstitial (Untuk App Open)
            if (interstitialAd == null && !isInterstitialLoading) {
                isInterstitialLoading = true
                InterstitialAd.load(
                    context.applicationContext,
                    INTERSTITIAL_UNIT_ID,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad
                            isInterstitialLoading = false
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            interstitialAd = null
                            isInterstitialLoading = false
                        }
                    }
                )
            }

            // Preload Rewarded (Untuk Download Preset)
            if (rewardedAd == null && !isRewardedLoading) {
                isRewardedLoading = true
                RewardedAd.load(
                    context.applicationContext,
                    REWARDED_UNIT_ID,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            rewardedAd = ad
                            isRewardedLoading = false
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            rewardedAd = null
                            isRewardedLoading = false
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            isInterstitialLoading = false
            isRewardedLoading = false
        }
    }

    // Tetap untuk App Open / Pindah layar utama
    fun showInterstitial(
        activity: Activity,
        onCompleted: (() -> Unit)? = null,
        onSkipped: (() -> Unit)? = null
    ) {
        try {
            if (interstitialAd != null) {
                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        preloadAd(activity)
                        activity.runOnUiThread { onCompleted?.invoke() }
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        interstitialAd = null
                        preloadAd(activity)
                        activity.runOnUiThread { onSkipped?.invoke() ?: onCompleted?.invoke() }
                    }
                    override fun onAdShowedFullScreenContent() {
                        interstitialAd = null
                    }
                }
                interstitialAd?.show(activity)
            } else {
                onSkipped?.invoke() ?: onCompleted?.invoke()
                preloadAd(activity)
            }
        } catch (t: Throwable) {
            onSkipped?.invoke() ?: onCompleted?.invoke()
        }
    }

    // Untuk Download Preset (Reward)
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onSkipped: () -> Unit
    ) {
        try {
            if (rewardedAd != null) {
                var userEarnedReward = false
                
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        preloadAd(activity)
                        activity.runOnUiThread {
                            if (userEarnedReward) {
                                onRewarded()
                            } else {
                                onSkipped()
                            }
                        }
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        rewardedAd = null
                        preloadAd(activity)
                        activity.runOnUiThread { onSkipped() } // Gagal tampil = lewati
                    }
                    override fun onAdShowedFullScreenContent() {
                        rewardedAd = null
                    }
                }
                
                rewardedAd?.show(activity) { rewardItem ->
                    // User nonton sampai selesai (dapat reward)
                    userEarnedReward = true
                }
            } else {
                // Kalo iklan belum siap, ponytail rule: langsung izinkan download (jangan blokir UX)
                onRewarded()
                preloadAd(activity)
            }
        } catch (t: Throwable) {
            onRewarded() // Fallback
        }
    }
}

// Banner Ad Composable
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdsManager.BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}