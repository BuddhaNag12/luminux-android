package com.buddhanag.luminux.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroProgress
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.grid.AssetThumbnail
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * A pushed settings picker page (`ChoicePage` in `BackgroundPicker.swift`): page title and optional caption over its
 * choices, with a back button in the app bar besides the system back gesture.
 */
@Composable
internal fun ChoicePage(title: String, caption: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val metro = LocalMetro.current
    val container = LocalContext.current.container
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    MetroAppBarHost(
        buttons = listOf(AppBarButton("back", MetroIcons.arrowLeft) { scope.launch { container.navigator.pop(reduceMotion) } }),
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.background(metro.background),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(padding)
                .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 16.dp, bottom = 40.dp),
        ) {
            Column(Modifier.metroFeather(row = 0).padding(bottom = 20.dp)) {
                MetroText("LUMINUX · SETTINGS", MetroType.overline)
                MetroText(title, MetroType.title, Modifier.padding(top = 2.dp))
                if (caption != null) {
                    MetroText(caption, MetroType.caption, Modifier.padding(top = 4.dp), color = metro.secondary)
                }
            }
            Column(Modifier.metroFeather(row = 1), content = content)
        }
    }
}

/** One choice: its 56 dp cover photo (or glyph), name and count, in the accent with a check when active. */
@Composable
internal fun ChoiceRow(
    cover: MediaItem?,
    title: String,
    detail: String?,
    isSelected: Boolean,
    fallbackIcon: ImageVector = MetroIcons.photo,
    isLocked: Boolean = false,
    isBusy: Boolean = false,
    onClick: () -> Unit,
) {
    val metro = LocalMetro.current
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .tiltClickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { selected = isSelected },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clipToBounds()
                .background(metro.chrome)
                .then(if (isSelected) Modifier.border(3.dp, metro.accentColor) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AssetThumbnail(cover, px, Modifier.fillMaxSize(), showsBadge = false)
                if (isSelected) Box(Modifier.matchParentSize().border(3.dp, metro.accentColor))
            } else {
                Image(fallbackIcon, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(metro.secondary))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(title, metroFont(22.sp, Semilight), color = if (isSelected) metro.accentColor else metro.foreground, maxLines = 1)
                if (isLocked) {
                    Image(
                        MetroIcons.lock, null,
                        Modifier.size(13.dp).semantics { contentDescription = "needs Luminux Pro" },
                        colorFilter = ColorFilter.tint(metro.secondary),
                    )
                }
            }
            if (detail != null) MetroText(detail, MetroType.caption, color = metro.secondary, maxLines = 1)
        }

        if (isBusy) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { MetroProgress() }
        } else if (isSelected) {
            Image(MetroIcons.check, null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(metro.accentColor))
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ChoiceHeader(title: String) {
    MetroText(
        title, metroFont(20.sp, Semilight),
        Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
        color = LocalMetro.current.secondary,
    )
}
