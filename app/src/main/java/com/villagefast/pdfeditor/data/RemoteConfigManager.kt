package com.villagefast.pdfeditor.data

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    
    // Keys
    private const val SHOW_BANNER_ADS = "show_banner_ads"
    private const val SHOW_INTERSTITIAL_ADS = "show_interstitial_ads"
    private const val INTERSTITIAL_INTERVAL = "interstitial_interval"
    private const val BANNER_AD_UNIT_ID = "banner_ad_unit_id"
    private const val INTERSTITIAL_AD_UNIT_ID = "interstitial_ad_unit_id"

    private val remoteConfig: FirebaseRemoteConfig? by lazy {
        try {
            Firebase.remoteConfig.apply {
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 0 // 0 for development, usually 3600 for production
                }
                setConfigSettingsAsync(configSettings)
                // Set defaults
                val defaults = mapOf(
                    SHOW_BANNER_ADS to true,
                    SHOW_INTERSTITIAL_ADS to true,
                    INTERSTITIAL_INTERVAL to 3,
                    BANNER_AD_UNIT_ID to "ca-app-pub-6758293165764534/5893389886", // Standard Test Banner
                    INTERSTITIAL_AD_UNIT_ID to "ca-app-pub-6758293165764534/6823328174" // Standard Test Interstitial
                )
                setDefaultsAsync(defaults)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Remote Config not initialized: ${e.message}")
            null
        }
    }

    fun fetchAndActivate(onComplete: (Boolean) -> Unit = {}) {
        val config = remoteConfig
        if (config == null) {
            onComplete(false)
            return
        }
        config.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")
                } else {
                    Log.d(TAG, "Fetch failed")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun showBannerAds(): Boolean {
        val value = remoteConfig?.getBoolean(SHOW_BANNER_ADS) ?: true
        Log.d(TAG, "showBannerAds: $value")
        return value
    }

    fun showInterstitialAds(): Boolean {
        val value = remoteConfig?.getBoolean(SHOW_INTERSTITIAL_ADS) ?: true
        Log.d(TAG, "showInterstitialAds: $value")
        return value
    }

    fun getInterstitialInterval(): Int {
        val value = remoteConfig?.getLong(INTERSTITIAL_INTERVAL)?.toInt() ?: 3
        Log.d(TAG, "getInterstitialInterval: $value")
        return value
    }

    fun getBannerAdUnitId(): String {
        val value = remoteConfig?.getString(BANNER_AD_UNIT_ID) ?: "ca-app-pub-6758293165764534/5893389886"
        Log.d(TAG, "getBannerAdUnitId: $value")
        return value
    }

    fun getInterstitialAdUnitId(): String {
        val value = remoteConfig?.getString(INTERSTITIAL_AD_UNIT_ID) ?: "ca-app-pub-6758293165764534/6823328174"
        Log.d(TAG, "getInterstitialAdUnitId: $value")
        return value
    }
}
