package com.buddhanag.luminux.feature.viewer

import android.app.Activity
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.EditRecipe
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroAppBar
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroPalette
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroThemeMode
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.feature.shell.Navigator
import com.buddhanag.luminux.feature.shell.ViewerRequest
import com.buddhanag.luminux.feature.viewer.editor.EditorScreen
import com.buddhanag.luminux.feature.viewer.slideshow.SlideshowScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class ProOffer(val title: String, val action: () -> Unit)

/**
 * Full-screen photo and video viewer (ViewerView.swift): swipe paging, zoom, a drag down to close and up for
 * details, and the Metro app bar.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ViewerScreen(request: ViewerRequest, navigator: Navigator, zoom: ViewerZoom, visibility: AnimatedVisibilityScope) {
    val context = LocalContext.current
    val view = LocalView.current
    val container = context.container
    val photos = container.photos
    val scope = rememberCoroutineScope()
    val snapshot by photos.snapshot.collectAsStateWithLifecycle()
    val assets = remember(snapshot, request.source) { photos.items(request.source) }
    // The pager's count and key lambdas outlive a composition; both read the newest list, or a new copy indexes past the end.
    val latestAssets by rememberUpdatedState(assets)
    val isProUnlocked by container.isProUnlocked.collectAsStateWithLifecycle()
    val pager = rememberPagerState(
        initialPage = remember { assets.indexOfFirst { it.id == request.startId }.takeIf { it >= 0 } ?: request.startIndex },
    ) { latestAssets.size }
    val current = assets.getOrNull(pager.settledPage)
    // Another request (or none) means this viewer is fading out.
    val isClosing = navigator.viewer !== request

    var showsChrome by remember { mutableStateOf(true) }
    var appBarExpanded by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    /** True after the Pro offer unlocked subject lifting for this visit. */
    var liftUnlocked by remember { mutableStateOf(false) }
    var lifted by remember { mutableStateOf<Bitmap?>(null) }
    /** Favourite state shown before MediaStore reports the change, so the heart answers the tap at once. */
    var pendingFavorites by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    var favoriteInFlight by remember { mutableStateOf<Long?>(null) }
    /** Quick-rotate turns for one photo, saved as one copy when the page changes or the viewer closes. */
    var rotation by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    var slideshowStart by remember { mutableStateOf<Int?>(null) }
    var proOffer by remember { mutableStateOf<ProOffer?>(null) }
    val playback = remember { VideoPlayback(context.applicationContext) }
    TrackVideoPosition(playback)

    var dismiss by remember { mutableFloatStateOf(0f) }
    /** How much of the details panel shows, in dp; the panel is open when this rests at the panel height. */
    val lift = remember { Animatable(0f) }
    var detailsOpen by remember { mutableStateOf(false) }
    /** Stays true until the close animation finishes, so the panel can slide away. */
    var panelMounted by remember { mutableStateOf(false) }
    val chromeVisible = showsChrome && dismiss == 0f && !panelMounted
    // A function, not a value: Telephoto keeps the first long-press lambda, so it must read the state when called.
    fun allowsLift() = isProUnlocked || liftUnlocked

    fun close() {
        if (!isClosing) navigator.viewer = null
    }

    fun commitRotation() {
        val (id, turns) = rotation ?: return
        rotation = null
        val item = photos.item(id)?.takeIf { turns % 4 != 0 } ?: return
        container.appScope.launch { runCatching { photos.saveEditedCopy(item, EditRecipe(quarterTurns = turns % 4)) } }
    }

    /** Runs a Pro feature, or offers it once next to what Pro adds; the action runs after that page closes. */
    fun withPro(trialTitle: String, action: () -> Unit) {
        if (isProUnlocked) action() else proOffer = ProOffer(trialTitle, action)
    }

    DisposableEffect(Unit) {
        onDispose {
            commitRotation()
            playback.unload()
            zoom.currentId = null
        }
    }
    LaunchedEffect(isClosing) { if (isClosing) playback.unload() }
    LaunchedEffect(current?.id) {
        val item = current ?: return@LaunchedEffect
        isZoomed = false
        lifted = null
        navigator.viewerCurrentId = item.id
        zoom.currentId = item.id
        if (rotation?.first != item.id) commitRotation()
        if (!isClosing) playback.load(item)
    }
    LaunchedEffect(assets) {
        // After a delete the pager keeps its position (clamped); nothing left closes the viewer.
        if (snapshot.isLoaded && assets.isEmpty()) close()
        // MediaStore has caught up with the favourite taps.
        pendingFavorites = pendingFavorites.filter { (id, value) -> snapshot.byId[id]?.let { it.isFavorite != value } ?: false }
    }
    LaunchedEffect(allowsLift()) { if (allowsLift()) runCatching { SubjectLift.prepare(context) } }
    // Like the original player, the controls get out of the way a moment after playback starts.
    LaunchedEffect(playback.isPlaying && showsChrome && !playback.isScrubbing) {
        if (!playback.isPlaying || !showsChrome || playback.isScrubbing) return@LaunchedEffect
        delay(2_500)
        if (!appBarExpanded) showsChrome = false
    }
    LaunchedEffect(chromeVisible) { if (!chromeVisible) appBarExpanded = false }
    SystemBarsHidden(!showsChrome)

    val favoriteLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        val id = favoriteInFlight
        if (result.resultCode != Activity.RESULT_OK && id != null) pendingFavorites = pendingFavorites - id
        favoriteInFlight = null
    }
    val trashLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) {}

    suspend fun liftSubject(item: MediaItem) = runCatching { SubjectLift.lift(photos.editPreview(item, SubjectLift.MAX_SIDE)) }.getOrNull()

    val outerPalette = LocalMetro.current
    CompositionLocalProvider(LocalMetro provides MetroPalette(MetroThemeMode.Dark, outerPalette.accent)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelHeight = (maxHeight.value * 0.55f).roundToInt().toFloat()
            fun rubberBand(l: Float) = if (l <= panelHeight) max(l, 0f) else panelHeight + (l - panelHeight) * 0.2f

            fun setDetails(open: Boolean) {
                detailsOpen = open
                appBarExpanded = false
                if (open) panelMounted = true
                scope.launch {
                    lift.animateTo(if (open) panelHeight else 0f, MetroMotion.standard())
                    if (!detailsOpen) panelMounted = false
                }
            }

            // Down past the start closes the viewer, up reveals details; with details open, the same drag moves the panel.
            fun onDrag(dy: Float) {
                when {
                    detailsOpen -> scope.launch { lift.snapTo(rubberBand(panelHeight - dy)) }
                    dy > 0 -> {
                        scope.launch { lift.snapTo(0f) }
                        panelMounted = false
                        dismiss = dy
                    }
                    else -> {
                        dismiss = 0f
                        val l = rubberBand(-dy)
                        scope.launch { lift.snapTo(l) }
                        panelMounted = l > 0
                    }
                }
            }

            fun onDragEnd(dy: Float, vy: Float) {
                when {
                    detailsOpen -> setDetails(!(dy > panelHeight * 0.3f || vy > 700))
                    dismiss > 0 -> if (dy > 140 || vy > 900) close() else scope.launch {
                        animate(dismiss, 0f, animationSpec = MetroMotion.release()) { v, _ -> dismiss = v }
                    }
                    else -> setDetails(-dy > 80 || vy < -700)
                }
            }

            fun onTap() {
                if (detailsOpen) setDetails(false) else showsChrome = !showsChrome
            }

            fun onLongPress(item: MediaItem) {
                if (!allowsLift()) return withPro("lift a subject once") { liftUnlocked = true }
                scope.launch { liftSubject(item)?.let { if (latestAssets.getOrNull(pager.settledPage)?.id == item.id) lifted = it } }
            }

            fun copySubject(item: MediaItem) {
                if (!allowsLift()) return withPro("lift a subject once") { liftUnlocked = true }
                scope.launch {
                    val subject = liftSubject(item)
                    subject?.let { SubjectLift.copy(context, it) }
                    @Suppress("DEPRECATION") // still the simplest one-off announcement; live regions need a visible node
                    view.announceForAccessibility(if (subject != null) "Subject copied" else "No subject found")
                }
            }

            BackHandler(enabled = !isClosing) {
                when {
                    panelMounted -> setDetails(false)
                    lifted != null -> lifted = null
                    else -> close()
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1 - min(dismiss / 500, 0.8f))))

            Box(
                Modifier
                    .fillMaxSize()
                    .verticalDragToDismiss(enabled = !isZoomed && lifted == null && !isClosing, ::onDrag, ::onDragEnd)
                    .graphicsLayer {
                        val scale = 1 - min(dismiss / 1600, 0.25f)
                        scaleX = scale
                        scaleY = scale
                        // Keeps the photo centred in the space left above the details panel.
                        translationY = (dismiss - min(lift.value, panelHeight) / 2).dp.toPx()
                    },
            ) {
                HorizontalPager(
                    pager,
                    Modifier.fillMaxSize(),
                    userScrollEnabled = lifted == null,
                    key = { latestAssets.getOrNull(it)?.id ?: it },
                ) { page ->
                    val item = latestAssets.getOrNull(page) ?: return@HorizontalPager
                    val isCurrent = page == pager.settledPage
                    val zoomTarget = if (isCurrent) {
                        with(zoom.scope) { Modifier.sharedElement(rememberSharedContentState(zoomKey(item.id)), visibility) }
                    } else Modifier
                    if (item.type == MediaType.VIDEO) {
                        VideoPage(item, isCurrent, playback, ::onTap, zoomTarget)
                    } else {
                        PhotoPage(
                            item, isCurrent,
                            resetsZoom = isClosing || lifted != null,
                            quarterTurns = rotation?.takeIf { it.first == item.id }?.second ?: 0,
                            onTap = ::onTap,
                            onLongPress = { onLongPress(item) },
                            onZoomChange = { if (page == pager.settledPage) isZoomed = it },
                            onCopySubject = { copySubject(item) },
                            modifier = zoomTarget,
                        )
                    }
                }
                lifted?.let { LiftedSubject(it, onPutDown = { lifted = null }) }
            }

            AnimatedVisibility(
                chromeVisible && current != null, Modifier.align(Alignment.TopStart),
                enter = fadeIn(MetroMotion.fade()), exit = fadeOut(MetroMotion.fade()),
            ) {
                current?.effectiveDate?.let { date ->
                    MetroText(
                        chromeDate(date), MetroType.caption,
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                            .padding(horizontal = MetroMetrics.margin + 12.dp)
                            .padding(top = 8.dp),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }

            if (panelMounted && current != null) {
                key(current.id) {
                    DetailsPanel(
                        current, ::onDrag, ::onDragEnd, onClose = { setDetails(false) },
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(max(panelHeight, lift.value).dp)
                            .offset { IntOffset(0, max(panelHeight - lift.value, 0f).dp.roundToPx()) },
                    )
                }
            }

            if (appBarExpanded) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { appBarExpanded = false } })
            }
            AnimatedVisibility(
                chromeVisible && current != null, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(MetroMotion.fade()) { it } + fadeIn(MetroMotion.fade()),
                exit = slideOutVertically(MetroMotion.fade()) { it } + fadeOut(MetroMotion.fade()),
            ) {
                val item = current ?: return@AnimatedVisibility
                val isFavorite = pendingFavorites[item.id] ?: item.isFavorite
                val buttons = buildList {
                    if (item.type == MediaType.VIDEO) {
                        add(AppBarButton(
                            if (playback.isPlaying) "pause" else "play",
                            if (playback.isPlaying) ViewerIcons.pause else ViewerIcons.play,
                            isEnabled = playback.isReady, id = "playPause",
                        ) { playback.togglePlay() })
                    }
                    add(AppBarButton("share", ViewerIcons.share) { context.startActivity(photos.shareIntent(listOf(item))) })
                    add(AppBarButton(
                        if (isFavorite) "unfavorite" else "favorite", if (isFavorite) ViewerIcons.heartFill else ViewerIcons.heart,
                        id = "favorite",
                    ) {
                        pendingFavorites = pendingFavorites + (item.id to !isFavorite)
                        favoriteInFlight = item.id
                        favoriteLauncher.launch(photos.favoriteRequest(listOf(item), !isFavorite))
                    })
                    if (item.isEditable) {
                        add(AppBarButton("edit", ViewerIcons.edit) { withPro("edit this photo once") { editing = item } })
                    }
                    add(AppBarButton("delete", ViewerIcons.trash) { trashLauncher.launch(photos.trashRequest(listOf(item))) })
                }
                val menu = buildList {
                    if (item.isEditable) {
                        add(AppBarMenuItem("rotate") {
                            val turns = rotation?.takeIf { it.first == item.id }?.second ?: 0
                            val rotate = { rotation = item.id to turns + 1 }
                            // One copy per photo, so only its first turn is gated.
                            if (turns == 0) withPro("rotate this photo once", rotate) else rotate()
                        })
                    }
                    // shortcut: spec 06's AddToAlbumSheet opens here once the grid lands; until then the item does nothing.
                    add(AppBarMenuItem("add to album") {})
                    add(AppBarMenuItem("slideshow") { withPro("play a slideshow once") { slideshowStart = pager.settledPage } })
                    add(AppBarMenuItem("details") { setDetails(true) })
                }
                Column {
                    if (item.type == MediaType.VIDEO) VideoControlsRow(playback)
                    MetroAppBar(buttons, appBarExpanded, { appBarExpanded = it }, menuItems = menu)
                }
            }
        }
    }

    CompositionLocalProvider(LocalMetro provides outerPalette) {
        editing?.let { item -> EditorScreen(item, onClose = { editing = null }) }
    }
    slideshowStart?.let { start -> SlideshowScreen(assets, start, onClose = { slideshowStart = null }) }
    proOffer?.let { offer ->
        ProOfferPage(
            offer.title,
            onTrial = {
                proOffer = null
                offer.action()
            },
            onClose = { proOffer = null },
        )
    }
}

