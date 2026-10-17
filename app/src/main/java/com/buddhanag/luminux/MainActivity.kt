package com.buddhanag.luminux

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.buddhanag.luminux.feature.shell.LuminuxRoot

class MainActivity : ComponentActivity() {
    /** A `luminux://` link waiting for the shell, which opens it once it's showing. */
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity has already handled its launch link.
        if (savedInstanceState == null) pendingDeepLink.value = intent.data
        // Debug builds: `adb shell am start -n com.buddhanag.luminux/.MainActivity --ez onboarding true` shows the tour.
        val forcesOnboarding = BuildConfig.DEBUG && intent.getBooleanExtra("onboarding", false)
        setContent { LuminuxRoot(pendingDeepLink, forcesOnboarding) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { pendingDeepLink.value = it }
    }
}
