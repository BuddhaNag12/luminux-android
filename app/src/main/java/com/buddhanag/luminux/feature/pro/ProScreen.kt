package com.buddhanag.luminux.feature.pro

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.settings.PageHeader
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp

/** A locked feature the Pro page offers to unlock once for a rewarded ad, e.g. "play a slideshow once". */
class ProTrial(val title: String, val onEarned: () -> Unit)

/**
 * The Pro page (ProView.swift), pushed from settings, the hub and `luminux://pro`, or shown over the viewer with a
 * [trial] and [onClose]; a pushed page goes back with the usual swipe.
 */
@Composable
fun ProScreen(trial: ProTrial? = null, onClose: (() -> Unit)? = null) {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val isUnlocked by container.proStore.isUnlocked.collectAsStateWithLifecycle()
    onClose?.let { BackHandler(onBack = it) }
    LaunchedEffect(Unit) {
        if (trial == null || isUnlocked) return@LaunchedEffect
        container.rewardedAds.clearMessage()
        // Loads ahead only once consent was settled this launch, so opening the page never contacts Google.
        container.rewardedAds.preload()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 16.dp, bottom = 40.dp),
    ) {
        PageHeader("pro", Modifier.metroFeather(row = 0).padding(bottom = 24.dp))
        if (trial != null && !isUnlocked) TryOnce(trial, onClose, Modifier.metroFeather(row = 1).padding(bottom = 24.dp))
        ProPitch(firstRow = if (trial == null) 1 else 2)
        // With an ad offer, "not now" sits beside it instead.
        if (onClose != null && (isUnlocked || trial == null)) {
            MetroButton(if (isUnlocked) "done" else "not now", onClose, Modifier.padding(top = 32.dp))
        }
    }
}

/** The quick way in when the page opened from a locked feature: one ad for one use. */
@Composable
private fun TryOnce(trial: ProTrial, onClose: (() -> Unit)?, modifier: Modifier) {
    val ads = LocalContext.current.container.rewardedAds
    val metro = LocalMetro.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val isLoading by ads.isLoading.collectAsStateWithLifecycle()
    val message by ads.message.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxWidth().border(1.dp, metro.foreground.copy(alpha = 0.35f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(trial.title, metroFont(20.sp, Semilight))
        MetroText("Watch a short ad to use it now, or unlock Pro below and never see one.", MetroType.caption, color = metro.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(
                if (isLoading) "loading ad…" else "watch an ad",
                { if (activity != null && !isLoading) scope.launch { if (ads.watch(activity)) trial.onEarned() } },
                Modifier.defaultMinSize(minHeight = 56.dp),
            )
            if (onClose != null) MetroButton("not now", onClose, Modifier.weight(1f).defaultMinSize(minHeight = 56.dp))
        }
        message?.let { MetroText(it, MetroType.caption, color = metro.accentColor) }
    }
}
