package com.villagefast.pdfeditor.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.villagefast.pdfeditor.data.RemoteConfigManager

object AdManager {
    private var interstitialAd: InterstitialAd? = null
    private var actionCount = 0
    private const val TAG = "AdManager"

    fun loadInterstitial(context: Context) {
        if (!RemoteConfigManager.showInterstitialAds()) return

        val adRequest = AdRequest.Builder().build()
        Log.d(TAG, "Loading interstitial ad...")
        InterstitialAd.load(
            context,
            RemoteConfigManager.getInterstitialAdUnitId(),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Ad failed to load: ${adError.message}")
                    interstitialAd = null
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Ad loaded successfully.")
                    interstitialAd = ad
                }
            })
    }

    fun showInterstitialWithInterval(activity: Activity, onAdDismissed: () -> Unit) {
        actionCount++
        val interval = RemoteConfigManager.getInterstitialInterval()
        
        if (actionCount >= interval) {
            actionCount = 0
            showInterstitial(activity, onAdDismissed)
        } else {
            onAdDismissed()
        }
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        if (!RemoteConfigManager.showInterstitialAds()) {
            onAdDismissed()
            return
        }

        if (interstitialAd != null) {
            Log.d(TAG, "Showing interstitial ad.")
            interstitialAd?.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed.")
                    interstitialAd = null
                    loadInterstitial(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                    interstitialAd = null
                    loadInterstitial(activity)
                    onAdDismissed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial ad not ready, loading for next time.")
            loadInterstitial(activity)
            onAdDismissed()
        }
    }
}
