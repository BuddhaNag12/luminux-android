package com.buddhanag.luminux.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress

/** iOS `TiltButtonStyle(touch: nil)`: the whole control tilts in from its centre while pressed. */
internal fun Modifier.tiltClickable(role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    tiltPress(interaction, trackTouch = false).clickable(interaction, indication = null, role = role, onClick = onClick)
}

/** "LUMINUX" over a light page title, the title pulled left so its glyphs line up with the overline. */
@Composable
internal fun PageHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        MetroText("LUMINUX", MetroType.overline)
        MetroText(title, MetroType.title, Modifier.offset(x = (-3).dp))
    }
}

@Composable
internal fun LinkRow(title: String, detail: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().tiltClickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MetroText(title, metroFont(20.sp, Semilight))
        MetroText(detail, MetroType.caption, color = LocalMetro.current.secondary)
    }
}

/** MetroToggleStyle: label on the left, a rectangular track with a square thumb on the right. */
@Composable
internal fun ToggleRow(title: String, caption: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val metro = LocalMetro.current
    val thumb by animateDpAsState(if (checked) 40.dp else 0.dp, MetroMotion.snappy(), label = "toggleThumb")
    val track by animateColorAsState(if (checked) metro.accentColor else Color.Transparent, MetroMotion.snappy(), label = "toggleTrack")
    val border by animateColorAsState(if (checked) metro.accentColor else metro.foreground, MetroMotion.snappy(), label = "toggleBorder")
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetroText(title, MetroType.body)
            if (caption != null) MetroText(caption, MetroType.caption, color = metro.secondary)
        }
        Box(Modifier.size(52.dp, 24.dp).background(track).border(2.dp, border)) {
            Box(Modifier.offset { IntOffset(thumb.roundToPx(), 0) }.size(12.dp, 24.dp).background(metro.foreground))
        }
    }
}

/** Equal-width choices in one outlined bar; the chosen one fills with the accent. */
@Composable
internal fun <T> Segments(label: String, options: List<T>, selection: T, title: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val metro = LocalMetro.current
    // The gaps between segments show this colour as dividers.
    Row(
        modifier.fillMaxWidth().background(metro.foreground).padding(2.dp).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val selected = option == selection
            val fill by animateColorAsState(if (selected) metro.accentColor else metro.background, MetroMotion.fade(), label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .background(fill)
                    .selectable(selected, remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                MetroText(title(option), metroFont(17.sp, Semilight), color = if (selected) Color.White else metro.foreground)
            }
        }
    }
}
