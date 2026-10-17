package com.buddhanag.luminux.monetization

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.buddhanag.luminux.BuildConfig
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Ad unit choice and the one kind of request the app makes (AdConsent.swift:56-71). */
object AdUnits {
    const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    /** The real unit only in Play builds: AdMob suspends accounts whose owner sees or taps their own live ads. */
    fun pick(configured: String, test: String) =
        if (BuildConfig.DEBUG || BuildConfig.PRO_FREE_FOR_TESTING) test else configured.ifEmpty { test }

    val rewarded get() = pick(BuildConfig.ADMOB_REWARDED_ID, TEST_REWARDED)
    val native get() = pick(BuildConfig.ADMOB_NATIVE_ID, TEST_NATIVE)

    /** Always non-personalised. */
    fun request(): AdRequest = AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") }).build()
}

/**
 * Google's consent step and SDK start, shared by every kind of ad. Nothing here runs until someone chooses an ad (or
 * the parked news panel is shown), so Pro users and people who never do never meet Google's SDK or its consent form.
 */
class AdConsent(private val app: Context) {
    private val info: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(app) }
    /** True where the law asks for a way to change the consent later; settings then shows "ad privacy choices". */
    val needsPrivacyOptions = MutableStateFlow(false)
    private var started = false

    /** True once this launch has asked and ads are allowed, so loading ahead never brings up the form unasked. */
    val isReady get() = started && info.canRequestAds()

    /** Consent first, then SDK start. False when ads can't be requested. */
    suspend fun prepare(activity: Activity): Boolean {
        // Offline, or the form failed to load: Google still answers canRequestAds from what it last knew.
        updateConsentInfo(activity)
        suspendCancellableCoroutine { c -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { c.resume(Unit) } }
        needsPrivacyOptions.value = info.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED
        if (!info.canRequestAds()) return false
        if (!started) {
            started = true
            withContext(Dispatchers.IO) { suspendCancellableCoroutine { c -> MobileAds.initialize(app) { c.resume(Unit) } } }
            MobileAds.setAppMuted(true)
        }
        return true
    }

    /** For settings, and only for people who've been through the consent step, so nobody else's app contacts Google. */
    suspend fun refreshPrivacyOptions(activity: Activity) {
        if (info.consentStatus == ConsentInformation.ConsentStatus.UNKNOWN) return
        updateConsentInfo(activity)
        needsPrivacyOptions.value = info.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun presentPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) {}

    private suspend fun updateConsentInfo(activity: Activity) = suspendCancellableCoroutine { c ->
        info.requestConsentInfoUpdate(activity, parameters(activity), { c.resume(Unit) }, { c.resume(Unit) })
    }

    private fun parameters(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE.isNotEmpty()) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE)
                    .build(),
            )
        }
        return builder.build()
    }
}
