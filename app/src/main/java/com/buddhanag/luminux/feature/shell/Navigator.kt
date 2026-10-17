package com.buddhanag.luminux.feature.shell

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.designsystem.Feather
import kotlinx.coroutines.delay

/** The pages above the hub (Navigator.swift `Route`). */
sealed interface Route {
    data class Collection(val page: Int, val showsJumpList: Boolean = false) : Route
    data class Album(val id: String) : Route
    data object Settings : Route
    data object Pro : Route
    data object About : Route
    data object Journal : Route
    data class JournalEntry(val id: String) : Route
    data class PhotoSet(val id: String) : Route
    data class Place(val id: String) : Route
}

/** The viewer is a full-screen cover, not a page: [startIndex] lets its pager open on [startId] at first layout. */
data class ViewerRequest(val source: AssetSource, val startId: Long, val startIndex: Int)

class Navigator {
    /** Pages above the hub, bottom first. */
    val path = mutableStateListOf<Route>()
    var viewer by mutableStateOf<ViewerRequest?>(null)
    /** The photo showing in the viewer, so the zoom closes onto the right tile. */
    var viewerCurrentId: Long? = null
    /** The top page's items are swinging out before [pop] removes it. */
    var isLeaving by mutableStateOf(false)
        private set

    fun push(route: Route) {
        path += route
    }

    /** Lets the page's items sink away first, then drops it; a second back while leaving is ignored. */
    suspend fun pop(reduceMotion: Boolean) {
        if (path.isEmpty() || isLeaving) return
        if (!reduceMotion) {
            isLeaving = true
            delay((Feather.exitTime * 1000).toLong())
        }
        path.removeAt(path.lastIndex)
        isLeaving = false
    }

    fun replaceStack(routes: List<Route>) {
        path.clear()
        path += routes
    }

    fun openViewer(source: AssetSource, id: Long, index: Int) {
        viewerCurrentId = id
        viewer = ViewerRequest(source, id, index)
    }

    /** The live tile opens the hub or its collection; a locked wide tile opens the Pro page. */
    fun handleDeepLink(uri: Uri, isProUnlocked: Boolean) {
        val stack = deepLinkStack(uri.scheme, uri.host, uri.getQueryParameter("id"), isProUnlocked) ?: return
        viewer = null
        replaceStack(stack)
    }
}

/** The page stack a `luminux://` link opens (LuminuxApp.swift:128-145); null for other schemes. [id] is only a lookup key. */
fun deepLinkStack(scheme: String?, host: String?, id: String?, isProUnlocked: Boolean): List<Route>? = when {
    scheme != "luminux" -> null
    host == "pro" && !isProUnlocked -> listOf(Route.Pro)
    host == "collection" && id?.startsWith("album:") == true -> listOf(Route.Album(id.removePrefix("album:")))
    host == "collection" && id?.startsWith("set:") == true -> listOf(Route.PhotoSet(id.removePrefix("set:")))
    else -> emptyList()
}
