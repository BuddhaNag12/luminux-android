package com.buddhanag.luminux.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

enum class MediaAccess {
    NOT_DETERMINED, FULL, LIMITED, DENIED;

    val canRead: Boolean get() = this == FULL || this == LIMITED
}

/**
 * Android has no "not determined", so it is derived. Pure, so it can be tested.
 * [askedBefore] is the only persisted state ([SettingsKeys.askedMediaAccess]);
 * [canAskAgain] is `shouldShowRequestPermissionRationale(READ_MEDIA_IMAGES)`.
 */
fun mediaAccess(images: Boolean, video: Boolean, userSelected: Boolean, askedBefore: Boolean, canAskAgain: Boolean) = when {
    images || video -> MediaAccess.FULL
    userSelected -> MediaAccess.LIMITED // Android 14+ partial access
    !askedBefore || canAskAgain -> MediaAccess.NOT_DETERMINED
    else -> MediaAccess.DENIED
}

/** Launch all four in one `RequestMultiplePermissions`; under LIMITED, launching again shows the reselect sheet. */
// The API 34 constant is a plain string; Android 13 just ignores the unknown permission.
@SuppressLint("InlinedApi")
val MEDIA_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    Manifest.permission.ACCESS_MEDIA_LOCATION,
)

internal fun Context.has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** The app's page in system settings, for "open settings" when access is denied. */
fun appSettingsIntent(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
