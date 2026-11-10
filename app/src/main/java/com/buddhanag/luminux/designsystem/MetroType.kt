package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.buddhanag.luminux.R

/** Selawik Semilight; Compose has no named constant for 350. */
val Semilight = FontWeight(350)

/** Selawik, the four weights the iOS app ships. Request only these weights, or Compose synthesizes one. */
val Selawik = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_semilight, Semilight),
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
)

/** iOS DynamicTypeSize.xxLarge (21 / 17) and .xxxLarge (23 / 17) font-scale caps. */
const val MAX_BODY_FONT_SCALE = 1.24f
const val MAX_OVERLINE_FONT_SCALE = 1.35f

/** iOS `Font.metro(size, weight)`; sizes are sp, so they follow the system font scale up to the Metro cap. */
fun metroFont(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Selawik, fontWeight = weight, fontSize = size)

object MetroType {
    /** Panorama title, e.g. "photos". */
    val panorama = metroFont(96.sp, FontWeight.Light)
    /** Panorama title across two panels on an unfolded screen (144). */
    val panoramaWide = metroFont(144.sp, FontWeight.Light)
    /** Page title, e.g. "settings". */
    val title = metroFont(56.sp, FontWeight.Light)
    val pivot = metroFont(44.sp, FontWeight.Light)
    val section = metroFont(28.sp, Semilight)
    val body = metroFont(17.sp)
    val caption = metroFont(14.sp)
    /** Uppercase app name above titles, e.g. "LUMINUX". Pass the text already uppercased. */
    val overline = metroFont(12.sp, FontWeight.SemiBold).copy(letterSpacing = 1.5.sp)
}

/** Clamps system font scaling so display type stays fixed and body/overline follow iOS xxLarge/xxxLarge limits. */
@Composable
fun TextStyle.cappedMetroScale(maxScale: Float? = null): TextStyle {
    val cap = maxScale ?: when {
        fontSize == MetroType.panorama.fontSize ||
            fontSize == MetroType.panoramaWide.fontSize ||
            fontSize == MetroType.title.fontSize ||
            fontSize == MetroType.pivot.fontSize -> 1f
        fontSize == MetroType.overline.fontSize && letterSpacing == MetroType.overline.letterSpacing -> MAX_OVERLINE_FONT_SCALE
        else -> MAX_BODY_FONT_SCALE
    }
    val current = LocalDensity.current.fontScale.coerceAtLeast(0.01f)
    if (current <= cap) return this
    val factor = cap / current
    return copy(
        fontSize = fontSize * factor,
        letterSpacing = if (letterSpacing.isSp) letterSpacing * factor else letterSpacing,
    )
}

/** Text in the Metro foreground unless told otherwise; foundation has no content color to inherit. */
@Composable
fun MetroText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalMetro.current.foreground,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    maxFontScale: Float? = null,
) {
    BasicText(
        text, modifier, style.cappedMetroScale(maxFontScale).copy(color = color),
        overflow = TextOverflow.Clip, softWrap = softWrap, maxLines = maxLines,
    )
}

/**
 * Long page title that runs past the trailing edge rather than wrapping, shrinking, or widening its page
 * (`metroOverflowingTitle`).
 */
@Composable
fun OverflowingTitle(text: String, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clipToBounds()) {
        MetroText(
            text, MetroType.title,
            textModifier.wrapContentWidth(Alignment.Start, unbounded = true),
            maxLines = 1, softWrap = false,
        )
    }
}
