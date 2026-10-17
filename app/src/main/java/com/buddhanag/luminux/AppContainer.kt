package com.buddhanag.luminux

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import coil3.SingletonImageLoader
import com.buddhanag.luminux.data.LiveTileExporter
import com.buddhanag.luminux.data.LiveTileStore
import com.buddhanag.luminux.data.LuminuxDatabase
import com.buddhanag.luminux.data.MediaStorePhotoRepository
import com.buddhanag.luminux.data.PhotoRepository
import com.buddhanag.luminux.data.SettingsRepository
import com.buddhanag.luminux.data.ThumbnailPrefetcher
import com.buddhanag.luminux.designsystem.LiveTileScheduler
import com.buddhanag.luminux.feature.shell.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual DI. Billing, ads and news join from spec 09. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LuminuxDatabase = Room.databaseBuilder(context, LuminuxDatabase::class.java, "luminux.db").build()

    val settings = SettingsRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") })

    val photos: PhotoRepository = MediaStorePhotoRepository(context, database.albumDao(), settings, appScope)

    val thumbnailPrefetcher by lazy { ThumbnailPrefetcher(context, SingletonImageLoader.get(context)) }

    val liveTileStore = LiveTileStore(context.filesDir, PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("live_tile") })

    val liveTileExporter by lazy {
        LiveTileExporter(context, SingletonImageLoader.get(context), photos, liveTileStore, photoSet = { null })
    }

    val navigator = Navigator()

    /** One clock for every live tile, so they take turns flipping. */
    val liveTileScheduler by lazy { LiveTileScheduler(MainScope()) }

    // shortcut: nobody has bought Pro until spec 09's ProStore replaces these; debug builds count as test builds.
    val isProUnlocked: StateFlow<Boolean> = MutableStateFlow(false)
    val isTestBuild = BuildConfig.DEBUG

    init {
        startLiveTileExport()
    }

    /** 4 s after the library or accent last changed, so the hub finishes opening before the photo work starts. */
    @OptIn(FlowPreview::class)
    private fun startLiveTileExport() = appScope.launch(Dispatchers.IO) {
        combine(photos.snapshot.filter { it.isLoaded }.map { it.generation }, settings.settings.map { it.accentHex }, ::Pair)
            .distinctUntilChanged()
            .debounce(4_000)
            .collectLatest { liveTileExporter.export() }
    }
}
