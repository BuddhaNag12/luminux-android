package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Outlined rectangular button; fills with the accent color while pressed, with no animation. */
@Composable
fun MetroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(if (pressed) metro.accentColor else Color.Transparent)
            .border(2.dp, if (pressed) metro.accentColor else metro.foreground)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, metroFont(17.sp, Semilight), color = if (pressed) Color.White else metro.foreground)
    }
}
