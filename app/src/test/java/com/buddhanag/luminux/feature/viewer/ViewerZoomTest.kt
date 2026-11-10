package com.buddhanag.luminux.feature.viewer

import androidx.compose.ui.geometry.Rect
import com.buddhanag.luminux.feature.shell.Navigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerZoomTest {
    @Test fun disposingOneTileLeavesOtherVisibleInstanceForSamePhoto() {
        val zoom = ViewerZoom(Navigator())
        val whatsNewToken = Any()
        val favoritesToken = Any()
        val whatsNewRect = Rect(0f, 0f, 300f, 300f)
        val favoritesRect = Rect(400f, 100f, 700f, 400f)

        zoom.register(id = 42L, token = whatsNewToken, rect = whatsNewRect)
        zoom.register(id = 42L, token = favoritesToken, rect = favoritesRect)
        assertEquals(favoritesRect, zoom.tile(42L))

        // Covered/offscreen tile disposing must not wipe out the active tile's rect.
        zoom.unregister(id = 42L, token = whatsNewToken)
        assertEquals(favoritesRect, zoom.tile(42L))

        zoom.unregister(id = 42L, token = favoritesToken)
        assertNull(zoom.tile(42L))
    }
}
