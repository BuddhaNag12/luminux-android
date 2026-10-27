package com.buddhanag.luminux.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Each "reload" is a new repository on the same DataStore (DataStore allows one instance per file). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fresh(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("s.preferences_pb") }

    private suspend fun DataStore<Preferences>.read() = SettingsRepository(this).settings.first()

    @Test fun defaultsToDarkCobaltWithEffectsOn() = runTest {
        val s = fresh().read()
        assertEquals(MetroThemeMode.Dark, s.theme)
        assertEquals(0x0050EF, s.accentHex)
        assertTrue(s.showsHubBackground)
        assertTrue(s.playsLivingImages)
        assertEquals(4, s.gridColumns)
        assertTrue(s.movesWithPhone)
        assertEquals(TiltStrength.MEDIUM, s.tiltStrength)
        assertTrue(s.animatesLiveTiles)
        assertTrue(s.animatesTilePatterns)
        assertTrue(s.showsStandBy)
        assertTrue(s.namesJournalPlaces)
        assertFalse(s.hasSeenOnboarding)
        assertEquals(PageTransition.ZOOM, s.pageTransition)
    }

    @Test fun theBackgroundSourceIsKept() = runTest {
        val store = fresh()
        assertEquals(HubBackgroundSource.Favorites, store.read().hubBackground)
        listOf(
            HubBackgroundSource.Everything,
            HubBackgroundSource.Album("a/1"),
            HubBackgroundSource.PhotoSet("s"),
            HubBackgroundSource.Photos(listOf(1L, 2L)),
        ).forEach { source ->
            SettingsRepository(store).setHubBackground(source)
            assertEquals(source, store.read().hubBackground)
        }
    }

    @Test fun anUnreadableBackgroundFallsBackToFavorites() = runTest {
        val store = fresh()
        SettingsRepository(store).set(SettingsKeys.hubBackground, "{not json")
        assertEquals(HubBackgroundSource.Favorites, store.read().hubBackground)
    }

    @Test fun tileAnimationSwitchesAreKept() = runTest {
        val store = fresh()
        SettingsRepository(store).run {
            set(SettingsKeys.animatesLiveTiles, false)
            set(SettingsKeys.animatesTilePatterns, false)
            set(SettingsKeys.showsStandBy, false)
        }
        val s = store.read()
        assertFalse(s.animatesLiveTiles)
        assertFalse(s.animatesTilePatterns)
        assertFalse(s.showsStandBy)
    }

    @Test fun persistsChanges() = runTest {
        val store = fresh()
        SettingsRepository(store).run {
            set(SettingsKeys.accent, 0xA4C400)
            setTheme(MetroThemeMode.Light)
            set(SettingsKeys.playsLivingImages, false)
            setPageTransition(PageTransition.TURNSTILE)
        }
        val s = store.read()
        assertEquals(PageTransition.TURNSTILE, s.pageTransition)
        assertEquals(0xA4C400, s.accentHex)
        assertEquals(MetroThemeMode.Light, s.theme)
        assertFalse(s.playsLivingImages)
    }

    @Test fun persistsACustomAccent() = runTest {
        val store = fresh()
        SettingsRepository(store).set(SettingsKeys.accent, 0x3A7D44)
        assertEquals(0x3A7D44, store.read().accentHex)
    }

    @Test fun gridColumnsSnapToLevels() = runTest {
        val store = fresh()
        mapOf(5 to 4, 7 to 6, 100 to 8, 3 to 3).forEach { (stored, expected) ->
            SettingsRepository(store).set(SettingsKeys.gridColumns, stored)
            assertEquals(expected, store.read().gridColumns)
        }
    }
}
