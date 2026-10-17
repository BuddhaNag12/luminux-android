package com.buddhanag.luminux.feature.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import java.io.File

/** The VisionKit subject lift, with ML Kit subject segmentation on a ≤1024 px copy of the photo. */
internal object SubjectLift {
    const val MAX_SIDE = 1024

    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }

    /** The model comes through Play services; asking early means the first lift doesn't fail with "module not available". */
    fun prepare(context: Context) {
        ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build())
    }

    /** The subjects cut out on a transparent background at the photo's size, or null when there are none. */
    suspend fun lift(photo: Bitmap): Bitmap? =
        runCatching { segmenter.process(InputImage.fromBitmap(photo, 0)).await().foregroundBitmap }.getOrNull()

    fun copy(context: Context, subject: Bitmap) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "subject", export(context, subject)))
    }

    fun share(context: Context, subject: Bitmap) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, export(context, subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }

    /** A PNG in the cache, shared through the FileProvider; each lift replaces the last. */
    private fun export(context: Context, subject: Bitmap): Uri {
        val file = File(context.cacheDir, "lift/subject.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { subject.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}

/**
 * The lifted subject over its dimmed photo, drawn where the photo sits at 1× (both fit the same box), with a soft
 * white glow and `copy · share`. A tap anywhere puts it down.
 */
@Composable
internal fun LiftedSubject(subject: Bitmap, onPutDown: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(subject) { subject.asImageBitmap() }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onPutDown),
    ) {
        Image(
            image, null, Modifier.fillMaxSize().blur(10.dp),
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.7f), BlendMode.SrcIn),
        )
        Image(image, "Lifted subject", Modifier.fillMaxSize())
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 40.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TextButton("copy", { SubjectLift.copy(context, subject); onPutDown() })
            TextButton("share", { SubjectLift.share(context, subject); onPutDown() })
        }
    }
}
