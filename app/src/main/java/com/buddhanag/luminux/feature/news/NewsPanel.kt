package com.buddhanag.luminux.feature.news

import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.buddhanag.luminux.container
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.PanoramaSection
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.settings.tiltClickable
import com.buddhanag.luminux.feature.shell.Route
import com.buddhanag.luminux.monetization.NewsArticle
import com.buddhanag.luminux.monetization.NewsFeed.Phase
import com.buddhanag.luminux.monetization.newsRows
import com.google.android.gms.ads.nativead.NativeAd
import java.time.Instant
import kotlinx.coroutines.launch

/** The hub's left-most panel: headlines with a native ad after every few (NewsPanel.swift). Pro hides it. */
@Composable
fun NewsPanel(bottomInset: Dp) {
    val container = LocalContext.current.container
    val feed by container.news.state.collectAsStateWithLifecycle()
    val ads by container.newsAds.ads.collectAsStateWithLifecycle()
    val metro = LocalMetro.current
    val scope = rememberCoroutineScope()
    val end = Modifier.padding(end = MetroMetrics.margin + 12.dp)

    PanoramaSection("top stories", bottomInset = bottomInset) {
        when {
            feed.phase == Phase.Failed -> item {
                Column(end.metroFeather(row = 2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetroText("Couldn't reach the news. Check your connection.", MetroType.body, color = metro.foreground.copy(alpha = 0.75f))
                    MetroButton("try again", { scope.launch { container.news.refresh() } })
                }
            }
            feed.articles.isEmpty() -> item {
                MetroText("Getting the headlines…", MetroType.body, end.metroFeather(row = 2), color = metro.foreground.copy(alpha = 0.75f))
            }
        }
        itemsIndexed(newsRows(feed.articles, ads), key = { i, row -> if (row is NewsArticle) "$i ${row.url}" else "ad-${System.identityHashCode(row)}" }) { index, row ->
            val rowModifier = end.metroFeather(row = 2 + index).padding(bottom = 22.dp)
            if (row is NativeAd) {
                Column(rowModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NativeAdCard(row)
                    MetroText(
                        "remove ads with pro", metroFont(15.sp, Semilight).copy(textDecoration = TextDecoration.Underline),
                        Modifier.defaultMinSize(minHeight = 44.dp).tiltClickable { container.navigator.push(Route.Pro) }.padding(vertical = 12.dp),
                    )
                }
            } else {
                Headline(row as NewsArticle, rowModifier)
            }
        }
        feed.attribution?.takeIf { feed.articles.isNotEmpty() }?.let { attribution ->
            item { MetroText(attribution, MetroType.caption, end, color = metro.foreground.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
private fun Headline(article: NewsArticle, modifier: Modifier) {
    val context = LocalContext.current
    val metro = LocalMetro.current
    val open = {
        // The proxy's URLs open only as web pages.
        val uri = article.url.toUri()
        if (uri.scheme == "https" || uri.scheme == "http") {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(metro.accentColor.toArgb()).build())
                .build()
                .launchUrl(context, uri)
        }
    }
    Row(
        modifier.tiltClickable(role = Role.Button) { open() }.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetroText(article.title, metroFont(20.sp, Semilight), maxLines = 3)
            MetroText(byline(article), MetroType.caption, color = metro.accentColor)
        }
        // Loads straight from the publisher's CDN, so they see the reader's IP (privacy copy must say so if news ships).
        article.image?.let { image ->
            AsyncImage(image, null, Modifier.size(72.dp).background(metro.chrome), contentScale = ContentScale.Crop)
        }
    }
}

/** "source · 2 hours ago", or just the source without a date. */
private fun byline(article: NewsArticle): String {
    val published = article.publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return article.source
    return "${article.source} · ${DateUtils.getRelativeTimeSpanString(published, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
}