/** "saturday, october 3, 2026 at 3:05 pm" (weekday, month, day, year, hour, minute; lowercased like iOS). */
private fun chromeDate(ms: Long): String {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMdyyyyjmm")
    return DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())).lowercase()
}

/**
 * The viewer's one vertical drag, for closing and for the details panel (`VerticalDragRecognizer`). It starts only
 * on a mostly vertical move (|dy| > |dx| × 1.2), so paging and zoomed panning keep their touches, and lets go of
 * pinches. Travel and velocity reach the handlers in dp and dp/s, positive downward.
 */
private fun Modifier.verticalDragToDismiss(enabled: Boolean, onDrag: (Float) -> Unit, onEnd: (Float, Float) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var decided = false
            var vertical = false
            val start = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                if (!decided) {
                    decided = true
                    val total = change.position - down.position
                    vertical = currentEvent.changes.size == 1 && abs(total.y) > abs(total.x) * 1.2f
                }
                if (vertical) change.consume()
            } ?: return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(start)
            var dy = (start.position - down.position).y
            onDrag(dy.toDp().value)
            verticalDrag(start.id) { change ->
                if (currentEvent.changes.size > 1) return@verticalDrag
                dy += change.positionChange().y
                tracker.addPointerInputChange(change)
                change.consume()
                onDrag(dy.toDp().value)
            }
            onEnd(dy.toDp().value, tracker.calculateVelocity().y.toDp().value)
        }
    }
