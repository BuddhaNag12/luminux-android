package com.buddhanag.luminux.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The paths every session takes: launch, the hub's panorama, then the camera roll grid scrolled both ways. The device
 * needs photo access granted and the tour already seen (open the app once by hand first).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun hubAndGrid() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        device.executeShellCommand("pm grant $PACKAGE android.permission.READ_MEDIA_IMAGES")
        device.executeShellCommand("pm grant $PACKAGE android.permission.READ_MEDIA_VIDEO")
        pressHome()
        startActivityAndWait()

        val cameraRoll = device.wait(Until.findObject(By.desc("camera roll")), 10_000) ?: error("The hub didn't show: is the tour done?")
        // The panorama: out to the last panels and back.
        repeat(2) { device.swipe(device.displayWidth * 9 / 10, device.displayHeight / 2, device.displayWidth / 10, device.displayHeight / 2, 20) }
        repeat(2) { device.swipe(device.displayWidth / 10, device.displayHeight / 2, device.displayWidth * 9 / 10, device.displayHeight / 2, 20) }
        device.waitForIdle()

        device.wait(Until.findObject(By.desc("camera roll")), 5_000)?.click() ?: cameraRoll.click()
        // The collection pivot's first page.
        device.wait(Until.hasObject(By.text("all")), 5_000)
        device.waitForIdle()
        // The grid: a few flings down and back up.
        repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 5, 8) }
        repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight / 5, device.displayWidth / 2, device.displayHeight * 4 / 5, 8) }
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.buddhanag.luminux"
    }
}
