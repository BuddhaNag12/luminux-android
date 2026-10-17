package com.buddhanag.luminux.feature.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.hub.HubLayout
import com.buddhanag.luminux.feature.shell.Route

/** An album or a handful of photos to pin to the hub's collection panel. */
sealed interface PinTarget {
    data class Album(val id: String) : PinTarget
    data class Photos(val ids: List<Long>) : PinTarget
}

/** [onPinned] runs once the tile is pinned, e.g. to leave select mode. Not a data class: each request is its own. */
class PinRequest(val target: PinTarget, val onPinned: () -> Unit = {})

/**
 * Pins what [request] asks for (HubPinning.swift): an album straight away, photos once they have a name. Past the free
 * limit it explains the limit and offers Pro instead. Spec 06's album page and select modes host it:
 * `var pin by remember { mutableStateOf<PinRequest?>(null) }; HubPinningHost(pin) { pin = null }`.
 */
@Composable
fun HubPinningHost(request: PinRequest?, onConsumed: () -> Unit) {
    val container = LocalContext.current.container
    val hubTiles = container.hubTiles
    var naming by remember { mutableStateOf<PinRequest?>(null) }
    var name by remember { mutableStateOf("") }
    var showsLimit by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        val pending = request ?: return@LaunchedEffect
        onConsumed()
        // isUnlocked, not unlocksPro, so test builds still meet the limit.
        if (!hubTiles.canPin(isPro = container.isProUnlocked.value)) {
            showsLimit = true
            return@LaunchedEffect
        }
        when (val target = pending.target) {
            is PinTarget.Album -> {
                hubTiles.pinAlbum(target.id)
                pending.onPinned()
            }
            is PinTarget.Photos -> {
                name = ""
                naming = pending
            }
        }
    }

    naming?.let { pending ->
        HubDialog(
            "Pin to collection",
            listOf(
                DialogAction("Cancel"),
                DialogAction("Pin") {
                    hubTiles.pinPhotoSet(name, (pending.target as PinTarget.Photos).ids)
                    pending.onPinned()
                },
            ),
            onDismiss = { naming = null },
            message = "The photos show up together as a live tile on the hub.",
            field = DialogField("Name", name) { name = it },
        )
    }
    if (showsLimit) {
        HubDialog(
            "Free pins used",
            listOf(DialogAction("See Pro") { container.navigator.push(Route.Pro) }, DialogAction("OK")),
            onDismiss = { showsLimit = false },
            message = "You can pin ${HubLayout.FREE_PIN_LIMIT} albums or photo sets for free. Unpin one from the hub, " +
                "or unlock Pro to pin as many as you like.",
        )
    }
}
