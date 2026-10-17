package com.buddhanag.luminux.data.journal

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.PhotoRepository
import com.buddhanag.luminux.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

/** The journal as the screens see it (the iOS `Journal` store): built entries and places, your notes, place names. */
data class JournalState(
    val entries: List<JournalEntry> = emptyList(),
    /** The most recently visited first. */
    val places: List<Place> = emptyList(),
    val isBuilt: Boolean = false,
    val notes: Map<String, JournalNoteEntity> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    /** Off in settings: names already looked up stay cached but hidden. */
    val usesPlaceNames: Boolean = true,
) {
    fun entry(id: String) = entries.firstOrNull { it.id == id }
    fun place(id: String) = places.firstOrNull { it.id == id }

    fun placeName(c: Coordinate?): String? =
        c?.takeIf { usesPlaceNames }?.let { placeNames[JournalBuilder.cellKey(it, PlaceGrouping.CELL)] }

    /** Your title wins over the generated one. */
    fun title(e: JournalEntry): String = notes[e.id]?.title?.takeIf { it.isNotEmpty() } ?: JournalTitles.title(e, placeName(e.center))

    fun note(e: JournalEntry): String? = notes[e.id]?.text?.takeIf { it.isNotEmpty() }

    /** Favourites, then an even spread through the entry. iOS reorders these by Vision aesthetics; Android has no such model. */
    fun keyPhotos(e: JournalEntry, count: Int): List<Long> = (e.favoriteIds + JournalBuilder.spread(e.photoIds, count)).distinct().take(count)

    /** The name, or the month of the latest visit while it has none. */
    fun title(p: Place, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        (placeName(p.center) ?: DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(p.latest.atZone(zone))).lowercase(locale)
}

/**
 * Builds the journal from the library and keeps your titles, notes and looked-up place names in Room. [keepBuilt] runs
 * while the app shell is showing.
 */
class JournalRepository(
    context: Context,
    private val photos: PhotoRepository,
    settings: SettingsRepository,
    private val dao: JournalDao,
    private val scope: CoroutineScope,
) {
    private data class Built(val entries: List<JournalEntry> = emptyList(), val places: List<Place> = emptyList(), val isBuilt: Boolean = false)

    private val built = MutableStateFlow(Built())
    private val namesPlaces = settings.settings.map { it.namesJournalPlaces }.distinctUntilChanged()
    private val namer = PlaceNamer(context)
    /** Lookups that came back empty this session; only touched from [keepBuilt]. */
    private val failedPlaces = mutableSetOf<String>()

    val state: StateFlow<JournalState> = combine(built, dao.notes(), dao.placeNames(), namesPlaces) { b, notes, names, uses ->
        JournalState(b.entries, b.places, b.isBuilt, notes.associateBy { it.entryId }, names.associate { it.cellKey to it.name }, uses)
    }.stateIn(scope, SharingStarted.Eagerly, JournalState())

    /** 1 s after the library or the place-names setting last changed: rebuild, then name places. A newer change cancels both. */
    suspend fun keepBuilt() {
        combine(photos.snapshot.filter { it.isLoaded }.map { it.generation }, namesPlaces, ::Pair)
            .distinctUntilChanged()
            .collectLatest { (_, usesNames) ->
                delay(1_000)
                rebuild()
                if (usesNames) lookUpPlaces()
            }
    }

    /** An empty title goes back to the generated one. Runs in the app scope, so leaving the page can't drop it. */
    fun rename(entryId: String, title: String) = scope.launch {
        dao.upsert((dao.note(entryId) ?: JournalNoteEntity(entryId)).copy(title = title.trim().ifEmpty { null }))
    }

    fun setNote(entryId: String, text: String) = scope.launch {
        dao.upsert((dao.note(entryId) ?: JournalNoteEntity(entryId)).copy(text = text.trim().ifEmpty { null }))
    }

    private suspend fun rebuild() {
        val points = loadPoints()
        val result = withContext(Dispatchers.Default) { Built(JournalBuilder.entries(points), PlaceGrouping.places(points), isBuilt = true) }
        built.value = result
    }

    /** Every photo and video except screenshots that has a date. */
    private suspend fun loadPoints(): List<PhotoPoint> {
        val items = photos.snapshot.value.all.filter { !it.isScreenshot && it.effectiveDate != null }
        val located = locations(items)
        return items.map { PhotoPoint(it.id, Instant.ofEpochMilli(it.effectiveDate!!), located[it.id], it.isFavorite) }
    }

    /** Locations from the Room cache; files are only read for new or modified photos, in batches that survive a cancel. */
    private suspend fun locations(items: List<MediaItem>): Map<Long, Coordinate?> = withContext(Dispatchers.IO) {
        val cached = dao.locations().associateBy { it.id }
        val out = HashMap<Long, Coordinate?>(items.size)
        val stale = mutableListOf<MediaItem>()
        for (item in items) {
            val row = cached[item.id]
            if (row != null && row.dateModified == item.dateModified) out[item.id] = row.coordinate() else stale += item
        }
        // Without ACCESS_MEDIA_LOCATION every location reads as none; caching that would hide them once it's granted.
        if (photos.hasMediaLocation) {
            for (batch in stale.chunked(100)) {
                ensureActive()
                val rows = batch.map { item ->
                    val ll = photos.location(item)
                    PhotoLocationEntity(item.id, item.dateModified, ll?.first, ll?.second)
                }
                dao.upsertLocations(rows)
                rows.forEach { out[it.id] = it.coordinate() }
            }
        }
        out
    }

    /** Away entries first (newest first), then every place, one at a time, 1.2 s apart; each name is saved as it comes. */
    private suspend fun lookUpPlaces() {
        val b = built.value
        val named = dao.placeNames().first().mapTo(mutableSetOf()) { it.cellKey }
        for (center in b.entries.filter { it.isAway }.mapNotNull { it.center } + b.places.map { it.center }) {
            val key = JournalBuilder.cellKey(center, PlaceGrouping.CELL)
            if (key in named || key in failedPlaces) continue
            currentCoroutineContext().ensureActive()
            val name = namer.name(center)
            if (name != null) {
                dao.upsert(PlaceNameEntity(key, name))
                named += key
            } else {
                failedPlaces += key
            }
            delay(1_200)
        }
    }

    private fun PhotoLocationEntity.coordinate() = if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null
}

/** Reverse geocoding through the platform Geocoder (Google's backend on Play devices). Null when it has no answer. */
class PlaceNamer(private val context: Context) {
    suspend fun name(c: Coordinate): String? {
        if (!Geocoder.isPresent()) return null
        return suspendCancellableCoroutine { cont ->
            Geocoder(context, Locale.getDefault()).getFromLocation(
                c.latitude, c.longitude, 1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        val a = addresses.firstOrNull()
                        if (cont.isActive) cont.resume(a?.locality ?: a?.adminArea ?: a?.countryName)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }
}
