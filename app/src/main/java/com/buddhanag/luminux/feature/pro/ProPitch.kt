package com.buddhanag.luminux.feature.pro

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.settings.tiltClickable
import kotlinx.coroutines.launch

private class Perk(val glyph: ImageVector, val title: String, val detail: String)

// ProView.swift:20-31, without StandBy (iOS only). Editing and lift say what the Android versions do.
private val perks = listOf(
    Perk(PerkIcons.noAds, "no ads", "Use every feature without watching an ad."),
    Perk(PerkIcons.palette, "any accent colour", "Pick your own accent, beyond the classic six."),
    Perk(PerkIcons.wideTiles, "wide live tiles", "Medium and large photo tiles for the home screen."),
    Perk(PerkIcons.grid, "grid sizes", "Pinch any photo grid for more or fewer photos in a row."),
    Perk(PerkIcons.photo, "hub background", "Pick the hub's background from any album, photo set or photos you choose."),
    Perk(PerkIcons.pin, "unlimited pins", "Pin as many albums and photo sets to the hub as you like."),
    Perk(PerkIcons.slideshow, "slideshow", "A slow pan and zoom through any collection."),
    Perk(PerkIcons.person, "lift subjects", "Touch and hold a person or pet to copy or share it."),
    Perk(PerkIcons.sliders, "editing", "Crop, light, colour and looks, saved as a new copy in your library."),
)

/** What Pro adds and the button that buys it, on the Pro page and in the hub's last panel (ProPitch). */
@Composable
fun ProPitch(modifier: Modifier = Modifier, firstRow: Int = 0) {
    val container = LocalContext.current.container
    val store = container.proStore
    val metro = LocalMetro.current
    val isUnlocked by store.isUnlocked.collectAsStateWithLifecycle()
    val message by store.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { store.refresh() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isUnlocked) {
            UnlockedCard(Modifier.metroFeather(row = firstRow))
        } else {
            OfferCard(Modifier.metroFeather(row = firstRow).padding(bottom = 16.dp))
            MetroText(
                "what you get", metroFont(22.sp, Semilight),
                Modifier.metroFeather(row = firstRow + 1).padding(bottom = 4.dp).semantics { heading() },
                color = metro.secondary,
            )
            // Slightly see-through, so the hub's photo shows behind the list in its Pro panel.
            Column(Modifier.background(metro.chrome.copy(alpha = 0.92f))) {
                perks.forEachIndexed { index, perk -> PerkRow(perk, index == 0, Modifier.metroFeather(row = firstRow + 2 + index)) }
            }
        }
        message?.let { MetroText(it, MetroType.caption, Modifier.padding(top = 8.dp)) }
    }
}

/** The price and the buy button on an accent block, so the offer is the first thing on the page. */
@Composable
private fun OfferCard(modifier: Modifier) {
    val store = LocalContext.current.container.proStore
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val price by store.price.collectAsStateWithLifecycle()
    val isPurchasing by store.isPurchasing.collectAsStateWithLifecycle()
    val canBuy = price != null && !isPurchasing

    Column(modifier.fillMaxWidth().background(LocalMetro.current.accentColor).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        MetroText("LUMINUX PRO", MetroType.overline, color = Color.White)
        MetroText(price ?: "pro", metroFont(48.sp, FontWeight.Light), Modifier.padding(top = 10.dp), color = Color.White)
        MetroText(
            if (price == null) "Can't reach Google Play right now." else "One payment, yours for good. No subscription, no ads.",
            MetroType.caption, Modifier.alpha(0.9f), color = Color.White,
        )
        Box(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .alpha(if (price == null) 0.5f else 1f)
                .then(if (canBuy) Modifier.tiltClickable { activity?.let(store::purchase) } else Modifier)
                .background(Color.White)
                .semantics { contentDescription = price?.let { "Unlock Pro for $it" } ?: "Unlock Pro" },
            contentAlignment = Alignment.Center,
        ) {
            MetroText(if (isPurchasing) "unlocking…" else "unlock pro", metroFont(17.sp, FontWeight.SemiBold), color = Color.Black)
        }
        MetroText(
            "restore purchase",
            metroFont(15.sp, Semilight).copy(textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center),
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .clickable(role = Role.Button) { scope.launch { store.restore() } }
                .padding(vertical = 13.dp),
            color = Color.White,
        )
    }
}

