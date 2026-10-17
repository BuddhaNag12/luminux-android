package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** iOS `Font.metro(size, weight)`; sizes are sp, so they follow the system font scale like Dynamic Type. */
fun metroFont(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Selawik, fontWeight = weight, fontSize = size)

object MetroType {
    /** Panorama title, e.g. "photos". */
    val panorama = metroFont(96.sp, FontWeight.Light)
    /** Page title, e.g. "settings". */
    val title = metroFont(56.sp, FontWeight.Light)
    val pivot = metroFont(44.sp, FontWeight.Light)
    val section = metroFont(28.sp, Semilight)
    val body = metroFont(17.sp)
    val caption = metroFont(14.sp)
    /** Uppercase app name above titles, e.g. "LUMINUX". Pass the text already uppercased. */
    val overline = metroFont(12.sp, FontWeight.SemiBold).copy(letterSpacing = 1.5.sp)
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
) {
    BasicText(
        text, modifier, style.copy(color = color),
        overflow = TextOverflow.Clip, softWrap = softWrap, maxLines = maxLines,
    )
}
