package com.buddhanag.luminux.feature.settings

import com.buddhanag.luminux.data.AppSettings
import com.buddhanag.luminux.data.HubBackgroundSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundPickerTest {
    @Test fun effectiveSourceAndNames() {
        val stored = AppSettings(hubBackground = HubBackgroundSource.Album("user:1"))
        assertEquals(HubBackgroundSource.Favorites, effectiveBackground(stored, unlocksPro = false))
        assertEquals(HubBackgroundSource.Album("user:1"), effectiveBackground(stored, unlocksPro = true))

        val name = { s: HubBackgroundSource -> backgroundName(s, album = { if (it == "user:1") "trips" else null }, photoSet = { null }) }
        assertEquals("favorites", name(HubBackgroundSource.Favorites))
        assertEquals("all photos", name(HubBackgroundSource.Everything))
        assertEquals("trips", name(HubBackgroundSource.Album("user:1")))
        assertEquals("album", name(HubBackgroundSource.Album("user:9")))
        assertEquals("photo set", name(HubBackgroundSource.PhotoSet("x")))
        assertEquals("1 chosen photo", name(HubBackgroundSource.Photos(listOf(1L))))
        assertEquals("3 chosen photos", name(HubBackgroundSource.Photos(listOf(1L, 2L, 3L))))
    }

    @Test fun pickedPhotosKeepOnlyLibraryIds() {
        assertEquals(listOf(5L, 9L), pickedPhotoIds(listOf("5", "cloud-abc", null, "9", "5", "12")) { it != 12L })
    }
}
