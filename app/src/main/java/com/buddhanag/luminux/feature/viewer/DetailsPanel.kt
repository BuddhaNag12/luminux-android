package com.buddhanag.luminux.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag

/**
 * Photo info that slides up under the viewer. Dragging the header, or pulling the list down past its top, hands the
 * drag back to the viewer ([onHeaderDrag] gets the finger's travel in dp, positive downward).
 */
@Composable
internal fun DetailsPanel(
    item: MediaItem,
    onHeaderDrag: (Float) -> Unit,
    onHeaderDragEnd: (Float, Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metro = LocalMetro.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val details by produceState<AssetDetails?>(null, item.id, item.dateModified) {
        value = AssetDetails.load(context, item, context.container.photos)
    }
    val close by rememberUpdatedState(onClose)
    val drag by rememberUpdatedState(onHeaderDrag)
    val dragEnd by rememberUpdatedState(onHeaderDragEnd)
    val headerLayout = remember { arrayOfNulls<LayoutCoordinates>(1) }

    // Pulling the list down past its top by more than 60 and letting go closes the panel. Back is the viewer's.
    val pullDown = remember {
        object : NestedScrollConnection {
            var distance = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) distance += available.y
                if (consumed.y < 0) distance = 0f
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (with(density) { distance.toDp() } > 60.dp) close()
                distance = 0f
                return Velocity.Zero
            }
        }
    }

    Column(modifier.background(metro.chrome)) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { headerLayout[0] = it }
                .headerDrag({ headerLayout[0] }, { drag(it) }, { dy, v -> dragEnd(dy, v) })
                .padding(horizontal = MetroMetrics.margin + 12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(metro.secondary.copy(alpha = 0.5f)),
            )
            MetroText("details", MetroType.pivot, Modifier.offset(x = (-2).dp).padding(bottom = 8.dp).semantics { heading() })
        }
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(pullDown)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MetroMetrics.margin + 12.dp),
        ) {
            val loaded = details
            if (loaded == null) {
                Spinner(Modifier.padding(top = 8.dp))
            } else {
                loaded.rows.forEach { row ->
                    Column(Modifier.padding(bottom = 16.dp).semantics(mergeDescendants = true) {}) {
                        MetroText(row.label, metroFont(15.sp, Semilight), color = metro.secondary)
                        Spacer(Modifier.size(2.dp))
                        SelectionContainer { MetroText(row.value, MetroType.body) }
                    }
                }
                // shortcut: the 200 dp lite-mode mini map needs Maps Compose and its API key, which spec 08 adds; show it here after the merge.
            }
            Spacer(Modifier.size(40.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Feeds a vertical drag to the viewer in root coordinates, since the panel itself moves under the finger. */
private fun Modifier.headerDrag(layout: () -> LayoutCoordinates?, onDrag: (Float) -> Unit, onEnd: (Float, Float) -> Unit) =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val coords = layout() ?: return@awaitEachGesture
            val startY = coords.localToRoot(down.position).y
            var dy = 0f
            val tracker = VelocityTracker()
            verticalDrag(down.id) { change ->
                dy = coords.localToRoot(change.position).y - startY
                // Root positions, so the velocity isn't cancelled out by the panel following the finger.
                tracker.addPosition(change.uptimeMillis, coords.localToRoot(change.position))
                change.consume()
                onDrag(dy.toDp().value)
            }
            onEnd(dy.toDp().value, tracker.calculateVelocity().y.toDp().value)
        }
    }
