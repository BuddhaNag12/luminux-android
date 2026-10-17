package com.buddhanag.luminux.feature.viewer

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImage
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaThumb
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem as PlayerItem

/** Plays the viewer's current video (VideoPlayback.swift). One player per viewer, swapped on every page change. */
@OptIn(UnstableApi::class) // seek tolerance
@Stable
internal class VideoPlayback(private val context: Context) {
    var assetId by mutableStateOf<Long?>(null)
        private set
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    /** The player has loaded enough to play; the play controls wait for it. */
    var isReady by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isScrubbing by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0)
        private set
    /** Updated about 30 times a second while playing; only the controls row should read it. */
    var positionMs by mutableLongStateOf(0)
        private set
    private var resumesAfterScrub = false

    /** Switches to [item], or stops playback when it's null or not a video. */
    fun load(item: MediaItem?) {
        val video = item?.takeIf { it.type == MediaType.VIDEO }
        if (video?.id == assetId) return
        unload()
        video ?: return
        assetId = video.id
        durationMs = video.durationMs
        player = ExoPlayer.Builder(context).build().apply {
            // Plays sound even with the ringer silenced, like the Photos app; pauses for calls and other audio.
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            volume = if (isMuted) 0f else 1f
            setMediaItem(PlayerItem.fromUri(video.contentUri))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        isReady = true
                        if (duration > 0) durationMs = duration
                    }
                    if (state == Player.STATE_ENDED) {
                        // Rewind and show the play button again, as the original did.
                        playWhenReady = false
                        seek(0, exact = true)
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    // Audio focus loss pauses the player; a scrub's own pause isn't a stop.
                    if (!isScrubbing) this@VideoPlayback.isPlaying = playWhenReady
                }
            })
        }
    }

    fun togglePlay() {
        val player = player ?: return
        player.playWhenReady = !isPlaying
    }

    fun toggleMute() {
        isMuted = !isMuted
        player?.volume = if (isMuted) 0f else 1f
    }

    fun scrub(ms: Long) {
        if (!isScrubbing) {
            isScrubbing = true
            resumesAfterScrub = isPlaying
            player?.pause()
        }
        seek(ms, exact = false)
    }

    fun endScrub(ms: Long) {
        seek(ms, exact = true)
        isScrubbing = false
        if (resumesAfterScrub) player?.play()
    }

    fun skip(byMs: Long) = seek(positionMs + byMs, exact = true)

    /** Reads the position while playing, at the iOS 1/30 s observer rate. */
    suspend fun trackPosition() {
        while (true) {
            if (!isScrubbing) player?.let { positionMs = it.currentPosition }
            delay(33)
        }
    }

    private fun seek(ms: Long, exact: Boolean) {
        positionMs = ms.coerceIn(0, durationMs.coerceAtLeast(0))
        // ±0.1 s while dragging, exact on release.
        player?.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters(100_000, 100_000))
        player?.seekTo(positionMs)
    }

    fun unload() {
        player?.release()
        player = null
        assetId = null
        isReady = false
        isPlaying = false
        isScrubbing = false
        positionMs = 0
        durationMs = 0
    }
}

/** Poster, then the video once loaded, with the center play button while it isn't playing. */
@OptIn(UnstableApi::class) // ContentFrame
@Composable
internal fun VideoPage(item: MediaItem, isCurrent: Boolean, playback: VideoPlayback, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val isLoaded = isCurrent && playback.assetId == item.id && playback.isReady
    val player = playback.player
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(MediaThumb(item.contentUri, 1536), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        // No shutter: the frame stays clear until its first picture, so the poster shows through while loading.
        if (isCurrent && playback.assetId == item.id && player != null) {
            ContentFrame(player, Modifier.fillMaxSize(), SURFACE_TYPE_TEXTURE_VIEW, ContentScale.Fit, shutter = {})
        }
        AnimatedVisibility(!(isLoaded && playback.isPlaying), enter = fadeIn(MetroMotion.fade()), exit = fadeOut(MetroMotion.fade())) {
            Box(
                Modifier
                    .size(76.dp)
                    .alpha(if (isLoaded) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(enabled = isLoaded, role = Role.Button) { playback.togglePlay() }
                    .semantics { contentDescription = "Play" },
                contentAlignment = Alignment.Center,
            ) {
                Image(ViewerIcons.play, null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
}

/** Mute, elapsed and remaining time and a scrubber, on top of the viewer's app bar (AirPlay is dropped). */
@Composable
internal fun VideoControlsRow(playback: VideoPlayback, modifier: Modifier = Modifier) {
    val metro = LocalMetro.current
    val digits = MetroType.caption.copy(fontFeatureSettings = "tnum")
    Row(
        modifier
            .fillMaxWidth()
            .background(metro.chrome)
            .padding(horizontal = MetroMetrics.margin + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp, 44.dp)
                .clickable(role = Role.Button) { playback.toggleMute() }
                .semantics { contentDescription = if (playback.isMuted) "Unmute" else "Mute" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                if (playback.isMuted) ViewerIcons.speakerSlash else ViewerIcons.speaker, null, Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(metro.foreground),
            )
        }
        MetroText(DetailsFormat.time(playback.positionMs / 1000.0), digits, Modifier.clearAndSetSemantics {})
        Scrubber(playback, Modifier.weight(1f))
        MetroText(
            DetailsFormat.remaining(playback.durationMs / 1000.0, playback.positionMs / 1000.0), digits,
            Modifier.clearAndSetSemantics {},
        )
    }
}

/** Thin track with an accent fill and a narrow thumb; drag anywhere on it to scrub. */
@Composable
private fun Scrubber(playback: VideoPlayback, modifier: Modifier = Modifier) {
    val metro = LocalMetro.current
    val duration = playback.durationMs
    val progress = if (duration > 0) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var width by remember { mutableFloatStateOf(1f) }
    fun msAt(x: Float) = ((x / width).coerceIn(0f, 1f) * duration).toLong()
    Canvas(
        modifier
            .height(44.dp)
            .pointerInput(playback) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    width = size.width.toFloat()
                    playback.scrub(msAt(down.position.x))
                    var last = down.position.x
                    drag(down.id) { change ->
                        last = change.position.x
                        change.consume()
                        playback.scrub(msAt(last))
                    }
                    playback.endScrub(msAt(last))
                }
            }
            .semantics {
                contentDescription = "Position"
                stateDescription = "${DetailsFormat.time(playback.positionMs / 1000.0)} of ${DetailsFormat.time(duration / 1000.0)}"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                // Swiping up or down with TalkBack skips 5 s, like the iOS adjustable action.
                setProgress { target ->
                    playback.skip(if (target > progress) 5_000 else -5_000)
                    true
                }
            },
    ) {
        val track = 2.dp.toPx()
        val midY = size.height / 2
        drawRect(metro.secondary.copy(alpha = 0.4f), Offset(0f, midY - track / 2), Size(size.width, track))
        drawRect(metro.accentColor, Offset(0f, midY - track / 2), Size(size.width * progress, track))
        val thumbH = (if (playback.isScrubbing) 20 else 14).dp.toPx()
        drawRect(metro.foreground, Offset(size.width * progress - 2.dp.toPx(), midY - thumbH / 2), Size(4.dp.toPx(), thumbH))
    }
}

/** Keeps [VideoPlayback.positionMs] current while the video plays. */
@Composable
internal fun TrackVideoPosition(playback: VideoPlayback) {
    LaunchedEffect(playback.player, playback.isPlaying) {
        if (playback.isPlaying) playback.trackPosition()
    }
}
