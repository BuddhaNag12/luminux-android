package com.buddhanag.luminux.monetization

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** "Watch an ad to use it once" for Pro features (RewardedAds.swift). The ad plays only when someone taps to watch one. */
class RewardedAds(private val app: Context, private val consent: AdConsent) {
    val isLoading = MutableStateFlow(false)
    /** Why the last attempt didn't play; the Pro page shows it. */
    val message = MutableStateFlow<String?>(null)
    private val scope = MainScope()
    private var ad: RewardedAd? = null
    private var loadedAt = 0L
    private var showing = false

    // Google expires loaded ads after an hour.
    private val fresh get() = ad?.takeIf { SystemClock.elapsedRealtime() - loadedAt < AD_LIFETIME_MS }

    /** Loads ahead once consent is settled this launch, so "watch an ad" can play straight away. */
    suspend fun preload() {
        if (!consent.isReady || fresh != null || isLoading.value) return
        ad = load()
        loadedAt = SystemClock.elapsedRealtime()
    }

    /** Plays an ad; true once the viewer has earned the reward. */
    suspend fun watch(activity: Activity): Boolean {
        if (isLoading.value || showing) return false
        message.value = null
        isLoading.value = true
        if (!consent.prepare(activity)) {
            isLoading.value = false
            message.value = "Ads can't be shown with your current ad choices. You can change them in settings."
            return false
        }
        val next = fresh ?: load()
        ad = null
        isLoading.value = false
        if (next == null) {
            message.value = "No ad is available right now. Try again in a moment."
            return false
        }
        showing = true
        // The reward counts only from the reward callback, never from closing the ad.
        val earned = suspendCancellableCoroutine { c ->
            var got = false
            next.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { if (c.isActive) c.resume(got) }
                override fun onAdFailedToShowFullScreenContent(error: AdError) { if (c.isActive) c.resume(got) }
            }
            next.show(activity) { got = true }
        }
        showing = false
        if (!earned) message.value = "The ad was closed before the end, so the feature stays locked."
        scope.launch { preload() }
        return earned
    }

    fun clearMessage() {
        message.value = null
    }

    private suspend fun load(): RewardedAd? = suspendCancellableCoroutine { c ->
        RewardedAd.load(app, AdUnits.rewarded, AdUnits.request(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { if (c.isActive) c.resume(ad) }
            override fun onAdFailedToLoad(error: LoadAdError) { if (c.isActive) c.resume(null) }
        })
    }

    companion object {
        const val AD_LIFETIME_MS = 55 * 60_000L
    }
}
