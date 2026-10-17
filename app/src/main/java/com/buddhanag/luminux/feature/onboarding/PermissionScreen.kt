package com.buddhanag.luminux.feature.onboarding

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MEDIA_PERMISSIONS
import com.buddhanag.luminux.data.MediaAccess
import com.buddhanag.luminux.data.appSettingsIntent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LogoLockup
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.feature.shell.LocalLaunchSplashShowing

/** Shown once the tour has been seen but photos can't be read yet (PermissionView.swift). */
@Composable
fun PermissionScreen() {
    val metro = LocalMetro.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val photos = context.container.photos
    val access by photos.access.collectAsStateWithLifecycle()
    val isDenied = access == MediaAccess.DENIED
    val request = rememberLauncherForActivityResult(RequestMultiplePermissions()) { activity?.let(photos::onPermissionResult) }

    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 16.dp),
    ) {
        LogoLockup(metro.background, Modifier.padding(top = 24.dp), isSplashShowing = LocalLaunchSplashShowing.current)
        MetroText(if (isDenied) "access is off" else "your photos, front and centre", MetroType.section, Modifier.padding(top = 32.dp))
        MetroText(
            if (isDenied) "To browse your photos here, turn on photo access for Luminux in your phone's settings."
            else "Luminux shows the photos and videos already on this phone. Your photos never leave it; only to name places in your journal does it ask your phone's map service about photo locations, and you can turn that off in settings.",
            MetroType.body, Modifier.padding(top = 12.dp), color = metro.secondary,
        )
        Spacer(Modifier.weight(1f))
        MetroButton(
            if (isDenied) "open settings" else "allow access",
            { if (isDenied) context.startActivity(appSettingsIntent(context)) else request.launch(MEDIA_PERMISSIONS) },
            Modifier.padding(bottom = 24.dp),
        )
    }
}
