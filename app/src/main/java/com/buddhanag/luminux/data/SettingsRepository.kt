package com.buddhanag.luminux.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buddhanag.luminux.feature.grid.GridZoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The iOS UserDefaults keys (AppSettings.swift:8–20), unchanged. */
object SettingsKeys {
    val accent = intPreferencesKey("accentHex")
    val theme = stringPreferencesKey("theme")
    val showsHubBackground = booleanPreferencesKey("showsHubBackground")
    val playsLivingImages = booleanPreferencesKey("playsLivingImages")
    val gridColumns = intPreferencesKey("gridColumns")
    val movesWithPhone = booleanPreferencesKey("movesWithPhone")
    val animatesLiveTiles = booleanPreferencesKey("animatesLiveTiles")
    val animatesTilePatterns = booleanPreferencesKey("animatesTilePatterns")
    val hubBackground = stringPreferencesKey("hubBackground")
    val showsStandBy = booleanPreferencesKey("showsStandBy")
    val namesJournalPlaces = booleanPreferencesKey("namesJournalPlaces")
    val tiltStrength = stringPreferencesKey("tiltStrength")
    val hasSeenOnboarding = booleanPreferencesKey("hasSeenOnboarding")
    val pageTransition = stringPreferencesKey("pageTransition")
    /** Android only: tells "never asked" apart from "denied for good" (see [mediaAccess]). */
    val askedMediaAccess = booleanPreferencesKey("askedMediaAccess")
}

const val COBALT_HEX = 0x0050EF

enum class MetroThemeMode { Dark, Light }

/** How far the hub moves with the phone. */
enum class TiltStrength(val raw: String, val scale: Float) { LOW("low", .45f), MEDIUM("medium", .7f), HIGH("high", 1f) }

/** How pages open and close: every line turning on the screen's edge, or the page underneath scattering away. */
enum class PageTransition(val raw: String) { TURNSTILE("turnstile"), ZOOM("zoom") }

/** The photos the hub's background is picked from. */
@Serializable
sealed interface HubBackgroundSource {
    @Serializable @SerialName("favorites") data object Favorites : HubBackgroundSource
    @Serializable @SerialName("everything") data object Everything : HubBackgroundSource
    @Serializable @SerialName("album") data class Album(val id: String) : HubBackgroundSource
    @Serializable @SerialName("photoSet") data class PhotoSet(val id: String) : HubBackgroundSource
    /** Photos picked one by one in settings. */
    @Serializable @SerialName("photos") data class Photos(val ids: List<Long>) : HubBackgroundSource
}

data class AppSettings(
    /** 0xRRGGBB. */
    val accentHex: Int = COBALT_HEX,
    val theme: MetroThemeMode = MetroThemeMode.Dark,
    val showsHubBackground: Boolean = true,
    /** Live Photos only, which v1 drops; kept so the key survives. */
    val playsLivingImages: Boolean = true,
    val gridColumns: Int = GridZoom.DEFAULT_COLUMNS,
    val movesWithPhone: Boolean = true,
    val animatesLiveTiles: Boolean = true,
    val animatesTilePatterns: Boolean = true,
    val hubBackground: HubBackgroundSource = HubBackgroundSource.Favorites,
    val showsStandBy: Boolean = true,
    val namesJournalPlaces: Boolean = true,
    val tiltStrength: TiltStrength = TiltStrength.MEDIUM,
    val hasSeenOnboarding: Boolean = false,
    val pageTransition: PageTransition = PageTransition.ZOOM,
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val json = Json

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            accentHex = p[SettingsKeys.accent] ?: d.accentHex,
            theme = MetroThemeMode.entries.firstOrNull { it.name.lowercase() == p[SettingsKeys.theme] } ?: d.theme,
            showsHubBackground = p[SettingsKeys.showsHubBackground] ?: d.showsHubBackground,
            playsLivingImages = p[SettingsKeys.playsLivingImages] ?: d.playsLivingImages,
            gridColumns = GridZoom.nearestLevel(p[SettingsKeys.gridColumns] ?: GridZoom.DEFAULT_COLUMNS),
            movesWithPhone = p[SettingsKeys.movesWithPhone] ?: d.movesWithPhone,
            animatesLiveTiles = p[SettingsKeys.animatesLiveTiles] ?: d.animatesLiveTiles,
            animatesTilePatterns = p[SettingsKeys.animatesTilePatterns] ?: d.animatesTilePatterns,
            hubBackground = p[SettingsKeys.hubBackground]
                ?.let { runCatching { json.decodeFromString<HubBackgroundSource>(it) }.getOrNull() }
                ?: d.hubBackground,
            showsStandBy = p[SettingsKeys.showsStandBy] ?: d.showsStandBy,
            namesJournalPlaces = p[SettingsKeys.namesJournalPlaces] ?: d.namesJournalPlaces,
            tiltStrength = TiltStrength.entries.firstOrNull { it.raw == p[SettingsKeys.tiltStrength] } ?: d.tiltStrength,
            hasSeenOnboarding = p[SettingsKeys.hasSeenOnboarding] ?: d.hasSeenOnboarding,
            pageTransition = PageTransition.entries.firstOrNull { it.raw == p[SettingsKeys.pageTransition] } ?: d.pageTransition,
        )
    }

    val askedMediaAccess: Flow<Boolean> = store.data.map { it[SettingsKeys.askedMediaAccess] ?: false }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    suspend fun setTheme(theme: MetroThemeMode) = set(SettingsKeys.theme, theme.name.lowercase())
    suspend fun setTiltStrength(strength: TiltStrength) = set(SettingsKeys.tiltStrength, strength.raw)
    suspend fun setPageTransition(transition: PageTransition) = set(SettingsKeys.pageTransition, transition.raw)
    suspend fun setHubBackground(source: HubBackgroundSource) =
        set(SettingsKeys.hubBackground, json.encodeToString(HubBackgroundSource.serializer(), source))
}
