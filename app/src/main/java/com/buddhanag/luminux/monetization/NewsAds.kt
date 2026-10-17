package com.buddhanag.luminux.monetization

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Native ads for the news panel, loaded the first time the panel is shown (NewsAds.swift). */
class NewsAds(private val app: Context, private val consent: AdConsent) {
    private val _ads = MutableStateFlow<List<NativeAd>>(emptyList())
    val ads: StateFlow<List<NativeAd>> = _ads
    private var loadedAt = 0L
    private var busy = false

    /** Asks for consent where it's required, starts the SDK and loads fresh ads if the current ones are old. */
    suspend fun prepare(activity: Activity, headlineCount: Int) {
        if (busy) return
        if (_ads.value.isNotEmpty() && SystemClock.elapsedRealtime() - loadedAt < RewardedAds.AD_LIFETIME_MS) return
        busy = true
        try {
            if (!consent.prepare(activity)) return
            val fresh = loadBatch(nativeAdCount(headlineCount))
            if (fresh.isNotEmpty()) {
                _ads.value.forEach { it.destroy() }
                _ads.value = fresh
                loadedAt = SystemClock.elapsedRealtime()
            }
        } finally {
            busy = false
        }
    }

    /** Lets go of the ads once Pro is bought. */
    fun clear() {
        _ads.value.forEach { it.destroy() }
        _ads.value = emptyList()
        loadedAt = 0
    }

    /** loadAds has no "finished" callback: the batch is done once the loader stops loading, or on a failure. */
    private suspend fun loadBatch(count: Int): List<NativeAd> = suspendCancellableCoroutine { c ->
        val received = mutableListOf<NativeAd>()
        lateinit var loader: AdLoader
        var done = false
        fun finish() {
            if (done) return
            done = true
            c.resume(received.toList())
        }
        loader = AdLoader.Builder(app, AdUnits.native)
            .forNativeAd { ad ->
                // One arriving after a failure already finished the batch is dropped.
                if (done) return@forNativeAd ad.destroy()
                received += ad
                if (!loader.isLoading) finish()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) = finish()
            })
            .withNativeAdOptions(NativeAdOptions.Builder().setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE).build())
            .build()
        loader.loadAds(AdUnits.request(), count)
    }

    companion object {
        /** One ad after every this many headlines. */
        const val HEADLINES_PER_AD = 5
        const val MAX_ADS = 3

        fun nativeAdCount(headlineCount: Int) = (headlineCount / HEADLINES_PER_AD).coerceIn(1, MAX_ADS)
    }
}

/** Each article, plus the next unused ad after every 5th one. */
fun <A> newsRows(articles: List<NewsArticle>, ads: List<A>): List<Any> = buildList {
    val queue = ArrayDeque(ads)
    articles.forEachIndexed { i, article ->
        add(article)
        if ((i + 1) % NewsAds.HEADLINES_PER_AD == 0) queue.removeFirstOrNull()?.let { add(it as Any) }
    }
}
