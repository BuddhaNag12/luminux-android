package com.buddhanag.luminux.feature.actions

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.feature.grid.SelectionModel

/**
 * The app bar buttons every screen with a select mode shares (AssetActions.swift `SelectionActions`): share, favorite,
 * add to album, delete. Favorite and delete go through the system's consent dialog and end select mode once the user
 * agrees; share and add keep it.
 */
@Composable
fun selectionActionButtons(selection: SelectionModel, onAddToAlbum: (List<MediaItem>) -> Unit): List<AppBarButton> {
    val context = LocalContext.current
    val photos = context.container.photos
    val currentSelection by rememberUpdatedState(selection)
    val consent = rememberLauncherForActivityResult(StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) currentSelection.end()
    }
    val hasSelection = selection.selected.isNotEmpty()
    fun withItems(action: (List<MediaItem>) -> Unit) {
        selection.selected.mapNotNull(photos::item).takeIf { it.isNotEmpty() }?.let(action)
    }
    return listOf(
        AppBarButton("share", ActionIcons.share, hasSelection) { withItems { context.startActivity(photos.shareIntent(it)) } },
        AppBarButton("favorite", ActionIcons.favorite, hasSelection) { withItems { consent.launch(photos.favoriteRequest(it, true)) } },
        AppBarButton("add", ActionIcons.add, hasSelection) { withItems(onAddToAlbum) },
        AppBarButton("delete", ActionIcons.delete, hasSelection) { withItems { consent.launch(photos.trashRequest(it)) } },
    )
}
