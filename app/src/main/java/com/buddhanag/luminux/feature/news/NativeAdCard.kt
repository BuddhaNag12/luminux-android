package com.buddhanag.luminux.feature.news

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.buddhanag.luminux.R
import com.buddhanag.luminux.designsystem.LocalMetro
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.math.roundToInt

/**
 * A native ad drawn in the Metro style (NativeAdCard.swift). Google needs the ad in its own NativeAdView with each asset
 * registered, so it's a View layout; missing assets are hidden.
 */
@Composable
fun NativeAdCard(ad: NativeAd, modifier: Modifier = Modifier) {
    val metro = LocalMetro.current
    val foreground = metro.foreground.toArgb()
    val dimmed = metro.foreground.copy(alpha = 0.75f).toArgb()
    val accent = metro.accentColor.toArgb()
    val border = with(LocalDensity.current) { 2.dp.roundToPx() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        AndroidView(
            factory = { context -> LayoutInflater.from(context).inflate(R.layout.news_ad_card, FrameLayout(context), false) as NativeAdView },
            modifier = Modifier.fillMaxWidth(),
            update = { view ->
                val headline = view.findViewById<TextView>(R.id.ad_headline)
                val body = view.findViewById<TextView>(R.id.ad_body)
                val advertiser = view.findViewById<TextView>(R.id.ad_advertiser)
                val action = view.findViewById<TextView>(R.id.ad_action)
                val icon = view.findViewById<ImageView>(R.id.ad_icon)
                val media = view.findViewById<MediaView>(R.id.ad_media)

                view.findViewById<TextView>(R.id.ad_badge).setTextColor(accent)
                headline.setTextColor(foreground)
                // Brighter than the usual secondary text, so it reads over the hub's photo.
                body.setTextColor(dimmed)
                advertiser.setTextColor(dimmed)
                action.setTextColor(foreground)
                action.background = GradientDrawable().apply { setStroke(border, foreground) }

                // Media at its own aspect ratio, 16:9 when unknown.
                val ratio = ad.mediaContent?.aspectRatio?.takeIf { it > 0f } ?: (16f / 9f)
                media.layoutParams = media.layoutParams.apply { height = (width / ratio).roundToInt() }

                if (view.tag === ad) return@AndroidView
                view.tag = ad
                headline.text = ad.headline
                body.show(ad.body)
                advertiser.show(ad.advertiser)
                action.show(ad.callToAction?.lowercase())
                icon.setImageDrawable(ad.icon?.drawable)
                icon.visibility = if (ad.icon?.drawable == null) View.GONE else View.VISIBLE
                media.mediaContent = ad.mediaContent

                view.headlineView = headline
                view.bodyView = body
                view.advertiserView = advertiser
                view.callToActionView = action
                view.iconView = icon
                view.mediaView = media
                // Registers the views above with Google, so set last.
                view.setNativeAd(ad)
            },
        )
    }
}

private fun TextView.show(value: String?) {
    text = value
    visibility = if (value == null) View.GONE else View.VISIBLE
}