@Composable
private fun UnlockedCard(modifier: Modifier) {
    val metro = LocalMetro.current
    Row(
        modifier.fillMaxWidth().background(metro.chrome).padding(12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(metro.accentColor), contentAlignment = Alignment.Center) {
            Image(MetroIcons.check, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroText("pro unlocked", metroFont(20.sp, Semilight))
            MetroText("Thank you for supporting Luminux.", MetroType.caption, color = metro.secondary)
        }
    }
}

@Composable
private fun PerkRow(perk: Perk, isFirst: Boolean, modifier: Modifier) {
    val metro = LocalMetro.current
    Column(modifier.semantics(mergeDescendants = true) {}) {
        if (!isFirst) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(metro.foreground.copy(alpha = 0.12f)))
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Not the accent: dark accents are too dim on the panel.
            Image(
                perk.glyph, null, Modifier.padding(top = 2.dp).width(28.dp).height(24.dp).clearAndSetSemantics {},
                colorFilter = ColorFilter.tint(metro.foreground),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetroText(perk.title, metroFont(17.sp, Semilight))
                MetroText(perk.detail, MetroType.caption, color = metro.secondary)
            }
        }
    }
}

/** Thin line stand-ins for the perks' SF Symbols, on a 24 grid. */
private object PerkIcons {
    val noAds = icon("play.slash") { moveTo(8f, 6f); lineTo(18f, 12f); lineTo(8f, 18f); close(); moveTo(4f, 4f); lineTo(20f, 20f) }
    val palette = icon("paintpalette") {
        moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, false, 12f, 21f); arcTo(2f, 2f, 0f, false, false, 13.5f, 17.5f)
        arcTo(2f, 2f, 0f, false, true, 15f, 14f); lineTo(17f, 14f); arcTo(4f, 4f, 0f, false, false, 21f, 10f)
        arcTo(9f, 7f, 0f, false, false, 12f, 3f)
        moveTo(7.5f, 11f); lineTo(7.6f, 11f); moveTo(10f, 7f); lineTo(10.1f, 7f); moveTo(15f, 7.5f); lineTo(15.1f, 7.5f)
    }
    val wideTiles = icon("rectangle.split.2x1") { rect(3f, 6f, 21f, 18f); moveTo(12f, 6f); lineTo(12f, 18f) }
    val grid = icon("square.grid.3x3") {
        rect(3f, 3f, 21f, 21f); moveTo(9f, 3f); lineTo(9f, 21f); moveTo(15f, 3f); lineTo(15f, 21f)
        moveTo(3f, 9f); lineTo(21f, 9f); moveTo(3f, 15f); lineTo(21f, 15f)
    }
    val photo = icon("photo") { rect(3f, 5f, 21f, 19f); moveTo(3f, 17f); lineTo(9f, 11f); lineTo(14f, 16f); lineTo(16f, 14f); lineTo(21f, 18f) }
    val pin = icon("pin") {
        moveTo(9f, 3f); lineTo(15f, 3f); moveTo(10f, 3f); lineTo(10f, 9f); lineTo(6f, 14f); lineTo(18f, 14f); lineTo(14f, 9f)
        lineTo(14f, 3f); moveTo(12f, 14f); lineTo(12f, 21f)
    }
    val slideshow = icon("play.rectangle") { rect(3f, 5f, 21f, 19f); moveTo(10f, 9f); lineTo(15f, 12f); lineTo(10f, 15f); close() }
    val person = icon("person.crop.rectangle.stack") {
        rect(4f, 6f, 20f, 21f); moveTo(7f, 3f); lineTo(17f, 3f)
        moveTo(12f, 9f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 14f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 9f)
        moveTo(7f, 21f); arcTo(5f, 4f, 0f, false, true, 17f, 21f)
    }
    val sliders = icon("slider.horizontal.3") {
        moveTo(3f, 6f); lineTo(21f, 6f); moveTo(3f, 12f); lineTo(21f, 12f); moveTo(3f, 18f); lineTo(21f, 18f)
        rect(13f, 4f, 16f, 8f); rect(6f, 10f, 9f, 14f); rect(15f, 16f, 18f, 20f)
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) { moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close() }

    private fun icon(name: String, block: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .path(stroke = SolidColor(Color.White), strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        .build()
}
