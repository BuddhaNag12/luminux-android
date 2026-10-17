package com.buddhanag.luminux.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** [id] stays the same when the title flips (favorite/unfavorite), so the button updates in place. */
data class AppBarButton(
    val title: String,
    val icon: ImageVector,
    val isEnabled: Boolean = true,
    val id: String = title,
    val onClick: () -> Unit,
)

data class AppBarMenuItem(val title: String, val isEnabled: Boolean = true, val onClick: () -> Unit)

/** Bottom bar of outlined circle buttons; "•••" reveals their labels and a text menu. */
@Composable
fun MetroAppBar(
    buttons: List<AppBarButton>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuItems: List<AppBarMenuItem> = emptyList(),
) {
    val metro = LocalMetro.current
    val fill by animateColorAsState(if (expanded) metro.appBarExpanded else metro.appBar, MetroMotion.standard(), label = "appBar")
    val collapse = { if (expanded) onExpandedChange(false) }
    val hasMenu = expanded && menuItems.isNotEmpty()

    // The fill runs under the navigation bar, like iOS ignoring the bottom safe area.
    Column(modifier.fillMaxWidth().background(fill)) {
        Column(Modifier.defaultMinSize(minHeight = MetroMetrics.appBarHeight).animateContentSize(MetroMotion.standard())) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    buttons.forEach { button ->
                        key(button.id) {
                            CircleButton(button, expanded) {
                                collapse()
                                button.onClick()
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(56.dp, 32.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            onExpandedChange(!expanded)
                        }
                        .clearAndSetSemantics { contentDescription = if (expanded) "Less" else "More" },
                    contentAlignment = Alignment.Center,
                ) {
                    MetroText("•••", metroFont(16.sp, FontWeight.SemiBold))
                }
            }
            Column(
                Modifier
                    .padding(horizontal = MetroMetrics.margin + 12.dp)
                    .padding(top = if (hasMenu) 12.dp else 0.dp, bottom = if (hasMenu) 8.dp else 0.dp),
            ) {
                menuItems.forEachIndexed { index, item ->
                    key(item.title) {
                        // Items rise in one after another, like the original menu; they leave together.
                        AnimatedVisibility(expanded, enter = EnterTransition.None, exit = fadeOut(MetroMotion.standard())) {
                            val rise = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(index * 20L)
                                rise.animateTo(1f, MetroMotion.standard())
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .graphicsLayer {
                                        alpha = rise.value
                                        translationY = (1 - rise.value) * 12.dp.toPx()
                                    }
                                    .alpha(if (item.isEnabled) 1f else 0.4f)
                                    .clickable(enabled = item.isEnabled, role = Role.Button) {
                                        collapse()
                                        item.onClick()
                                    },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                MetroText(item.title, metroFont(20.sp, Semilight))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Outlined circle that fills while pressed, inverting the glyph. */
@Composable
private fun CircleButton(button: AppBarButton, expanded: Boolean, onClick: () -> Unit) {
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateFloatAsState(if (pressed) 1f else 0f, MetroMotion.press(), label = "circle")
    val labelRise = with(LocalDensity.current) { 6.dp.roundToPx() }
    Column(
        Modifier
            .alpha(if (button.isEnabled) 1f else 0.4f)
            .clickable(interaction, indication = null, enabled = button.isEnabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(MetroMetrics.appBarButton)
                .drawBehind {
                    drawCircle(metro.foreground.copy(alpha = fill))
                    val stroke = 2.dp.toPx()
                    drawCircle(metro.foreground, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(button.icon, animationSpec = MetroMotion.snappy(), label = "icon") { icon ->
                Image(
                    icon, button.title, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(lerp(metro.foreground, metro.chrome, fill)),
                )
            }
        }
        AnimatedVisibility(
            expanded,
            enter = fadeIn(MetroMotion.standard()) + slideInVertically(MetroMotion.standard<IntOffset>()) { labelRise },
            exit = fadeOut(MetroMotion.standard()) + slideOutVertically(MetroMotion.standard<IntOffset>()) { labelRise },
        ) {
            // As wide as the circle, so a longer label doesn't push the other buttons aside.
            Box(Modifier.width(MetroMetrics.appBarButton), contentAlignment = Alignment.TopCenter) {
                MetroText(
                    button.title, metroFont(12.sp),
                    Modifier.wrapContentWidth(unbounded = true).clearAndSetSemantics {},
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/**
 * Pins a [MetroAppBar] to the bottom. [content] gets the bottom padding the bar covers (bar plus navigation bar);
 * the expanded bar floats over the content, and tapping outside collapses it.
 */
@Composable
fun MetroAppBarHost(
    buttons: List<AppBarButton>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuItems: List<AppBarMenuItem> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val onChange by rememberUpdatedState(onExpandedChange)
    Box(modifier.fillMaxSize()) {
        val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        content(PaddingValues(bottom = MetroMetrics.appBarHeight + navigation))
        if (expanded) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onChange(false) } })
        }
        MetroAppBar(buttons, expanded, onExpandedChange, Modifier.align(Alignment.BottomCenter), menuItems)
    }
}
